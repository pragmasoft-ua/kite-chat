/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package ua.com.pragmasoft.k1te.server.router.application;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.*;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import ua.com.pragmasoft.k1te.backend.router.domain.*;
import ua.com.pragmasoft.k1te.backend.router.infrastructure.DynamoDbChannels;
import ua.com.pragmasoft.k1te.backend.router.infrastructure.DynamoDbMessages;

public class RouterConfiguration {

  @Produces
  @RequestScoped
  @DefaultBean
  public DynamoDbChannels channels(
      DynamoDbEnhancedAsyncClient ddb,
      @ConfigProperty(name = "serverless.environment") final String serverlessEnvironmentName) {
    return new DynamoDbChannels(ddb, serverlessEnvironmentName);
  }

  public void observeRequestDestroyed1(
      @Observes @BeforeDestroyed(RequestScoped.class) Object event, DynamoDbChannels channels) {
    channels.flush();
  }

  @Produces
  @ApplicationScoped
  @DefaultBean
  public Messages messages(
      Channels channels,
      DynamoDbEnhancedAsyncClient ddb,
      DynamoDbAsyncClient dynamoDbClient,
      @ConfigProperty(name = "serverless.environment") final String serverlessEnvironmentName) {
    return new DynamoDbMessages(channels, ddb, dynamoDbClient, serverlessEnvironmentName);
  }

  @Produces
  @ApplicationScoped
  public PeerUpdatePostProcessor peerUpdatePostProcessor() {
    return new PeerUpdatePostProcessor();
  }

  @Produces
  @ApplicationScoped
  public RouterPostProcessor historyPostProcessor(Messages messages) {
    return new HistoryPostProcessor(messages);
  }

  @Produces
  @ApplicationScoped
  public Router router(
      Channels channels, Messages messages, Instance<RouterPostProcessor> postProcessors) {
    return new KiteRouter(channels, postProcessors.stream().toList(), messages);
  }
}
