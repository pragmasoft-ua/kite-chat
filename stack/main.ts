import { AwsProvider } from "@cdktf/provider-aws/lib/provider";
import { App, Aspects, TerraformStack } from "cdktf";
import { Construct } from "constructs";
import { DynamoDbSchema } from "./dynamodb-schema";
import { DynamodbLocalContainer } from "./local-dynamodb";
import { Role } from "./iam";
import { TagsAddingAspect } from "./tags";
import { QuarkusLambdaAsset } from "./asset";
import { Lambda } from "./lambda";
import { WebsocketApi } from "./websocket-api";
import { RestApi } from "./rest-api";

const TAGGING_ASPECT = new TagsAddingAspect({ app: "kite-chat" });
class KiteStack extends TerraformStack {
  constructor(scope: Construct, id: string) {
    super(scope, id);

    new AwsProvider(this, "AWS");

    const schema = new DynamoDbSchema(this, id, { pointInTimeRecovery: true });

    const role = new Role(this, "kite-lambda-execution-role");

    role.attachManagedPolicyArn(
      "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
    );

    schema.allowAllWrite(role);

    const asset = new QuarkusLambdaAsset(this, "kite-lambda-code", {
      relativeProjectPath: "../kite-lambda",
    });

    const wsHandler = new Lambda(this, "ws-handler", {
      role,
      asset,
      environment: { QUARKUS_LAMBDA_HANDLER: "ws" },
    });

    new WebsocketApi(this, "kite-ws-api", { handler: wsHandler.fn });

    const testHandler = new Lambda(this, "test-handler", {
      role,
      asset,
      environment: { QUARKUS_LAMBDA_HANDLER: "test" },
    });

    const tgHandler = new Lambda(this, "tg-handler", {
      role,
      asset,
      environment: { QUARKUS_LAMBDA_HANDLER: "tg" },
    });

    new RestApi(this, "kite-rest-api")
      .addHandler("/tg", "ANY", tgHandler)
      .addHandler("/test", "ANY", testHandler);

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
