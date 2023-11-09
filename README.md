# 🪁🗨️ Kite Chat Server

## About

Kite chat allows to add live web chat widget on any static or dynamic web site and use Telegram channel as a support team's backend to respond to live chat messages.

## License

Both backend and frontend of Kite chat are available under LGPL v3 license.

## Design

Kite chat server is a multimodule Maven project.

It uses Java and [Quarkus](https://quarkus.io/)

Kite chat server supports both containerized and serverless deployments.

`k1te-backend` module contains core domain logic and abstractions common for both containerized and serverless backends.

### Containerized backend

Containerized backend mode is more suited for VPS, OpenShift, Kubernetes, ECS and standalone local deployments. It is also more convenient for local development and debugging.

`k1te-server` module supports building standalone or containerized server and deploy it
locally or on the OpenShift cluster. It supports switching between SQL (H2) and NoSQL (DynamoDB) databases, as well as choose one of the Filesystem or S3 storage options for binary messages using build profiles.

Preview of the OpenShift deployment is available here: https://openshift.k1te.chat/
It uses StatefulSet to mount persistent volume for both H2 database and filesystem based object store implementation.

More detailed build documentation and deployment instructions are available in the `k1te-server\README.md`

### Serverless backend

Serverless mode uses AWS Lambda and AWS API Gateway as a deployment target.

`k1te-serverless` module uses Quarkus GraalVM native compilation profile to build AWS Lambda with optimized cold start time and lower memory requirements. arm64 CPU architecture allows to optimize costs and performance even more, compared to x86_64

`k1te-serverless-stack-cdktf` module contains nodejs/typescript based serverless infrastructure as a code definition, used to automate deployment of the serverless application. It uses CDK for Terraform as IaaC framework.

Serverless backend is cost effective and scalable, well suitable for unpredictable and
spiky traffic of the typical web support system.

It is available for preview here: https://www.k1te.chat/ This site also contains Kite chat frontend reference documentation, user and integration guides.

`k1te-serverless\README.md` contains more details about building and (re)deploying serverless functions.

## Coding standards

Kite server uses modern build tools and maintains high coding standards. It uses unit and integration tests (JUnit, Playwright), static code analysis (SonarLint), formatter
(spotless), CI/CD pipelines (GitHub actions, CodeBuild), manual QA (Redmine, Github issues) to maintain high code quality.

### Format source code with spotless

Use `mvn spotless:apply` to reformat changed files according to google format guidelines.

Use `mvn verify` or `mvn spotless:check` to verify formatting is correct

Currently spotless is configured to ratchett mode and only formats files changed
from those in the main branch

Comment out this spotless option to format all files instead.
