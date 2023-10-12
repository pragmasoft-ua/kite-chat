import { Apigatewayv2Api } from "@cdktf/provider-aws/lib/apigatewayv2-api";
import { TerraformOutput } from "cdktf";

import { AcmCertificate } from "@cdktf/provider-aws/lib/acm-certificate";
import { Apigatewayv2ApiMapping } from "@cdktf/provider-aws/lib/apigatewayv2-api-mapping";
import { Apigatewayv2DomainName } from "@cdktf/provider-aws/lib/apigatewayv2-domain-name";
import { Apigatewayv2Integration } from "@cdktf/provider-aws/lib/apigatewayv2-integration";
import { Apigatewayv2IntegrationResponse } from "@cdktf/provider-aws/lib/apigatewayv2-integration-response";
import { Apigatewayv2Route } from "@cdktf/provider-aws/lib/apigatewayv2-route";
import { Apigatewayv2RouteResponse } from "@cdktf/provider-aws/lib/apigatewayv2-route-response";
import { Apigatewayv2Stage } from "@cdktf/provider-aws/lib/apigatewayv2-stage";
import { CloudwatchLogGroup } from "@cdktf/provider-aws/lib/cloudwatch-log-group";
import { Construct } from "constructs";
import { ExecuteApi } from "iam-floyd";
import { Lambda } from "./lambda";
import { TlsCertificate } from "./tls-certificate";
import { ApiGatewayPrincipal } from "./apigateway-principal";

const PING_REQUEST_TEMPLATE = JSON.stringify({ statusCode: 200 });

const PONG_RESPONSE_TEMPLATE = JSON.stringify(["PONG"]);

export type WebsocketApiStageProps = {
  stage: string;
  logRetentionDays?: number;
};

export type ApiProps = {
  domainName: string;
  certificate: TlsCertificate;
};

export class WebsocketApiStage extends Construct {
  private stage: Apigatewayv2Stage;
  private readonly api: WebsocketApi;

  constructor(
    scope: WebsocketApi,
    id: string,
    props: Readonly<WebsocketApiStageProps>
  ) {
    super(scope, id);

    const { stage, logRetentionDays: retentionInDays = 7 } = props;
    this.api = scope;

    const accessLogGroup = new CloudwatchLogGroup(this, "access-logs", {
      name: `/aws/apigateway/${scope.api.name}/${stage}/access-logs`,
      retentionInDays,
    });

    new CloudwatchLogGroup(this, "execution-logs", {
      name: `/aws/apigateway/${scope.api.id}/${stage}`,
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
          eventType: "$context.eventType",
          routeKey: "$context.routeKey",
          status: "$context.status",
          connectionId: "$context.connectionId",
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
        loggingLevel: "INFO",
        detailedMetricsEnabled: false,
        throttlingRateLimit: 10,
        throttlingBurstLimit: 5,
      },
    });
  }

  getInvokeUrl() {
    return this.stage.invokeUrl;
  }

  addDefaultRoutes(handler: Lambda, principal: ApiGatewayPrincipal) {
    const scope = this.api;
    const stage = this.stage.name;

    const integration = new Apigatewayv2Integration(
      this,
      "default-integration",
      {
        apiId: scope.api.id,
        integrationType: "AWS_PROXY",
        integrationUri: handler.arn,
        credentialsArn: principal.role.arn,
        contentHandlingStrategy: "CONVERT_TO_TEXT",
        passthroughBehavior: "WHEN_NO_MATCH",
      }
    );

    new Apigatewayv2IntegrationResponse(this, "default-integration-response", {
      apiId: scope.api.id,
      integrationId: integration.id,
      integrationResponseKey: "/200/",
    });

    const defaultRoute = new Apigatewayv2Route(this, "default-route", {
      apiId: scope.api.id,
      routeKey: "$default",
      target: "integrations/" + integration.id,
    });

    new Apigatewayv2RouteResponse(this, "default-route-response", {
      apiId: scope.api.id,
      routeId: defaultRoute.id,
      routeResponseKey: "$default",
    });

    const connectRoute = new Apigatewayv2Route(this, "connect-route", {
      apiId: scope.api.id,
      routeKey: "$connect",
      target: "integrations/" + integration.id,
    });

    new Apigatewayv2RouteResponse(this, "connect-route-response", {
      apiId: scope.api.id,
      routeId: connectRoute.id,
      routeResponseKey: "$default",
    });

    const disconnectRoute = new Apigatewayv2Route(this, "disconnect-route", {
      apiId: scope.api.id,
      routeKey: "$disconnect",
      target: "integrations/" + integration.id,
    });

    new Apigatewayv2RouteResponse(this, "disconnect-route-response", {
      apiId: scope.api.id,
      routeId: disconnectRoute.id,
      routeResponseKey: "$default",
    });

    // PING

    const pingIntegration = new Apigatewayv2Integration(
      this,
      "ping-integration",
      {
        apiId: scope.api.id,
        integrationType: "MOCK",
        templateSelectionExpression: "200",
        requestTemplates: {
          "200": PING_REQUEST_TEMPLATE,
        },
      }
    );

    new Apigatewayv2IntegrationResponse(this, "ping-integration-response", {
      apiId: scope.api.id,
      integrationId: pingIntegration.id,
      integrationResponseKey: "/200/",
      templateSelectionExpression: "200",
      responseTemplates: {
        "200": PONG_RESPONSE_TEMPLATE,
      },
    });

    const pingRoute = new Apigatewayv2Route(this, "ping-route", {
      apiId: scope.api.id,
      routeKey: "PING",
      routeResponseSelectionExpression: "$default",
      target: "integrations/" + pingIntegration.id,
    });

    new Apigatewayv2RouteResponse(this, "ping-route-response", {
      apiId: scope.api.id,
      routeId: pingRoute.id,
      routeResponseKey: "$default",
    });

    handler.allowToInvoke(principal.role);

    /*
     * We cannot use token to define policy resource, like
     * '.on(stage.executionArn)' as it causes terraform cycle
     */
    const policyStatement = new ExecuteApi()
      .allow()
      .allActions()
      .onExecuteApiGeneral(scope.api.id, stage, "*", "*");

    handler.role.grant(
      `allow-execute-api-${scope.node.id}-${stage}`,
      policyStatement
    );

    // Outputs the WebSocket URL
    new TerraformOutput(this, "url", {
      value: this.stage.invokeUrl,
    });

    if (scope.domainName) {
      const nameMapping = new Apigatewayv2ApiMapping(this, "domain-mapping", {
        apiId: scope.api.id,
        domainName: scope.domainName.domainName,
        stage,
        apiMappingKey: stage,
      });

      // Outputs the WebSocket URL
      new TerraformOutput(this, "mapped-url", {
        value: `wss://${nameMapping.domainName}/${stage}`,
      });
    }
  }
}

export class WebsocketApi extends Construct {
  readonly api: Apigatewayv2Api;
  readonly cert?: AcmCertificate;
  readonly domainName?: Apigatewayv2DomainName;

  constructor(scope: Construct, id: string, props?: ApiProps) {
    super(scope, id);

    this.api = new Apigatewayv2Api(this, id, {
      name: id,
      protocolType: "WEBSOCKET",
      routeSelectionExpression: "$request.body.[0]", // "\\$default",
      // dependsOn: [account],
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

  public addStage(props: Readonly<WebsocketApiStageProps>) {
    return new WebsocketApiStage(this, `${props.stage}-stage`, props);
  }
}
