import { AwsProvider } from "@cdktf/provider-aws/lib/provider";
import { Aspects, S3Backend, TerraformStack } from "cdktf";
import { Construct } from "constructs";
import { Role } from "./iam";
import {
  Architecture,
  Handler,
  Lambda,
  LAMBDA_SERVICE_PRINCIPAL,
  Runtime,
} from "./lambda";
import { RestApi } from "./rest-api";
import { ALLOW_TAGS, TagsAddingAspect } from "./tags";
import { WebsocketApi } from "./websocket-api";
import { ArchiveProvider } from "@cdktf/provider-archive/lib/provider";
import { Stage } from "./stage";
import { DataAwsCallerIdentity } from "@cdktf/provider-aws/lib/data-aws-caller-identity";
import assert = require("assert");
import { DomainName } from "./domain-name";
import { SsmParameter } from "./ssm-parameter";

const TAGGING_ASPECT = new TagsAddingAspect({ app: "k1te-chat" });
export const TELEGRAM_ROUTE = "/tg";

export type KiteStackProps = {
  /**
   * The name of the S3 bucket where Terraform state will be stored.
   * Default name for kite-stack is 'kite/terraform.tfstate'
   * */
  s3BucketWithState: string;
  /**
   * The AWS region where S3 bucket with state is located.
   * */
  region: string;
  /**
   * The name of the S3 bucket where 'main' and 'lifecycle' lambdas are located.
   * */
  s3SourceBucketName: string;
  /**
   * The S3 key to mainLambda
   * */
  mainLambdaS3Key: string;
  /**
   * The S3 key to lifecycleLambda
   * */
  lifecycleLambdaS3Key: string;
  /**
   * The S3 key to authorizerLambda
   * */
  authorizerLambdaS3Key: string;
  /**
   * Whether to create prod stage or not
   * */
  prodStage?: boolean;
  /**
   * DomainName that will be used for Api Gateway
   * */
  domainName?: string;
  /**
   * The Main lambda's architecture. Default is set to 'arm64'
   * */
  architecture?: Architecture;
  /**
   * The Main lambda's runtime. Default is set to 'provided.al2'
   * */
  runtime?: Runtime;
  /**
   * The Main lambda's handler. Default is set to 'hello.handler'
   * */
  handler?: Handler;
  /**
   * The Main lambda's memorySize. Default is set to '256'
   * */
  memorySize?: number;
};

export class KiteStack extends TerraformStack {
  private readonly restApi: RestApi;
  private readonly wsApi: WebsocketApi;
  private readonly role: Role;
  private readonly prodStage: boolean;
  private readonly telegramSecret: string;
  private readonly telegramDevToken: string;
  private readonly telegramProdToken: string | undefined;

  constructor(scope: Construct, id: string, props: KiteStackProps) {
    super(scope, id);
    this.node.setContext(ALLOW_TAGS, true);
    const { s3BucketWithState, region, domainName, prodStage = false } = props;

    this.prodStage = prodStage;
    this.telegramSecret = this.getEnvParam("TELEGRAM_SECRET_TOKEN");
    this.telegramDevToken = this.getEnvParam("TELEGRAM_BOT_TOKEN");

    new S3Backend(this, {
      bucket: s3BucketWithState,
      key: "kite/terraform.tfstate",
      region,
    });
    new AwsProvider(this, "AWS");
    new ArchiveProvider(this, "archive-provider");

    const callerIdentity = new DataAwsCallerIdentity(this, "current-caller");
    const handlerArn =
      `arn:aws:lambda:${region}:${callerIdentity.accountId}:function:` +
      "$${stageVariables.function}";

    this.role = new Role(this, "lambda-execution-role", {
      forService: LAMBDA_SERVICE_PRINCIPAL,
    });

    const domain = new DomainName(this, "domain-name", domainName);
    const authorizerHandler = this.createAuthorizerLambda(props);

    this.wsApi = new WebsocketApi(this, "ws-api", {
      handlerArn,
      domainName: `ws.${domainName}`,
      certificate: domain.certificate,
    });
    this.restApi = new RestApi(this, "http-api", {
      handlerArn,
      authorizerHandler,
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

    this.createStage("dev", this.telegramDevToken, props);

    if (prodStage) {
      this.telegramProdToken = this.getEnvParam("TELEGRAM_PROD_BOT_TOKEN");
      this.createStage("prod", this.telegramProdToken, props);
    }

    this.addSsmParameters();

    Aspects.of(this).add(TAGGING_ASPECT);
  }

  createStage(
    name: "dev" | "prod",
    telegramToken: string,
    props: KiteStackProps,
  ) {
    const {
      s3SourceBucketName,
      mainLambdaS3Key,
      lifecycleLambdaS3Key,
      architecture = "arm64",
      runtime = "provided.al2",
      handler = "hello.handler",
      memorySize = 256,
    } = props;

    return new Stage(this, name, {
      role: this.role,
      restApi: this.restApi,
      wsApi: this.wsApi,
      telegramToken,
      telegramSecretToken: this.telegramSecret,
      mainLambdaProps: {
        runtime,
        handler,
        s3Bucket: s3SourceBucketName,
        s3Key: mainLambdaS3Key,
        memorySize,
        architecture,
      },
      lifecycleLambdaProps: {
        s3Bucket: s3SourceBucketName,
        s3Key: lifecycleLambdaS3Key,
      },
    });
  }

  createAuthorizerLambda({
    s3SourceBucketName,
    authorizerLambdaS3Key,
  }: KiteStackProps) {
    return new Lambda(this, "tg-authorizer", {
      role: this.role,
      s3Bucket: s3SourceBucketName,
      s3Key: authorizerLambdaS3Key,
      memorySize: 128,
      handler: "index.handler",
      runtime: "nodejs18.x",
      architecture: "arm64",
      environment: {
        TELEGRAM_SECRET_TOKEN: this.telegramSecret,
      },
    });
  }

  getEnvParam(name: string): string {
    const param = process.env[name];
    assert(param, `You need to specify ${name} in .env file`);
    return param;
  }

  addSsmParameters() {
    const arr: SsmParameter[] = [];

    arr.push(
      new SsmParameter(this, "telegram.secret", {
        type: "SecureString",
        value: this.telegramSecret,
        description: "Secret token that is used to validate WebHook Updates",
      }),
    );

    arr.push(
      new SsmParameter(this, "dev-telegram.bot.token", {
        type: "SecureString",
        value: this.telegramDevToken,
        description:
          "dev telegram bot token that is used to communicate with bot in dev env",
      }),
    );

    if (this.prodStage) {
      arr.push(
        new SsmParameter(this, "prod-telegram.bot.token", {
          type: "SecureString",
          value: this.telegramProdToken!,
          description:
            "prod telegram bot token that is used to communicate with bot in prod env",
        }),
      );
    }

    arr.forEach((param) => param.allowGetRequest(this.role));
  }
}
