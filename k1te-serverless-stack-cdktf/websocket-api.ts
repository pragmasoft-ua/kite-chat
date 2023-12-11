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
import { TlsCertificate } from "./tls-certificate";
import { ApiGatewayPrincipal } from "./apigateway-principal";
import {
  Api,
  ApiIntegration,
  ApiStage,
  ApiStageConfig,
  IntegrationConfig,
  InvocationConfig,
} from "./api";

const PING_REQUEST_TEMPLATE = JSON.stringify({ statusCode: 200 });

const PONG_RESPONSE_TEMPLATE = JSON.stringify(["PONG"]);

export type ApiProps = {
  domainName: string;
  certificate: TlsCertificate;
};

export class WebsocketApiStage extends Construct implements ApiStage {
  private stage: Apigatewayv2Stage;
  private readonly api: WebsocketApi;

  constructor(
    scope: WebsocketApi,
    id: string,
    props: Readonly<ApiStageConfig>
  ) {
    super(scope, id);

    const {
      stage,
      functionStageVariable,
      logRetentionDays: retentionInDays = 7,
      loggingLevel = "INFO",
    } = props;
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
      stageVariables: {
        function: functionStageVariable,
      },
      defaultRouteSettings: {
        dataTraceEnabled: true,
        loggingLevel,
        detailedMetricsEnabled: false,
        throttlingRateLimit: 10,
        throttlingBurstLimit: 5,
      },
    });

    // Outputs the WebSocket URL
    new TerraformOutput(this, "url", {
      value: this.stage.invokeUrl,
    });
  }

  get invokeUrl() {
    if (this.api.domainName) {
      return `https://${this.api.domainName.domainName}/${this.stage.name}`;
    }
    return this.stage.invokeUrl;
  }
}

export class WebsocketApi extends Construct implements Api {
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

  public addStage(props: Readonly<ApiStageConfig>) {
    return new WebsocketApiStage(this, `${props.stage}-stage`, props);
  }

  public attachDefaultIntegration(integrationConfig: IntegrationConfig) {
    const { region, accountId, integrationName, principal } = integrationConfig;

    const handlerArn =
      "arn:aws:lambda:" +
      region +
      ":" +
      accountId +
      ":function:$${stageVariables.function}";
    return new WsApiIntegration(this, integrationName, handlerArn, principal!);
  }
}

export class WsApiIntegration extends Construct implements ApiIntegration {
  private readonly websocketApi: WebsocketApi;
  private readonly integration: Apigatewayv2Integration;
  private readonly principal: ApiGatewayPrincipal;

  constructor(
    scope: WebsocketApi,
    id: string,
    handlerArn: string,
    principal: ApiGatewayPrincipal
  ) {
    super(scope, id);
    this.websocketApi = scope;
    this.principal = principal;

    this.integration = new Apigatewayv2Integration(this, id, {
      apiId: scope.api.id,
      integrationType: "AWS_PROXY",
      integrationUri: handlerArn,
      credentialsArn: principal.role.arn,
      contentHandlingStrategy: "CONVERT_TO_TEXT",
      passthroughBehavior: "WHEN_NO_MATCH",
    });
  }

  addRouteDefaultRoutes() {
    new Apigatewayv2IntegrationResponse(this, "default-integration-response", {
      apiId: this.websocketApi.api.id,
      integrationId: this.integration.id,
      integrationResponseKey: "/200/",
    });

    const defaultRoute = new Apigatewayv2Route(this, "default-route", {
      apiId: this.websocketApi.api.id,
      routeKey: "$default",
      target: "integrations/" + this.integration.id,
    });

    new Apigatewayv2RouteResponse(this, "default-route-response", {
      apiId: this.websocketApi.api.id,
      routeId: defaultRoute.id,
      routeResponseKey: "$default",
    });

    const connectRoute = new Apigatewayv2Route(this, "connect-route", {
      apiId: this.websocketApi.api.id,
      routeKey: "$connect",
      target: "integrations/" + this.integration.id,
    });

    new Apigatewayv2RouteResponse(this, "connect-route-response", {
      apiId: this.websocketApi.api.id,
      routeId: connectRoute.id,
      routeResponseKey: "$default",
    });

    const disconnectRoute = new Apigatewayv2Route(this, "disconnect-route", {
      apiId: this.websocketApi.api.id,
      routeKey: "$disconnect",
      target: "integrations/" + this.integration.id,
    });

    new Apigatewayv2RouteResponse(this, "disconnect-route-response", {
      apiId: this.websocketApi.api.id,
      routeId: disconnectRoute.id,
      routeResponseKey: "$default",
    });

    // PING

    const pingIntegration = new Apigatewayv2Integration(
      this,
      "ping-integration",
      {
        apiId: this.websocketApi.api.id,
        integrationType: "MOCK",
        templateSelectionExpression: "200",
        requestTemplates: {
          "200": PING_REQUEST_TEMPLATE,
        },
      }
    );

    new Apigatewayv2IntegrationResponse(this, "ping-integration-response", {
      apiId: this.websocketApi.api.id,
      integrationId: pingIntegration.id,
      integrationResponseKey: "/200/",
      templateSelectionExpression: "200",
      responseTemplates: {
        "200": PONG_RESPONSE_TEMPLATE,
      },
    });

    const pingRoute = new Apigatewayv2Route(this, "ping-route", {
      apiId: this.websocketApi.api.id,
      routeKey: "PING",
      routeResponseSelectionExpression: "$default",
      target: "integrations/" + pingIntegration.id,
    });

    new Apigatewayv2RouteResponse(this, "ping-route-response", {
      apiId: this.websocketApi.api.id,
      routeId: pingRoute.id,
      routeResponseKey: "$default",
    });

    return this;
  }

  allowInvocation(invocationConfig: InvocationConfig) {
    const { handler, stage } = invocationConfig;
    if (!handler) {
      return this;
    }

    handler.allowToInvoke(this.principal.role);

    /*
     * We cannot use token to define policy resource, like
     * '.on(stage.executionArn)' as it causes terraform cycle
     */
    const policyStatement = new ExecuteApi()
      .allow()
      .allActions()
      .onExecuteApiGeneral(this.websocketApi.api.id, stage, "*", "*");

    handler.role.grant(
      `allow-execute-api-${this.node.id}-${stage}`,
      policyStatement
    );

    if (this.websocketApi.domainName) {
      const nameMapping = new Apigatewayv2ApiMapping(this, "domain-mapping", {
        apiId: this.websocketApi.api.id,
        domainName: this.websocketApi.domainName.domainName,
        stage,
        apiMappingKey: stage,
      });

      // Outputs the WebSocket URL
      new TerraformOutput(this, "mapped-url", {
        value: `wss://${nameMapping.domainName}/${stage}`,
      });
    }

    return this;
  }
}
