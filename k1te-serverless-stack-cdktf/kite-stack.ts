import { AwsProvider } from "@cdktf/provider-aws/lib/provider";
import { Aspects, TerraformStack } from "cdktf";
import { Construct } from "constructs";
import { ApiGatewayPrincipal } from "./apigateway-principal";
import { CloudflareDnsZone } from "./dns-zone";
import { Role } from "./iam";
import {
  Architecture,
  Handler,
  LAMBDA_SERVICE_PRINCIPAL,
  Runtime,
} from "./lambda";
import { RestApi } from "./rest-api";
import { ALLOW_TAGS, TagsAddingAspect } from "./tags";
import { TlsCertificate } from "./tls-certificate";
import { WebsocketApi } from "./websocket-api";
import { ArchiveProvider } from "@cdktf/provider-archive/lib/provider";
import { MainComponent } from "./main-component";
import { DataAwsRegion } from "@cdktf/provider-aws/lib/data-aws-region";
import { DataAwsCallerIdentity } from "@cdktf/provider-aws/lib/data-aws-caller-identity";
import assert = require("assert");

const TAGGING_ASPECT = new TagsAddingAspect({ app: "k1te-chat" });
export const TELEGRAM_ROUTE = "/tg";
export type KiteStackProps = {
  domainName?: string;
  devFunctionName: string;
  prodFunctionName?: string;
  sourceBucketName: string;
  functionS3Key: string;
  lifecycleS3Key: string;
  architecture?: Architecture;
  runtime?: Runtime;
  handler?: Handler;
  memorySize?: number;
};

export class KiteStack extends TerraformStack {
  constructor(scope: Construct, id: string, props: KiteStackProps) {
    super(scope, id);
    this.node.setContext(ALLOW_TAGS, true);
    const {
      domainName,
      architecture = "x86_64",
      runtime = "provided.al2",
      handler = "hello.handler",
      memorySize = 256,
      devFunctionName,
      prodFunctionName,
      lifecycleS3Key,
      functionS3Key,
      sourceBucketName,
    } = props;

    new AwsProvider(this, "AWS");
    new ArchiveProvider(this, "archive-provider");

    const region = new DataAwsRegion(this, "current-region");
    const callerIdentity = new DataAwsCallerIdentity(this, "current-caller");

    const dnsZone = domainName
      ? new CloudflareDnsZone(this, domainName)
      : undefined;

    const certificate =
      dnsZone && new TlsCertificate(this, `${domainName}-cert`, dnsZone);

    const role = new Role(this, "lambda-execution-role", {
      forService: LAMBDA_SERVICE_PRINCIPAL,
    });

    role.attachManagedPolicyArn(
      "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole",
    );

    const apiGatewayPrincipal = new ApiGatewayPrincipal(
      this,
      "apigateway-principal",
    );

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

    const telegramDevBotToken = process.env.TELEGRAM_BOT_TOKEN;

    const createMainComponent = (
      name: string,
      functionName: string,
      token: string,
    ) =>
      new MainComponent(this, name, {
        //todo rename
        role,
        mainLambda: {
          functionName,
          runtime,
          handler,
          s3Bucket: sourceBucketName,
          s3Key: functionS3Key,
          memorySize,
          architecture,
        },
        lifecycleLambda: {
          s3Bucket: sourceBucketName,
          s3Key: lifecycleS3Key,
        },
        restApi,
        wsApi,
        telegramToken: token,
      });

    assert(telegramDevBotToken, "You need to specify TELEGRAM_BOT_TOKEN");
    const devStage = createMainComponent(
      "dev",
      devFunctionName,
      telegramDevBotToken,
    );

    let prodHandler;
    if (prodFunctionName) {
      const telegramProdBotToken = process.env.TELEGRAM_PROD_BOT_TOKEN;
      assert(
        telegramProdBotToken,
        "In order to create Prod stage you need to specify TELEGRAM_PROD_BOT_TOKEN in .env",
      );

      const prodStage = createMainComponent(
        "prod",
        prodFunctionName,
        telegramProdBotToken,
      );
      prodHandler = prodStage.lambdaFunction;
    }

    // Creating Integration, adding Routes to it and complementing Role to invoke Lambda
    wsApi
      .attachDefaultIntegration({
        region: region.name,
        accountId: callerIdentity.accountId,
        integrationName: "default-integration",
        principal: apiGatewayPrincipal,
      })
      .addRouteDefaultRoutes()
      .allowInvocation({ handler: devStage.lambdaFunction, stage: "dev" })
      .allowInvocation({ handler: prodHandler, stage: "prod" }); //If dev env is not specified - does nothing

    restApi
      .attachDefaultIntegration({
        region: region.name,
        accountId: callerIdentity.accountId,
        integrationName: "default-integration",
      })
      .addRouteDefaultRoutes({ route: TELEGRAM_ROUTE, method: "POST" })
      .allowInvocation({ handler: devStage.lambdaFunction, stage: "dev" })
      .allowInvocation({ handler: prodHandler, stage: "prod" }); //If dev env is not specified - does nothing

    Aspects.of(this).add(TAGGING_ASPECT);
  }
}
