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

This application consists of two CDKTF stacks:

**local** stack starts local dynamodb container to listen on http://localhost:8000 and initializes dynamodb schema for local development

Local stack requires docker runtime on the local machine.

**kite** stack is a production stack and creates serverless environment in the AWS, including dynamodb, api gateway, lambda function, iam roles.

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

- ✅ Set up DNS name k1te.chat
- ❌Maven Build (null resource, local-exec provisioner) (replace with CI instead, see below)
- ✅Log retention - explicit log groups
- ✅JSON logs
- ❌Cloudfront? Decided to use Cloudflare for now
- ✅s3 terraform backend as in https://awstip.com/websocket-api-gateway-with-terraform-8a509585121d
- ✅ping ws route (mock integration) as in https://www.obytes.com/blog/aws-websocket-api
- ✅Refactor DNS part, add custom DNS name to REST API as well
- ✅Add access logs to both rest and ws stages
- WAF rule, budget alarm?
- replace http-api with function url?
- aws split charges (per customer) ?
- Move this TODO section items to **doc/adr** (architecture decision records)
- dev and prod stages
- consider sqs integration between ws api gw and lambda?
- CI as in https://www.obytes.com/blog/aws-lambda-ci
- Logging and telemetry to S3 as in https://lumigo.io/blog/lambda-telemetry-api-a-new-way-to-process-lambda-telemetry-data-in-real-time/
- Query logs above with Athena (S3 select only queries one file) or OpenSearch
