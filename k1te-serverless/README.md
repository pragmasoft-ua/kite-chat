# Serverless entry point for Kite Chat

Serverless mode uses AWS Lambda and AWS API Gateway as a deployment target.

`k1te-serverless` (this) module uses Quarkus GraalVM native compilation profile to build AWS Lambda with optimized cold start time and lower memory requirements.

## Build and install

### Prerequisites

- AWS account
- AWS credentials in ~/.aws with account administration rigths
- AWS [CLI tool](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)

### CDKTF stack

`k1te-serverless-stack-cdktf` module contains nodejs/typescript based serverless infrastructure as a code definition, used to automate deployment of the serverless application. It uses CDK for Terraform as IaaC framework. This module contains its own deployment documentation.

The deployment flow is following:

- In order to deploy CDKTF stack on your AWS account first time, you need this lambda project built properly first. Execute the command provided in the **Build native function version** section to build native lambda code in the `target/function.zip`. Building native code is lengthy process and may take 5-10 min depending on how powerful your build machine is.
- After your lambda function is built, go to the `k1te-serverless-stack-cdktf` module and use instructions there to deploy the stack to your AWS account.
- If you have changes in your lambda code or shared `k1te-backend` project you need to deploy, there's no need to redeploy entire CDKTF stack. Instead, **Build native function version** again from the updated code, then follow **Deploy serverless function version** instructions to update the lambda function only, this is much faster than updating the entire stack.

### Build native function version

`./mvnw package -Dnative -DskipTests -Dquarkus.native.container-build=true`

### Deploy serverless function version

`aws lambda update-function-code --function-name  request-dispatcher --zip-file fileb://k1te-serverless/target/function.zip --no-cli-pager`

`aws lambda publish-version --function-name request-dispatcher --no-cli-pager`
