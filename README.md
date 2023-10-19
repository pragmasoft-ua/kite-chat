# K1te chat

## Multimodule maven project

## Build serverless function

`mvn -pl k1te-serverless -am clean package`

## Deploy serverless function

`aws lambda update-function-code --function-name  tg-handler --zip-file fileb://k1te-serverless/target/function.zip --no-cli-pager`

`aws lambda publish-version --function-name tg-handler --no-cli-pager`

"https://api.telegram.org/file/bot5522890470:AAHlsO9qwcH0uDWpZdz-ZzerBJRbVqEH-fQ/photos/file_2.jpg"
