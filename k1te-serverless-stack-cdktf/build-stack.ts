import { TerraformStack } from "cdktf";
import { Construct } from "constructs";
import { AwsProvider } from "@cdktf/provider-aws/lib/provider";
import { BuildComponent, CODEBUILD_SERVICE_PRINCIPAL } from "./build-component";
import { Role } from "./iam";

export class BuildStack extends TerraformStack {
  constructor(scope: Construct, id: string) {
    super(scope, id);

    new AwsProvider(this, "AWS");

    const role = new Role(this, "lambda-build-role", {
      forService: CODEBUILD_SERVICE_PRINCIPAL,
    });

    new BuildComponent(this, "arm64-lambda-build", { role }).showOutput();
  }
}
