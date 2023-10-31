import { App, LocalBackend, S3Backend } from "cdktf";
import "dotenv/config";
import { LocalStack } from "./local-stack";
import { KiteStack } from "./kite-stack";

const app = new App();

const kiteStack = new KiteStack(app, "kite", "k1te.chat");
new S3Backend(kiteStack, {
  bucket: "k1te-chat-tfstate",
  key: `kite/terraform.tfstate`,
  region: "eu-north-1",
});

const kiteStackLocal = new KiteStack(app, "kite-local", undefined, true);
new LocalBackend(kiteStackLocal);

new LocalStack(app, "local");
app.synth();
