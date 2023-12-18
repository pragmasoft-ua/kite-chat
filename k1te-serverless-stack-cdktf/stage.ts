import { Construct } from "constructs";
import { DynamoDbSchema } from "./dynamodb-schema";
import { Role } from "./iam";
import { ObjectStore } from "./object-store";
import { WebsocketApi } from "./websocket-api";
import { RestApi } from "./rest-api";
import { Architecture, Handler, Lambda, Runtime } from "./lambda";
import { LambdaInvocation } from "@cdktf/provider-aws/lib/lambda-invocation";
import { TerraformOutput } from "cdktf";
import { TELEGRAM_ROUTE } from "./kite-stack";

export type MainComponentProps = {
  role: Role;
  wsApi: WebsocketApi;
  restApi: RestApi;
  telegramToken: string;
  mainLambda: {
    functionName: string;
    s3Bucket: string;
    s3Key: string;
    architecture?: Architecture;
    runtime?: Runtime;
    handler?: Handler;
    memorySize?: number;
  };
  lifecycleLambda: {
    s3Bucket: string;
    s3Key: string;
  };
};

export class Stage extends Construct {
  readonly lambdaFunction: Lambda;

  constructor(
    scope: Construct,
    id: string,
    props: Readonly<MainComponentProps>,
  ) {
    super(scope, id);

    const { role, wsApi, restApi, telegramToken, mainLambda, lifecycleLambda } =
      props;

    const stageProps = {
      stage: id,
      functionStageVariable: mainLambda.functionName,
    };

    const schema = new DynamoDbSchema(this, `${id}`, {
      //todo not to delete for prod
      pointInTimeRecovery: false,
      preventDestroy: false,
    });

    schema.allowAll(role);

    const objectStore = new ObjectStore(this, "object-store", {
      //todo not to destroy for prod
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

    const lambdaFunction = new Lambda(this, mainLambda.functionName, {
      role,
      s3Bucket: mainLambda.s3Bucket,
      s3Key: mainLambda.s3Key,
      runtime: mainLambda.runtime,
      handler: mainLambda.handler,
      environment: {
        ...ENV,
      },
      architecture: mainLambda.architecture,
      memorySize: mainLambda.memorySize,
      timeout: 30,
    });

    restApiStage.allowInvocation(lambdaFunction);
    wsApiStage.allowInvocation(lambdaFunction);

    const lifecycleHandler = new Lambda(this, `${id}-lifecycle-handler`, {
      role,
      runtime: "nodejs18.x",
      handler: "index.handler",
      s3Bucket: lifecycleLambda.s3Bucket,
      s3Key: lifecycleLambda.s3Key,
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

    this.lambdaFunction = lambdaFunction;
  }
}
