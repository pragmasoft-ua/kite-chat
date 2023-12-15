import { AwsProvider } from "@cdktf/provider-aws/lib/provider";
import { Aspects, TerraformStack } from "cdktf";
import { Construct } from "constructs";
import { ApiGatewayPrincipal } from "./apigateway-principal";
import { ArchiveResource, LambdaAsset, S3Source } from "./asset";
import { CloudflareDnsZone } from "./dns-zone";
import { Role } from "./iam";
import { Lambda, LAMBDA_SERVICE_PRINCIPAL } from "./lambda";
import { RestApi } from "./rest-api";
import { ALLOW_TAGS, TagsAddingAspect } from "./tags";
import { TlsCertificate } from "./tls-certificate";
import { WebsocketApi } from "./websocket-api";
import { ArchiveProvider } from "@cdktf/provider-archive/lib/provider";
import { MainComponent } from "./main-component";
import { DataAwsRegion } from "@cdktf/provider-aws/lib/data-aws-region";
import { DataAwsCallerIdentity } from "@cdktf/provider-aws/lib/data-aws-caller-identity";
import { CiCdCodebuild } from "./ci-cd-codebuild";
import assert = require("assert");

const TAGGING_ASPECT = new TagsAddingAspect({ app: "k1te-chat" });
export const TELEGRAM_ROUTE = "/tg";
export type KiteStackProps = {
  domainName?: string;
  architecture?: "x86_64" | "arm64";
  runtime?: "provided.al2" | "java17" | "java21";
  handler?:
    | "hello.handler"
    | "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest";
  memorySize?: number;
  devEnv?: boolean;
  cicd?: {
    gitRepositoryUrl: string;
    s3BucketWithState: string;
    emailToSendAlarm: string;
    driftCheckCronEx?: string;
  };
};

export class KiteStack extends TerraformStack {
  constructor(scope: Construct, id: string, props: KiteStackProps = {}) {
    super(scope, id);
    this.node.setContext(ALLOW_TAGS, true);
    const {
      domainName,
      architecture = "x86_64",
      runtime = "provided.al2",
      handler = "hello.handler",
      memorySize = 256,
      devEnv = false,
      cicd,
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

    const telegramProdBotToken = process.env.TELEGRAM_BOT_TOKEN;
    const lambdaS3Bucket = process.env.MAIN_LAMBDA_S3_BUCKET;
    const lambdaS3Key = process.env.MAIN_LAMBDA_S3_OBJECT_KEY;
    const sourceProps =
      lambdaS3Bucket && lambdaS3Key
        ? {
            s3Bucket: lambdaS3Bucket,
            s3Props: {
              s3Key: lambdaS3Key,
              runtime,
              handler,
            },
          }
        : {
            asset: new LambdaAsset(this, "k1te-serverless-quarkus", {
              relativeProjectPath: "../k1te-serverless",
              handler,
              runtime,
            }),
          };

    const archiveResource = new ArchiveResource(
      this,
      "kite-serverless-nodejs",
      {
        output: "lifecycle-handler/lifecycle.zip",
        sourceFile: "lifecycle-handler/index.mjs",
      },
    );

    const mainHandlerSource = new S3Source(
      this,
      "main-handler-source",
      sourceProps,
    );
    const lifecycleSource = new S3Source(this, "lifecycle-handler-source", {
      asset: archiveResource,
      s3Bucket: mainHandlerSource.s3Bucket, //In order not to create a new S3Bucket
    });

    const createMainComponent = (name: string, token: string) =>
      new MainComponent(this, name, {
        role,
        lambda: { asset: mainHandlerSource, memorySize, architecture },
        lifecycleAsset: lifecycleSource,
        restApi,
        wsApi,
        telegramToken: token,
      });

    assert(telegramProdBotToken, "You need to specify TELEGRAM_BOT_TOKEN");
    const prod = createMainComponent("prod", telegramProdBotToken);
    const functions: Lambda[] = [prod.lambdaFunction];

    let devHandler;
    if (devEnv) {
      const telegramDevBotToken = process.env.TELEGRAM_DEV_BOT_TOKEN;
      assert(
        telegramDevBotToken,
        "You specified devEnv=true but TELEGRAM_DEV_BOT_TOKEN was not provided",
      );

      const dev = createMainComponent("dev", telegramDevBotToken);
      devHandler = dev.lambdaFunction;
      functions.push(devHandler);
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
      .allowInvocation({ handler: prod.lambdaFunction, stage: "prod" })
      .allowInvocation({ handler: devHandler, stage: "dev" }); //If dev env is not specified - does nothing

    restApi
      .attachDefaultIntegration({
        region: region.name,
        accountId: callerIdentity.accountId,
        integrationName: "default-integration",
      })
      .addRouteDefaultRoutes({ route: TELEGRAM_ROUTE, method: "POST" })
      .allowInvocation({ handler: prod.lambdaFunction, stage: "prod" })
      .allowInvocation({ handler: devHandler, stage: "dev" }); //If dev env is not specified - does nothing

    if (cicd) {
      const {
        driftCheckCronEx,
        emailToSendAlarm,
        gitRepositoryUrl,
        s3BucketWithState,
      } = cicd;
      new CiCdCodebuild(this, "ci-cd-codebuild", {
        functions,
        prodFunctionName: prod.lambdaFunction.functionName,
        gitRepositoryUrl: gitRepositoryUrl,
        s3BucketWithState: s3BucketWithState,
        emailToSendAlarm,
        driftCheckCronEx,
      });
    }

    Aspects.of(this).add(TAGGING_ASPECT);
  }
}
