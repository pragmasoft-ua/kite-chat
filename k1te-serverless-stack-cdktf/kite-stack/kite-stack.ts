import { AwsProvider } from "@cdktf/provider-aws/lib/provider";
import { Aspects, S3Backend, TerraformStack } from "cdktf";
import { Construct } from "constructs";
import { ApiGatewayPrincipal } from "./apigateway-principal";
import { Role } from "./iam";
import {
  Architecture,
  Handler,
  LAMBDA_SERVICE_PRINCIPAL,
  Runtime,
} from "./lambda";
import { RestApi } from "./rest-api";
import { ALLOW_TAGS, TagsAddingAspect } from "./tags";
import { WebsocketApi } from "./websocket-api";
import { ArchiveProvider } from "@cdktf/provider-archive/lib/provider";
import { Stage } from "./stage";
import { DataAwsRegion } from "@cdktf/provider-aws/lib/data-aws-region";
import { DataAwsCallerIdentity } from "@cdktf/provider-aws/lib/data-aws-caller-identity";
import assert = require("assert");
import { DomainName } from "./domain-name";

const TAGGING_ASPECT = new TagsAddingAspect({ app: "k1te-chat" });
export const TELEGRAM_ROUTE = "/tg";

export type KiteStackProps = {
  /**
   * S3Backend of build stack that is used for building and uploading Main lambda
   * and Lifecycle lambda to S3. It's used to get S3Bucket name, and S3Key of main & lifecycle lambdas.
   * */
  s3Backend: S3Backend;
  buildStackName: string;
  /**
   * Name of Main lambda function that will be used for 'dev' stage, it should contain
   * dev prefix.
   * */
  devLambdaName: string;
  /**
   * Name of Main lambda function that will be used for 'prod' stage, it should contain
   * prod prefix.
   * If specified - prod stage and all related resources will be created.
   * */
  prodLambdaName?: string;
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
      s3Backend,
      buildStackName,
      devLambdaName,
      prodLambdaName,
      domainName,
      architecture = "arm64",
      runtime = "provided.al2",
      handler = "hello.handler",
      memorySize = 256,
    } = props;

    new AwsProvider(this, "AWS");
    new ArchiveProvider(this, "archive-provider");

    const buildState = s3Backend.getRemoteStateDataSource(
      this,
      `build-state`,
      buildStackName,
    );
    const region = new DataAwsRegion(this, "current-region");
    const callerIdentity = new DataAwsCallerIdentity(this, "current-caller");

    const role = new Role(this, "lambda-execution-role", {
      forService: LAMBDA_SERVICE_PRINCIPAL,
    });

    const apiGatewayPrincipal = new ApiGatewayPrincipal(
      this,
      "apigateway-principal",
    );

    const domain = new DomainName(this, "domain-name", domainName);

    const handlerArn =
      `arn:aws:lambda:${region.name}:${callerIdentity.accountId}:function:` +
      "$${stageVariables.function}";

    const wsApi = new WebsocketApi(this, "ws-api", {
      principal: apiGatewayPrincipal,
      handlerArn,
      domainName: `ws.${domainName}`,
      certificate: domain.certificate,
    });
    const restApi = new RestApi(this, "http-api", {
      handlerArn,
      method: "POST",
      route: TELEGRAM_ROUTE,
      domainName: `api.${domainName}`,
      certificate: domain.certificate,
    });

    domain.createCname(wsApi.getDomainName(), wsApi.getTargetDomainName());
    domain.createCname(restApi.getDomainName(), restApi.getTargetDomainName());

    const telegramDevBotToken = process.env.TELEGRAM_BOT_TOKEN;
    assert(telegramDevBotToken, "You need to specify TELEGRAM_BOT_TOKEN");
    new Stage(this, "dev", {
      role,
      restApi,
      wsApi,
      telegramToken: telegramDevBotToken,
      mainLambda: {
        functionName: devLambdaName,
        runtime,
        handler,
        s3Bucket: buildState.getString("s3-source-bucket"),
        s3Key: buildState.getString("function-s3-key"),
        memorySize,
        architecture,
      },
      lifecycleLambda: {
        s3Bucket: buildState.getString("s3-source-bucket"),
        s3Key: buildState.getString("lifecycle-s3-key"),
      },
    });

    if (prodLambdaName) {
      const telegramProdToken = process.env.TELEGRAM_PROD_BOT_TOKEN;
      assert(telegramProdToken, "You need to specify TELEGRAM_PROD_BOT_TOKEN");
      new Stage(this, "prod", {
        role,
        restApi,
        wsApi,
        telegramToken: telegramProdToken,
        mainLambda: {
          functionName: prodLambdaName,
          runtime,
          handler,
          s3Bucket: buildState.getString("s3-source-bucket"),
          s3Key: buildState.getString("function-s3-key"),
          memorySize,
          architecture,
        },
        lifecycleLambda: {
          s3Bucket: buildState.getString("s3-source-bucket"),
          s3Key: buildState.getString("lifecycle-s3-key"),
        },
      });
    }

    Aspects.of(this).add(TAGGING_ASPECT);
  }
}
