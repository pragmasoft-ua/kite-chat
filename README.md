# K1te chat

## Multimodule maven project

## Build serverless function

`mvn -pl k1te-serverless -am clean package`

## Deploy serverless function

`aws lambda update-function-code --function-name  tg-handler --zip-file fileb://k1te-serverless/target/function.zip --no-cli-pager`

`aws lambda publish-version --function-name tg-handler --no-cli-pager`

## Format source code with spotless

`mvn spotless:apply`

Use `mvn verify` or `mvn spotless:check` to verify formatting is correct

Currently spotless is configured to ratchett mode and only formats files changed
from those in the main branch

Comment out this spotless option to format all files instead.
