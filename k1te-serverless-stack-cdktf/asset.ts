import { AssetType, TerraformAsset, TerraformOutput } from "cdktf";

import { Construct } from "constructs";
import path = require("node:path");
import fs = require("node:fs");
import assert = require("node:assert");

type Runtime = "java11" | "java17" | "nodejs18.x";
type Handler =
  | "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"
  | "index.handler";

export type LambdaAssetProps = {
  relativeProjectPath: string;
  target?: string;
  runtime?: Runtime;
  assetType?: AssetType;
  handler?: Handler;
};

const DEFAULT_PROPS: Partial<LambdaAssetProps> = {
  runtime: "java17",
  assetType: AssetType.FILE,
  handler:
    "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest",
};

export class LambdaAsset extends Construct {
  readonly asset: TerraformAsset;
  readonly runtime: Runtime;
  readonly handler: string;

  constructor(scope: Construct, id: string, props: Readonly<LambdaAssetProps>) {
    super(scope, id);

    const {
      relativeProjectPath,
      runtime,
      assetType: type,
      handler,
      target = "target/function.zip",
    } = Object.assign({}, DEFAULT_PROPS, props);

    const absoluteAssetPath = path.resolve(
      __dirname,
      relativeProjectPath,
      target
    );

    assert(
      fs.existsSync(absoluteAssetPath),
      `Lambda asset does not exist: "${absoluteAssetPath}".
       Go to the asset's project directory and build asset with the command
       "./mvnw package -DskipTests" then redeploy this stack.`
    );

    this.asset = new TerraformAsset(this, "asset", {
      path: absoluteAssetPath,
      type,
    });

    this.runtime = runtime as Runtime;

    this.handler = handler as string;

    new TerraformOutput(this, "path", {
      value: this.asset.path,
    });
  }

  get path() {
    return this.asset.path;
  }

  get hash() {
    return this.asset.assetHash;
  }
}
