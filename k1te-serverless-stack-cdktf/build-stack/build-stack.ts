import { S3Backend, TerraformOutput, TerraformStack } from "cdktf";
import { Construct } from "constructs";
import { AwsProvider } from "@cdktf/provider-aws/lib/provider";
import { S3Bucket } from "@cdktf/provider-aws/lib/s3-bucket";
import { CiCdCodebuild } from "./ci-cd-codebuild";
import { ArchiveS3Source, AssetS3Source } from "./asset";
import { LAMBDA_BUILD_OUTPUT_PATH } from "./build";
import { ArchiveProvider } from "@cdktf/provider-archive/lib/provider";
import {
  DEV_MAIN_LAMBDA_NAME,
  PROD_MAIN_LAMBDA_NAME,
} from "../kite-stack/stage";

export type BuildSpecProps = {
  /**
   * Url to your GitHub repository that will be used to attach
   * CI/CD webhooks
   * */
  gitRepositoryUrl: string;
  /**
   * The name of S3 Bucket where Terraform state is stored. It's used for S3Backend and DriftCheck.
   * In case with DriftCheck it should be a bucket that stores kite-stack's state. The default value
   * for build-stack's state is 'kite/terraform-build.tfstate'
   * */
  s3BucketWithState: string;
  /**
   * AWS region where s3 bucket with state is located.
   * */
  region: string;
  /**
   * If set to true - MainHandler Lambda will be uploaded from source and not built via CodeBuild. It will look for
   * function.zip in k1te-serverless/target. If there will be no function.zip the error will be thrown. Default is false.
   * IMPORTANT: if this set to true - CodeBuild Project that responsible for DriftCheck will not be created
   * due to function.zip doesn't exist in GitHub Source that is used for drift check because of that Error will always be thrown,
   * and it will make DriftCheck impossible.
   * */
  buildLambdaViaAsset?: boolean;
  /**
   * Whether to create prod stage or not
   * */
  prodStage?: boolean;
};

export class BuildStack extends TerraformStack {
  public readonly s3BucketWithState: string;
  public readonly region: string;
  public readonly prodStage: boolean;
  public readonly s3SourceBucketName: string;
  public readonly mainLambdaS3Key: string;
  public readonly lifecycleLambdaS3Key: string;

  constructor(scope: Construct, id: string, props: Readonly<BuildSpecProps>) {
    super(scope, id);

    const {
      gitRepositoryUrl,
      s3BucketWithState,
      region,
      buildLambdaViaAsset = false,
      prodStage = false,
    } = props;

    new S3Backend(this, {
      bucket: s3BucketWithState,
      key: "kite/terraform-build.tfstate",
      region,
    });
    new AwsProvider(this, "AWS");
    new ArchiveProvider(this, "ARCHIVE");

    const s3Bucket = new S3Bucket(this, "s3-bucket", {
      bucketPrefix: "s3-source-bucket",
      forceDestroy: true,
    });

    new CiCdCodebuild(this, "ci-cd-codebuild", {
      gitRepositoryUrl,
      s3SourceBucket: s3Bucket,
      stackName: id.replace("-build", ""),
      s3BucketWithState: !buildLambdaViaAsset ? s3BucketWithState : undefined,
      devLambdaName: DEV_MAIN_LAMBDA_NAME,
      prodLambdaName: prodStage ? PROD_MAIN_LAMBDA_NAME : undefined,
    });

    if (buildLambdaViaAsset) {
      new AssetS3Source(this, "function", {
        s3BucketName: s3Bucket.bucket,
        relativeProjectPath: "../../k1te-serverless",
      });
    }

    const archiveS3Source = new ArchiveS3Source(this, "lifecycle", {
      s3BucketName: s3Bucket.bucket,
      sourceFile: "../lifecycle-handler/index.mjs",
      output: "../lifecycle-handler/lifecycle.zip",
    });

    new TerraformOutput(this, "s3-source-bucket", {
      value: s3Bucket.bucket,
      description:
        "Name of the S3 Bucket where Lifecycle and MainHandler are stored",
    });

    new TerraformOutput(this, "function-s3-key", {
      value: LAMBDA_BUILD_OUTPUT_PATH,
      description: "Name of the S3 Key to MainHandler function",
    });

    new TerraformOutput(this, "lifecycle-s3-key", {
      value: archiveS3Source.key,
      description: "Name of the S3 Key to Lifecycle function",
    });

    this.s3BucketWithState = s3BucketWithState;
    this.region = region;
    this.prodStage = prodStage;
    this.s3SourceBucketName = s3Bucket.bucket;
    this.lifecycleLambdaS3Key = archiveS3Source.key;
    this.mainLambdaS3Key = LAMBDA_BUILD_OUTPUT_PATH;
  }
}
