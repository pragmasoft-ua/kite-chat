package ua.com.pragmasoft.k1te.serverless.router.application;

import java.util.stream.Collectors;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.ApiGatewayManagementApiException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GetConnectionRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import ua.com.pragmasoft.k1te.backend.router.domain.Connector;

/**
 * This class eagerly initializes all connectors, not only those
 * injected to the active lambda handler
 * <p>
 * https://quarkus.io/guides/cdi-reference#eager-instantiation-of-beans
 */
@Startup
@ApplicationScoped
public class ConnectorsInitializer implements Resource {

  private final Instance<Connector> connectors;
  private final DynamoDbClient dbClient;
  private final ApiGatewayManagementApiClient apiClient;

  @Inject
  public ConnectorsInitializer(final Instance<Connector> connectors,
                               DynamoDbClient dbClient,
                               ApiGatewayManagementApiClient apiClient) {
    this.connectors = connectors;
    this.dbClient = dbClient;
    this.apiClient = apiClient;
  }

  @PostConstruct
  void init() {
    Core.getGlobalContext().register(this);
  }

  @Override
  public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {
    dbClient.describeEndpoints();
    Log.debug("DynamoDBClient has been initialized");
    try {
      apiClient.getConnection(GetConnectionRequest.builder()
          .connectionId("dummy")
          .build());
    } catch (ApiGatewayManagementApiException apiException) {
      Log.debug("Catch failed dummy request");
    }
    Log.debug("ApiGatewayManagementApiClient has been initialized");
    Log.debug(
        "Initialized connectors: " + this.connectors.stream().map(Connector::id).collect(Collectors.joining(",")));
  }

  @Override
  public void afterRestore(Context<? extends Resource> context) throws Exception {
    Log.debug("After Restore");
  }
}
