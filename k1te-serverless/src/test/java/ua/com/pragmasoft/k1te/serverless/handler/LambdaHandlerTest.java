package ua.com.pragmasoft.k1te.serverless.handler;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@Disabled
public class LambdaHandlerTest {

  @Test
  public void testSimpleLambdaSuccess() throws Exception {
    // you test your lambdas by invoking on http://localhost:8081
    // this works in dev mode too

    Lifecycle in = new Lifecycle();
    in.tf = new Lifecycle.Tf();
    in.tf.action = Lifecycle.Action.create.name();
    given()
        .contentType("application/json")
        .accept("application/json")
        .body(in)
        .when()
        .post()
        .then()
        .statusCode(200);
  }

}
