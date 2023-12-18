import { AssetType, TerraformAsset } from "cdktf";

import { Construct } from "constructs";
import path = require("node:path");
import { DataArchiveFile } from "@cdktf/provider-archive/lib/data-archive-file";
import { S3Object } from "@cdktf/provider-aws/lib/s3-object";

export class S3Source extends Construct {
  constructor(scope: Construct, id: string, props: Readonly<S3SourceProps>) {
    super(scope, id);

    const { s3BucketName, asset, archive } = props;

    let pathToFile;
    if (asset) {
      const {
        relativeProjectPath,
        target = "target/function.zip",
        assetType = AssetType.FILE,
      } = asset;

      const assetSource = new TerraformAsset(this, "asset", {
        type: assetType,
        path: path.resolve(__dirname, relativeProjectPath, target),
      });

      pathToFile = assetSource.path;
    } else if (archive) {
      const { output, sourceFile, archiveType = "zip" } = archive;

      const lifecycleArchive = new DataArchiveFile(this, "archive", {
        outputPath: path.resolve(__dirname, output),
        type: archiveType,
        sourceFile: path.resolve(__dirname, sourceFile),
      });

      pathToFile = lifecycleArchive.outputPath;
    }

    new S3Object(this, `s3-object-${id}`, {
      bucket: s3BucketName,
      key: `build/${id}.zip`,
      source: pathToFile,
      lifecycle: {
        ignoreChanges: ["etag", "source"],
      },
    });
  }
}

export type S3SourceProps = {
  s3BucketName: string;
  asset?: {
    relativeProjectPath: string;
    target?: string;
    assetType?: AssetType;
  };
  archive?: {
    output: string;
    sourceFile: string;
    archiveType?: string;
  };
};
