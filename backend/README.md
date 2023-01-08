# Kite Chat Backend

Kite chat allows to add live web chat widget to any web site and use Telegram channel as a support team's backend
to reply live chat requests.

Kite chat backend provides websocket endpoint for webchat widget and telegram bot webhook to forward messages to the Telegram channels.

This project uses Quarkus. If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

## Running the application in dev mode

In order to test telegram webhooks, you need public url. One way to obtain it for local development is [ngrok](https://ngrok.com/download). Install it with `choco install ngrok` on Windows, `snap install ngrok` on Linux, `brew install ngrok/ngrok/ngrok` on Mac. Then run command `ngrok http 8080` from the command line. Ngrok writes public url to its output like
`Forwarding https://71d6-185-235-173-207.eu.ngrok.io -> http://localhost:8080`. Copy the first url and add it as a value of the `%dev.webhook.host` property in the `.env` config file. If you don't have `.env` config file, first create it by copying from `.env.example.txt`

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

### TODO

stop exposing real chat ids and websocket ids as member ids
https://quarkus.io/guides/smallrye-fault-tolerance (timeouts? circuit breaker?)
suport of message editing
instead of connections use secondary index
get rid of duplicated dto classes in ws connector
ttl
Throttling (waf, api gw)
Authentication - blockchain like message ids signing idea? keep hashed userid using site's domain name? read about telegrambot token signature protection
For web - use js challenge - respond web client with random uri to which it can connect, rather than connecting always to the same uri
Serverless
CDKTF
AnsweringMachineConnector
MessageRecorderConnector
? GraalVM - Nanoid initialize random @runtime configuration (broken build)
@nxrocks/nx-quarkus
https://github.com/schnatterer/moby-names-generator-java
initialize lambda based webhook - local-exec provisioner using curl
