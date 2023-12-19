import { App } from "cdktf";
import "dotenv/config";
import { LocalStack } from "./local-stack";
import { MainStack } from "./main-stack";

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

new MainStack(app, "kite", {
  prodStage: true,
  build: {
    gitRepositoryUrl: "https://github.com/Alex21022001/drift",
  },
  kite: {
    architecture: "arm64",
    runtime: "provided.al2",
    handler: "hello.handler",
    memorySize: 256,
  },
  s3Backend: {
    bucket: "my-test-arm-bucket",
    key: `kite/terraform`,
    region: "us-west-2",
  },
});

new LocalStack(app, "local");
app.synth();
