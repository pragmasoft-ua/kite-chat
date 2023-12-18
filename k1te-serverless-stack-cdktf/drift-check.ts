import { Construct } from "constructs";
import { CloudwatchEventRule } from "@cdktf/provider-aws/lib/cloudwatch-event-rule";
import { SnsTopic } from "@cdktf/provider-aws/lib/sns-topic";
import { SnsTopicPolicy } from "@cdktf/provider-aws/lib/sns-topic-policy";
import { Codebuild, Events, S3, Sns } from "iam-floyd/lib/generated";
import { SnsTopicSubscription } from "@cdktf/provider-aws/lib/sns-topic-subscription";
import { CloudwatchEventTarget } from "@cdktf/provider-aws/lib/cloudwatch-event-target";
import { CodebuildProjectEnvironmentEnvironmentVariable } from "@cdktf/provider-aws/lib/codebuild-project";
import { Build } from "./build";
import { Role } from "./iam";
import { SchedulerSchedule } from "@cdktf/provider-aws/lib/scheduler-schedule";

const environmentVariableNames = [
  "CLOUDFLARE_API_TOKEN",
  "TELEGRAM_BOT_TOKEN",
  "TELEGRAM_PROD_BOT_TOKEN",
  "EMAIL",
];

export type DriftCheckProps = {
  role: Role;
  gitRepositoryUrl: string;
  s3BucketWithState: string;
  emailToSendAlarmTo: string;
  driftCheckCronEx?: string;
  timezone?: string;
};

export class DriftCheck extends Construct {
  constructor(scope: Construct, id: string, props: Readonly<DriftCheckProps>) {
    super(scope, id);

    const {
      role,
      gitRepositoryUrl,
      s3BucketWithState,
      emailToSendAlarmTo,
      driftCheckCronEx = "0 12 ? * 2-6 *", // Invoke DriftCheck at 12 PM on Monday-Friday
      timezone = "Europe/Kiev",
    } = props;

    const readS3BucketStatePolicy = new S3()
      .allow()
      .toListBucket()
      .toGetObject()
      .onBucket(s3BucketWithState)
      .onObject(s3BucketWithState, "*");

    const eventsPolicy = new Events().toPutEvents().on("*");

    role.grant("allow-read-tf-state", readS3BucketStatePolicy);
    role.grant("allow-put-events", eventsPolicy);

    const environmentVariable: CodebuildProjectEnvironmentEnvironmentVariable[] =
      environmentVariableNames
        .map((name) => ({
          name: name,
          value: process.env[name],
        }))
        .filter((item) => item.value !== undefined)
        .map((item) => item as CodebuildProjectEnvironmentEnvironmentVariable);

    environmentVariable.push({
      name: "EVENT",
      value: JSON.stringify({
        DetailType: "drift",
        Source: "codebuild",
        Detail: "{}",
      }),
    });

    const driftCheckProject = new Build(this, "drift-check-project", {
      role,
      gitRepositoryUrl,
      buildspec: "k1te-serverless-stack-cdktf/drift-check-buildspec.yml",
      image: "public.ecr.aws/b1z6s7i3/cdktf-arm64-build:latest", // It's a custom image for ARM64 with TF, CDKTF, AWS CLI, Node.js, npm
      environmentVariable,
      description:
        "It's used to check drift in Terraform stack and send event to EventBridge if drift is detected",
      buildTimeout: 5,
    });

    const schedulerRole = new Role(this, "codebuild-invocation-role", {
      forService: "scheduler.amazonaws.com",
    });

    const codebuildStartPolicy = new Codebuild()
      .toStartBuild()
      .on(driftCheckProject.arn);

    schedulerRole.grant("allow-start-build", codebuildStartPolicy);

    new SchedulerSchedule(this, "drift-scheduler", {
      name: "drift-scheduler",
      flexibleTimeWindow: {
        mode: "OFF",
      },
      target: {
        arn: driftCheckProject.arn,
        roleArn: schedulerRole.arn,
      },
      scheduleExpressionTimezone: timezone,
      scheduleExpression: `cron(${driftCheckCronEx})`,
    });

    const driftEventRule = new CloudwatchEventRule(this, "drift-event-rule", {
      name: "drift-event-rule",
      eventPattern: `{
          "source": ["codebuild"],
          "detail-type": ["drift"]
        }
      `,
    });

    const snsTopic = new SnsTopic(this, "drift-topic", {
      name: "drift-topic",
    });

    new SnsTopicPolicy(this, "sns-access-policy", {
      arn: snsTopic.arn,
      policy: JSON.stringify({
        Version: "2012-10-17",
        Statement: [
          new Sns()
            .allow()
            .toPublish()
            .forService("events.amazonaws.com")
            .on(snsTopic.arn)
            .toJSON(),
        ],
      }),
    });

    new SnsTopicSubscription(this, "drift-sns-sub", {
      protocol: "email",
      endpoint: emailToSendAlarmTo,
      topicArn: snsTopic.arn,
    });

    new CloudwatchEventTarget(this, "sns-target", {
      rule: driftEventRule.name,
      arn: snsTopic.arn,
      inputTransformer: {
        inputTemplate:
          '"Drift was detected in the Stack! You should re-deploy stack manually."',
      },
    });
  }
}
