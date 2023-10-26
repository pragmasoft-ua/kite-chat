/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.serverless.handler.event;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;

public class WebSocketLambdaEvent extends APIGatewayV2WebSocketEvent implements LambdaEvent {}
