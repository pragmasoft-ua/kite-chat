# End-to-End Testing for Kite to Telegram Integration

This project includes comprehensive end-to-end tests for the integration between Kite and Telegram.

## Telegram Authentication App

Before running any tests, it's essential to authenticate with Telegram Web and generate the '
auth.json' file. The 'auth.json' file stores essential cookies and local storage data necessary for
conducting tests in the Telegram Web.

To run the Telegram Authentication App, execute the following Maven command in the 'kite-chat'
directory:

```bash
./mvnw -pl k1te-serverless-test compile exec:java -Dexec.mainClass=ua.com.pragmasoft.TelegramAuthenticationApp
```

This command launches the Chromium browser, navigates to the Telegram Web page, and prompts you to
log in. Once the login is successful, the browser closes, and the 'auth.json' file is generated in
the 'k1te-serverless-test' directory.

You can also specify the login timeout as a system property. The default timeout is 60 seconds. To
set a custom timeout, include the '-Dtimeout' parameter in the command. For example:

```bash
./mvnw -pl k1te-serverless-test compile exec:java -Dexec.mainClass=ua.com.pragmasoft.TelegramAuthenticationApp -Dtimeout=80000
```

## Test Classes

### BaseTest

- **Purpose**: Base test class for end-to-end testing of the Telegram and Kite chat integration.
- **Tags**: None
- **Description**: Manages setup and teardown of necessary resources and provides utility methods
  for testing.

### BeforeAllCallbackExtension

- **Purpose**: Abstract class that implements setup and teardown methods run before and after all
  tests.
- **Tags**: None
- **Description**: Use `@ExtendWith()` to ensure setup and teardown for all tests.

### KiteToTelegramTests

- **Purpose**: Test class focusing on Kite to Telegram integration scenarios.
- **Tags**: `kite-to-telegram`
- **Description**: Covers various test cases related to interactions from Kite Chat to Telegram
  Chat.

### TelegramToTelegramTests

- **Purpose**: Test class focusing on Telegram to Telegram interactions.
- **Tags**: `telegram-to-telegram`
- **Description**: Tests scenarios involving interactions between Telegram Host chat and Telegram
  Member chat.

### TelegramTests

- **Purpose**: Test class for general Telegram integration testing.
- **Tags**: `telegram`
- **Description**: Includes a variety of tests related to Telegram commands.

## Running Tests

To run specific groups of tests, you can use Maven with the following command:

```bash
./mvnw -pl k1te-serverless-test test -Dgroups=<tag-name>
```
When you initiate tests, two Telegram Groups will be created before all tests.
The specified bot will be added to these groups and promoted to the admin.
Once all tests are completed, the two groups created during the initialization step 
will be automatically deleted. This logic is implemented to streamline the testing 
process and eliminate the need for manual intervention.

## System Properties

You can customize test behavior by passing system properties. Here are some available properties:

- **kite.url**: URL for Kite chat that is used for testing. Default is `https://www.k1te.chat/test`.
- **channel**: Channel name of Telegram channel. Default is `k1te_chat_test`.
- **headless**: Run tests in headless mode. Default is `true`.

### Run Tests in Head Mode

```bash
./mvnw -pl k1te-serverless-test test -Dheadless
```

### Customize Kite URL and Telegram Channel

```bash
./mvnw -pl k1te-serverless-test test -Dkite.url=https://www.custom-kite-url.com -Dchannel=custom_channel
```
