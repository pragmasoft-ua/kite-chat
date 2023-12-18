import { LambdaFunction } from "@cdktf/provider-aws/lib/lambda-function";
import { Lambda as LambdaPolicy } from "iam-floyd/lib/generated";

import { CloudwatchLogGroup } from "@cdktf/provider-aws/lib/cloudwatch-log-group";
import { Lazy } from "cdktf";
import { Construct } from "constructs";
import { Grantable, ServicePrincipal } from "./grantable";
import { Role } from "./iam";
import { LambdaPermission } from "@cdktf/provider-aws/lib/lambda-permission";

export const LAMBDA_SERVICE_PRINCIPAL = "lambda.amazonaws.com";
export type Runtime =
  | "java11"
  | "java17"
  | "java21"
  | "nodejs18.x"
  | "provided.al2";
export type Handler =
  | "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"
  | "index.handler"
  | "hello.handler";
export type Architecture = "x86_64" | "arm64";

export type LambdaProps = {
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

  architecture?: Architecture;
  runtime?: Runtime;
  handler?: Handler;
  s3Bucket: string;
  s3Key: string;
};
const DEFAULT_PROPS: Partial<LambdaProps> = {
  logRetentionInDays: 7,
  timeout: 15,
  memorySize: 256,
  architecture: "arm64",
  handler: "hello.handler",
};

export class Lambda extends Construct {
  readonly fn: LambdaFunction;
  readonly role: Role;
  readonly environment: { [key: string]: string };

  constructor(scope: Construct, id: string, props: Readonly<LambdaProps>) {
    super(scope, id);

    const {
      role,
      memorySize,
      timeout,
      environment,
      architecture,
      s3Bucket,
      s3Key,
      runtime,
      handler,
    } = {
      ...DEFAULT_PROPS,
      ...props,
    };
    this.environment = environment ?? {};

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
      s3Bucket,
      s3Key,
      runtime,
      handler,
      lifecycle: {
        ignoreChanges: ["s3_key", "s3_bucket", "layers"], //, "filename"],
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
      "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole",
    );
  }

  allowToInvoke(to: Grantable) {
    const policyStatement = new LambdaPolicy()
      .allow()
      .toInvokeFunction()
      .on(this.fn.arn);
    to.grant(`allow-invoke-${this.fn.functionName}`, policyStatement);
    return this;
  }

  allowInvocationForService(service: ServicePrincipal) {
    const { principal, sourceArn } = service;
    const serviceName = principal.replaceAll(".", "-");
    const statementId = `allow-invocation-for-${serviceName}`;
    new LambdaPermission(this, statementId, {
      statementId,
      functionName: this.fn.functionName,
      action: "lambda:InvokeFunction",
      principal,
      sourceArn,
    });
    return this;
  }

  get arn() {
    return this.fn.arn;
  }

  get functionName() {
    return this.fn.functionName;
  }
}
