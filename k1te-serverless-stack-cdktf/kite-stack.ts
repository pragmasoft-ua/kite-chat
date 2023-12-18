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
import { RestApi, RestApiProps } from "./rest-api";
import { ALLOW_TAGS, TagsAddingAspect } from "./tags";
import { TlsCertificate } from "./tls-certificate";
import { WebsocketApi, WebSocketApiProps } from "./websocket-api";
import { ArchiveProvider } from "@cdktf/provider-archive/lib/provider";
import { Stage } from "./stage";
import { DataAwsRegion } from "@cdktf/provider-aws/lib/data-aws-region";
import { DataAwsCallerIdentity } from "@cdktf/provider-aws/lib/data-aws-caller-identity";
import assert = require("assert");

const TAGGING_ASPECT = new TagsAddingAspect({ app: "k1te-chat" });
export const TELEGRAM_ROUTE = "/tg";
export type KiteStackProps = {
  /**
   * Name of the MainLambda that is used in 'dev' stage.
   * */
  devFunctionName: string;
  /**
   * Optional: Name of the MainLambda that is used in 'prod' stage.
   * If set, 'prod' stage and all related resources will be created.
   * */
  prodFunctionName?: string;
  /**
   * Name of S3Bucket that contains Lambdas.
   * */
  sourceBucketName: string;
  /**
   * S3Key of SourceBucket that point at MainHandler zip archive.
   * */
  functionS3Key: string;
  /**
   * S3Key of SourceBucket that point at Lifecycle zip archive.
   * */
  lifecycleS3Key: string;
  domainName?: string;
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
    const handlerArn =
      `arn:aws:lambda:${region.name}:${callerIdentity.accountId}:function:` +
      "$${stageVariables.function}";

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

    const wsApiProps: WebSocketApiProps = {
      principal: apiGatewayPrincipal,
      handlerArn,
      domainName: `ws.${domainName}`,
      certificate,
    };

    const restApiProps: RestApiProps = {
      handlerArn,
      method: "POST",
      route: TELEGRAM_ROUTE,
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

    const createStage = (
      name: string,
      functionName: string,
      telegramToken: string,
    ) =>
      new Stage(this, name, {
        role,
        restApi,
        wsApi,
        telegramToken,
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
      });

    const telegramDevBotToken = process.env.TELEGRAM_BOT_TOKEN;
    assert(telegramDevBotToken, "You need to specify TELEGRAM_BOT_TOKEN");
    createStage("dev", devFunctionName, telegramDevBotToken);

    if (prodFunctionName) {
      const telegramProdToken = process.env.TELEGRAM_PROD_BOT_TOKEN;
      assert(telegramProdToken, "You need to specify TELEGRAM_PROD_BOT_TOKEN");
      createStage("prod", prodFunctionName, telegramProdToken);
    }

    Aspects.of(this).add(TAGGING_ASPECT);
  }
}
