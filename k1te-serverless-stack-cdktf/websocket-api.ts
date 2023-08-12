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
import { LambdaPermission } from "@cdktf/provider-aws/lib/lambda-permission";
import { Construct } from "constructs";
import { ExecuteApi } from "iam-floyd";
import {
  API_GATEWAY_SERVICE_PRINCIPAL,
  ApiGatewayPrincipal,
} from "./apigateway-principal";
import { Lambda } from "./lambda";

const PING_REQUEST_TEMPLATE = JSON.stringify({ statusCode: 200 });

const PONG_RESPONSE_TEMPLATE = JSON.stringify(["PONG"]);

export type WebsocketApiStageProps = {
  handler: Lambda;
  principal: ApiGatewayPrincipal;
  stage?: string;
  logRetentionDays?: number;
};

export type ApiProps = {
  domainName: string;
  certificateArn: string;
};

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
  }

  public addStage(props: Readonly<WebsocketApiStageProps>) {
    const {
      handler,
      stage: name = "prod",
      logRetentionDays: retentionInDays = 7,
      principal,
    } = props;

    const id = this.node.id;

    const accessLogGroup = new CloudwatchLogGroup(
      this,
      `${id}-${name}-access-logs`,
      {
        name: `/aws/apigateway/${id}/${name}/access-logs`,
        retentionInDays,
      }
    );

    new CloudwatchLogGroup(this, `${id}-${name}-execution-logs`, {
      name: `/aws/apigateway/${id}/${name}`,
      retentionInDays,
    });

    const stage = new Apigatewayv2Stage(this, `${id}-${name}-stage`, {
      apiId: this.api.id,
      name,
      autoDeploy: true,
      accessLogSettings: {
        destinationArn: accessLogGroup.arn,
        format: JSON.stringify({
          requestId: "$context.requestId",
          ip: "$context.identity.sourceIp",
          caller: "$context.identity.caller",
          user: "$context.identity.user",
          requestTime: "$context.requestTime",
          eventType: "$context.eventType",
          routeKey: "$context.routeKey",
          status: "$context.status",
          connectionId: "$context.connectionId",
        }),
      },
      defaultRouteSettings: {
        dataTraceEnabled: true,
        loggingLevel: "INFO",
        detailedMetricsEnabled: false,
        throttlingRateLimit: 1000,
        throttlingBurstLimit: 500,
      },
    });

    const integration = new Apigatewayv2Integration(
      this,
      `${id}-${name}-default-integration`,
      {
        apiId: this.api.id,
        integrationType: "AWS_PROXY",
        integrationUri: handler.fn.arn,
        credentialsArn: principal.role.arn,
        contentHandlingStrategy: "CONVERT_TO_TEXT",
        passthroughBehavior: "WHEN_NO_MATCH",
      }
    );

    new Apigatewayv2IntegrationResponse(
      this,
      `${id}-${name}-default-integration-response`,
      {
        apiId: this.api.id,
        integrationId: integration.id,
        integrationResponseKey: "/200/",
      }
    );

    const defaultRoute = new Apigatewayv2Route(
      this,
      `${id}-${name}-default-route`,
      {
        apiId: this.api.id,
        routeKey: "$default",
        target: "integrations/" + integration.id,
      }
    );

    new Apigatewayv2RouteResponse(
      this,
      `${id}-${name}-default-route-response`,
      {
        apiId: this.api.id,
        routeId: defaultRoute.id,
        routeResponseKey: "$default",
      }
    );

    const connectRoute = new Apigatewayv2Route(
      this,
      `${id}-${name}-connect-route`,
      {
        apiId: this.api.id,
        routeKey: "$connect",
        target: "integrations/" + integration.id,
      }
    );

    new Apigatewayv2RouteResponse(
      this,
      `${id}-${name}-connect-route-response`,
      {
        apiId: this.api.id,
        routeId: connectRoute.id,
        routeResponseKey: "$default",
      }
    );

    const disconnectRoute = new Apigatewayv2Route(
      this,
      `${id}-${name}-disconnect-route`,
      {
        apiId: this.api.id,
        routeKey: "$disconnect",
        target: "integrations/" + integration.id,
      }
    );

    new Apigatewayv2RouteResponse(
      this,
      `${id}-${name}-disconnect-route-response`,
      {
        apiId: this.api.id,
        routeId: disconnectRoute.id,
        routeResponseKey: "$default",
      }
    );

    // PING

    const pingIntegration = new Apigatewayv2Integration(
      this,
      `${id}-${name}-ping-integration`,
      {
        apiId: this.api.id,
        integrationType: "MOCK",
        templateSelectionExpression: "200",
        requestTemplates: {
          "200": PING_REQUEST_TEMPLATE,
        },
      }
    );

    new Apigatewayv2IntegrationResponse(
      this,
      `${id}-${name}-ping-integration-response`,
      {
        apiId: this.api.id,
        integrationId: pingIntegration.id,
        integrationResponseKey: "/200/",
        templateSelectionExpression: "200",
        responseTemplates: {
          "200": PONG_RESPONSE_TEMPLATE,
        },
      }
    );

    const pingRoute = new Apigatewayv2Route(this, `${id}-${name}-ping-route`, {
      apiId: this.api.id,
      routeKey: "PING",
      routeResponseSelectionExpression: "$default",
      target: "integrations/" + pingIntegration.id,
    });

    new Apigatewayv2RouteResponse(this, `${id}-${name}-ping-route-response`, {
      apiId: this.api.id,
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
      .onExecuteApiGeneral(this.node.id, name, "*", "*");

    handler.role.grant(
      `allow-execute-api-${this.node.id}-${name}`,
      policyStatement
    );

    new LambdaPermission(this, `${id}-${name}-lambda-permission`, {
      functionName: handler.fn.functionName,
      action: "lambda:InvokeFunction",
      principal: API_GATEWAY_SERVICE_PRINCIPAL,
      sourceArn: `${this.api.executionArn}/*/*`,
    });

    // Outputs the WebSocket URL
    new TerraformOutput(this, `${id}-${name}-url`, {
      value: stage.invokeUrl,
    });

    if (this.domainName) {
      const nameMapping = new Apigatewayv2ApiMapping(
        this,
        `${id}-${name}-name-mapping`,
        {
          apiId: this.api.id,
          domainName: this.domainName.domainName,
          stage: stage.name,
          apiMappingKey: stage.name,
        }
      );

      // Outputs the WebSocket URL
      new TerraformOutput(this, `${id}-${name}-mapped-url`, {
        value: `wss://${nameMapping.domainName}/${name}`,
      });
    }

    return this;
  }
}
