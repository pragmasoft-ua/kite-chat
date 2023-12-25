import { Construct } from "constructs";
import { Role } from "../kite-stack/iam";
import { Cloudwatch } from "iam-floyd/lib/generated";
import { CloudwatchLogGroup } from "@cdktf/provider-aws/lib/cloudwatch-log-group";
import {
  CodebuildProject,
  CodebuildProjectArtifacts,
  CodebuildProjectEnvironmentEnvironmentVariable,
} from "@cdktf/provider-aws/lib/codebuild-project";
import {
  CodebuildWebhook,
  CodebuildWebhookFilterGroupFilter,
} from "@cdktf/provider-aws/lib/codebuild-webhook";

export const BUILD_DIR = "build";
export const OUTPUT_PATH = `${BUILD_DIR}/function.zip`;
export const CODEBUILD_SERVICE_PRINCIPAL = "codebuild.amazonaws.com";

export type LambdaBuildProps = {
  role: Role;
  gitRepositoryUrl: string;
  buildspec: string;
  image: string;
  s3BucketName?: string;
  environmentVariable?: CodebuildProjectEnvironmentEnvironmentVariable[];
  buildTimeout?: number;
  description?: string;
};

export class Build extends Construct {
  private readonly codeBuildProject: CodebuildProject;

  constructor(scope: Construct, id: string, props: Readonly<LambdaBuildProps>) {
    super(scope, id);

    const {
      role,
      gitRepositoryUrl,
      buildspec,
      image,
      s3BucketName,
      environmentVariable,
      buildTimeout = 12,
      description,
    } = props;

    const artifacts: CodebuildProjectArtifacts = s3BucketName
      ? {
          type: "S3",
          location: s3BucketName,
          name: BUILD_DIR,
          packaging: "NONE",
        }
      : {
          type: "NO_ARTIFACTS",
        };

    const logGroup = new CloudwatchLogGroup(this, `${id}-logs`, {
      name: `/aws/${id}`,
      retentionInDays: 7,
    });

    const cloudwatchPolicyStatement = new Cloudwatch()
      .allow()
      .to("logs:CreateLogGroup")
      .to("logs:CreateLogStream")
      .to("logs:PutLogEvents")
      .on(logGroup.arn, `${logGroup.arn}:*`);
    role.grant(
      `allow-all-logs-actions-to-${id}-logs`,
      cloudwatchPolicyStatement,
    );

    this.codeBuildProject = new CodebuildProject(this, id, {
      name: id,
      description,
      serviceRole: role.arn,
      source: {
        type: "GITHUB",
        gitCloneDepth: 1,
        location: gitRepositoryUrl,
        buildspec,
      },
      sourceVersion: "main",
      environment: {
        computeType: "BUILD_GENERAL1_SMALL",
        type: "ARM_CONTAINER",
        image,
        privilegedMode: true,
        environmentVariable,
      },
      artifacts,
      buildTimeout,
      cache: {
        modes: ["LOCAL_SOURCE_CACHE", "LOCAL_CUSTOM_CACHE"],
        type: "LOCAL",
      },
      logsConfig: {
        cloudwatchLogs: {
          groupName: logGroup.name,
        },
      },
    });
  }

  /**
   * The AWS account that Terraform uses to create this resource
   * must have authorized CodeBuild to access GitHub's OAuth API
   * in each applicable region. This is a manual step that must
   * be done before creating webhooks with this resource.
   * https://docs.aws.amazon.com/codebuild/latest/userguide/access-tokens.html
   * */
  attachWebHook(filter: CodebuildWebhookFilterGroupFilter[]) {
    new CodebuildWebhook(this, `${this.node.id}-webhook`, {
      projectName: this.codeBuildProject.name,
      buildType: "BUILD",
      filterGroup: [
        {
          filter,
        },
      ],
    });
  }

  get arn() {
    return this.codeBuildProject.arn;
  }
}
