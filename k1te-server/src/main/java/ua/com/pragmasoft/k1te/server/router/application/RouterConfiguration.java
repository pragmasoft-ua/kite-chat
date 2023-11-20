/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.server.router.application;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import ua.com.pragmasoft.k1te.backend.router.domain.*;
import ua.com.pragmasoft.k1te.backend.router.infrastructure.DynamoDbChannels;
import ua.com.pragmasoft.k1te.backend.router.infrastructure.DynamoDbMessages;

public class RouterConfiguration {

  @Produces
  @ApplicationScoped
  @DefaultBean
  public Channels channels(
      DynamoDbEnhancedClient ddb,
      @ConfigProperty(name = "serverless.environment") final String serverlessEnvironmentName) {
    return new DynamoDbChannels(ddb, serverlessEnvironmentName);
  }

  @Produces
  @ApplicationScoped
  @DefaultBean
  public Messages messages(
      DynamoDbEnhancedClient ddb,
      @ConfigProperty(name = "serverless.environment") final String serverlessEnvironmentName) {
    return new DynamoDbMessages(ddb, serverlessEnvironmentName);
  }

  @Produces
  @Dependent
  public PeerUpdatePostProcessor peerUpdatePostProcessor(Channels channels) {
    return new PeerUpdatePostProcessor(channels);
  }

  @Produces
  @Dependent
  public RouterPostProcessor historyPostProcessor(Channels channels, Messages messages) {
    return new HistoryPostProcessor(channels, messages);
  }

  @Produces
  @ApplicationScoped
  public Router router(
      Channels channels, Messages messages, Instance<RouterPostProcessor> postProcessors) {
    return new KiteRouter(channels, messages, postProcessors.stream().toList());
  }
}
