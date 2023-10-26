/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.serverless.handler.event;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class HttpV2LambdaEvent extends APIGatewayV2HTTPEvent implements LambdaEvent {

  @JsonIgnore
  @Override
  public boolean getIsBase64Encoded() {
    return super.getIsBase64Encoded();
  }
}
