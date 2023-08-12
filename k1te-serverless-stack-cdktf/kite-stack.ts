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
import { TagsAddingAspect } from "./tags";
import { WebsocketApi } from "./websocket-api";
import { CloudflareDnsZone } from "./dns-zone";
import { TlsCertificate } from "./tls-certificate";
import { ApiGatewayPrincipal } from "./apigateway-principal";

const TAGGING_ASPECT = new TagsAddingAspect({ app: "k1te-chat" });

const DOMAIN_NAME = "k1te.chat";

export class KiteStack extends TerraformStack {
  constructor(scope: Construct, id: string) {
    super(scope, id);

    new AwsProvider(this, "AWS");

    const currentRegion = new DataAwsRegion(this, "current-region");

    new S3Backend(this, {
      bucket: "k1te-chat-tfstate",
      key: `${id}/terraform.tfstate`,
      region: "eu-north-1",
    });

    const dnsZone = new CloudflareDnsZone(this, DOMAIN_NAME);

    const cert = new TlsCertificate(this, `${DOMAIN_NAME}-cert`, dnsZone);

    const schema = new DynamoDbSchema(this, `${id}-database`, {
      pointInTimeRecovery: false,
      preventDestroy: false,
    });

    const role = new Role(this, "kite-lambda-execution-role", {
      forService: LAMBDA_SERVICE_PRINCIPAL,
    });

    role.attachManagedPolicyArn(
      "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
    );

    schema.allowAll(role);

    const asset = new QuarkusLambdaAsset(this, "kite-lambda-code", {
      relativeProjectPath: "../k1te-serverless",
    });

    const wsApiDomainName = `ws.${DOMAIN_NAME}`;

    const apiGatewayPrincipal = new ApiGatewayPrincipal(
      this,
      `${id}-apigateway`
    );

    const wsApi = new WebsocketApi(this, `${id}-ws-api`, {
      domainName: wsApiDomainName,
      certificateArn: cert.cert.arn,
    });
    wsApi.node.addDependency(cert); // we need to await certificate validation

    wsApi.domainName &&
      dnsZone.createRecord(wsApiDomainName, {
        type: "CNAME",
        name: wsApiDomainName,
        value: wsApi.domainName.domainNameConfiguration.targetDomainName,
      });

    const stage = "prod";

    const prodWsEndpoint = `https://${wsApi.api.id}.execute-api.${currentRegion.name}.amazonaws.com/${stage}`;

    const prodEnvironment = {
      SERVERLESS_ENVIRONMENT: id,
      WS_API_EXECUTION_ENDPOINT: prodWsEndpoint,
    };

    const memorySize = 128;

    const wsHandler = new Lambda(this, "ws-handler", {
      role,
      asset,
      environment: {
        QUARKUS_LAMBDA_HANDLER: "ws",
        ...prodEnvironment,
      },
      memorySize,
    });

    wsApi.addStage({
      stage,
      handler: wsHandler,
      principal: apiGatewayPrincipal,
    });

    const testHandler = new Lambda(this, "test-handler", {
      role,
      asset,
      environment: {
        QUARKUS_LAMBDA_HANDLER: "test",
        ...prodEnvironment,
      },
      memorySize,
    });

    const tgHandler = new Lambda(this, "tg-handler", {
      role,
      asset,
      environment: {
        QUARKUS_LAMBDA_HANDLER: "tg",
        ...prodEnvironment,
      },
      memorySize,
    });

    new RestApi(this, "kite-rest-api").addHandler("/tg", "ANY", tgHandler);

    const testEvent = {
      name: "Dmytro",
      greeting: "Hi From Terraform,",
    };

    new LambdaInvocation(this, "test-invocation", {
      functionName: testHandler.fn.functionName,
      input: JSON.stringify(testEvent),
      lifecycleScope: "CRUD",
    });

    Aspects.of(this).add(TAGGING_ASPECT);
  }
}
