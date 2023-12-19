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
import { RestApi, RestApiProps } from "./rest-api";
import { ALLOW_TAGS, TagsAddingAspect } from "./tags";
import { WebsocketApi, WebSocketApiProps } from "./websocket-api";
import { ArchiveProvider } from "@cdktf/provider-archive/lib/provider";
import { Stage } from "./stage";
import { DataAwsRegion } from "@cdktf/provider-aws/lib/data-aws-region";
import { DataAwsCallerIdentity } from "@cdktf/provider-aws/lib/data-aws-caller-identity";
import assert = require("assert");
import { DomainName } from "./domain-name";

const TAGGING_ASPECT = new TagsAddingAspect({ app: "k1te-chat" });
export const TELEGRAM_ROUTE = "/tg";

export type KiteStackProps = {
  s3Backend: S3Backend;
  buildStackName: string;
  devLambdaName: string;
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
      architecture = "x86_64",
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
    const handlerArn =
      `arn:aws:lambda:${region.name}:${callerIdentity.accountId}:function:` +
      "$${stageVariables.function}";

    const role = new Role(this, "lambda-execution-role", {
      forService: LAMBDA_SERVICE_PRINCIPAL,
    });

    const apiGatewayPrincipal = new ApiGatewayPrincipal(
      this,
      "apigateway-principal",
    );

    const domain = new DomainName(this, "domain-name", domainName);

    const wsApiProps: WebSocketApiProps = {
      principal: apiGatewayPrincipal,
      handlerArn,
      domainName: `ws.${domainName}`,
      certificate: domain.certificate,
    };

    const restApiProps: RestApiProps = {
      handlerArn,
      method: "POST",
      route: TELEGRAM_ROUTE,
      domainName: `api.${domainName}`,
      certificate: domain.certificate,
    };

    const wsApi = new WebsocketApi(this, "ws-api", wsApiProps);
    const restApi = new RestApi(this, "http-api", restApiProps);

    domain.createRecord(wsApi, wsApiProps);
    domain.createRecord(restApi, restApiProps);

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

    const telegramDevBotToken = process.env.TELEGRAM_BOT_TOKEN;
    assert(telegramDevBotToken, "You need to specify TELEGRAM_BOT_TOKEN");
    createStage("dev", devLambdaName, telegramDevBotToken);

    if (prodLambdaName) {
      const telegramProdToken = process.env.TELEGRAM_PROD_BOT_TOKEN;
      assert(telegramProdToken, "You need to specify TELEGRAM_PROD_BOT_TOKEN");
      createStage("prod", prodLambdaName, telegramProdToken);
    }

    Aspects.of(this).add(TAGGING_ASPECT);
  }
}
