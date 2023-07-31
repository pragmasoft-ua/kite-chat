import { LambdaFunction } from "@cdktf/provider-aws/lib/lambda-function";

import { Construct } from "constructs";
import { QuarkusLambdaAsset } from "./asset";
import { Role } from "./iam";
import { Grantable } from "./grantable";
import { Lambda as LambdaPolicy } from "iam-floyd/lib/generated";
import { CloudwatchLogGroup } from "@cdktf/provider-aws/lib/cloudwatch-log-group";

export const LAMBDA_SERVICE_PRINCIPAL = "lambda.amazonaws.com";

export type LambdaProps = {
  asset: QuarkusLambdaAsset;
  role?: Role;

  /**
   * The environment variables to be passed to the Lambda function.
   */
  environment?: { [key: string]: string };

  /**
   * The log retention period in days. Defaults to 7.
   */
  logRetentionInDays?: number;

  /**
   * The memory limit in MB. Defaults to 256.
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
  environment: {},
};

export class Lambda extends Construct {
  readonly fn: LambdaFunction;
  readonly role: Role;

  constructor(scope: Construct, id: string, props: Readonly<LambdaProps>) {
    super(scope, id);

    const { role, asset, memorySize, timeout, environment } = Object.assign(
      {},
      DEFAULT_PROPS,
      props
    );

    this.role = role ?? this.defaultRole();

    this.fn = new LambdaFunction(scope, this.node.id + "-" + this.node.addr, {
      functionName: id,
      role: this.role.arn,
      memorySize,
      timeout,
      environment: {
        variables: environment,
      },
      architectures: ["arm64"],
      filename: asset.path,
      sourceCodeHash: asset.hash,
      runtime: asset.runtime,
      handler: asset.handler,
    });

    new CloudwatchLogGroup(this, `${id}-logs`, {
      name: `/aws/lambda/${id}`,
      retentionInDays: 7,
    });
  }

  private defaultRole(): Role {
    return new Role(this, `${this.node.id}-execution-role`, {
      forService: LAMBDA_SERVICE_PRINCIPAL,
    }).attachManagedPolicyArn(
      "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
    );
  }

  public allowToInvoke(to: Grantable) {
    const policyStatement = new LambdaPolicy()
      .allow()
      .toInvokeFunction()
      .on(this.fn.arn);
    to.grant(policyStatement);
    return this;
  }
}
