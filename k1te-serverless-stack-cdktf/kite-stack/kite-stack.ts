import { AwsProvider } from "@cdktf/provider-aws/lib/provider";
import {
  Aspects,
  S3Backend,
  TerraformRemoteState,
  TerraformStack,
} from "cdktf";
import { Construct } from "constructs";
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
  private readonly restApi: RestApi;
  private readonly wsApi: WebsocketApi;
  private readonly role: Role;
  private readonly remoteState: TerraformRemoteState;

  constructor(scope: Construct, id: string, props: KiteStackProps) {
    super(scope, id);
    this.node.setContext(ALLOW_TAGS, true);
    const { s3Backend, buildStackName, prodLambdaName, domainName } = props;

    new AwsProvider(this, "AWS");
    new ArchiveProvider(this, "archive-provider");

    // todo
    this.remoteState = s3Backend.getRemoteStateDataSource(
      this,
      "build-state",
      buildStackName,
    );
    const region = new DataAwsRegion(this, "current-region");
    const callerIdentity = new DataAwsCallerIdentity(this, "current-caller");
    const handlerArn =
      `arn:aws:lambda:${region.name}:${callerIdentity.accountId}:function:` +
      "$${stageVariables.function}";

    this.role = new Role(this, "lambda-execution-role", {
      forService: LAMBDA_SERVICE_PRINCIPAL,
    });

    const domain = new DomainName(this, "domain-name", domainName);

    this.wsApi = new WebsocketApi(this, "ws-api", {
      handlerArn,
      domainName: `ws.${domainName}`,
      certificate: domain.certificate,
    });
    this.restApi = new RestApi(this, "http-api", {
      handlerArn,
      method: "POST",
      route: TELEGRAM_ROUTE,
      domainName: `api.${domainName}`,
      certificate: domain.certificate,
    });

    domain.createCname(
      this.wsApi.getDomainName(),
      this.wsApi.getTargetDomainName(),
    );
    domain.createCname(
      this.restApi.getDomainName(),
      this.restApi.getTargetDomainName(),
    );

    const telegramDevBotToken = process.env.TELEGRAM_BOT_TOKEN;
    assert(telegramDevBotToken, "You need to specify TELEGRAM_BOT_TOKEN");
    this.createStage("dev", telegramDevBotToken, props);

    if (prodLambdaName) {
      const telegramProdToken = process.env.TELEGRAM_PROD_BOT_TOKEN;
      assert(telegramProdToken, "You need to specify TELEGRAM_PROD_BOT_TOKEN");
      this.createStage("prod", telegramProdToken, props);
    }

    Aspects.of(this).add(TAGGING_ASPECT);
  }

  createStage(name: string, telegramToken: string, props: KiteStackProps) {
    const {
      architecture = "arm64",
      runtime = "provided.al2",
      handler = "hello.handler",
      memorySize = 256,
    } = props;

    new Stage(this, name, {
      role: this.role,
      restApi: this.restApi,
      wsApi: this.wsApi,
      telegramToken,
      mainLambdaProps: {
        runtime,
        handler,
        s3Bucket: this.remoteState.getString("s3-source-bucket"),
        s3Key: this.remoteState.getString("function-s3-key"),
        memorySize,
        architecture,
      },
      lifecycleLambdaProps: {
        s3Bucket: this.remoteState.getString("s3-source-bucket"),
        s3Key: this.remoteState.getString("lifecycle-s3-key"),
      },
    });
  }
}
