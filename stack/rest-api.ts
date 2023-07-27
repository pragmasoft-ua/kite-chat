import { Apigatewayv2Api } from "@cdktf/provider-aws/lib/apigatewayv2-api";
import { TerraformOutput } from "cdktf";

import { Apigatewayv2Integration } from "@cdktf/provider-aws/lib/apigatewayv2-integration";
import { Apigatewayv2Route } from "@cdktf/provider-aws/lib/apigatewayv2-route";
import { LambdaFunction } from "@cdktf/provider-aws/lib/lambda-function";
import { Construct } from "constructs";
import { Lambda } from "./lambda";

export type RestApiHandlerProps = {
  route: string;
  method?: string;
  handler: LambdaFunction;
};

class RestApiHandler extends Construct {
  readonly integration: Apigatewayv2Integration;
  readonly route: Apigatewayv2Route;

  constructor(
    scope: Construct,
    id: string,
    props: Readonly<RestApiHandlerProps>
  ) {
    super(scope, id);

    const { route, method = "GET", handler } = props;

    const gw = (scope as RestApi).gw;

    let name = `${this.node.id}-rest-lambda-integration`;

    this.integration = new Apigatewayv2Integration(this, name, {
      apiId: gw.id,
      integrationType: "AWS_PROXY",
      integrationUri: handler.arn,
    });

    name = `${this.node.id}-rest-default-route`;

    this.route = new Apigatewayv2Route(this, name, {
      apiId: gw.id,
      routeKey: method + " " + route,
      target: "integrations/" + this.integration.id,
    });
  }
}

export class RestApi extends Construct {
  readonly gw: Apigatewayv2Api;

  constructor(scope: Construct, id: string) {
    super(scope, id);

    this.gw = new Apigatewayv2Api(this, this.node.id, {
      name: this.node.id,
      protocolType: "HTTP",
    });

    // Outputs the REST API URL
    new TerraformOutput(this, this.node.id + "-url", {
      value: this.gw.apiEndpoint,
    });
  }

  addHandler(route: string, method: string, handler: Lambda): this {
    new RestApiHandler(this, route, { route, method, handler: handler.fn });
    return this;
  }
}
