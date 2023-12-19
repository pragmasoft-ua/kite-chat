# Kite chat CDKTF infrastructrure

## Description

Kite chat serverless backend is managed using CDK for Terraform.

## Prerequisites

CDK TF requires:

- node v16+ and npm
- terraform cli
- cdktf cli `npm install --global cdktf-cli@latest`
- aws cli with configured credentials (for production stack only) `aws sts get-caller-identity`
- docker runtime (for local stack only)

More details can be found here: [CDK TF Installation](https://developer.hashicorp.com/terraform/tutorials/cdktf/cdktf-install)

For node installation it's recommended to use `nvm`

On Windows you can use `choco` to install most dependencies

### Cloudflare hosted custom domain dependencies

Currently, custom api domains are optional, but when requested, they require Cloudflare api token configured, because only Cloudflare hosted domain is supported for now.

To configure Cloudflare api token:

- go to the https://dash.cloudflare.com/profile/api-tokens
- use **Edit zone DNS** as a template
- create the token and copy it to the clipboard.

Then, create `.env` file in the cdktf project root with the content:

`CLOUDFLARE_API_TOKEN="your cloudflare api token"`

Alternatively, copy `.env.example.txt`, rename it to `.env` and add your token there.
## Structure
### Local Stack
The **_local_** stack sets up a local DynamoDB container, listening on http://localhost:8000, and initializes the DynamoDB schema for local development. The local stack requires a Docker runtime on the local machine.

### Kite Stack
The **_kite-stack_** is a production stack that creates a serverless environment in AWS. It includes the following resources:
- DomainName (CloudFlare...)
- WebSocket API Gateway (API, stage, integration, route...)
- HTTP API Gateway (API, stage, integration, route...)
- Lambda (request-dispatcher, lifecycle)
- DynamoDB
- S3
- CloudWatch
- IAM

### Build Stack
The **_build-stack_** is used to establish CI/CD for the app. It can also build the native executable for the Main lambda or upload it from the source. It contains the following resources:
- S3
- CodeBuild
- IAM
- CloudWatch
- EventBridge (event rule, scheduler)
- SNS

## Deploying Kite-Stack
The **_kite-stack_** is dependent on the **_build-stack_**, which is used to build the native
executable for the **Main** lambda or upload it from the source. It also rolls out the CI/CD
infrastructure for the **_kite-stack_**. The **_kite-stack_** utilizes DataTerraformRemoteState
to retrieve necessary variables from the **_build-stack_**, such as S3Bucket containing Main
and Lifecycle lambdas and S3Keys to them. Deploying **_kite-stack_** without deploying
**_build-stack_** first will result in a CDKTF Error.

To deploy **_kite-stack_**, we recommend using the main-stack that combines both **_kite-stack_** and **_build-stack_**. It can be created using the following TypeScript code:

```typescript
new MainStack(app, "kite", {
   prodStage: true,
   build: {
      gitRepositoryUrl: "https://github.com/pragmasoft-ua/kite-chat",
      buildLambdaViaAsset: false
   },
   kite: {
      domainName: "k1te.chat",
      architecture: "arm64",
      runtime: "provided.al2",
      handler: "hello.handler",
      memorySize: 256,
   },
   s3Backend: {
      bucket: "my-test-arm-bucket",
      key: `kite/terraform`,
      region: "us-west-2",
   },
});
```
This code unites two stacks (kite-stack and build-stack), with the names depending on
the specified name of MainStack. In the example above, **_kite-stack_** will be **_kite-stack_**
and **_build-stack_** will be **_kite-build_**. It also utilizes one S3Bucket for state storing,
with slightly different keys for kite-stack (kite/terraform.tfstate) and
build-stack (kite/terraform-build.tfstate).

### Build-Stack
To deploy **_kite-stack_**, you need to deploy **_build-stack_** first. **_build-stack_** creates CodeBuild projects with the following capabilities:
- **build-and-deploy-project**: Builds the native executable lambda and deploys a new version for the `dev`
  stage. It can also be used to build the native executable manually via **AWS CLI**.
  This project automatically starts building when new changes are pushed to the **main** branch of the
  specified Git repository.
- **deploy-on-tag-project** (optional): Updates the existing **Main** lambda function for the `prod` stage.
  It is automatically triggered when a new **TAG** is pushed to the Git repository.
  This project is omitted if the `prod` stage is not created.
- **drift-check-project** (optional): Checks drift in the specified stack (default is **_kite-stack_**).
  This project uses EventBridge Scheduler with a CRON expression to run it on a regular
  basis (Monday-Friday at 12 PM). It also uses SNS (email) to inform you of drift in the specified stack.
  **Important: When you deploy **_build-stack_** and DriftCheck is created, you will have to confirm the
  email that you need to specify in the .env file like this `EMAIL="example@gmail.com"` first**.
  The DriftCheck project is not created if `buildLambdaViaAsset=true` due to the function.zip not
  existing in the GitHub source used for drift check, resulting in an Error.
  If drift is detected and the email is confirmed, you will receive an email similar to this: `Drift was detected in the Stack! You should re-deploy the stack manually`.

With **_build-stack_**, you can build or upload the lambda needed for **_kite-stack_**. **_kite-stack_** can work with both `arm64` and `x86_64` architectures and `common` and `native` lambdas. You can specify all these properties in **_kite-stack_**.

#### Uploading Existing Lambda
If you use `x86_64` or have an `ARM64` OS, you can build a zip archive locally containing the
lambda using the following commands:

```bash
# This will create function.zip with 'common' lambda and save it in the target directory
./mvnw -pl k1te-serverless -am install -DskipTests

# This will create function.zip with 'native' lambda and save it in the target directory
./mvnw -pl k1te-serverless -am install -Dnative -DskipTests
```
Once **function.zip** is successfully saved in the target directory, you need to specify
`buildLambdaViaAsset=true` in **_build-stack_** and deploy it using:

```bash
cd k1te-serverless-stack-cdktf
# It will get function.zip from the target directory and upload it to the just created S3Bucket,
# it will also output S3Bucket, S3Keys which are necessary for kite-stack
cdktf deploy kite-build
```

#### Building Lambda Manually via CodeBuild
This method is helpful if you want to build `arm64` native executable but can't do it locally.
To do so, make use of the **build-and-deploy-project** that builds **function.zip** with the native
lambda and puts it into the S3Bucket. Follow these steps:
1) Specify `buildLambdaViaAsset=false` (false by default).
2) Deploy **_build-stack_** (check the sections above).
3) Start the build manually.

```bash
# It will start the build, and when **function.zip** is created, upload it to S3Bucket (usually takes 7-8 minutes)
aws codebuild start-build --project-name "build-and-deploy-project" --buildspec-override "k1te-serverless-stack-cdktf/build-lambda-buildspec.yml"
```
4) To check the status of the build, use the following command:
```bash
# Your-build-id can be seen when starting the build.
# When the status of the build is **SUCCEEDED**, you have successfully built the Lambda function for ARM64
aws codebuild batch-get-builds --ids "your-build-id" --query 'builds[].buildStatus' --output text 
```

### Kite-Stack

Once **_build-stack_** is successfully deployed, and the Main lambda is uploaded to the S3Bucket,
you can deploy **_kite-stack_**. However, you also need to specify some environment variables.
**_kite-stack_** utilizes several output variables from **_build-stack_**, which will be automatically
retrieved from there, namely:
- "s3-source-bucket"
- "function-s3-key"
- "lifecycle-s3-key"

The `.env` file should look like this:

```dotenv
CLOUDFLARE_API_TOKEN="your-token"
# The Telegram token that is mandatory. The specified Bot Token is used for dev stage.
TELEGRAM_BOT_TOKEN="your dev telegram bot token"
# (Optional) The Telegram token that is used for prod stage. Mandatory only if prod stage is specified.
TELEGRAM_PROD_BOT_TOKEN="your prod telegram bot token"
# (Optional) Your email address that is used to send drift detection messages. Mandatory if you create
# DriftCheck CodeBuild project. You also need to confirm it after build-stack is created.
EMAIL="example@gmail.com"
```
In order to deploy, run the following command:
```bash
cd k1te-serverless-stack-cdktf
cdktf deploy kite-stack --var-file=.env
```


## Build

`npm install` to install dependencies (once after checking the project from git)

`npm run deploy-local` to deploy local stack
`npm run deploy-prod` to deploy production stack

`npm run destroy-local` to destroy local stack
`npm run destroy-prod` to destroy production stack

## Work with cdktf

`npm run get` Import/update Terraform providers and modules (you should check-in this directory)
`npm run compile` Compile typescript code to javascript (or "npm run watch")
`npm run watch` Watch for changes and compile typescript in the background
`npm run build` Compile typescript

## Synthesize

`cdktf synth [stack]` Synthesize Terraform resources from stacks to cdktf.out/ (ready for 'terraform apply')

## Diff

`cdktf diff [stack]` Perform a diff (terraform plan) for the given stack

## Deploy

`cdktf deploy [stack]` Deploy the given stack

## Destroy

`cdktf destroy [stack]` Destroy the stack

## Test

`npm run test` Runs unit tests (edit **tests**/main-test.ts to add your own tests)
`npm run test:watch` Watches the tests and reruns them on change

## Upgrade

`npm run upgrade` Upgrade cdktf modules to latest version
`npm run upgrade:next` Upgrade cdktf modules to latest "@next" version (last commit)

## Use Providers

You can add prebuilt providers (if available) or locally generated ones using the add command:

`cdktf provider add "aws@~>3.0" null kreuzwerker/docker`

You can find all prebuilt providers on npm: https://www.npmjs.com/search?q=keywords:cdktf
You can also install these providers directly through npm:

`npm install @cdktf/provider-aws`
`npm install @cdktf/provider-google`
`npm install @cdktf/provider-azurerm`
`npm install @cdktf/provider-docker`
`npm install @cdktf/provider-github`
`npm install @cdktf/provider-null`

You can also build any module or provider locally. Learn more https://cdk.tf/modules-and-providers

## Testing websocket connection

```bash
npx wscat -s "k1te.chat.v1" -c wss://ws.k1te.chat/prod?param=test
Connected (press CTRL+C to quit)
> Hi
< OK
> ["PING"]
< ["PONG"]
>
```

## Testing REST connection

```bash
curl https://api.k1te.chat/prod/tg
OK
```

## Cloudflare API snippet

```bash
curl --request POST --url "https://api.cloudflare.com/client/v4/zones/ee68495b7c3238f5b738f1aaa49ac569/dns_records" -H "Content-Type: application/json" -H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}" --data '{"content": "_7d6f6f19acae3e1bed4c89035c765ba6.dflhkvdxlx.acm-validations.aws.","name": "_2ba4fa11bc3015df1ab70e82a4680300.k1te.chat.","proxied": false,"type": "CNAME","tags":[],"ttl":1}'
```

## TODO

- WAF rule, budget alarm?
- replace http-api with function url?
- aws split charges (per customer) ?
- bot token in SSM store (requires lambda extension) - https://docs.aws.amazon.com/systems-manager/latest/userguide/ps-integration-lambda-extensions.html
- dev and prod stages
- CI as in https://www.obytes.com/blog/aws-lambda-ci
- Logging and telemetry to S3 as in https://lumigo.io/blog/lambda-telemetry-api-a-new-way-to-process-lambda-telemetry-data-in-real-time/
- Query logs above with Athena (S3 select only queries one file) or OpenSearch
