# Serverless entry point for Kite Chat

## Build and install

### Prerequisites

- AWS account
- AWS credentials in ~/.aws
- AWS CLI

### Set up IAM role

```bash
aws iam create-role --role-name lambda-execution --assume-role-policy-document '{"Version": "2012-10-17","Statement": [{ "Effect": "Allow", "Principal": {"Service": "lambda.amazonaws.com"}, "Action": "sts:AssumeRole"}]}'
```

and notice its **Arn** in the output like `arn:aws:iam::763507571005:role/lambda-execution`

### Create function

```bash
 aws lambda create-function --function-name TestLambda --zip-file fileb://./target/function.zip --handler "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest" --runtime java17 --role "arn:aws:iam::763507571005:role/lambda-execution" --timeout 15 --memory-size 128
```

### Invoke function

```bash
aws lambda invoke outfile.json --cli-binary-format raw-in-base64-out --function-name TestLambda --payload file://./payload.json --output json
```

### Delete function

```bash
aws lambda delete-function --function-name TestLambda
```
