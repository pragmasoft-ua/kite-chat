import { Construct } from "constructs";
import { DynamoDbSchema } from "./dynamodb-schema";
import { Role } from "./iam";
import { ObjectStore } from "./object-store";
import { WebsocketApi } from "./websocket-api";
import { RestApi } from "./rest-api";
import { Lambda } from "./lambda";
import { S3Source } from "./asset";
import { LambdaInvocation } from "@cdktf/provider-aws/lib/lambda-invocation";
import { TerraformOutput } from "cdktf";
import { TELEGRAM_ROUTE } from "./kite-stack";

export type MainComponentProps = {
  role: Role;
  wsApi: WebsocketApi;
  restApi: RestApi;
  telegramToken: string;
  lambda: {
    asset: S3Source;
    architecture: "x86_64" | "arm64";
    memorySize: number;
  };
  lifecycleAsset: S3Source;
};

export class MainComponent extends Construct {
  readonly lambdaFunction: Lambda;

  constructor(
    scope: Construct,
    id: string,
    props: Readonly<MainComponentProps>
  ) {
    super(scope, id);

    const { role, wsApi, restApi, telegramToken, lambda, lifecycleAsset } =
      props;
    const baseMainFunctionName = "request-dispatcher";
    const mainFunctionName = `${id}-${baseMainFunctionName}`;
    const stageProps = {
      stage: id,
      functionStageVariable: `${mainFunctionName}:${baseMainFunctionName}-alias`,
    };

    const schema = new DynamoDbSchema(this, `${id}`, {
      pointInTimeRecovery: false,
      preventDestroy: false,
    });

    schema.allowAll(role);

    const objectStore = new ObjectStore(this, "object-store", {
      bucketPrefix: `${id}-k1te-chat-object-store-`,
    });

    objectStore.allowReadWrite(role);

    const wsApiStage = wsApi.addStage(stageProps);
    const restApiStage = restApi.addStage(stageProps);

    const ENV = {
      SERVERLESS_ENVIRONMENT: id,
      WS_API_EXECUTION_ENDPOINT: wsApiStage.invokeUrl,
      TELEGRAM_BOT_TOKEN: telegramToken,
      TELEGRAM_WEBHOOK_ENDPOINT: `${restApiStage.invokeUrl}${TELEGRAM_ROUTE}`,
      BUCKET_NAME: objectStore.bucket.bucket,
      DISABLE_SIGNAL_HANDLERS: "true",
    };

    this.lambdaFunction = new Lambda(this, mainFunctionName, {
      role,
      asset: lambda.asset,
      environment: {
        ...ENV,
      },
      architecture: lambda.architecture,
      memorySize: lambda.memorySize,
      timeout: 30,
    });

    const lifecycleHandler = new Lambda(this, `${id}-lifecycle-handler`, {
      role,
      asset: lifecycleAsset,
      environment: {
        TELEGRAM_BOT_TOKEN: telegramToken,
        TELEGRAM_WEBHOOK_ENDPOINT: `${restApiStage.invokeUrl}${TELEGRAM_ROUTE}`,
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
