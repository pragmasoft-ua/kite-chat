import { TerraformStack } from "cdktf";
import { Construct } from "constructs";
import { AwsProvider } from "@cdktf/provider-aws/lib/provider";
import { BuildComponent, CODEBUILD_SERVICE_PRINCIPAL } from "./build-component";
import { Role } from "./iam";

export type BuildSpecProps = {
  gitRepositoryUrl: string;
};

export class BuildStack extends TerraformStack {
  constructor(scope: Construct, id: string, props: Readonly<BuildSpecProps>) {
    super(scope, id);

    const { gitRepositoryUrl } = props;

    new AwsProvider(this, "AWS");

    const role = new Role(this, "lambda-build-role", {
      forService: CODEBUILD_SERVICE_PRINCIPAL,
    });

    new BuildComponent(this, "arm64-lambda-build", {
      role,
      gitRepositoryUrl,
    }).showOutput();
  }
}
