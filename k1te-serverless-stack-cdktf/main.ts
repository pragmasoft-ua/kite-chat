import { App } from "cdktf";
import "dotenv/config";
import { KiteStack } from "./kite-stack";
import { LocalStack } from "./local-stack";

const app = new App();
new KiteStack(app, "kite");
new LocalStack(app, "local");
app.synth();
