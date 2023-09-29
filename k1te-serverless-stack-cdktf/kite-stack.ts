import { LambdaInvocation } from "@cdktf/provider-aws/lib/lambda-invocation";
import { AwsProvider } from "@cdktf/provider-aws/lib/provider";
import {
  Aspects,
  S3Backend,
  TerraformOutput,
  TerraformStack,
  TerraformVariable,
} from "cdktf";
import { Construct } from "constructs";
import { ApiGatewayPrincipal } from "./apigateway-principal";
import { QuarkusLambdaAsset } from "./asset";
import { CloudflareDnsZone } from "./dns-zone";
import { DynamoDbSchema } from "./dynamodb-schema";
import { Role } from "./iam";
import { LAMBDA_SERVICE_PRINCIPAL, Lambda } from "./lambda";
import { RestApi } from "./rest-api";
import { ALLOW_TAGS, TagsAddingAspect } from "./tags";
import { TlsCertificate } from "./tls-certificate";
import { WebsocketApi } from "./websocket-api";

const TAGGING_ASPECT = new TagsAddingAspect({ app: "k1te-chat" });

export class KiteStack extends TerraformStack {
  constructor(scope: Construct, id: string, domainName?: string) {
    super(scope, id);
    this.node.setContext(ALLOW_TAGS, true);

    new AwsProvider(this, "AWS");

    new S3Backend(this, {
      bucket: "k1te-chat-tfstate",
      key: `${id}/terraform.tfstate`,
      region: "eu-north-1",
    });

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

    const apiGatewayPrincipal = new ApiGatewayPrincipal(
      this,
      "apigateway-principal"
    );

    const wsApiDomainName = `ws.${domainName}`;
    const restApiDomainName = `api.${domainName}`;
    const telegramRoute = "/tg";

    const wsApiProps = certificate && {
      domainName: wsApiDomainName,
      certificate,
    };

    const wsApi = new WebsocketApi(this, "ws-api", wsApiProps);

    wsApi.domainName &&
      dnsZone &&
      dnsZone.createRecord(wsApiDomainName, {
        type: "CNAME",
        name: wsApiDomainName,
        value: wsApi.domainName.domainNameConfiguration.targetDomainName,
      });

    const PROD_WS_API_EXECUTION_ENDPOINT = `https://${wsApiDomainName}/${prod}`;
    const PROD_TELEGRAM_WEBHOOK_ENDPOINT = `https://${restApiDomainName}/${prod}${telegramRoute}`;

    const telegramBotToken = new TerraformVariable(this, "TELEGRAM_BOT_TOKEN", {
      type: "string",
      nullable: false,
      description: "telegram bot token, obtain in telegram from botfather",
      sensitive: true,
    });

    const PROD_ENV = {
      SERVERLESS_ENVIRONMENT: prod,
      WS_API_EXECUTION_ENDPOINT: PROD_WS_API_EXECUTION_ENDPOINT,
      TELEGRAM_BOT_TOKEN: telegramBotToken.value,
      TELEGRAM_WEBHOOK_ENDPOINT: PROD_TELEGRAM_WEBHOOK_ENDPOINT,
    };

    const memorySize = 256;

    const asset = new QuarkusLambdaAsset(this, "k1te-serverless", {
      relativeProjectPath: "../k1te-serverless",
    });

    const wsHandler = new Lambda(this, "ws-handler", {
      role,
      asset,
      environment: {
        QUARKUS_LAMBDA_HANDLER: "ws",
        ...PROD_ENV,
      },
      memorySize,
    });

    wsApi.addStage({
      stage: prod,
      handler: wsHandler,
      principal: apiGatewayPrincipal,
    });

    const telegramHandler = new Lambda(this, "tg-handler", {
      role,
      asset,
      environment: {
        QUARKUS_LAMBDA_HANDLER: "tg",
        ...PROD_ENV,
      },
      memorySize,
    });

    const restApiProps = certificate && {
      domainName: restApiDomainName,
      certificate,
    };

    const restApi = new RestApi(this, "http-api", restApiProps)
      .addStage("prod")
      .addHandler(telegramRoute, "POST", telegramHandler)
      .done();

    restApi.domainName &&
      dnsZone &&
      dnsZone.createRecord(restApiDomainName, {
        type: "CNAME",
        name: restApiDomainName,
        value: restApi.domainName.domainNameConfiguration.targetDomainName,
      });

    const lifecycleHandler = new Lambda(this, "lifecycle-handler", {
      role,
      asset,
      environment: {
        QUARKUS_LAMBDA_HANDLER: "lifecycle",
        ...PROD_ENV,
      },
      memorySize,
    });

    const lifecycle = new LambdaInvocation(this, "lifecycle-invocation", {
      functionName: lifecycleHandler.functionName,
      input: JSON.stringify({}),
      lifecycleScope: "CRUD",
    });

    new TerraformOutput(this, "lifecycle-output", {
      value: lifecycle.result,
    });

    Aspects.of(this).add(TAGGING_ASPECT);
  }
}
