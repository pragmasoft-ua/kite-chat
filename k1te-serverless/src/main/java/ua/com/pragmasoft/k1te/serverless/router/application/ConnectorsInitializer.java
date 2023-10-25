/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.serverless.router.application;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.GetMe;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.stream.Collectors;
import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.ApiGatewayManagementApiException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GetConnectionRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import ua.com.pragmasoft.k1te.backend.router.domain.Connector;

/**
 * This class eagerly initializes all connectors, not only those injected to the active lambda
 * handler
 *
 * <p>https://quarkus.io/guides/cdi-reference#eager-instantiation-of-beans
 */
@Startup
@ApplicationScoped
public class ConnectorsInitializer implements Resource {

  private static final String DUMMY = "dummy";

  private final Instance<Connector> connectors;
  private final DynamoDbClient dbClient;
  private final ApiGatewayManagementApiClient apiClient;
  private final S3Client s3Client;
  private final S3Presigner presigner;
  private final TelegramBot bot;

  @Inject
  public ConnectorsInitializer(
      final Instance<Connector> connectors,
      DynamoDbClient dbClient,
      ApiGatewayManagementApiClient apiClient,
      S3Client s3Client,
      S3Presigner presigner,
      TelegramBot bot) {
    this.connectors = connectors;
    this.dbClient = dbClient;
    this.apiClient = apiClient;
    this.s3Client = s3Client;
    this.presigner = presigner;
    this.bot = bot;
  }

  @PostConstruct
  void init() {
    Core.getGlobalContext().register(this);
  }

  @Override
  public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {
    this.initDynamoDBClient();
    this.initApiGatewayClient();
    this.initS3();
    this.initTelegramBot();
    Log.debug(
        "Initialized connectors: "
            + this.connectors.stream().map(Connector::id).collect(Collectors.joining(",")));
  }

  @Override
  public void afterRestore(Context<? extends Resource> context) throws Exception {
    Log.debug("After Restore");
  }

  private void initDynamoDBClient() {
    dbClient.describeEndpoints();
    Log.debug("DynamoDBClient has been initialized");
  }

  private void initApiGatewayClient() {
    try {
      apiClient.getConnection(GetConnectionRequest.builder().connectionId(DUMMY).build());
    } catch (ApiGatewayManagementApiException apiException) {
      Log.debug("Catch ApiGatewayManagementApiException during dummy request");
    }
    Log.debug("ApiGatewayManagementApiClient has been initialized");
  }

  private void initS3() {
    try {
      s3Client.listBuckets();
    } catch (S3Exception s3Exception) {
      Log.debug("Catch S3Exception during dummy request");
    }
    Log.debug("S3Client has been initialized");
    presigner.presignGetObject(
        GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofDays(1))
            .getObjectRequest(b -> b.bucket(DUMMY).key(DUMMY).build())
            .build());
    Log.debug("S3Presigner has been initialized");
  }

  private void initTelegramBot() {
    bot.execute(new GetMe());
    Log.debug("Telegram Bot has been initialized");
  }
}
