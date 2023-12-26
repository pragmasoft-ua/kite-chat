import { App } from "cdktf";
import "dotenv/config";
import { LocalStack } from "./local-stack/local-stack";
import { BuildStack } from "./build-stack/build-stack";
import { KiteStack } from "./kite-stack/kite-stack";

const app = new App();

const buildStack = new BuildStack(app, "kite-build", {
  gitRepositoryUrl: "https://github.com/pragmasoft-ua/kite-chat",
  s3BucketWithState: "k1te-chat-tfstate",
  region: "eu-north-1",
});
new KiteStack(app, "kite", {
  s3BucketWithState: buildStack.s3BucketWithState,
  region: buildStack.region,
  s3SourceBucketName: buildStack.s3SourceBucketName,
  lifecycleLambdaS3Key: buildStack.lifecycleLambdaS3Key,
  mainLambdaS3Key: buildStack.mainLambdaS3Key,
  prodStage: buildStack.prodStage,
  architecture: "arm64",
  runtime: "provided.al2",
  handler: "hello.handler",
  memorySize: 256,
});

const kiteLocalBuild = new BuildStack(app, "kite-local-build", {
  gitRepositoryUrl: "https://github.com/Alex21022001/drift",
  s3BucketWithState: "my-test-arm-bucket",
  region: "us-west-2",
  prodStage: true,
});
new KiteStack(app, "kite-local", {
  s3BucketWithState: kiteLocalBuild.s3BucketWithState,
  region: kiteLocalBuild.region,
  s3SourceBucketName: kiteLocalBuild.s3SourceBucketName,
  lifecycleLambdaS3Key: kiteLocalBuild.lifecycleLambdaS3Key,
  mainLambdaS3Key: kiteLocalBuild.mainLambdaS3Key,
  prodStage: kiteLocalBuild.prodStage,
  architecture: "arm64",
  runtime: "provided.al2",
  handler: "hello.handler",
  memorySize: 256,
});

new LocalStack(app, "local");
app.synth();
