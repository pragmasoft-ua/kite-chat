/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft;

import java.nio.file.Path;
import java.util.List;

public class ClassPreloader {
  public static void main(String[] args) {
    String logGroupNames = System.getProperty("groupNames");
    List<String> groupNames = List.of(logGroupNames.replace("\"", "").split(" "));
    Path output =
        Path.of(
            System.getProperty(
                "output",
                "../k1te-serverless/src/main/resources/META-INF/quarkus-preload-classes.txt"));
    Integer limit = Integer.parseInt(System.getProperty("limit", "3"));

    LogService logService = new LogService(groupNames, output, limit);
    logService.generateClasses();
  }
}
