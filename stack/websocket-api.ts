import { Apigatewayv2Api } from "@cdktf/provider-aws/lib/apigatewayv2-api";
import { TerraformOutput } from "cdktf";

import { LambdaFunction } from "@cdktf/provider-aws/lib/lambda-function";
import { LambdaPermission } from "@cdktf/provider-aws/lib/lambda-permission";
import { Construct } from "constructs";
import { Apigatewayv2Stage } from "@cdktf/provider-aws/lib/apigatewayv2-stage";
import { Apigatewayv2Integration } from "@cdktf/provider-aws/lib/apigatewayv2-integration";
import { Apigatewayv2Route } from "@cdktf/provider-aws/lib/apigatewayv2-route";

export type WebsocketApiProps = {
  handler: LambdaFunction;
};

export class WebsocketApi extends Construct {
  readonly gw: Apigatewayv2Api;

  constructor(
    scope: Construct,
    id: string,
    props: Readonly<WebsocketApiProps>
  ) {
    super(scope, id);

    const { handler } = props;

    let name = id;

    this.gw = new Apigatewayv2Api(this, name, {
      name,
      protocolType: "WEBSOCKET",
      routeSelectionExpression: "$request.body.action",
    });

    name = id + "-stage";

    const defaultStage = new Apigatewayv2Stage(this, name, {
      apiId: this.gw.id,
      name: "prod",
      autoDeploy: true,
    });

    name = "default-integration";

    const integration = new Apigatewayv2Integration(this, name, {
      apiId: this.gw.id,
      integrationType: "AWS_PROXY",
      integrationUri: handler.arn,
    });

    name = "default-route";

    new Apigatewayv2Route(this, name, {
      apiId: this.gw.id,
      routeKey: "$default",
      target: "integrations/" + integration.id,
    });

    name = `${id}-lambda-permission`;

    new LambdaPermission(this, name, {
      functionName: handler.functionName,
      action: "lambda:InvokeFunction",
      principal: "apigateway.amazonaws.com",
      sourceArn: `${this.gw.executionArn}/*/*`,
    });

    // Outputs the WebSocket URL
    new TerraformOutput(this, "url", {
      value: defaultStage.invokeUrl,
    });
  }
}
