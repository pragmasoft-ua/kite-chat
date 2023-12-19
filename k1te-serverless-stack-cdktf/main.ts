import { App } from "cdktf";
import "dotenv/config";
import { LocalStack } from "./local-stack";
import { MainStack } from "./main-stack";

const app = new App();

new MainStack(app, "kite", {
  build: {
    gitRepositoryUrl: "https://github.com/pragmasoft-ua/kite-chat",
  },
  kite: {
    architecture: "arm64",
    runtime: "provided.al2",
    handler: "hello.handler",
    memorySize: 256,
  },
  s3Backend: {
    bucket: "k1te-chat-tfstate",
    key: `kite/terraform.tfstate`,
    region: "eu-north-1",
  },
});

new LocalStack(app, "local");
app.synth();
