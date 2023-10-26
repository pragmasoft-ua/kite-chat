import { AssetType, TerraformAsset, TerraformOutput } from "cdktf";

import { Construct } from "constructs";
import path = require("node:path");
import fs = require("node:fs");
import assert = require("node:assert");
import { DataArchiveFile } from "@cdktf/provider-archive/lib/data-archive-file";

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

export class LambdaAsset extends Construct implements Resource {
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

export type ArchiveResourceProps = {
  output: string;
  sourceFile: string;
  runtime?: Runtime;
  archiveType?: string;
  handler?: Handler;
};

const ARCHIVE_RESOURCE_DEFAULT_PROPS: Partial<ArchiveResourceProps> = {
  runtime: "nodejs18.x",
  archiveType: "zip",
  handler: "index.handler",
};
export class ArchiveResource extends Construct implements Resource {
  readonly handler: string;
  readonly hash: string;
  readonly path: string;
  readonly runtime: Runtime;

  constructor(
    scope: Construct,
    id: string,
    props: Readonly<ArchiveResourceProps>
  ) {
    super(scope, id);

    const {
      output,
      sourceFile,
      runtime,
      archiveType = "zip",
      handler,
    } = Object.assign({}, props, ARCHIVE_RESOURCE_DEFAULT_PROPS);

    const lifecycleArchive = new DataArchiveFile(this, "archive", {
      outputPath: path.resolve(__dirname, output),
      type: archiveType,
      sourceFile: path.resolve(__dirname, sourceFile),
    });

    this.handler = handler!;
    this.runtime = runtime!;
    this.path = lifecycleArchive.outputPath;
    this.hash = lifecycleArchive.outputBase64Sha256;
  }
}

export interface Resource {
  readonly path: string;
  readonly hash: string;
  readonly handler: string;
  readonly runtime: Runtime;
}
