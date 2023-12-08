import { LambdaFunction } from "@cdktf/provider-aws/lib/lambda-function";
import { Lambda as LambdaPolicy } from "iam-floyd/lib/generated";

import { CloudwatchLogGroup } from "@cdktf/provider-aws/lib/cloudwatch-log-group";
import { LambdaAlias } from "@cdktf/provider-aws/lib/lambda-alias";
import { Lazy } from "cdktf";
import { Construct } from "constructs";
import { Resource } from "./asset";
import { Grantable, ServicePrincipal } from "./grantable";
import { Role } from "./iam";
import { LambdaPermission } from "@cdktf/provider-aws/lib/lambda-permission";

export const LAMBDA_SERVICE_PRINCIPAL = "lambda.amazonaws.com";

export type LambdaProps = {
  asset: Resource;
  isSnapStart?: boolean;
  architecture?: string;
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
  architecture: "x86_64",
};

export class Lambda extends Construct {
  readonly fn: LambdaFunction;
  readonly alias: LambdaAlias;
  readonly role: Role;
  readonly environment: { [key: string]: string };

  constructor(scope: Construct, id: string, props: Readonly<LambdaProps>) {
    super(scope, id);

    const {
      role,
      asset,
      memorySize,
      timeout,
      environment,
      architecture,
      isSnapStart = false,
    } = {
      ...DEFAULT_PROPS,
      ...props,
    };
    this.environment = environment ?? {};
    const snapStart = isSnapStart
      ? {
          applyOn: "PublishedVersions",
        }
      : undefined;

    this.role = role ?? this.defaultRole();

    this.fn = new LambdaFunction(this, "fn", {
      functionName: id,
      role: this.role.arn,
      memorySize,
      timeout,
      environment: {
        variables: Lazy.anyValue({
          produce: () => {
            return this.environment;
          },
        }) as unknown as Record<string, string>,
      },
      architectures: [architecture!],
      snapStart,
      publish: true,
      filename: asset.path,
      sourceCodeHash: asset.hash,
      runtime: asset.runtime,
      handler: asset.handler,
      lifecycle: {
        ignoreChanges: ["s3_key", "s3_bucket", "layers"], //, "filename"],
      },
    });

    this.alias = new LambdaAlias(this, "alias", {
      name: `${id.replaceAll(/dev-|prod-/gi, "")}-alias`,
      functionName: this.fn.functionName,
      functionVersion: this.fn.version,
      lifecycle: {
        ignoreChanges: ["function_version", "description", "routing_config"],
      },
    });

    new CloudwatchLogGroup(this, "logs", {
      name: `/aws/lambda/${id}`,
      retentionInDays: 7,
    });
  }

  private defaultRole(): Role {
    return new Role(this, "default-role", {
      forService: LAMBDA_SERVICE_PRINCIPAL,
    }).attachManagedPolicyArn(
      "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
    );
  }

  allowToInvoke(to: Grantable) {
    const policyStatement = new LambdaPolicy()
      .allow()
      .toInvokeFunction()
      .on(this.alias.arn);
    to.grant(`allow-invoke-${this.fn.functionName}`, policyStatement);
    return this;
  }

  allowToUpdate(to: Grantable) {
    const policyStatement = new LambdaPolicy()
      .allow()
      .toGetFunction()
      .toUpdateFunctionCode()
      .toUpdateAlias()
      .toPublishVersion()
      .on(this.fn.arn, this.arn);
    to.grant(`allow-update-${this.fn.functionName}`, policyStatement);
  }

  allowInvocationForService(service: ServicePrincipal) {
    const { principal, sourceArn } = service;
    const serviceName = principal.replaceAll(".", "-");
    const statementId = `allow-invocation-for-${serviceName}`;
    new LambdaPermission(this, statementId, {
      statementId,
      functionName: this.fn.functionName,
      qualifier: this.alias.name,
      action: "lambda:InvokeFunction",
      principal,
      sourceArn,
    });
    return this;
  }

  get arn() {
    return this.alias.arn;
  }

  get functionName() {
    return this.fn.functionName;
  }
}
