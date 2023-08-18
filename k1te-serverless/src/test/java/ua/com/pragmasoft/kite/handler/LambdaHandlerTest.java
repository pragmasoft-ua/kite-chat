package ua.com.pragmasoft.kite.handler;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class LambdaHandlerTest {

  @Test
  public void testSimpleLambdaSuccess() throws Exception {
    // you test your lambdas by invoking on http://localhost:8081
    // this works in dev mode too

    Lifecycle in = new Lifecycle();
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
