# 🪁Kite Chat Server

Kite chat server provides websocket endpoint for webchat widget and telegram bot webhook to forward messages to Telegram channels.

## Running the application in dev mode

### Public URL

In order to test telegram webhooks, you need public https url. One way to obtain it for local development is [ngrok](https://ngrok.com/download).
Install it with `choco install ngrok` on Windows, `snap install ngrok` on
Linux, `brew install ngrok/ngrok/ngrok` on Mac. Then run command `ngrok http 8080` from the command line. Ngrok writes public url to its output like
`Forwarding https://71d6-185-235-173-207.eu.ngrok.io -> http://localhost:8080`. Copy the **host name** from the first
url and use it as a value of the `%dev.host.name=71d6-185-235-173-207.eu.ngrok.io` property in the `.env` config file.
If you don't have `.env` config file, first create it by copying from `.env.example.txt`

❗`ngrok` requires you to create a free account and authenticate its cli.

⛔ https://ngrok.com/abuse Browser Warning section explains that `ngrok` may block
first request from the browser. This particularly may prevent proper file downloading.
You can use [ModHeader](https://chrome.google.com/webstore/detail/modheader-modify-http-hea/idgpnmonknjnojddfkpgkljpfnnfcklj) Chrome extension to add `ngrok-skip-browser-warning` header to all requests, which solves this problem.

Alternatively, you can use
Microsoft [devtunnels](https://learn.microsoft.com/ru-ru/azure/developer/dev-tunnels/overview) for the same purpose,
which has some benefits, like VsCode integration, permanent URL, auth.

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

Kite chat uses DynamoDB as its NoSQL database when built with the default profile.

For development purposes you can run DynamoDB locally using docker container with `amazon/dynamodb-local` image.

`k1te-serverless-stack-cdktf` folder contains CDKTF local stack which launches dynamodb-local container and initializes database schema in it.

To start local stack, switch to the `k1te-serverless-stack-cdktf` folder and run

`npm init` followed by `npm run deploy-local`.

When local DynamoDB container starts, you need to uncomment the following line in your `.env` file, to let DynamoDB client know it needs to connect to the local endpoint:

`%dev.quarkus.dynamodb.endpoint-override=http://localhost:8000`

If you don't have `.env` file, create it first by copying `.env.example.txt` and updating accordingly.

Once you're done, you can shut down local dynamodb with `npm run destroy-local`

For more details see README.md in the `k1te-serverless-stack-cdktf` folder.

### Run backend in dev mode

Then you can run your application in dev mode that enables live coding using:

```shell script
./mvnw compile quarkus:dev
```

> **NOTE:** Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev-ui/.

### Run backend in dev mode with local H2 DataBase and local filesystem

Run with a **standalone** and **dev** Maven profiles:

```bash
mvn quarkus:dev -Pstandalone,dev
```

If you don't have `.env` file, create it first by copying `.env.example.txt` and updating as explained in the Build Configuration section

## Deploy on OpenShift

To deploy this application on the OpenShift platform, follow these steps:

### Install OpenShift CLI

Ensure you have the OpenShift CLI installed on your computer. You can find installation
instructions [here](https://docs.openshift.com/container-platform/4.14/cli_reference/openshift_cli/getting-started-cli.html)

### Sign in to your OpenShift account via CLI

```bash
oc login --web
```

```bash
oc login -u myUsername
```

```bash
oc login --token=myToken --server=myServerUrl
```

You can obtain the token via the "Copy Login Command" link in the OpenShift web console.

### Create Persistent Volume Claim (PVC)

To store application state (messages, images, files, channels) backend needs a persistent volume.

To create the Persistent Volume Claim resource, run the following command:

```bash
oc apply -f ./k1te-server-pvc.yaml
```

The command creates a PVC named **k1te-server-pvc**.

To verify PVC was created successfully, use:

```bash
oc get pvc
```

### Build and install dependency

This project depends on the `k1te-backend` peer project, so you need to build and install it first:

```bash
cd ../k1te-backend
./mvnw clean install
cd ../k1te-server
```

### Configuring server build

If you don't have `.env` file, create it first by copying `.env.example.txt` and updating as explained in the Build Configuration section

`.env` file should contain the following mandatory configuration properties:

`telegram.bot.token` You need to create your Telegram bot with
https://t.me/BotFather and add here its token.

`%standalone.host.name` On start K1te server should register webhook on Telegram Bot API in order to receive updates. Only secure (https) webhooks are supported. This means, application should know its domain name before it's deployed and OpenShift
Route is created.

Our domain `k1te.chat` is hosted on the Cloudflare. Cloudflare also terminates TLS and provides CDN.

I was unable to find an easy way to obtain canonical domain name from OpenShift router before Route is created, which makes it kind of chicken and egg problem.

Canonical hostname looks like `router-default.apps.sandbox-m2.ll9k.p1.openshiftapps.com`
where `router-default` is prefixed to your sandbox app domain name you can take from your OpenShift admin dashboard address bar.

You need to create CNAME DNS record in your DNS provider like:

`CNAME openshift.k1te.com router-default.apps.sandbox-m2.ll9k.p1.openshiftapps.com`

and then configure host name in `.env` as:

`%standalone.host.name=openshift.k1te.com`

Once OpenShift Route is created, you can get canonical hostname from the Route details in admin dashboard

If you will have problems configuring hostname, please contact me pragmasoft@gmail.com or https://t.me/pragmasoft and I'll create for you temporary domain name in our zone.

`%standalone.jwt.secret` Arbitrary long string you keep in secret, used to sign and verify jwt tokens.

### Deploy the application

```bash
./mvnw deploy "-Dquarkus.kubernetes.deploy=true" -Pstandalone
```

This command performs an S2I binary build, where the input is the locally built JAR, and the output is an ImageStream configured to automatically trigger a deployment.

After some time, necessary resources will be created. You can verify this using the CLI:

- `oc get pods` should display a single generated Pod with **k1te-server-\*** as its name.
- `oc get statefulSet` should show a single generated StatefulSet with **k1te-server** as its name.
- `oc get svc` should present a single generated Service named **k1te-server** with Port **80**.
- `oc get routes` should exhibit a single generated Route named **k1te-server**, attached to the **k1te-server**
  Service.

Additional properties can be added or modified for this deployment as needed. Refer to
this [resource](https://quarkus.io/guides/deploying-to-openshift#configuration-reference) for more information.

### Destroy the Deployment

After using the application, you can remove it using the following commands:

- `oc delete statefulSet k1te-server`: Deletes the created StatefulSet.
- `oc delete svc k1te-server`: Deletes the created Service.
- `oc delete route k1te-server`: Deletes the created Route.
- `oc delete pvc k1te-server-pvc`: Deletes the created Persistent Volume Claim (PVC).
- `oc delete buildConfigs k1te-server`: Deletes the created Build Configs.
- `oc delete is k1te-server`: Deletes the created ImageStream.

Make sure to execute these commands cautiously as they will permanently remove the associated resources in your OpenShift environment.

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

On Windows, the command above needs to be launched from the "x64 native tools command prompt for VS 2019" which appears
in the Windows start menu after the installation of Visual Studio Biold Tools 2019

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Pnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/k1te-chat-backend-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/maven-tooling.
