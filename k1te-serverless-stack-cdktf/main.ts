import { App, LocalBackend, S3Backend } from "cdktf";
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
const kiteStack = new KiteStack(app, "kite", {
  domainName: "k1te.chat",
  architecture: "x86_64",
  runtime: "provided.al2",
  handler: "hello.handler",
  memorySize: 256,
});
new S3Backend(kiteStack, {
  bucket: "k1te-chat-tfstate",
  key: `kite/terraform.tfstate`,
  region: "eu-north-1",
});

const kiteStackLocal = new KiteStack(app, "kite-local", {
  architecture: "arm64",
  runtime: "provided.al2",
  handler: "hello.handler",
  memorySize: 256,
  cicd: {
    gitRepositoryUrl: "https://github.com/Alex21022001/drift-check2",
    s3BucketWithState: "my-test-arm-bucket",
    emailToSendAlarm: "alexeysitiy@gmail.com",
  },
});
new S3Backend(kiteStackLocal, {
  bucket: "my-test-arm-bucket",
  key: `kite/terraform.tfstate`,
  region: "us-west-2",
});

const buildStack = new BuildStack(app, "lambda-build", {
  gitRepositoryUrl: "https://github.com/Alex21022001/drift-check2",
});
new LocalBackend(buildStack);

new LocalStack(app, "local");
app.synth();
