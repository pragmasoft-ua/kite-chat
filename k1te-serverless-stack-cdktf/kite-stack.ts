import { DataAwsRegion } from "@cdktf/provider-aws/lib/data-aws-region";
import { LambdaInvocation } from "@cdktf/provider-aws/lib/lambda-invocation";
import { AwsProvider } from "@cdktf/provider-aws/lib/provider";
import { Aspects, S3Backend, TerraformStack } from "cdktf";
import { Construct } from "constructs";
import { QuarkusLambdaAsset } from "./asset";
import { DynamoDbSchema } from "./dynamodb-schema";
import { Role } from "./iam";
import { LAMBDA_SERVICE_PRINCIPAL, Lambda } from "./lambda";
import { RestApi } from "./rest-api";
import { ALLOW_TAGS, TagsAddingAspect } from "./tags";
import { WebsocketApi } from "./websocket-api";
import { CloudflareDnsZone } from "./dns-zone";
import { TlsCertificate } from "./tls-certificate";
import { ApiGatewayPrincipal } from "./apigateway-principal";

const TAGGING_ASPECT = new TagsAddingAspect({ app: "k1te-chat" });

export class KiteStack extends TerraformStack {
  constructor(scope: Construct, id: string, domainName?: string) {
    super(scope, id);
    this.node.setContext(ALLOW_TAGS, true);

    new AwsProvider(this, "AWS");

    const currentRegion = new DataAwsRegion(this, "current-region");

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

    const PROD_WS_API_EXECUTION_ENDPOINT = `https://${wsApi.api.id}.execute-api.${currentRegion.name}.amazonaws.com/${prod}`;

    const PROD_ENV = {
      SERVERLESS_ENVIRONMENT: prod,
      WS_API_EXECUTION_ENDPOINT: PROD_WS_API_EXECUTION_ENDPOINT,
    };

    const memorySize = 128;

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

    const tgHandler = new Lambda(this, "tg-handler", {
      role,
      asset,
      environment: {
        QUARKUS_LAMBDA_HANDLER: "tg",
        ...PROD_ENV,
      },
      memorySize,
    });

    const restApiDomainName = `api.${domainName}`;

    const restApiProps = certificate && {
      domainName: restApiDomainName,
      certificate,
    };

    const restApi = new RestApi(this, "http-api", restApiProps)
      .addStage("prod")
      .addHandler("/tg", "ANY", tgHandler)
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

    new LambdaInvocation(this, "lifecycle-invocation", {
      functionName: lifecycleHandler.functionName,
      input: JSON.stringify({}),
      lifecycleScope: "CRUD",
    });

    Aspects.of(this).add(TAGGING_ASPECT);
  }
}
