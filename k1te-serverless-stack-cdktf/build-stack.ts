import { TerraformOutput, TerraformStack } from "cdktf";
import { Construct } from "constructs";
import { AwsProvider } from "@cdktf/provider-aws/lib/provider";
import { S3Bucket } from "@cdktf/provider-aws/lib/s3-bucket";
import { CiCdCodebuild } from "./ci-cd-codebuild";
import { S3Source } from "./asset";
import { OUTPUT_PATH } from "./build";
import { ArchiveProvider } from "@cdktf/provider-archive/lib/provider";

export type BuildSpecProps = {
  /**
   * Url to your GitHub repository that will be used to attach
   * CI/CD webhooks
   * */
  gitRepositoryUrl: string;
  /**
   * The name of dev Lambda, It's needed to updated it automatically on PUSH event
   * */
  devLambdaName: string;
  /**
   * The name of prod Lambda, It's needed to update it automatically on TAG event
   * */
  prodLambdaName?: string;
  /**
   * If set to true - MainHandler Lambda will be uploaded from source and not built via CodeBuild. It will look for
   * function.zip in k1te-serverless/target. If there will be no function.zip the error will be thrown. Default is false.
   * IMPORTANT: if this set to true - CodeBuild Project that responsible for DriftCheck will not be created
   * due to function.zip doesn't exist in GitHub Source that is used for drift check because of that Error will always be thrown
   * */
  buildLambdaViaAsset?: boolean;
  /**
   * The name of S3 Bucket where Terraform state is stored. It's used for DriftCheck.
   * If not specified - DriftCheck CodeBuild Project and all related resources will be omitted.
   * */
  s3BucketWithState?: string;
};

export class BuildStack extends TerraformStack {
  readonly devFunctionName: string;
  readonly prodFunctionName?: string;
  readonly sourceBucketName: string;
  readonly functionS3Key: string;
  readonly lifecycleS3Key: string;

  constructor(scope: Construct, id: string, props: Readonly<BuildSpecProps>) {
    super(scope, id);

    const {
      gitRepositoryUrl,
      devLambdaName,
      prodLambdaName,
      buildLambdaViaAsset = false,
      s3BucketWithState,
    } = props;

    new AwsProvider(this, "AWS");
    new ArchiveProvider(this, "ARCHIVE");

    const s3Bucket = new S3Bucket(this, "s3-bucket", {
      bucketPrefix: "s3-source-bucket",
    });

    new CiCdCodebuild(this, "ci-cd-codebuild", {
      gitRepositoryUrl,
      s3SourceBucket: s3Bucket,
      devLambdaName,
      prodLambdaName,
      s3BucketWithState:
        !buildLambdaViaAsset && s3BucketWithState
          ? s3BucketWithState
          : undefined,
    });

    if (buildLambdaViaAsset) {
      new S3Source(this, "function", {
        s3BucketName: s3Bucket.bucket,
        asset: {
          relativeProjectPath: "../k1te-serverless",
        },
      });
    }

    new S3Source(this, "lifecycle", {
      s3BucketName: s3Bucket.bucket,
      archive: {
        sourceFile: "lifecycle-handler/index.mjs",
        output: "lifecycle-handler/lifecycle.zip",
      },
    });

    new TerraformOutput(this, "s3-source-bucket", {
      value: s3Bucket.bucket,
      description:
        "Name of the S3 Bucket where Lifecycle and MainHandler are stored",
    });

    new TerraformOutput(this, "function-s3-key", {
      value: OUTPUT_PATH,
      description: "Name of the S3 Key to MainHandler function",
    });

    new TerraformOutput(this, "lifecycle-s3-key", {
      value: `build/lifecycle.zip`,
      description: "Name of the S3 Key to Lifecycle function",
    });

    this.devFunctionName = devLambdaName;
    this.prodFunctionName = prodLambdaName;
    this.sourceBucketName = s3Bucket.bucket;
    this.functionS3Key = OUTPUT_PATH;
    this.lifecycleS3Key = `build/lifecycle.zip`;
  }
}
