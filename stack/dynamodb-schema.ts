import { DynamodbTable } from "@cdktf/provider-aws/lib/dynamodb-table";
import { ITerraformDependable } from "cdktf";
import { Construct } from "constructs";

const billingMode = "PAY_PER_REQUEST";
const STRING = "S";

export type DynamoDbSchemaProps = {
  dependsOn?: ITerraformDependable[];
  pointInTimeRecovery?: boolean;
};

export class DynamoDbSchema extends Construct {
  constructor(
    scope: Construct,
    id: string,
    { dependsOn, pointInTimeRecovery = false }: DynamoDbSchemaProps = {}
  ) {
    super(scope, id);

    const channels = new DynamodbTable(scope, "Channels", {
      name: `${id}.Channels`,
      dependsOn,
      billingMode,
      pointInTimeRecovery: {
        enabled: pointInTimeRecovery,
      },
      ttl: {
        enabled: true,
        attributeName: "ttl",
      },
      hashKey: "name",
      attribute: [{ name: "name", type: STRING }],
    });

    new DynamodbTable(scope, "Members", {
      name: `${id}.Members`,
      dependsOn: [channels],
      billingMode,
      pointInTimeRecovery: {
        enabled: pointInTimeRecovery,
      },
      hashKey: "channelName",
      rangeKey: "id",
      attribute: [
        { name: "channelName", type: STRING },
        { name: "id", type: STRING },
        { name: "connectionUri", type: STRING },
      ],
      globalSecondaryIndex: [
        {
          name: "ByConnection",
          hashKey: "connectionUri",
          projectionType: "ALL",
        },
      ],
    });
  }
}
