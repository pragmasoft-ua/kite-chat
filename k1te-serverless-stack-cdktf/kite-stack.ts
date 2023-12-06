import { AwsProvider } from "@cdktf/provider-aws/lib/provider";
import { Aspects, TerraformStack, TerraformVariable } from "cdktf";
import { Construct } from "constructs";
import { ApiGatewayPrincipal } from "./apigateway-principal";
import { ArchiveResource, LambdaAsset } from "./asset";
import { CloudflareDnsZone } from "./dns-zone";
import { Role } from "./iam";
import { LAMBDA_SERVICE_PRINCIPAL } from "./lambda";
import { RestApi } from "./rest-api";
import { ALLOW_TAGS, TagsAddingAspect } from "./tags";
import { TlsCertificate } from "./tls-certificate";
import { RouteConfig, WebsocketApi } from "./websocket-api";
import { ArchiveProvider } from "@cdktf/provider-archive/lib/provider";
import { Codebuild } from "./codebuild";
import { MainComponent } from "./main-component";

const TAGGING_ASPECT = new TagsAddingAspect({ app: "k1te-chat" });

export type KiteStackProps = {
  domainName?: string;
  architecture?: "x86_64" | "arm64";
  runtime?: "provided.al2" | "java17" | "java21";
  handler?:
    | "hello.handler"
    | "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest";
  memorySize?: number;
  addDev?: boolean;
  addCodeBuild?: boolean;
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
      addDev = false,
      addCodeBuild = false,
    } = props;

    new AwsProvider(this, "AWS");
    new ArchiveProvider(this, "archive-provider");

    const dnsZone = domainName
      ? new CloudflareDnsZone(this, domainName)
      : undefined;

    const certificate =
      dnsZone && new TlsCertificate(this, `${domainName}-cert`, dnsZone);

    const role = new Role(this, "lambda-execution-role", {
      forService: LAMBDA_SERVICE_PRINCIPAL,
    });

    role.attachManagedPolicyArn(
      "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
    );

    const apiGatewayPrincipal = new ApiGatewayPrincipal(
      this,
      "apigateway-principal"
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

    const telegramProdBotToken = new TerraformVariable(
      this,
      "TELEGRAM_BOT_TOKEN",
      {
        type: "string",
        nullable: false,
        description: "telegram bot token, obtain in telegram from botfather",
        sensitive: true,
      }
    );

    const quarkusAsset = new LambdaAsset(this, "k1te-serverless-quarkus", {
      relativeProjectPath: "../k1te-serverless",
      handler,
      runtime,
    });

    const archiveResource = new ArchiveResource(
      this,
      "kite-serverless-nodejs",
      {
        output: "lifecycle-handler/lifecycle.zip",
        sourceFile: "lifecycle-handler/index.mjs",
      }
    );

    const prod = new MainComponent(this, "prod", {
      role,
      lambda: {
        asset: quarkusAsset,
        memorySize,
        architecture,
      },
      lifecycleAsset: archiveResource,
      restApi,
      wsApi,
      // apiGatewayPrincipal,
      telegramToken: telegramProdBotToken.value,
    });
    const routeConfigs: [RouteConfig] = [
      { handler: prod.lambdaFunction, stage: "prod" },
    ];

    if (addDev) {
      const telegramDevBotToken = new TerraformVariable(
        this,
        "TELEGRAM_DEV_BOT_TOKEN",
        {
          type: "string",
          nullable: false,
          description: "telegram bot token, obtain in telegram from botfather",
          sensitive: true,
        }
      );

      const dev = new MainComponent(this, "dev", {
        role,
        lambda: {
          asset: quarkusAsset,
          memorySize,
          architecture,
        },
        lifecycleAsset: archiveResource,
        restApi,
        wsApi,
        // apiGatewayPrincipal,
        telegramToken: telegramDevBotToken.value,
      });

      routeConfigs.push({ handler: dev.lambdaFunction, stage: "dev" });
    }

    wsApi.addDefaultRoutes(routeConfigs, apiGatewayPrincipal);

    if (addCodeBuild) {
      new Codebuild(this, "arm-lambda-build", {
        prodLambda: prod.lambdaFunction,
        gitProjectUrl: "https://github.com/Alex21022001/arm",
      });
    }

    Aspects.of(this).add(TAGGING_ASPECT);
  }
}
