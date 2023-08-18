import { DynamodbTable } from "@cdktf/provider-aws/lib/dynamodb-table";
import { ITerraformDependable } from "cdktf";
import { Construct } from "constructs";
import { Dynamodb } from "iam-floyd/lib/generated";
import { Grantable } from "./grantable";

const billingMode = "PAY_PER_REQUEST";
const STRING = "S";

export type DynamoDbSchemaProps = {
  dependsOn?: ITerraformDependable[];
  pointInTimeRecovery?: boolean;
  preventDestroy?: boolean;
};

export class DynamoDbSchema extends Construct {
  readonly tables: DynamodbTable[];
  constructor(
    scope: Construct,
    id: string,
    {
      dependsOn,
      pointInTimeRecovery = false,
      preventDestroy = true,
    }: DynamoDbSchemaProps = {}
  ) {
    super(scope, id);

    const channels = new DynamodbTable(this, "Channels", {
      name: `${id}.Channels`,
      dependsOn,
      lifecycle: {
        preventDestroy,
      },
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

    const members = new DynamodbTable(this, "Members", {
      name: `${id}.Members`,
      dependsOn: [channels],
      lifecycle: {
        preventDestroy,
      },
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

    this.tables = [channels, members];
  }
  public allowAll(to: Grantable) {
    const policyStatement = new Dynamodb()
      .allow()
      .allActions()
      .on(...this.tables.map((t) => t.arn));
    to.grant(
      `allow-all-${this.tables.map((t) => t.name).join(",")}`,
      policyStatement
    );
    return this;
  }
}
