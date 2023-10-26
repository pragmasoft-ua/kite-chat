```
 █████   ████  ███   █████                         █████                 █████
░░███   ███░  ░░░   ░░███                         ░░███                 ░░███
 ░███  ███    ████  ███████    ██████      ██████  ░███████    ██████   ███████
 ░███████    ░░███ ░░░███░    ███░░███    ███░░███ ░███░░███  ░░░░░███ ░░░███░
 ░███░░███    ░███   ░███    ░███████    ░███ ░░░  ░███ ░███   ███████   ░███
 ░███ ░░███   ░███   ░███ ███░███░░░     ░███  ███ ░███ ░███  ███░░███   ░███ ███
 █████ ░░████ █████  ░░█████ ░░██████    ░░██████  ████ █████░░████████  ░░█████
░░░░░   ░░░░ ░░░░░    ░░░░░   ░░░░░░      ░░░░░░  ░░░░ ░░░░░  ░░░░░░░░    ░░░░░
```

# Kite Chat Server

Kite chat allows to add live web chat widget to any web site and use Telegram channel as a support team's backend
to reply live chat requests.

Kite chat backend provides websocket endpoint for webchat widget and telegram bot webhook to forward messages to the Telegram channels.

This project uses Quarkus. If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

## Running the application in dev mode

### Public URL

In order to test telegram webhooks, you need public url. One way to obtain it for local development is [ngrok](https://ngrok.com/download). Install it with `choco install ngrok` on Windows, `snap install ngrok` on Linux, `brew install ngrok/ngrok/ngrok` on Mac. Then run command `ngrok http 8080` from the command line. Ngrok writes public url to its output like
`Forwarding https://71d6-185-235-173-207.eu.ngrok.io -> http://localhost:8080`. Copy the **host name** from the first url and use it as a value of the `%dev.host.name=71d6-185-235-173-207.eu.ngrok.io` property in the `.env` config file. If you don't have `.env` config file, first create it by copying from `.env.example.txt`

Alternatively, you can use Microsoft [devtunnels](https://learn.microsoft.com/ru-ru/azure/developer/dev-tunnels/overview) for the same purpose, which has some benefits, like VsCode integration, permanent URL, auth.

**⛔IMPORTANT** devtunnels seems do not tunnel websockets properly.

```bash
#login with github account
devtunnel user login -g
devtunnel host -p 8080 -a
## after tunnel is created, you simply can host it with
devtunnel host
curl https://sw1r28pt-8080.euw.devtunnels.ms/
```

### Local DynamoDb database

Kite chat uses DynamoDB as its NoSQL database. For development purposes you need docker container running `amazon/dynamodb-local` image.

`k1te-serverless-stack-cdktf` folder contains CDKTF local stack which launches dynamodb-local container and initializes
DynamoDB database schema in it. Shortly, you need switch to the `k1te-serverless-stack-cdktf` folder and run

`npm init` followed by `npm run deploy-local`.

Then once you're done, you can shut down local dynamodb with `npm run destroy-local`

For more details see README.md in the `k1te-serverless-stack-cdktf` folder.

### Run backend in dev mode

Then you can run your application in dev mode that enables live coding using:

```shell script
./mvnw compile quarkus:dev
```

> **NOTE:** Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Pnative
```

On Windows, the command above needs to be launched from the "x64 native tools command prompt for VS 2019" which appears in the Windows start menu after the installation of Visual Studio Biold Tools 2019

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Pnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/k1te-chat-backend-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/maven-tooling.

### Upgrade dependencies

First, upgrade Quarkus with `choco upgrade quarkus` (on Windows) or `sdk upgrade quarkus` (on Linux), then use `quarkus upgrade` to upgrade Quarkus BOM in the pom, then upgrade other dependencies `./mvnw versions:use-latest-releases`

### TODO

- ✅`/leave` does not work **Cannot dispatch request, no route found**, then simply **Not found**
- if I send an image it responds with error message `text`
- After I add a member to the support chat, got ⛔ Not found member

- support for interactive inputs (like telegram keyboards / inline keyboards)
- typing indicator `sendChatAction`
- prototype using forum topics for threads
- telegram login for web client. Other ways to verify user's contact (email validation, social login)
- deep links to start conversation in Telegram immediately
- check error handling
- localization (uk, en); message bundles
- document creating group in Telegram. Requires to turn off privacy mode for https://t.me/k1techatbot
- stop exposing real chat ids and websocket ids as member ids
- https://quarkus.io/guides/smallrye-fault-tolerance (timeouts? circuit breaker?)
- suport of message editing
- messages sent in telegram needs to be rewritten by bot (removed and added again with the dialog id hashtag)
- ttl
- Throttling (waf, api gw) https://github.com/aws-samples/fine-grained-rate-limit-demo/
- Authentication - blockchain like message ids signing idea? keep hashed userid using site's domain name? Add telegrambot token signature protection. Put s3 behind api gw for throttling and custom lambda auth
- For web - use js challenge - respond web client with random uri to which it can connect, rather than connecting always to the same uri
- ✅Serverless
- AnsweringMachineConnector - try AI chatbot. https://github.com/langchain4j/langchain4j
- MessageRecorderListener
- ❌@nxrocks/nx-quarkus
- https://github.com/schnatterer/moby-names-generator-java
- ✅initialize lambda based webhook - ~~local-exec provisioner using curl~~ used lambda invocation instead
- ✅SnapStart
- GraalVM native image
- /info command - am I joined or hosted ? or amend /help
- /join alias of /start
- WhatsApp, Facebook messenger bots
- when channel is dropped by host, all clients have to leave as well
- pin unanswered messages? Maybe make this configurable.
- telegram throttling sometimes will be a concern. Consider using dedicated bot tokens for commercial clients. Also, research aws lambda retry mechanism as a solution to tg throttling for other clients.
- ✅proxy or vpn may cause ws connection closed after a minute of inactivity. Send ping from server every 30s or so and await for pong.
- API Gateway supports message payloads up to 128 KB with a maximum frame size of 32 KB. If a message exceeds 32 KB, you must split it into multiple frames, each 32 KB or smaller. If a larger message is received, the connection is closed with code 1009. Consider sending text larger than 4K (see below) as files.
- tg api largest text message is 4kb. Larger ones need to be split (or sent as file attachements).
- ✅it is better to send files with http put/post rather than websocket. Presigned s3 for large files. Only send url over websocket.
- trunk based development, feature flags
- ipv6 (not supported in ip gw currently)
- captcha or https://blog.cloudflare.com/turnstile-private-captcha-alternative/
- / bot menu
- CI/CD
