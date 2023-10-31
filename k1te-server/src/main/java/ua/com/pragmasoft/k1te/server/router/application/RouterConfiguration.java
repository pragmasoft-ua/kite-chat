package ua.com.pragmasoft.k1te.server.router.application;

import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import ua.com.pragmasoft.k1te.backend.router.domain.Channels;
import ua.com.pragmasoft.k1te.backend.router.domain.KiteRouter;
import ua.com.pragmasoft.k1te.backend.router.domain.Router;
import ua.com.pragmasoft.k1te.backend.router.infrastructure.DynamoDbChannels;
import ua.com.pragmasoft.k1te.server.hackathon.service.ChannelsService;

public class RouterConfiguration {

  @Produces
  @ApplicationScoped
  @DefaultBean
  public Channels channels(DynamoDbEnhancedClient ddb,
                           @ConfigProperty(name = "serverless.environment") final String serverlessEnvironmentName) {
    return new DynamoDbChannels(ddb, serverlessEnvironmentName);
  }

  @Produces
  @ApplicationScoped
  @IfBuildProfile("hackathon")
  public Channels channels(ChannelsService channelsService) {
    return channelsService;
  }


  @Produces
  @ApplicationScoped
  public Router router(Channels channels) {
    return new KiteRouter(channels);
  }

}
