import { AssetType, TerraformAsset, TerraformOutput } from "cdktf";

import { Construct } from "constructs";
import path = require("node:path");
import fs = require("node:fs");
import assert = require("node:assert");
import { DataArchiveFile } from "@cdktf/provider-archive/lib/data-archive-file";
import { S3Bucket } from "@cdktf/provider-aws/lib/s3-bucket";
import { S3Object } from "@cdktf/provider-aws/lib/s3-object";

type Runtime = "java11" | "java17" | "java21" | "nodejs18.x" | "provided.al2";
type Handler =
  | "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"
  | "index.handler"
  | "hello.handler";

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

export class S3Source extends Construct {
  readonly s3Bucket: string;
  readonly s3Key: string;
  readonly handler: string;
  readonly runtime: Runtime;

  constructor(scope: Construct, id: string, props: Readonly<S3SourceProps>) {
    super(scope, id);

    const { asset, s3Bucket, s3Props } = props;

    if (asset) {
      let s3BucketName;
      if (!s3Bucket) {
        const s3Bucket = new S3Bucket(this, "lambda-source-storage", {
          bucketPrefix: "lambda-source-storage-",
        });
        s3BucketName = s3Bucket.bucket;
      } else {
        s3BucketName = s3Bucket;
      }

      const s3Object = new S3Object(this, "main-lambda-handler", {
        bucket: s3BucketName,
        key: `${id}.zip`,
        source: asset.path,
        etag: asset.hash,
      });

      this.s3Bucket = s3BucketName;
      this.s3Key = s3Object.key;
      this.handler = asset.handler;
      this.runtime = asset.runtime;
    } else if (s3Props) {
      assert(s3Bucket);
      this.s3Bucket = s3Bucket;
      this.s3Key = s3Props.s3Key;
      this.runtime = s3Props.runtime;
      this.handler = s3Props.handler;
    } else {
      throw new Error(
        "You must specify at least Resource or S3Bucket and S3Key"
      );
    }
  }
}

export type S3SourceProps = {
  asset?: Resource;
  s3Bucket?: string;
  s3Props?: {
    s3Key: string;
    handler: string;
    runtime: Runtime;
  };
};

export interface Resource {
  readonly path: string;
  readonly hash: string;
  readonly handler: string;
  readonly runtime: Runtime;
}
