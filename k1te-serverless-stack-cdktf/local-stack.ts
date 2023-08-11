import { AwsProvider } from "@cdktf/provider-aws/lib/provider";
import { TerraformStack } from "cdktf";
import { Construct } from "constructs";
import { DynamoDbSchema } from "./dynamodb-schema";
import { DynamodbLocalContainer } from "./local-dynamodb";

export class LocalStack extends TerraformStack {
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
