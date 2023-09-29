package ua.com.pragmasoft.k1te.serverless.router.application;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import ua.com.pragmasoft.k1te.backend.router.domain.Channels;
import ua.com.pragmasoft.k1te.backend.router.domain.KiteRouter;
import ua.com.pragmasoft.k1te.backend.router.domain.Router;
import ua.com.pragmasoft.k1te.backend.router.infrastructure.DynamoDbChannels;

public class RouterConfiguration {

  @Produces
  @ApplicationScoped
  public Channels channels(DynamoDbEnhancedClient ddb,
      @ConfigProperty(name = "serverless.environment") final String serverlessEnvironmentName) {
    return new DynamoDbChannels(ddb, serverlessEnvironmentName);
  }

  @Produces
  @ApplicationScoped
  public Router router(Channels channels) {
    return new KiteRouter(channels);
  }

}
