import { AwsProvider } from "@cdktf/provider-aws/lib/provider";
import { App, Aspects, TerraformStack } from "cdktf";
import { Construct } from "constructs";
import { DynamoDbSchema } from "./dynamodb-schema";
import { DynamodbLocalContainer } from "./local-dynamodb";
import { Role } from "./iam";
import { TagsAddingAspect } from "./tags";
import { QuarkusLambdaAsset } from "./asset";
import { LAMBDA_SERVICE_PRINCIPAL, Lambda } from "./lambda";
import { WebsocketApi } from "./websocket-api";
import { RestApi } from "./rest-api";
import { LambdaInvocation } from "@cdktf/provider-aws/lib/lambda-invocation";

const TAGGING_ASPECT = new TagsAddingAspect({ app: "kite-chat" });
class KiteStack extends TerraformStack {
  constructor(scope: Construct, id: string) {
    super(scope, id);

    new AwsProvider(this, "AWS");

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
      relativeProjectPath: "../kite-lambda",
    });

    const wsHandler = new Lambda(this, "ws-handler", {
      role,
      asset,
      environment: { QUARKUS_LAMBDA_HANDLER: "ws" },
      memorySize: 128,
    });

    new WebsocketApi(this, "kite-ws-api", { handler: wsHandler });

    const testHandler = new Lambda(this, "test-handler", {
      role,
      asset,
      environment: { QUARKUS_LAMBDA_HANDLER: "test" },
      memorySize: 128,
    });

    const tgHandler = new Lambda(this, "tg-handler", {
      role,
      asset,
      environment: { QUARKUS_LAMBDA_HANDLER: "tg" },
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
new KiteStack(app, "kite");
new LocalStack(app, "local");
app.synth();
