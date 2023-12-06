import { App, LocalBackend, S3Backend } from "cdktf";
import "dotenv/config";
import { LocalStack } from "./local-stack";
import { KiteStack } from "./kite-stack";

const app = new App();

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
  addDev: true,
});
new LocalBackend(kiteStackLocal);

new LocalStack(app, "local");
app.synth();
