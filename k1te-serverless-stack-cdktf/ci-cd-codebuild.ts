import { Construct } from "constructs";
import { Lambda } from "./lambda";
import { BuildComponent, CODEBUILD_SERVICE_PRINCIPAL } from "./build-component";
import { Role } from "./iam";
import { CodebuildProjectEnvironmentEnvironmentVariable } from "@cdktf/provider-aws/lib/codebuild-project";
import { Codebuild, Events, S3, Sns } from "iam-floyd/lib/generated";
import { CloudwatchEventRule } from "@cdktf/provider-aws/lib/cloudwatch-event-rule";
import { CloudwatchEventTarget } from "@cdktf/provider-aws/lib/cloudwatch-event-target";
import { SnsTopic } from "@cdktf/provider-aws/lib/sns-topic";
import { SnsTopicSubscription } from "@cdktf/provider-aws/lib/sns-topic-subscription";
import { SchedulerSchedule } from "@cdktf/provider-aws/lib/scheduler-schedule";
import { SnsTopicPolicy } from "@cdktf/provider-aws/lib/sns-topic-policy";

const environmentVariableNames = [
  "CLOUDFLARE_API_TOKEN",
  "TELEGRAM_BOT_TOKEN",
  "TELEGRAM_DEV_BOT_TOKEN",
  "MAIN_LAMBDA_S3_BUCKET",
  "MAIN_LAMBDA_S3_OBJECT_KEY",
];

export type CodebuildProps = {
  gitRepositoryUrl: string;
  functions: Lambda[];
  prodFunctionName: string;
  s3BucketWithState: string;
  emailToSendAlarm: string;
  driftCheckCronEx?: string;
};

export class CiCdCodebuild extends Construct {
  constructor(scope: Construct, id: string, props: Readonly<CodebuildProps>) {
    super(scope, id);

    const {
      gitRepositoryUrl,
      functions,
      prodFunctionName,
      s3BucketWithState,
      driftCheckCronEx = "0 12 ? * 2-6 *",
      emailToSendAlarm,
    } = props;

    const role = new Role(this, "ci-cd-role", {
      forService: CODEBUILD_SERVICE_PRINCIPAL,
    });

    functions.forEach((fun) => fun.allowToUpdate(role));

    new BuildComponent(this, "main-lambda-build", {
      role,
      gitRepositoryUrl,
      buildspec: "k1te-serverless-stack-cdktf/buildspec.yml",
      description:
        "It's used to create native executable for ARM64 platform and update Lambda code when code is changed in GitHub main branch",
      environmentVariable: [
        {
          name: "FUNCTIONS",
          value: functions.map((fun) => fun.functionName).join(","),
        },
      ],
    }).attachWebHook([
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
    ]);

    //I would like to use Lambda compute type here since it costs less, but we can't create it via Terraform
    //https://github.com/hashicorp/terraform-provider-aws/issues/34376
    new BuildComponent(this, "tag-codebuild-project", {
      role: role,
      gitRepositoryUrl,
      image: "aws/codebuild/amazonlinux2-aarch64-standard:3.0",
      description:
        "It's invoked when new tag is published to GitHub repository then it updates prod function",
      artifactType: "NO_ARTIFACTS",
      buildspec: "k1te-serverless-stack-cdktf/tag-buildspec.yml",
      environmentVariable: [
        {
          name: "FUNCTION",
          value: prodFunctionName,
        },
      ],
      buildTimeout: 5,
    }).attachWebHook([
      {
        type: "EVENT",
        pattern: "PUSH",
      },
      {
        type: "HEAD_REF",
        pattern: "^refs/tags/.*",
      },
    ]);

    // Drift check
    /**
     * 1) CodeBuild Project
     * 2) Give permission to S3Bucket with state
     * 3) Give permission to PutEvents
     * 4) Specify custom image
     * 5) specify env from .env as envVariables
     * 6) Add Event variable that will be sent to EventBridge
     *
     * EventBridge Rule
     * Specify scheduler every day or CRON
     * Add Target CodeBuild Project
     * add permission to invoke CodeBuild Start Project
     *
     * SNS Topic that send emails
     * confirm email
     *
     * EventBridge Rule
     * Specify rule that process Event given to CodeBuild
     * Add Target SNS that send Message
     * Transform event to input before sending
     * Add Role to trigger SNS Topic
     *
     * Rectify Docs
     * */

    const readS3BucketStatePolicy = new S3()
      .allow()
      .allListActions()
      .allReadActions()
      .to("s3:ListObjectsV2")
      .to("s3:HeadObject")
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
        Detail: {},
      }),
    });

    const driftCheckProject = new BuildComponent(this, "drift-check-project", {
      role,
      gitRepositoryUrl,
      buildspec: "k1te-serverless-stack-cdktf/drift-check-buildspec.yml",
      image: "public.ecr.aws/b1z6s7i3/cdktf-arm64-build:latest", // It's a custom image for ARM64 with TF, CDKTF, AWS CLI, Node.js, npm
      environmentVariable,
      description:
        "It's used to check drift in Terraform stack and send event to EventBridge if drift is detected",
      artifactType: "NO_ARTIFACTS",
      buildTimeout: 5,
    });

    // Drift Check Scheduler
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
      scheduleExpressionTimezone: "Europe/Kiev",
      scheduleExpression: `cron(${driftCheckCronEx})`,
    });

    //   Drift Event
    const driftEvent = new CloudwatchEventRule(this, "drift-event-rule", {
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
      endpoint: emailToSendAlarm,
      topicArn: snsTopic.arn,
    });

    new CloudwatchEventTarget(this, "sns-target", {
      rule: driftEvent.name,
      arn: snsTopic.arn,
      inputTransformer: {
        inputTemplate: '"Drift was detected in the Stack"',
      },
    });
  }
}
