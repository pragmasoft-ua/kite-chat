import { Apigatewayv2Api } from "@cdktf/provider-aws/lib/apigatewayv2-api";
import { IamRole } from "@cdktf/provider-aws/lib/iam-role";
import { AssetType, TerraformAsset } from "cdktf";
import * as iam from "iam-floyd";

import { ITerraformDependable } from "cdktf";
import { Construct } from "constructs";
import path = require("node:path");
import { Sts } from "iam-floyd/lib/generated";
import { Grantable } from "./grantable";

type Runtime =
  | "nodejs"
  | "nodejs4.3"
  | "nodejs6.10"
  | "nodejs8.10"
  | "nodejs10.x"
  | "nodejs12.x"
  | "nodejs14.x"
  | "nodejs16.x"
  | "java8"
  | "java8.al2"
  | "java11"
  | "python2.7"
  | "python3.6"
  | "python3.7"
  | "python3.8"
  | "python3.9"
  | "dotnetcore1.0"
  | "dotnetcore2.0"
  | "dotnetcore2.1"
  | "dotnetcore3.1"
  | "dotnet6"
  | "nodejs4.3-edge"
  | "go1.x"
  | "ruby2.5"
  | "ruby2.7"
  | "provided"
  | "provided.al2"
  | "nodejs18.x";

export type LambdaProps = {
  name: string;
  relativeAssetPath: string;
  runtime?: Runtime;
  assetType?: AssetType;
  dependsOn?: ITerraformDependable[];
  /**
   * The environment variables to be passed to the Lambda function.
   */
  environment?: { [key: string]: string };

  /**
   * The log retention period in days. Defaults to 7.
   */
  logRetentionInDays?: number;

  /**
   * The memory limit in MB. Defaults to 512.
   */
  memorySize?: number;

  /**
   * The timout in seconds. Defaults to 15.
   */
  timeout?: number;
};

const DEFAULT_PROPS: Partial<LambdaProps> = {
  logRetentionInDays: 7,
  timeout: 15,
  memorySize: 256,
  runtime: "java11",
  assetType: AssetType.ARCHIVE,
  environment: {},
  dependsOn: [],
};

const LAMBDA_ASSUME_ROLE_POLICY = new Sts()
  .allow()
  .toAssumeRole()
  .forService("lambda.amazonaws.com")
  .toJSON();

export class ApiGateway extends Construct implements Grantable {
  readonly gw: Apigatewayv2Api;
  private role: IamRole;

  constructor(scope: Construct, id: string, props: Readonly<LambdaProps>) {
    super(scope, id);

    const { name, relativeAssetPath, memorySize, timeout, runtime, assetType } =
      Object.assign({}, DEFAULT_PROPS, props);

    const absoluteAssetPath = path.resolve(__dirname, relativeAssetPath);

    const asset = new TerraformAsset(this, `${name}-lambda-asset`, {
      path: absoluteAssetPath,
      type: assetType,
    });

    const assumeRolePolicy = JSON.stringify({
      Version: "2012-10-17",
      Statement: [LAMBDA_ASSUME_ROLE_POLICY],
    });

    this.role = new IamRole(this, `${name}-function-role`, {
      assumeRolePolicy,
    });

    this.gw = new Apigatewayv2Api(this, 'KiteApi', {
      name: 'KiteApi',
      protocolType: 'WEBSOCKET',
    });

    this.integration = new ApiGatewayv2Integration(this, 'MyIntegration', {
      apiId: this.gw.id,
      integrationType: 'AWS_PROXY',
      integrationUri: lambda.fn.arn,
    });  }

  grant(policyStatement: iam.PolicyStatement): this {
    const statement = policyStatement.for(this.role.arn).toJSON();
    const policy = JSON.stringify({
      Version: "2012-10-17",
      Statement: [statement],
    });
    this.role.putInlinePolicy([{ policy }]);
    return this;
  }
}
