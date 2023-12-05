import { Construct } from "constructs";
import { DynamoDbSchema } from "./dynamodb-schema";
import { Role } from "./iam";
import { ObjectStore } from "./object-store";
import { WebsocketApi } from "./websocket-api";
import { RestApi } from "./rest-api";
import { Lambda } from "./lambda";
import { Resource } from "./asset";
import { ApiGatewayPrincipal } from "./apigateway-principal";

export type MainComponentProps = {
  role: Role;
  wsApi: WebsocketApi;
  restApi: RestApi;
  telegramToken: string;
  lambda: {
    asset: Resource;
    architecture: "x86_64" | "arm64";
    memorySize: number;
  };
  apiGatewayPrincipal: ApiGatewayPrincipal;
};

export class MainComponent extends Construct {
  constructor(
    scope: Construct,
    id: string,
    props: Readonly<MainComponentProps>
  ) {
    super(scope, id);

    const { role, wsApi, restApi, telegramToken, lambda, apiGatewayPrincipal } =
      props;
    const telegramRoute = "/tg";

    const schema = new DynamoDbSchema(this, `${id}`, {
      pointInTimeRecovery: false,
      preventDestroy: false,
    });

    schema.allowAll(role);

    const objectStore = new ObjectStore(this, "object-store", {
      bucketPrefix: `${id}-k1te-chat-object-store-`,
    });

    objectStore.allowReadWrite(role);

    const wsApiStage = wsApi.addStage({ stage: id });
    const restApiStage = restApi.addStage(id);

    const ENV = {
      SERVERLESS_ENVIRONMENT: id,
      WS_API_EXECUTION_ENDPOINT: wsApiStage.invokeUrl,
      TELEGRAM_BOT_TOKEN: telegramToken,
      TELEGRAM_WEBHOOK_ENDPOINT: `${restApiStage.invokeUrl}${telegramRoute}`,
      BUCKET_NAME: objectStore.bucket.bucket,
      DISABLE_SIGNAL_HANDLERS: "true",
    };

    const mainHandler = new Lambda(this, `${id}-request-dispatcher`, {
      role,
      asset: lambda.asset,
      environment: {
        ...ENV,
      },
      architecture: lambda.architecture,
      memorySize: lambda.memorySize,
      timeout: 30,
    });

    wsApiStage.addDefaultRoutes(mainHandler, apiGatewayPrincipal);
    restApiStage.addHandler(telegramRoute, "POST", mainHandler);
  }
}
