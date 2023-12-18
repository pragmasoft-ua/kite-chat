import { App, S3Backend } from "cdktf";
import "dotenv/config";
import { LocalStack } from "./local-stack";
import { KiteStack } from "./kite-stack";
import { BuildStack } from "./build-stack";

const app = new App();

/**
 * To use both prod and dev env specify devEnv:true
 * To add ci/cd - cicd:true
 * To build MainHandler Lambda from S3Bucket and S3Key - s3LambdaStorage,
 * but it also required several variable defined in .env file, please check README.md
 * Deploying Stack and Stack Variables section
 * */
// const kiteStack = new KiteStack(app, "kite", {
//   domainName: "k1te.chat",
//   architecture: "x86_64",
//   runtime: "provided.al2",
//   handler: "hello.handler",
//   memorySize: 256,
//   lambdaBuildS3Key: "kite/terraform.tfstate",
//   lambdaBuildS3Bucket: "k1te-chat-tfstate",
// });
// new S3Backend(kiteStack, {
//   bucket: "k1te-chat-tfstate",
//   key: `kite/terraform.tfstate`,
//   region: "eu-north-1",
// });

// Local
const buildStack = new BuildStack(app, "lambda-build", {
  gitRepositoryUrl: "https://github.com/Alex21022001/drift",
  s3BucketWithState: "my-test-arm-bucket",
  devLambdaName: "dev-request-dispatcher",
  prodLambdaName: "prod-request-dispatcher",
  buildLambdaViaAsset: true,
});
new S3Backend(buildStack, {
  bucket: "my-test-arm-bucket",
  key: `kite/terraform-build.tfstate`,
  region: "us-west-2",
});

const kiteStackLocal = new KiteStack(app, "kite-local", {
  architecture: "arm64",
  runtime: "provided.al2",
  handler: "hello.handler",
  memorySize: 256,
  devFunctionName: buildStack.devFunctionName,
  prodFunctionName: buildStack.prodFunctionName,
  sourceBucketName: buildStack.sourceBucketName,
  functionS3Key: buildStack.functionS3Key,
  lifecycleS3Key: buildStack.lifecycleS3Key,
});
new S3Backend(kiteStackLocal, {
  bucket: "my-test-arm-bucket",
  key: `kite/terraform.tfstate`,
  region: "us-west-2",
});

new LocalStack(app, "local");
app.synth();
