import { AssetType, TerraformAsset } from "cdktf";

import { Construct } from "constructs";
import path = require("node:path");

type Runtime = "java11" | "java17";

export type QuarkusLambdaAssetProps = {
  relativeProjectPath: string;
  runtime?: Runtime;
  assetType?: AssetType;
  handler?: string;
};

const DEFAULT_PROPS: Partial<QuarkusLambdaAssetProps> = {
  runtime: "java17",
  assetType: AssetType.ARCHIVE,
  handler:
    "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest",
};

export class QuarkusLambdaAsset extends Construct {
  readonly asset: TerraformAsset;
  readonly runtime: Runtime;
  readonly handler: string;

  constructor(
    scope: Construct,
    id: string,
    props: Readonly<QuarkusLambdaAssetProps>
  ) {
    super(scope, id);

    const { relativeProjectPath, runtime, assetType, handler } = Object.assign(
      {},
      DEFAULT_PROPS,
      props
    );

    const absoluteAssetPath = path.resolve(
      __dirname,
      relativeProjectPath,
      "target/function.zip"
    );

    // TODO validate?

    this.asset = new TerraformAsset(this, this.node.id, {
      path: absoluteAssetPath,
      type: assetType,
    });

    this.runtime = runtime as Runtime;

    this.handler = handler as string;
  }

  get path() {
    return this.asset.path;
  }

  get hash() {
    return this.asset.assetHash;
  }
}
