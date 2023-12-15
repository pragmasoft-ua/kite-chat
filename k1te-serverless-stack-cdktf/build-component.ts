import { Construct } from "constructs";
import { Role } from "./iam";
import { S3Bucket } from "@cdktf/provider-aws/lib/s3-bucket";
import { Cloudwatch, S3 } from "iam-floyd/lib/generated";
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
import { TerraformOutput } from "cdktf";

export const CODEBUILD_SERVICE_PRINCIPAL = "codebuild.amazonaws.com";
const LAMBDA_BUILD_SPEC = `
version: 0.2
phases:
  build:
    on-failure: ABORT
    commands:
      - ./mvnw -pl k1te-serverless -am install -Dnative -Dquarkus.native.native-image-xmx=1700m
artifacts:
  files:
    - k1te-serverless/target/function.zip
  name: build
  discard-paths: yes`;

export type LambdaBuildProps = {
  role: Role;
  gitRepositoryUrl: string;
  buildspec?: string;
  image?: string;
  artifactType?: "S3" | "NO_ARTIFACTS";
  environmentVariable?: CodebuildProjectEnvironmentEnvironmentVariable[];
  buildTimeout?: number;
  description?: string;
};

export class BuildComponent extends Construct {
  private readonly s3Bucket?: S3Bucket;
  private readonly codeBuildProject: CodebuildProject;

  constructor(scope: Construct, id: string, props: Readonly<LambdaBuildProps>) {
    super(scope, id);

    const {
      role,
      gitRepositoryUrl,
      buildspec,
      image = "quay.io/quarkus/ubi-quarkus-graalvmce-builder-image:jdk-21",
      artifactType = "S3",
      environmentVariable,
      buildTimeout = 12,
      description,
    } = props;

    let artifacts: CodebuildProjectArtifacts = {
      type: artifactType,
    };

    if (artifactType === "S3") {
      const s3Bucket = new S3Bucket(this, "s3-build-bucket", {
        bucketPrefix: "build-bucket-",
        forceDestroy: true,
      });

      const s3PolicyStatement = new S3()
        .allow()
        .toPutObject()
        .toGetObject()
        .toGetObjectVersion()
        .toGetBucketAcl()
        .toGetBucketLocation()
        .on(s3Bucket.arn, `${s3Bucket.arn}/*`, "arn:aws:s3:::codepipeline-*");

      role.grant(`allow-crud-${s3Bucket.bucket}`, s3PolicyStatement);

      artifacts = {
        type: artifactType,
        location: s3Bucket.bucket,
        name: "build",
        packaging: "NONE",
      };
      this.s3Bucket = s3Bucket;
    }

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
        buildspec: buildspec ?? LAMBDA_BUILD_SPEC,
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

  showOutput() {
    new TerraformOutput(this, "CODEBUILD_PROJECT", {
      value: this.codeBuildProject.name,
      description:
        "Name of CodeBuild Project that you can use to start build manually",
    });

    if (this.s3Bucket) {
      new TerraformOutput(this, "LAMBDA_S3_BUCKET", {
        value: this.s3Bucket.bucket,
        description:
          "This S3 Bucket will contain function.zip archive with built Lambda for ARM64 platform",
      });

      new TerraformOutput(this, "LAMBDA_BUILT_OBJECT_KEY", {
        value: "build/function.zip",
        description:
          "S3 Object Key of archive with built Lambda for ARM64 platform",
      });
    }
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
