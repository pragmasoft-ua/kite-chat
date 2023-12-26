import { App } from "cdktf";
import "dotenv/config";
import { LocalStack } from "./local-stack/local-stack";
import { BuildStack } from "./build-stack/build-stack";
import { KiteStack } from "./kite-stack/kite-stack";

const app = new App();

// new MainStack(app, "kite", {
//   build: {
//     gitRepositoryUrl: "https://github.com/pragmasoft-ua/kite-chat",
//   },
//   kite: {
//     architecture: "arm64",
//     runtime: "provided.al2",
//     handler: "hello.handler",
//     memorySize: 256,
//   },
//   s3Backend: {
//     bucket: "k1te-chat-tfstate",
//     key: `kite/terraform.tfstate`,
//     region: "eu-north-1",
//   },
// });

const buildStack = new BuildStack(app, "kite-build", {
  gitRepositoryUrl: "https://github.com/Alex21022001/kite-chat",
  s3BucketWithState: "my-test-arm-bucket",
  region: "us-west-2",
  prodStage: true,
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

new LocalStack(app, "local");
app.synth();
