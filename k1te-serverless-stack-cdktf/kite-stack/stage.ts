import { Construct } from "constructs";
import { DynamoDbSchema } from "./dynamodb-schema";
import { Role } from "./iam";
import { ObjectStore } from "./object-store";
import { WebsocketApi, WebsocketApiStage } from "./websocket-api";
import { RestApi, RestApiStage } from "./rest-api";
import { Architecture, Handler, Lambda, Runtime } from "./lambda";
import { LambdaInvocation } from "@cdktf/provider-aws/lib/lambda-invocation";
import { TerraformOutput } from "cdktf";
import { TELEGRAM_ROUTE } from "./kite-stack";

export const MAIN_LAMBDA_NAME = "request-dispatcher";

export type StageProps = {
  role: Role;
  wsApi: WebsocketApi;
  restApi: RestApi;
  telegramToken: string;
  mainLambdaProps: MainLambdaProps;
  lifecycleLambdaProps: LifecycleLambdaProps;
};

type MainLambdaProps = {
  s3Bucket: string;
  s3Key: string;
  architecture?: Architecture;
  runtime?: Runtime;
  handler?: Handler;
  memorySize?: number;
};

type LifecycleLambdaProps = {
  s3Bucket: string;
  s3Key: string;
};

export class Stage extends Construct {
  private readonly role: Role;
  private readonly telegramToken: string;
  private readonly restApiStage: RestApiStage;
  private readonly wsApiStage: WebsocketApiStage;
  private readonly schema: DynamoDbSchema;
  private readonly objectStore: ObjectStore;

  constructor(scope: Construct, id: string, props: Readonly<StageProps>) {
    super(scope, id);
    const {
      role,
      telegramToken,
      wsApi,
      restApi,
      mainLambdaProps,
      lifecycleLambdaProps,
    } = props;
    this.role = role;
    this.telegramToken = telegramToken;

    const isProd = id === "prod";
    const mainLambdaName = `${id}-${MAIN_LAMBDA_NAME}`;
    const stageProps = {
      stage: id,
      functionStageVariable: mainLambdaName,
    };

    this.schema = new DynamoDbSchema(this, id, {
      pointInTimeRecovery: false,
      preventDestroy: isProd,
    });
    this.schema.allowAll(role);

    this.objectStore = new ObjectStore(this, "object-store", {
      bucketPrefix: `${id}-k1te-chat-object-store-`,
      forceDestroy: !isProd,
    });
    this.objectStore.allowReadWrite(role);

    this.wsApiStage = wsApi.addStage(stageProps);
    this.restApiStage = restApi.addStage(stageProps);

    this.createMainLambda(mainLambdaName, isProd, mainLambdaProps);
    this.createLifecycleLambda(lifecycleLambdaProps);
  }

  createMainLambda(name: string, isProd: boolean, props: MainLambdaProps) {
    const { s3Bucket, s3Key, runtime, handler, architecture, memorySize } =
      props;

    const lambdaFunction = new Lambda(this, name, {
      role: this.role,
      s3Bucket,
      s3Key,
      runtime,
      handler,
      environment: {
        SERVERLESS_ENVIRONMENT: this.node.id,
        WS_API_EXECUTION_ENDPOINT: this.wsApiStage.invokeUrl,
        TELEGRAM_BOT_TOKEN: this.telegramToken,
        TELEGRAM_WEBHOOK_ENDPOINT: `${this.restApiStage.invokeUrl}${TELEGRAM_ROUTE}`,
        BUCKET_NAME: this.objectStore.bucket.bucket,
        DISABLE_SIGNAL_HANDLERS: "true",
        QUARKUS_LOG_CATEGORY__UA_COM_PRAGMASOFT__LEVEL: isProd
          ? "INFO"
          : "DEBUG",
      },
      architecture,
      memorySize,
      timeout: 30,
    });

    this.restApiStage.allowInvocation(lambdaFunction);
    this.wsApiStage.allowInvocation(lambdaFunction);
  }

  createLifecycleLambda(props: LifecycleLambdaProps) {
    const { s3Bucket, s3Key } = props;

    const lifecycleHandler = new Lambda(
      this,
      `${this.node.id}-lifecycle-handler`,
      {
        role: this.role,
        runtime: "nodejs18.x",
        handler: "index.handler",
        s3Bucket,
        s3Key,
        environment: {
          TELEGRAM_BOT_TOKEN: this.telegramToken,
          TELEGRAM_WEBHOOK_ENDPOINT: `${this.restApiStage.invokeUrl}${TELEGRAM_ROUTE}`,
        },
        memorySize: 128,
        architecture: "arm64",
      },
    );

    const lifecycle = new LambdaInvocation(this, "lifecycle-invocation", {
      functionName: lifecycleHandler.functionName,
      input: JSON.stringify({}),
      lifecycleScope: "CRUD",
      dependsOn: [lifecycleHandler.fn],
    });

    new TerraformOutput(this, "lifecycle-output", {
      value: lifecycle.result,
    });
  }
}
