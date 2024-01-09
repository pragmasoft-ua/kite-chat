import { Construct } from "constructs";
import { AssetS3Source } from "./asset";
import { Build } from "./build";
import { BUILDSPEC_BASE_PATH } from "./ci-cd-codebuild";
import { S3Bucket } from "@cdktf/provider-aws/lib/s3-bucket";
import { Role } from "../kite-stack/iam";
import { Codebuild } from "iam-floyd/lib/generated";
import { CloudwatchEventRule } from "@cdktf/provider-aws/lib/cloudwatch-event-rule";
import { CloudwatchEventTarget } from "@cdktf/provider-aws/lib/cloudwatch-event-target";
import { CodebuildReportGroup } from "@cdktf/provider-aws/lib/codebuild-report-group";

export type EndToEndTestProps = {
  role: Role;
  gitRepositoryUrl: string;
  s3SourceBucket: S3Bucket;
};

export class EndToEndTest extends Construct {
  readonly telegramStatePath: string;
  constructor(
    scope: Construct,
    id: string,
    props: Readonly<EndToEndTestProps>,
  ) {
    super(scope, id);
    const { role, gitRepositoryUrl, s3SourceBucket } = props;
    const projectName = `${id}-project`;

    const reportGroup = new CodebuildReportGroup(
      this,
      "e2e-test-report-group",
      {
        name: `${projectName}-e2e-test-report-group`,
        type: "TEST",
        exportConfig: {
          type: "NO_EXPORT",
        },
        deleteReports: true,
      },
    );

    const allowTestReports = new Codebuild()
      .allow()
      .toCreateReport()
      .toUpdateReport()
      .toBatchPutTestCases()
      .toBatchPutCodeCoverages()
      .onReportGroup(reportGroup.name);

    role.grant(`allow-test-reports`, allowTestReports);

    const telegramAuthState = new AssetS3Source(this, "auth-state", {
      s3BucketName: s3SourceBucket.bucket,
      relativeProjectPath: "../../k1te-serverless-test",
      target: "auth.json",
    });
    this.telegramStatePath = telegramAuthState.fullPath;

    const testProject = new Build(this, projectName, {
      role,
      gitRepositoryUrl,
      buildspec: `${BUILDSPEC_BASE_PATH}/e2e-test-buildspec.yml`,
      image: "aws/codebuild/standard:7.0",
      type: "LINUX_CONTAINER",
      description: "Runs e2e tests in Playwright",
      environmentVariable: [
        { name: "AUTH_PATH", value: telegramAuthState.fullPath },
      ],
    });

    const eventRole = new Role(this, "test-project-invocation-role", {
      forService: "events.amazonaws.com",
    });

    const codebuildStartPolicy = new Codebuild()
      .toStartBuild()
      .on(testProject.arn);

    eventRole.grant("allow-start-build", codebuildStartPolicy);

    const rule = new CloudwatchEventRule(this, "test-event-rule", {
      name: "test-event-rule",
      eventPattern: `{
          "source": ["codebuild"],
          "detail-type": ["test"]
        }
      `,
    });

    new CloudwatchEventTarget(this, "test-project-target", {
      rule: rule.name,
      arn: testProject.arn,
      roleArn: eventRole.arn,
    });
  }
}
