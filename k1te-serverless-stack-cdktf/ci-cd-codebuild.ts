import { Construct } from "constructs";
import { Lambda } from "./lambda";
import { BuildComponent, CODEBUILD_SERVICE_PRINCIPAL } from "./build-component";
import { Role } from "./iam";

export type CodebuildProps = {
  functions: Lambda[];
  prodFunctionName: string;
};

export class CiCdCodebuild extends Construct {
  constructor(scope: Construct, id: string, props: Readonly<CodebuildProps>) {
    super(scope, id);

    const { functions, prodFunctionName } = props;

    const role = new Role(this, "ci-cd-role", {
      forService: CODEBUILD_SERVICE_PRINCIPAL,
    });

    functions.forEach((fun) => fun.allowToUpdate(role));

    new BuildComponent(this, "main-lambda-build", {
      role,
      buildspec: "k1te-serverless-stack-cdktf/buildspec.yml",
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
  }
}
