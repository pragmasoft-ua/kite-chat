# 🪁Kite Chat Server

Kite chat server provides websocket endpoint for webchat widget and telegram bot webhook to forward messages to Telegram channels.

## Running the application in dev mode

### Public URL

In order to test telegram webhooks, you need public url. One way to obtain it for local development
is [ngrok](https://ngrok.com/download). Install it with `choco install ngrok` on Windows, `snap install ngrok` on
Linux, `brew install ngrok/ngrok/ngrok` on Mac. Then run command `ngrok http 8080` from the command line. Ngrok writes
public url to its output like
`Forwarding https://71d6-185-235-173-207.eu.ngrok.io -> http://localhost:8080`. Copy the **host name** from the first
url and use it as a value of the `%dev.host.name=71d6-185-235-173-207.eu.ngrok.io` property in the `.env` config file.
If you don't have `.env` config file, first create it by copying from `.env.example.txt`

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

Kite chat uses DynamoDB as its NoSQL database. For development purposes you need docker container
running `amazon/dynamodb-local` image.

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

### Run the Application with local H2 DataBase and Local filesystem

In order to do so, you need run Quarkus app with a **standalone** profile specified
for Maven. It can be done by the following command.

```bash
mvn quarkus:dev -P standalone
```

You also need to check **.env.example.txt** file to specify all the necessary properties.

#### Additional information that you should know before running the app

During working Local file system implementation you may encounter to problem how **Ngrok** works.

1. It may **ask** you to register account (It's for free)
2. It may also **block** file downloading that happens when you send any file from Telegram
   to WS and get .**htm** file with an error as a result. In order to solve this problem you can make use
   of **[ModHeader](https://chrome.google.com/webstore/detail/modheader-modify-http-hea/idgpnmonknjnojddfkpgkljpfnnfcklj)**
   Chrome extension and specify **ngrok-skip-browser-warning** for all Http requests.

### Deploy on OpenShift

To deploy this application on the OpenShift platform, follow these steps:

1. **Install OpenShift CLI**: Ensure you have the OpenShift CLI installed on your computer. You can find installation
   instructions [here](https://docs.openshift.com/container-platform/4.9/cli_reference/openshift_cli/getting-started-cli.html)
   .
2. **Sign in to your OpenShift account via CLI**:

   ```bash
   oc login -u myUsername
   ```

   or

   ```bash
   oc login --token=myToken --server=myServerUrl
   ```

   You can obtain the token via the "Copy Login Command" link in the OpenShift web console.

3. **Set up Persistent Volume Claim (PVC)**:

   To ensure your application's data persistence, you need to utilize a Persistent Volume Claim (PVC). This allows the
   app to save files to a chosen Persistent Volume, which can be either manually selected or dynamically generated via
   StorageClass provided by OpenShift. It allows you re-deploy the Pod without risking to lose your data, because for
   OpenShift we use
   **standalone** Maven Profile that utilizes H2 as a DataBase and Local FileSystem for saving chats' files.
   <br/>
   To create the PVC resource, run the following command:

   ```bash
   oc apply -f ./k1te-server-pvc.yaml
   ```

   The command creates a PVC named **k1te-server-pvc**. To verify the creation of this resource, execute the following
   command:

   ```bash
   oc get pvc
   ```

   This will display the PVC resource named **k1te-server-pvc**.

4. **Checking application.properties file for OpenShift**:

   Before going further you should check application.properties file, OpenShift section.
   <br/>
   As the main resource we use StatefulSet, because with simple Deployment it will be difficult to re-deploy
   the app, because we use PVC, and it provides us with ReadWriteOnce AccessMode that doesn't work with Rolling Update (
   during re-deploy new Pod is created and only afterwards the old one is destroyed, but new one can't be created
   because
   PVC is already attached). But you can also fix it via **Recreate** Strategy type for Deployment.
   <br/>
   You also need to specify HOST_NAME as env to make the app works, because Telegram WebHook works only with https.
   As a solution to it, we use CloudFlare that allows us to proxy one of the OpenShift Routes and attach domain to it.

5. **Deploy the application**:

   Execute the following command, replacing the example values with your own:

   ```bash
   ./mvnw install -Dquarkus.kubernetes.deploy=true \
                  -P standalone \
                  -Dquarkus.openshift.env.vars.secret=your-secret \
                  -Dquarkus.openshift.env.vars.telegram-bot-token=your-bot-token \
                  -DHOST_NAME=your-host-name
   ```

   This command performs an S2I binary build, where the input is the locally built JAR, and the output is an ImageStream
   configured to automatically trigger a deployment.

   After some time, necessary resources will be created. You can verify this using the CLI:

   - `oc get pods` should display a single generated Pod with **k1te-server-\*** as its name.
   - `oc get statefulSet` should show a single generated StatefulSet with **k1te-server** as its name.
   - `oc get svc` should present a single generated Service named **k1te-server** with Port **80**.
   - `oc get routes` should exhibit a single generated Route named **k1te-server**, attached to the **k1te-server**
     Service.

   Additional properties can be added or modified for this deployment as needed. Refer to
   this [resource](https://quarkus.io/guides/deploying-to-openshift#configuration-reference) for more information.

#### Destroy the Deployment

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
