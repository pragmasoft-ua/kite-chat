import { Construct } from "constructs";
import { DynamoDbSchema } from "./dynamodb-schema";
import { Role } from "./iam";
import { ObjectStore } from "./object-store";
import { WebsocketApi } from "./websocket-api";
import { RestApi } from "./rest-api";
import { Lambda } from "./lambda";
import { ArchiveResource, Resource } from "./asset";
import { LambdaInvocation } from "@cdktf/provider-aws/lib/lambda-invocation";
import { TerraformOutput } from "cdktf";

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
  lifecycleAsset: ArchiveResource;
  // apiGatewayPrincipal: ApiGatewayPrincipal;
};

export class MainComponent extends Construct {
  readonly lambdaFunction: Lambda;

  constructor(
    scope: Construct,
    id: string,
    props: Readonly<MainComponentProps>
  ) {
    super(scope, id);

    const {
      role,
      wsApi,
      restApi,
      telegramToken,
      lambda,
      lifecycleAsset,
      // apiGatewayPrincipal,
    } = props;
    const telegramRoute = "/tg";
    const functionName = `${id}-request-dispatcher`;

    const schema = new DynamoDbSchema(this, `${id}`, {
      pointInTimeRecovery: false,
      preventDestroy: false,
    });

    schema.allowAll(role);

    const objectStore = new ObjectStore(this, "object-store", {
      bucketPrefix: `${id}-k1te-chat-object-store-`,
    });

    objectStore.allowReadWrite(role);

    const wsApiStage = wsApi.addStage({
      stage: id,
      stageVariables: {
        function: functionName,
        functionAlias: functionName + "-alias",
      },
    });
    const restApiStage = restApi.addStage(id);

    const ENV = {
      SERVERLESS_ENVIRONMENT: id,
      WS_API_EXECUTION_ENDPOINT: wsApiStage.invokeUrl,
      TELEGRAM_BOT_TOKEN: telegramToken,
      TELEGRAM_WEBHOOK_ENDPOINT: `${restApiStage.invokeUrl}${telegramRoute}`,
      BUCKET_NAME: objectStore.bucket.bucket,
      DISABLE_SIGNAL_HANDLERS: "true",
    };
    const mainHandler = new Lambda(this, functionName, {
      role,
      asset: lambda.asset,
      environment: {
        ...ENV,
      },
      architecture: lambda.architecture,
      memorySize: lambda.memorySize,
      timeout: 30,
    });
    this.lambdaFunction = mainHandler;

    // wsApiStage.addDefaultRoutes(mainHandler, apiGatewayPrincipal);
    restApiStage.addHandler(telegramRoute, "POST", mainHandler);

    const lifecycleHandler = new Lambda(this, `${id}-lifecycle-handler`, {
      role,
      asset: lifecycleAsset,
      environment: {
        TELEGRAM_BOT_TOKEN: telegramToken,
        TELEGRAM_WEBHOOK_ENDPOINT: `${restApiStage.invokeUrl}${telegramRoute}`,
      },
      memorySize: 128,
      architecture: "arm64",
    });

    const lifecycle = new LambdaInvocation(this, "lifecycle-invocation", {
      functionName: lifecycleHandler.functionName,
      input: JSON.stringify({}),
      lifecycleScope: "CRUD",
      triggers: ENV,
      dependsOn: [lifecycleHandler.fn],
    });

    new TerraformOutput(this, "lifecycle-output", {
      value: lifecycle.result,
    });
  }
}
