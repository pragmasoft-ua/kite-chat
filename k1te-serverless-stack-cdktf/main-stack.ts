import { Construct } from "constructs";
import { App, S3Backend } from "cdktf";
import { BuildStack } from "./build-stack";
import { KiteStack } from "./kite-stack";
import { Architecture, Handler, Runtime } from "./lambda";

export type MainStackProps = {
  build: {
    gitRepositoryUrl: string;
    prodStage?: boolean;
    buildLambdaViaAsset?: boolean;
  };
  kite: {
    domainName?: string;
    architecture?: Architecture;
    runtime?: Runtime;
    handler?: Handler;
    memorySize?: number;
  };
  s3Backend: {
    bucket: string;
    /**
     * Key where TF state will be stored. Pass without extension like
     * kite/terraform
     * */
    key: string;
    region: string;
  };
};

export class MainStack extends Construct {
  public constructor(app: App, id: string, props: MainStackProps) {
    super(app, id);
    const {
      build: { buildLambdaViaAsset, prodStage = false, gitRepositoryUrl },
      kite: { domainName, handler, runtime, memorySize, architecture },
      s3Backend: { bucket, key, region },
    } = props;

    const devLambdaName = `dev-request-dispatcher`;
    const prodLambdaName = prodStage ? `prod-request-dispatcher` : undefined;

    const buildStack = new BuildStack(app, `${id}-build`, {
      gitRepositoryUrl,
      s3BucketWithState: bucket,
      buildLambdaViaAsset,
      devLambdaName,
      prodLambdaName,
    });
    const s3Backend = new S3Backend(buildStack, {
      bucket,
      key: `${key}-build.tfstate`,
      region,
    });

    const kiteStackLocal = new KiteStack(app, `${id}-stack`, {
      s3Backend,
      buildStackName: `${id}-build`,
      devLambdaName,
      prodLambdaName,
      domainName,
      architecture,
      runtime,
      handler,
      memorySize,
    });
    new S3Backend(kiteStackLocal, {
      bucket,
      key: `${key}.tfstate`,
      region,
    });
  }
}
