import { LambdaInvocation } from "@cdktf/provider-aws/lib/lambda-invocation";
import { AwsProvider } from "@cdktf/provider-aws/lib/provider";
import { App, Aspects, S3Backend, TerraformStack } from "cdktf";
import { Construct } from "constructs";
import { QuarkusLambdaAsset } from "./asset";
import { DynamoDbSchema } from "./dynamodb-schema";
import { Role } from "./iam";
import { LAMBDA_SERVICE_PRINCIPAL, Lambda } from "./lambda";
import { DynamodbLocalContainer } from "./local-dynamodb";
import { RestApi } from "./rest-api";
import { TagsAddingAspect } from "./tags";
import { WebsocketApi } from "./websocket-api";

const TAGGING_ASPECT = new TagsAddingAspect({ app: "k1te-chat" });
class KiteStack extends TerraformStack {
  constructor(scope: Construct, id: string) {
    super(scope, id);

    new AwsProvider(this, "AWS");

    // const currentRegion = new DataAwsRegion(this, "current-region");

    new S3Backend(this, {
      bucket: "k1te-chat-tfstate",
      key: `${id}/terraform.tfstate`,
      region: "eu-north-1",
    });

    const schema = new DynamoDbSchema(this, id, {
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

    const wsApi = new WebsocketApi(this, "kite-ws-api");

    const wsHandler = new Lambda(this, "ws-handler", {
      role,
      asset,
      environment: {
        QUARKUS_LAMBDA_HANDLER: "ws",
        SERVERLESS_ENVIRONMENT: id,
      },
      memorySize: 128,
    });

    const stage = "prod";

    wsApi.addStage({
      stage,
      handler: wsHandler,
    });

    // const stageEndpoint = `https://${wsApi.api.id}.execute-api.${currentRegion.name}.amazonaws.com/${stage}`;
    // We probably don't need it, as it can be calculated from the request, as explained here
    // https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-how-to-call-websocket-api-connections.html

    const testHandler = new Lambda(this, "test-handler", {
      role,
      asset,
      environment: {
        QUARKUS_LAMBDA_HANDLER: "test",
        SERVERLESS_ENVIRONMENT: id,
      },
      memorySize: 128,
    });

    const tgHandler = new Lambda(this, "tg-handler", {
      role,
      asset,
      environment: {
        QUARKUS_LAMBDA_HANDLER: "tg",
        SERVERLESS_ENVIRONMENT: id,
      },
      memorySize: 128,
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

class LocalStack extends TerraformStack {
  constructor(scope: Construct, id: string) {
    super(scope, id);

    new AwsProvider(this, "AWS", {
      endpoints: [
        {
          dynamodb: "http://localhost:8000",
        },
      ],
      skipCredentialsValidation: true,
      skipMetadataApiCheck: "true",
      skipRequestingAccountId: true,
      region: "eu-north-1",
    });

    const dynamoDbLocalContainer = new DynamodbLocalContainer(
      this,
      "dynamodb-local"
    );

    new DynamoDbSchema(this, id, {
      dependsOn: [dynamoDbLocalContainer.container],
      preventDestroy: false,
    });
  }
}

const app = new App();
new KiteStack(app, "k1te");
new LocalStack(app, "local");
app.synth();
