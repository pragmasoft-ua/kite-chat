import { S3Bucket } from "@cdktf/provider-aws/lib/s3-bucket";
import { S3BucketPolicy } from "@cdktf/provider-aws/lib/s3-bucket-policy";
import { S3BucketPublicAccessBlock } from "@cdktf/provider-aws/lib/s3-bucket-public-access-block";
import { TerraformOutput } from "cdktf";
import { Construct } from "constructs";
import { S3 } from "iam-floyd/lib/generated";
import { Grantable } from "./grantable";
import { S3BucketCorsConfiguration } from "@cdktf/provider-aws/lib/s3-bucket-cors-configuration";
import { S3BucketLifecycleConfiguration } from "@cdktf/provider-aws/lib/s3-bucket-lifecycle-configuration";

export type ObjectStoreProps = {
  bucketPrefix: string;
  allowAnonymousRead?: boolean;
};

export class ObjectStore extends Construct {
  readonly bucket: S3Bucket;
  constructor(
    scope: Construct,
    id: string,
    { bucketPrefix, allowAnonymousRead = false }: ObjectStoreProps
  ) {
    super(scope, id);

    this.bucket = new S3Bucket(this, "object-store", {
      bucketPrefix,
    });

    new S3BucketLifecycleConfiguration(this, "object-store-lifecycle-config", {
      bucket: this.bucket.id,
      rule: [
        {
          id: "expire-objects",
          expiration: { days: 7 },
          status: "Enabled",
        },
      ],
    });

    new S3BucketCorsConfiguration(this, "object-store-cors-config", {
      bucket: this.bucket.id,
      corsRule: [
        {
          allowedMethods: ["HEAD", "GET", "PUT"],
          allowedOrigins: ["*"],
          allowedHeaders: ["*"],
          exposeHeaders: [],
        },
      ],
    });

    if (allowAnonymousRead) {
      new S3BucketPublicAccessBlock(this, "object-store-public-access-block", {
        bucket: this.bucket.id,
        blockPublicAcls: true,
        blockPublicPolicy: false,
        ignorePublicAcls: true,
        restrictPublicBuckets: false,
      });

      new S3BucketPolicy(this, "allow-anonymous-read", {
        bucket: this.bucket.id,
        policy: JSON.stringify({
          Version: "2012-10-17",
          Statement: [
            new S3()
              .allow()
              .forPublic()
              .toGetObject()
              .onObject(this.bucket.bucket, "*")
              .toJSON(),
          ],
        }),
      });
    }

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
      .onObject(this.bucket.bucket, "*");
    to.grant(`allow-crud-${this.bucket.bucket}`, policyStatement);
    return this;
  }
}
