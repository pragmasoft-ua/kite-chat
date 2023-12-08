import { Construct } from "constructs";
import { CodebuildProject } from "@cdktf/provider-aws/lib/codebuild-project";
import { S3Bucket } from "@cdktf/provider-aws/lib/s3-bucket";
import { CodebuildWebhook } from "@cdktf/provider-aws/lib/codebuild-webhook";
import { Role } from "./iam";
import { Lambda } from "./lambda";
import { S3 } from "iam-floyd/lib/generated";
import { CloudwatchLogGroup } from "@cdktf/provider-aws/lib/cloudwatch-log-group";

export type CodebuildProps = {
  functions: Lambda[];
  gitProjectUrl: string;
  buildspecPath?: string;
  buildTimeout?: number;
};

const CODEBUILD_SERVICE_PRINCIPAL = "codebuild.amazonaws.com";

export class Codebuild extends Construct {
  constructor(scope: Construct, id: string, props: Readonly<CodebuildProps>) {
    super(scope, id);

    const {
      functions,
      gitProjectUrl,
      buildspecPath = "k1te-serverless-stack-cdktf/buildspec.yml",
      buildTimeout = 12,
    } = props;

    const role = new Role(this, "code-build-role", {
      forService: CODEBUILD_SERVICE_PRINCIPAL,
    });

    const s3Bucket = new S3Bucket(this, "codebuild-artifact-storage", {
      bucketPrefix: `${id}-artifact-storage`,
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
    role.attachManagedPolicyArn(
      "arn:aws:iam::aws:policy/CloudWatchLogsFullAccess"
    );
    functions.forEach((fun) => fun.allowToUpdate(role));

    const logGroup = new CloudwatchLogGroup(this, "logs", {
      name: `/aws/${id}`,
      retentionInDays: 7,
    });

    const codebuildProject = new CodebuildProject(this, "code-build", {
      name: id,
      serviceRole: role.arn,
      source: {
        type: "GITHUB",
        gitCloneDepth: 1,
        location: gitProjectUrl,
        buildspec: buildspecPath,
      },
      sourceVersion: "main",
      environment: {
        computeType: "BUILD_GENERAL1_SMALL",
        type: "ARM_CONTAINER",
        image: "quay.io/quarkus/ubi-quarkus-graalvmce-builder-image:jdk-21",
        privilegedMode: true,
        environmentVariable: [
          {
            name: "FUNCTIONS",
            value: functions.map((fun) => fun.functionName).join(","),
          },
        ],
      },
      artifacts: {
        type: "S3",
        location: s3Bucket.bucket,
        name: "build",
        packaging: "NONE",
      },
      cache: {
        modes: ["LOCAL_SOURCE_CACHE", "LOCAL_CUSTOM_CACHE"],
        type: "LOCAL",
      },
      buildTimeout,
      logsConfig: {
        cloudwatchLogs: {
          groupName: logGroup.name,
        },
      },
    });

    /**
     * The AWS account that Terraform uses to create this resource
     * must have authorized CodeBuild to access GitHub's OAuth API
     * in each applicable region. This is a manual step that must
     * be done before creating webhooks with this resource.
     * https://docs.aws.amazon.com/codebuild/latest/userguide/access-tokens.html
     * */
    new CodebuildWebhook(this, "code-build-webhook", {
      projectName: codebuildProject.name,
      buildType: "BUILD",
      filterGroup: [
        {
          filter: [
            {
              type: "EVENT",
              pattern: "PUSH,PULL_REQUEST_MERGED",
            },
            {
              type: "HEAD_REF",
              pattern: "^refs/heads/main$",
            },
            {
              type: "FILE_PATH",
              pattern: "^k1te-backend/src/.+|^k1te-serverless/src/.+",
            },
          ],
        },
      ],
    });
  }
}
