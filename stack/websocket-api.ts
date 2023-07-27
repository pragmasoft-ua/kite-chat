import { Apigatewayv2Api } from "@cdktf/provider-aws/lib/apigatewayv2-api";
import { TerraformOutput } from "cdktf";

import { Apigatewayv2Integration } from "@cdktf/provider-aws/lib/apigatewayv2-integration";
import { Apigatewayv2Route } from "@cdktf/provider-aws/lib/apigatewayv2-route";
import { LambdaFunction } from "@cdktf/provider-aws/lib/lambda-function";
import { Construct } from "constructs";

export type WebsocketApiProps = {
  handler: LambdaFunction;
};

export class WebsocketApi extends Construct {
  readonly gw: Apigatewayv2Api;
  readonly integration: Apigatewayv2Integration;
  readonly route: Apigatewayv2Route;

  constructor(
    scope: Construct,
    id: string,
    props: Readonly<WebsocketApiProps>
  ) {
    super(scope, id);

    const { handler } = props;

    let name = this.node.id;

    this.gw = new Apigatewayv2Api(this, name, {
      name,
      protocolType: "WEBSOCKET",
    });

    name = `${this.node.id}-ws-lambda-integration`;

    this.integration = new Apigatewayv2Integration(this, name, {
      apiId: this.gw.id,
      integrationType: "AWS_PROXY",
      integrationUri: handler.arn,
    });

    name = `${this.node.id}-ws-default-route`;

    this.route = new Apigatewayv2Route(this, name, {
      apiId: this.gw.id,
      routeKey: "$default",
      target: "integrations/" + this.integration.id,
    });

    // Outputs the WebSocket URL
    new TerraformOutput(this, "WebSocketURL", {
      value: this.gw.apiEndpoint,
    });
  }
}
