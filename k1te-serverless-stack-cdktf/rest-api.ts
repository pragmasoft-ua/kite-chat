import { Apigatewayv2Api } from "@cdktf/provider-aws/lib/apigatewayv2-api";
import { TerraformOutput } from "cdktf";

import { Apigatewayv2ApiMapping } from "@cdktf/provider-aws/lib/apigatewayv2-api-mapping";
import { Apigatewayv2DomainName } from "@cdktf/provider-aws/lib/apigatewayv2-domain-name";
import { Apigatewayv2Integration } from "@cdktf/provider-aws/lib/apigatewayv2-integration";
import { Apigatewayv2Route } from "@cdktf/provider-aws/lib/apigatewayv2-route";
import { Apigatewayv2Stage } from "@cdktf/provider-aws/lib/apigatewayv2-stage";
import { CloudwatchLogGroup } from "@cdktf/provider-aws/lib/cloudwatch-log-group";
import { Construct } from "constructs";
import { API_GATEWAY_SERVICE_PRINCIPAL } from "./apigateway-principal";
import { ServicePrincipal } from "./grantable";
import { Lambda } from "./lambda";
import { ApiProps } from "./websocket-api";

export type RestApiHandlerProps = {
  route: string;
  method?: string;
  handler: Lambda;
};

class RestApiHandler extends Construct {
  readonly integration: Apigatewayv2Integration;
  readonly route: Apigatewayv2Route;

  constructor(scope: RestApiStage, props: Readonly<RestApiHandlerProps>) {
    const { route, method = "GET", handler } = props;

    const routeKey = `${method} ${route}`;

    super(scope, routeKey);

    const gw = scope.api.api;

    let name = "lambda-integration";

    this.integration = new Apigatewayv2Integration(this, name, {
      apiId: gw.id,
      integrationType: "AWS_PROXY",
      integrationUri: handler.arn,
      payloadFormatVersion: "2.0",
    });

    name = "route";

    this.route = new Apigatewayv2Route(this, name, {
      apiId: gw.id,
      routeKey,
      target: "integrations/" + this.integration.id,
    });

    name = "lambda-permission";

    handler.allowInvocationForService(scope.api);
  }
}

export type RestApiStageProps = {
  stage: string;
  logRetentionDays?: number;
  loggingLevel?: "ERROR" | "INFO" | "OFF";
};

export class RestApiStage extends Construct {
  readonly stage: Apigatewayv2Stage;
  readonly api: RestApi;

  constructor(scope: RestApi, id: string, props: RestApiStageProps) {
    super(scope, id);

    this.api = scope;

    const {
      stage,
      logRetentionDays: retentionInDays = 7,
      loggingLevel = "INFO",
    } = props || {};

    const accessLogGroup = new CloudwatchLogGroup(this, "access-logs", {
      name: `/aws/apigateway/${scope.api.name}/${stage}/access-logs`,
      retentionInDays,
    });

    this.stage = new Apigatewayv2Stage(this, "stage", {
      apiId: scope.api.id,
      name: stage,
      autoDeploy: true,
      accessLogSettings: {
        destinationArn: accessLogGroup.arn,
        format: JSON.stringify({
          requestId: "$context.requestId",
          requestTime: "$context.requestTime",
          httpMethod: "$context.httpMethod",
          path: "$context.path",
          routeKey: "$context.routeKey",
          status: "$context.status",
          responseLatency: "$context.responseLatency",
          integrationRequestId: "$context.integration.requestId",
          functionResponseStatus: "$context.integration.status",
          integrationLatency: "$context.integration.latency",
          integrationServiceStatus: "$context.integration.integrationStatus",
          ip: "$context.identity.sourceIp",
          userAgent: "$context.identity.userAgent",
          principalId: "$context.authorizer.principalId",
        }),
      },
      defaultRouteSettings: {
        dataTraceEnabled: true,
        loggingLevel,
        detailedMetricsEnabled: false,
        throttlingRateLimit: 10,
        throttlingBurstLimit: 5,
      },
    });

    new TerraformOutput(this, "url", {
      value: this.stage.invokeUrl,
    });

    if (scope.domainName) {
      const nameMapping = new Apigatewayv2ApiMapping(this, "name-mapping", {
        apiId: scope.api.id,
        domainName: scope.domainName.domainName,
        stage: stage,
        apiMappingKey: stage,
      });

      // Outputs the WebSocket URL
      new TerraformOutput(this, "mapped-url", {
        value: `https://${nameMapping.domainName}/${stage}`,
      });
    }
  }

  addHandler(route: string, method: string, handler: Lambda): this {
    new RestApiHandler(this, { route, method, handler });
    return this;
  }

  getInvokeUrl() {
    return this.stage.invokeUrl;
  }

  done() {
    return this.api;
  }
}

export class RestApi extends Construct implements ServicePrincipal {
  readonly api: Apigatewayv2Api;
  readonly domainName?: Apigatewayv2DomainName;

  constructor(scope: Construct, id: string, props?: ApiProps) {
    super(scope, id);

    this.api = new Apigatewayv2Api(this, "api", {
      name: this.node.id,
      protocolType: "HTTP",
    });

    if (props) {
      const {
        domainName,
        certificate: { cert, validation },
      } = props;

      this.domainName = new Apigatewayv2DomainName(this, "domain-name", {
        domainName,
        domainNameConfiguration: {
          certificateArn: cert.arn,
          endpointType: "REGIONAL",
          securityPolicy: "TLS_1_2",
        },
        dependsOn: [validation],
      });
    }
  }

  addStage(stage: string, props: Readonly<RestApiStageProps> = { stage }) {
    return new RestApiStage(this, `${props.stage}-stage`, props);
  }

  get name() {
    return this.node.id;
  }

  get principal() {
    return API_GATEWAY_SERVICE_PRINCIPAL;
  }

  get sourceArn() {
    return `${this.api.executionArn}/*/*`;
  }
}
