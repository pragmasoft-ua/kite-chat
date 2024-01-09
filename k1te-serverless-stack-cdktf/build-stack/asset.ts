import { AssetType, TerraformAsset } from "cdktf";

import { Construct } from "constructs";
import path = require("node:path");
import { DataArchiveFile } from "@cdktf/provider-archive/lib/data-archive-file";
import { S3Object } from "@cdktf/provider-aws/lib/s3-object";
import { BUILD_DIR } from "./build";

export class ArchiveS3Source extends Construct {
  private readonly s3Object: S3Object;
  constructor(
    scope: Construct,
    id: string,
    props: Readonly<ArchiveS3SourceProps>,
  ) {
    super(scope, id);
    const { s3BucketName, output, sourceFile, archiveType = "zip" } = props;

    const archive = new DataArchiveFile(this, `${id}-archive`, {
      outputPath: path.resolve(__dirname, output),
      type: archiveType,
      sourceFile: path.resolve(__dirname, sourceFile),
    });

    this.s3Object = new S3Object(this, `s3-object-${id}`, {
      bucket: s3BucketName,
      key: `${BUILD_DIR}/${id}.${archiveType}`,
      source: archive.outputPath,
      lifecycle: {
        ignoreChanges: ["etag", "source"],
      },
    });
  }
  get key() {
    return this.s3Object.key;
  }
}

export class AssetS3Source extends Construct {
  private readonly s3Object: S3Object;
  constructor(
    scope: Construct,
    id: string,
    props: Readonly<AssetS3SourceProps>,
  ) {
    super(scope, id);

    const {
      s3BucketName,
      relativeProjectPath,
      target = "target/function.zip",
      assetType = AssetType.FILE,
    } = props;

    const assetSource = new TerraformAsset(this, `${id}-asset`, {
      type: assetType,
      path: path.resolve(__dirname, relativeProjectPath, target),
    });

    this.s3Object = new S3Object(this, `s3-object-${id}`, {
      bucket: s3BucketName,
      key: `${BUILD_DIR}/${id}${assetSource.path.substring(
        assetSource.path.lastIndexOf("."),
      )}`,
      source: assetSource.path,
      lifecycle: {
        ignoreChanges: ["etag", "source"],
      },
    });
  }

  get key() {
    return this.s3Object.key;
  }

  get fullPath() {
    return `${this.s3Object.bucket}/${this.key}`;
  }
}

export type ArchiveS3SourceProps = {
  s3BucketName: string;
  output: string;
  sourceFile: string;
  archiveType?: string;
};

export type AssetS3SourceProps = {
  s3BucketName: string;
  relativeProjectPath: string;
  target?: string;
  assetType?: AssetType;
};
