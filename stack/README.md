# Kite chat CDKTF infrastructrure

## Description

Kite chat serverless backend is managed using CDK for Terraform.

## Prerequisites

CDK TF requires:

- node v16+ and npm
- terraform cli
- cdktf cli `npm install --global cdktf-cli@latest`
- aws cli with configured credentials (for production stack only)
- docker runtime (for local stack only)

More details can be found here: [CDK TF Installation](https://developer.hashicorp.com/terraform/tutorials/cdktf/cdktf-install)

For node installation it's recommended to use `nvm`

On Windows you can use `choco` to install most dependencies

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
