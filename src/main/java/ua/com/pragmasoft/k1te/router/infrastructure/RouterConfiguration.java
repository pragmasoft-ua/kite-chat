package ua.com.pragmasoft.k1te.router.infrastructure;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import ua.com.pragmasoft.k1te.router.domain.Channels;
import ua.com.pragmasoft.k1te.router.domain.KiteRouter;
import ua.com.pragmasoft.k1te.router.domain.Router;

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
