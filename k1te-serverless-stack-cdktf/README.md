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

This application consists of several CDKTF stacks:

- **local** stack starts local dynamodb container to listen on http://localhost:8000 and initializes dynamodb schema for local development

Local stack requires docker runtime on the local machine.

- **kite** stack is a production stack and creates serverless environment in the AWS, including dynamodb, api gateway, lambda function, iam roles.

## Deploying Stack
In order to run the cdktf stack you will need to have built native executable
of **k1te-serverless**. You can do it in two different ways:

I. This way utilizes separate cdktf stack called `lambda-build` that will create
AWS **CodeBuild** project and **S3** bucket which you will have to use to build
native executable Lambda function for ARM64 platform.
**It's important to noticed that this way requires to Sing In your GitHub account via aws cli so that it
has access to your Repository, please follow the [instruction](https://docs.aws.amazon.com/codebuild/latest/userguide/access-tokens.html).**

To do so follow these steps:
1. Go to cdktf directory via `cd k1te-serverless-stack-cdktf`
2. Run `lambda-build` stack (it can take some time to deploy):
```bash
cdktf deploy lambda-build 
```
3. When stack is successfully deployed it will output several values (if it doesn't use `cdktf output lambda-build`) namely:
    - S3 Bucket name that will contain built Lambda archive.
    - S3 Object Key to built Lambda.
    - Name of CodeBuild Project that you need to run it manually.

4. Afterward you will need to manually start just created CodeBuild Project
   and retrieve BuildProject id that we will use in the future. To do so run the next command and
   replace **your-project-name** with the value that you got from the previous step.

```bash
aws codebuild start-build --project-name your-project-name --query 'build.id' --output text
```
this command will return Project id that you can assign to variable like that:
```bash
id=$(aws codebuild start-build --project-name your-project-name --query 'build.id' --output text)
```
5. Wait until build finish (It usually takes 8 min). In order to check the status of the build you can use the following command,
   but don't forget to replace **your-build-id** with the value that you got in the previous step.
```bash
aws codebuild batch-get-builds --ids "your-build-id" --query 'builds[].buildStatus' --output text 
```
6. When the status of the Build is **SUCCEEDED** you successfully built Lambda function for ARM64.
7. In order to deploy main stack you need to specify retrieved S3 and S3 Object values in `.env` file, like this:
   `MAIN_LAMBDA_S3_BUCKET="build-bucket-20231211093835484800000001"`
   `MAIN_LAMBDA_S3_OBJECT_KEY="build/function.zip"`
   this values will be used to deploy Lambda function. And also go to `main.ts` and add
   `s3LambdaStorage: true` to your stack.
8. Now you can deploy the main stack via:
```bash
cdktf deploy "kite" --var-file=.env --auto-approve=true
```
9. After successful deployment of the main stack you can delete `lambda-build` stack if you wish via:
```bash
cdktf destroy lambda-build 
```

**II.** This way assume that you have ARM64 based platform.
1) Build native executable of k1te-serverless module via:
```bash
./mvnw -pl k1te-serverless -am install -Dnative -DskipTests
```
2) Deploy `kite` stack via 
```bash
cdktf deploy "kite" --var-file=.env --auto-approve=true
```

## Stack Variables
When you deploy stack you can specify several non-required variables wich
will add some additional logic to your stack namely:
- **devEnv** - If set to **true**, add additional stack called **dev** to your stack.
This dev environment incorporates additional Rest/WebSocket stages, Lambda, Logs, S3, DynamoDB
- **cicd** - If set to **true**, add CodeBuild projects which Build 
updated ARM64 Lambda and publish new Lambda version for both dev(if exists)/prod, and also updates
Lambda Alias for dev Lambda when code in GitHub Repository is updated. When a new Tag is pushed to GitHub it updates
Lambda Alias for prod env function. GitHub URL is specified in `build-component.ts` as a `https://github.com/pragmasoft-ua/kite-chat`
- **s3LambdaStorage** - if set to **true**, uses defined **MAIN_LAMBDA_S3_BUCKET**
and **MAIN_LAMBDA_S3_OBJECT_KEY** variables in **.env** file which point at function.zip archive of ARM64 Lambda to create a **mainHandler** that will
be used for both **prod/dev**(if devEnv is true) Lambdas.
If set to **false** - MainHandler Lambda will use **function.zip** from k1te-serverless/target and
upload it to S3 Bucket.

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
