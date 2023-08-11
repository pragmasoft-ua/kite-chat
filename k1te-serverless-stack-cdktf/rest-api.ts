import { Apigatewayv2Api } from "@cdktf/provider-aws/lib/apigatewayv2-api";
import { TerraformOutput } from "cdktf";

import { Apigatewayv2Integration } from "@cdktf/provider-aws/lib/apigatewayv2-integration";
import { Apigatewayv2Route } from "@cdktf/provider-aws/lib/apigatewayv2-route";
import { Construct } from "constructs";
import { Lambda } from "./lambda";
import { LambdaPermission } from "@cdktf/provider-aws/lib/lambda-permission";
import { Apigatewayv2Stage } from "@cdktf/provider-aws/lib/apigatewayv2-stage";
import { ApiProps } from "./websocket-api";
import { Apigatewayv2DomainName } from "@cdktf/provider-aws/lib/apigatewayv2-domain-name";

export type RestApiHandlerProps = {
  route: string;
  method?: string;
  handler: Lambda;
};

class RestApiHandler extends Construct {
  readonly integration: Apigatewayv2Integration;
  readonly route: Apigatewayv2Route;

  constructor(scope: Construct, props: Readonly<RestApiHandlerProps>) {
    const { route, method = "GET", handler } = props;

    const routeKey = `${method} ${route}`;

    super(scope, routeKey);

    const gw = (scope as RestApi).gw;

    let name = `${routeKey}-integration`;

    this.integration = new Apigatewayv2Integration(this, name, {
      apiId: gw.id,
      integrationType: "AWS_PROXY",
      integrationUri: handler.fn.arn,
    });

    name = `${routeKey}-route`;

    this.route = new Apigatewayv2Route(this, name, {
      apiId: gw.id,
      routeKey,
      target: "integrations/" + this.integration.id,
    });

    name = `${routeKey}-lambda-permission`;

    new LambdaPermission(this, name, {
      functionName: handler.fn.functionName,
      action: "lambda:InvokeFunction",
      principal: "apigateway.amazonaws.com",
      sourceArn: `${gw.executionArn}/*/*`,
    });
  }
}

export class RestApi extends Construct {
  readonly gw: Apigatewayv2Api;
  readonly domainName?: Apigatewayv2DomainName;

  constructor(scope: Construct, id: string, props?: ApiProps) {
    super(scope, id);

    this.gw = new Apigatewayv2Api(this, id, {
      name: this.node.id,
      protocolType: "HTTP",
    });

    let name = id + "-stage";

    const defaultStage = new Apigatewayv2Stage(this, name, {
      apiId: this.gw.id,
      name: "$default",
      autoDeploy: true,
    });

    if (props) {
      const { domainName, certificateArn } = props;

      this.domainName = new Apigatewayv2DomainName(this, `${id}-domain-name`, {
        domainName,
        domainNameConfiguration: {
          certificateArn,
          endpointType: "REGIONAL",
          securityPolicy: "TLS_1_2",
        },
      });
    }

    new TerraformOutput(this, "url", {
      value: defaultStage.invokeUrl,
    });
  }

  addHandler(route: string, method: string, handler: Lambda): this {
    new RestApiHandler(this, { route, method, handler });
    return this;
  }
}
