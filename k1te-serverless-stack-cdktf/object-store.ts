import { S3Bucket } from "@cdktf/provider-aws/lib/s3-bucket";
import { S3 } from "iam-floyd/lib/generated";
import { Grantable } from "./grantable";
import { Construct } from "constructs";
import { TerraformOutput } from "cdktf";

export type ObjectStoreProps = {
  bucketPrefix: string;
};

export class ObjectStore extends Construct {
  readonly bucket: S3Bucket;
  constructor(
    scope: Construct,
    id: string,
    { bucketPrefix }: ObjectStoreProps
  ) {
    super(scope, id);

    this.bucket = new S3Bucket(this, "object-store", {
      bucketPrefix,
      lifecycleRule: [
        {
          id: "expire-objects",
          expiration: { days: 7 },
          enabled: true,
        },
      ],
    });

    new TerraformOutput(this, "bucket-name", {
      value: this.bucket.bucket,
    });
  }

  public allowReadWrite(to: Grantable) {
    const policyStatement = new S3()
      .allow()
      .toGetObject()
      .toPutObject()
      .toDeleteObject()
      .toListBucket()
      .on(this.bucket.arn);
    to.grant(`allow-crud-${this.bucket.bucket}`, policyStatement);
    return this;
  }
}
