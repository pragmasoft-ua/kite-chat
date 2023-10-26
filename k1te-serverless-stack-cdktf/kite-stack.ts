import { LambdaInvocation } from "@cdktf/provider-aws/lib/lambda-invocation";
import { AwsProvider } from "@cdktf/provider-aws/lib/provider";
import {
  Aspects,
  TerraformOutput,
  TerraformStack,
  TerraformVariable,
} from "cdktf";
import { Construct } from "constructs";
import { ApiGatewayPrincipal } from "./apigateway-principal";
import { ArchiveResource, LambdaAsset } from "./asset";
import { CloudflareDnsZone } from "./dns-zone";
import { DynamoDbSchema } from "./dynamodb-schema";
import { Role } from "./iam";
import { LAMBDA_SERVICE_PRINCIPAL, Lambda } from "./lambda";
import { RestApi } from "./rest-api";
import { ALLOW_TAGS, TagsAddingAspect } from "./tags";
import { TlsCertificate } from "./tls-certificate";
import { WebsocketApi } from "./websocket-api";
import { ObjectStore } from "./object-store";
import { ArchiveProvider } from "@cdktf/provider-archive/lib/provider";

const TAGGING_ASPECT = new TagsAddingAspect({ app: "k1te-chat" });

export class KiteStack extends TerraformStack {
  constructor(scope: Construct, id: string, domainName?: string) {
    super(scope, id);
    this.node.setContext(ALLOW_TAGS, true);

    new AwsProvider(this, "AWS");
    new ArchiveProvider(this, "archive-provider");

    const dnsZone = domainName
      ? new CloudflareDnsZone(this, domainName)
      : undefined;

    const certificate =
      dnsZone && new TlsCertificate(this, `${domainName}-cert`, dnsZone);

    const prod = "prod";

    const schema = new DynamoDbSchema(this, prod, {
      pointInTimeRecovery: false,
      preventDestroy: false,
    });

    const role = new Role(this, "lambda-execution-role", {
      forService: LAMBDA_SERVICE_PRINCIPAL,
    });

    role.attachManagedPolicyArn(
      "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
    );

    schema.allowAll(role);

    const objectStore = new ObjectStore(this, "prod-object-store", {
      bucketPrefix: "prod-k1te-chat-object-store-",
    });

    objectStore.allowReadWrite(role);

    const apiGatewayPrincipal = new ApiGatewayPrincipal(
      this,
      "apigateway-principal"
    );

    const telegramRoute = "/tg";

    const wsApiProps = certificate && {
      domainName: `ws.${domainName}`,
      certificate,
    };

    const restApiProps = certificate && {
      domainName: `api.${domainName}`,
      certificate,
    };

    const wsApi = new WebsocketApi(this, "ws-api", wsApiProps);
    const restApi = new RestApi(this, "http-api", restApiProps);

    wsApi.domainName &&
      wsApiProps?.domainName &&
      dnsZone &&
      dnsZone.createRecord(wsApiProps.domainName, {
        type: "CNAME",
        name: wsApi.domainName.domainName,
        value: wsApi.domainName.domainNameConfiguration.targetDomainName,
      });

    restApi.domainName &&
      restApiProps?.domainName &&
      dnsZone &&
      dnsZone.createRecord(restApiProps.domainName, {
        type: "CNAME",
        name: restApi.domainName.domainName,
        value: restApi.domainName.domainNameConfiguration.targetDomainName,
      });

    const wsApiStage = wsApi.addStage({ stage: prod });
    const restApiStage = restApi.addStage(prod);

    const telegramBotToken = new TerraformVariable(this, "TELEGRAM_BOT_TOKEN", {
      type: "string",
      nullable: false,
      description: "telegram bot token, obtain in telegram from botfather",
      sensitive: true,
    });

    const PROD_ENV = {
      SERVERLESS_ENVIRONMENT: prod,
      WS_API_EXECUTION_ENDPOINT: wsApiStage.invokeUrl,
      TELEGRAM_BOT_TOKEN: telegramBotToken.value,
      TELEGRAM_WEBHOOK_ENDPOINT: `${restApiStage.invokeUrl}${telegramRoute}`,
      BUCKET_NAME: objectStore.bucket.bucket,
      JAVA_TOOL_OPTIONS: "-XX:+TieredCompilation -XX:TieredStopAtLevel=1",
    };

    const memorySize = 256;

    const quarkusAsset = new LambdaAsset(this, "k1te-serverless-quarkus", {
      relativeProjectPath: "../k1te-serverless",
    });
    const archiveResource = new ArchiveResource(
      this,
      "kite-serverless-nodejs",
      {
        output: "lifecycle-handler/lifecycle.zip",
        sourceFile: "lifecycle-handler/index.mjs",
      }
    );

    const mainHandler = new Lambda(this, "request-dispatcher", {
      role,
      asset: quarkusAsset,
      environment: {
        QUARKUS_LAMBDA_HANDLER: "main",
        ...PROD_ENV,
      },
      memorySize,
      isSnapStart: true,
    });

    wsApiStage.addDefaultRoutes(mainHandler, apiGatewayPrincipal);
    restApiStage.addHandler(telegramRoute, "POST", mainHandler);

    const lifecycleHandler = new Lambda(this, "lifecycle-handler", {
      role,
      asset: archiveResource,
      environment: {
        TELEGRAM_BOT_TOKEN: telegramBotToken.value,
        TELEGRAM_WEBHOOK_ENDPOINT: `${restApiStage.invokeUrl}${telegramRoute}`,
      },
      memorySize: 128,
      architectures: ["arm64"],
    });

    const lifecycle = new LambdaInvocation(this, "lifecycle-invocation", {
      functionName: lifecycleHandler.functionName,
      input: JSON.stringify({}),
      lifecycleScope: "CRUD",
      triggers: PROD_ENV,
      dependsOn: [lifecycleHandler.fn],
    });

    new TerraformOutput(this, "lifecycle-output", {
      value: lifecycle.result,
    });

    Aspects.of(this).add(TAGGING_ASPECT);
  }
}
