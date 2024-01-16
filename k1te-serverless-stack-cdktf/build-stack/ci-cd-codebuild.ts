import { Construct } from "constructs";
import {
  Build,
  CODEBUILD_SERVICE_PRINCIPAL,
  LAMBDA_BUILD_OUTPUT_PATH,
} from "./build";
import { Role } from "../kite-stack/iam";
import { Lambda as LambdaPolicy } from "iam-floyd/lib/generated/lambda";
import { S3Bucket } from "@cdktf/provider-aws/lib/s3-bucket";
import { S3 } from "iam-floyd/lib/generated";
import { DriftCheck } from "./drift-check";
import assert = require("assert");
import { EndToEndTest } from "./e2e-test";

export const BUILDSPEC_BASE_PATH = "k1te-serverless-stack-cdktf/build-stack";

export type CodebuildProps = {
  gitRepositoryUrl: string;
  s3SourceBucket: S3Bucket;
  stackName: string;
  devLambdaName: string;
  prodLambdaName?: string;
  s3BucketWithState?: string;
};

export class CiCdCodebuild extends Construct {
  constructor(scope: Construct, id: string, props: Readonly<CodebuildProps>) {
    super(scope, id);

    const {
      gitRepositoryUrl,
      s3SourceBucket,
      devLambdaName,
      prodLambdaName,
      stackName,
      s3BucketWithState,
    } = props;

    const role = new Role(this, "ci-cd-role", {
      forService: CODEBUILD_SERVICE_PRINCIPAL,
    });

    const s3PolicyStatement = new S3()
      .allow()
      .toPutObject()
      .toGetObject()
      .toGetObjectVersion()
      .toGetBucketAcl()
      .toGetBucketLocation()
      .toListBucket()
      .on(
        s3SourceBucket.arn,
        `${s3SourceBucket.arn}/*`,
        "arn:aws:s3:::codepipeline-*",
      );

    const allowToUpdateLambdaPolicy = new LambdaPolicy()
      .allow()
      .toGetFunction()
      .toUpdateFunctionCode()
      .onFunction(devLambdaName);

    role.grant(`allow-crud-${s3SourceBucket.bucket}`, s3PolicyStatement);
    role.grant(`allow-update-${devLambdaName}`, allowToUpdateLambdaPolicy);

    new Build(this, "build-and-deploy-project", {
      role,
      gitRepositoryUrl,
      s3BucketName: s3SourceBucket.bucket,
      computeType: "BUILD_GENERAL1_LARGE",
      image: "quay.io/quarkus/ubi-quarkus-graalvmce-builder-image:jdk-21",
      buildspec: `${BUILDSPEC_BASE_PATH}/build-and-deploy-buildspec.yml`,
      description:
        "It's used to create native executable for ARM64 platform and update Lambda code when code is changed in GitHub main branch",
      environmentVariable: [
        {
          name: "FUNCTION",
          value: devLambdaName,
        },
        {
          name: "EVENT", // Event for running end-to-end tests
          value: JSON.stringify({
            DetailType: "test",
            Source: "codebuild",
            Detail: "{}",
          }),
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

    const endToEndTest = new EndToEndTest(this, "e2e-test", {
      role,
      gitRepositoryUrl,
      s3SourceBucket,
    });

    if (prodLambdaName) {
      const allowToUpdateLambdaPolicy = new LambdaPolicy()
        .allow()
        .toGetFunction()
        .toUpdateFunctionCode()
        .onFunction(prodLambdaName);

      role.grant(`allow-update-${prodLambdaName}`, allowToUpdateLambdaPolicy);

      //I would like to use Lambda compute type here since it costs less, but we can't create it via Terraform
      //https://github.com/hashicorp/terraform-provider-aws/issues/34376
      new Build(this, "deploy-on-tag-project", {
        role,
        gitRepositoryUrl,
        image: "aws/codebuild/amazonlinux2-aarch64-standard:3.0",
        buildspec: `${BUILDSPEC_BASE_PATH}/deploy-on-tag-buildspec.yml`,
        description:
          "It's invoked when new tag is published to GitHub repository then it updates prod function",
        environmentVariable: [
          {
            name: "FUNCTION",
            value: prodLambdaName,
          },
          {
            name: "S3_BUCKET",
            value: s3SourceBucket.bucket,
          },
          {
            name: "S3_KEY",
            value: LAMBDA_BUILD_OUTPUT_PATH,
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
    }

    if (s3BucketWithState) {
      const emailToSendAlarmTo = process.env.EMAIL;
      assert(
        emailToSendAlarmTo,
        "EMAIL is not specified in .env file, but It must be there to create DriftCheck",
      );

      new DriftCheck(this, "drift-check", {
        role,
        gitRepositoryUrl,
        s3BucketWithState,
        stackName,
        emailToSendAlarmTo,
        telegramStatePath: endToEndTest.telegramStatePath,
      });
    }
  }
}
