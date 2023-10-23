/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.logs.model.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogService {

  private static final Logger log = LoggerFactory.getLogger(LogService.class);
  private static final String FILTER_PATTERN = "class load";
  private static final Pattern PATTERN = Pattern.compile("(\\[.+]\\[.+]\\[class.+]) ([\\w.$/]+)");

  private final AWSLogs client;
  private final Set<String> result = new HashSet<>();

  private final List<String> groupNames;
  private final Path outputFile;
  private final Integer maxLogStreamLimit;

  public LogService(List<String> groupNames, Path outputFile, Integer maxLogStreamLimit) {
    Objects.requireNonNull(groupNames);
    Objects.requireNonNull(outputFile);
    Objects.requireNonNull(maxLogStreamLimit);

    this.groupNames = groupNames;
    this.outputFile = outputFile;
    this.maxLogStreamLimit = maxLogStreamLimit;
    this.client = AWSLogsClientBuilder.defaultClient();
  }

  public void generateClasses() {
    for (String groupName : groupNames) {
      if (groupName.isEmpty()) {
        throw new IllegalStateException("LogGroupName can't be empty");
      }
      extractLogs(groupName);
    }
    writeToFile();
    log.debug(
        "{} unique classes were successfully generated, you can find them -> {}",
        result.size(),
        outputFile);
  }

  private void extractLogs(String groupName) {
    log.debug("{} is under process", groupName);
    List<String> streamNames = getLogStreamNames(groupName);
    log.debug("For LogGroup {} were found the following LogStreams \n{}", groupName, streamNames);

    StringBuilder builder = new StringBuilder();
    String nextToken = null;
    log.debug("Start retrieving log events from {}", groupName);
    long start = System.currentTimeMillis();
    do {
      FilterLogEventsRequest filterLogEventsRequest =
          new FilterLogEventsRequest()
              .withLogGroupName(groupName)
              .withLogStreamNames(streamNames)
              .withFilterPattern(FILTER_PATTERN)
              .withNextToken(nextToken);

      FilterLogEventsResult logEventsResult = client.filterLogEvents(filterLogEventsRequest);
      nextToken = logEventsResult.getNextToken();
      for (var event : logEventsResult.getEvents()) {
        builder.append(event.getMessage());
      }
    } while (nextToken != null);
    long finish = System.currentTimeMillis() - start;
    log.debug(
        "All logs for LogGroup: {} which match Filter Pattern were extracted. It took {}ms",
        groupName,
        finish);

    Matcher matcher = PATTERN.matcher(builder);
    while (matcher.find()) {
      this.result.add(matcher.group(2));
    }
  }

  private void writeToFile() {
    try {
      if (!Files.exists(this.outputFile)) {
        Files.createFile(this.outputFile);
        log.debug("{} was created", this.outputFile);
      }
      log.debug("Writing classes to the File");
      Files.write(this.outputFile, result, StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private List<LogStream> getLogStreams(String groupName) {
    DescribeLogStreamsResult streamsResult =
        client.describeLogStreams(
            new DescribeLogStreamsRequest()
                .withLogGroupName(groupName)
                .withOrderBy(OrderBy.LastEventTime)
                .withDescending(true)
                .withLimit(maxLogStreamLimit));
    return streamsResult.getLogStreams();
  }

  private List<String> getLogStreamNames(String groupName) {
    return getLogStreams(groupName).stream().map(LogStream::getLogStreamName).toList();
  }
}
