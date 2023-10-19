# K1te chat

## Multimodule maven project

## Build serverless function

`mvn -pl k1te-serverless -am clean package`

## Deploy serverless function

`aws lambda update-function-code --function-name  tg-handler --zip-file fileb://k1te-serverless/target/function.zip --no-cli-pager`

`aws lambda publish-version --function-name tg-handler --no-cli-pager`
