import { Apigatewayv2Api } from "@cdktf/provider-aws/lib/apigatewayv2-api";
import { TerraformOutput } from "cdktf";

import { ApiGatewayAccount } from "@cdktf/provider-aws/lib/api-gateway-account";
import { Apigatewayv2Integration } from "@cdktf/provider-aws/lib/apigatewayv2-integration";
import { Apigatewayv2IntegrationResponse } from "@cdktf/provider-aws/lib/apigatewayv2-integration-response";
import { Apigatewayv2Route } from "@cdktf/provider-aws/lib/apigatewayv2-route";
import { Apigatewayv2RouteResponse } from "@cdktf/provider-aws/lib/apigatewayv2-route-response";
import { Apigatewayv2Stage } from "@cdktf/provider-aws/lib/apigatewayv2-stage";
import { CloudwatchLogGroup } from "@cdktf/provider-aws/lib/cloudwatch-log-group";
import { LambdaPermission } from "@cdktf/provider-aws/lib/lambda-permission";
import { Construct } from "constructs";
import { Role } from "./iam";
import { Lambda } from "./lambda";
import { ExecuteApi } from "iam-floyd";

const PING_REQUEST_TEMPLATE = JSON.stringify({ statusCode: 200 });

const PONG_RESPONSE_TEMPLATE = JSON.stringify([
  "PONG",
  "$context.connectionId",
]);

export type WebsocketApiProps = {
  handler: Lambda;
  stage?: string;
  logRetentionDays?: number;
};

export const API_GATEWAY_SERVICE_PRINCIPAL = "apigateway.amazonaws.com";
export class WebsocketApi extends Construct {
  readonly api: Apigatewayv2Api;
  readonly role: Role;

  constructor(scope: Construct, id: string) {
    super(scope, id);

    this.role = new Role(this, `${id}-execution-role`, {
      forService: API_GATEWAY_SERVICE_PRINCIPAL,
    });

    this.role.attachManagedPolicyArn(
      "arn:aws:iam::aws:policy/service-role/AmazonAPIGatewayPushToCloudWatchLogs"
    );

    const account = new ApiGatewayAccount(this, `${id}-account`, {
      cloudwatchRoleArn: this.role.arn,
    });

    this.api = new Apigatewayv2Api(this, id, {
      name: id,
      protocolType: "WEBSOCKET",
      routeSelectionExpression: "$request.body.[0]", // "\\$default",
      dependsOn: [account],
    });

    new CloudwatchLogGroup(this, `${id}-welcome-logs`, {
      name: "/aws/apigateway/welcome",
      retentionInDays: 1,
    });
  }

  public addStage(props: Readonly<WebsocketApiProps>) {
    const {
      handler,
      stage: name = "prod",
      logRetentionDays: retentionInDays = 7,
    } = props;

    const accessLogGroup = new CloudwatchLogGroup(
      this,
      `${this.node.id}-${name}-access-logs`,
      {
        name: `/aws/apigateway/${this.api.id}/${name}/access-logs`,
        retentionInDays,
      }
    );

    new CloudwatchLogGroup(this, `${this.node.id}-execution-logs`, {
      name: `/aws/apigateway/${this.api.id}/${name}`,
      retentionInDays,
    });

    const stage = new Apigatewayv2Stage(this, `${this.node.id}-${name}-stage`, {
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
      "default-integration",
      {
        apiId: this.api.id,
        integrationType: "AWS_PROXY",
        integrationUri: handler.fn.arn,
        credentialsArn: this.role.arn,
        contentHandlingStrategy: "CONVERT_TO_TEXT",
        passthroughBehavior: "WHEN_NO_MATCH",
      }
    );

    new Apigatewayv2IntegrationResponse(this, "default-integration-response", {
      apiId: this.api.id,
      integrationId: integration.id,
      integrationResponseKey: "/200/",
    });

    const defaultRoute = new Apigatewayv2Route(this, "default-route", {
      apiId: this.api.id,
      routeKey: "$default",
      target: "integrations/" + integration.id,
    });

    new Apigatewayv2RouteResponse(this, "default-route-response", {
      apiId: this.api.id,
      routeId: defaultRoute.id,
      routeResponseKey: "$default",
    });

    const connectRoute = new Apigatewayv2Route(this, "connect-route", {
      apiId: this.api.id,
      routeKey: "$connect",
      target: "integrations/" + integration.id,
    });

    new Apigatewayv2RouteResponse(this, "connect-route-response", {
      apiId: this.api.id,
      routeId: connectRoute.id,
      routeResponseKey: "$default",
    });

    const disconnectRoute = new Apigatewayv2Route(this, "disconnect-route", {
      apiId: this.api.id,
      routeKey: "$disconnect",
      target: "integrations/" + integration.id,
    });

    new Apigatewayv2RouteResponse(this, "disconnect-route-response", {
      apiId: this.api.id,
      routeId: disconnectRoute.id,
      routeResponseKey: "$default",
    });

    // PING

    const pingIntegration = new Apigatewayv2Integration(
      this,
      "ping-integration",
      {
        apiId: this.api.id,
        integrationType: "MOCK",
        templateSelectionExpression: "200",
        requestTemplates: {
          "200": PING_REQUEST_TEMPLATE,
        },
      }
    );

    new Apigatewayv2IntegrationResponse(this, "ping-integration-response", {
      apiId: this.api.id,
      integrationId: pingIntegration.id,
      integrationResponseKey: "/200/",
      templateSelectionExpression: "200",
      responseTemplates: {
        "200": PONG_RESPONSE_TEMPLATE,
      },
    });

    const pingRoute = new Apigatewayv2Route(this, "ping-route", {
      apiId: this.api.id,
      routeKey: "PING",
      routeResponseSelectionExpression: "$default",
      target: "integrations/" + pingIntegration.id,
    });

    new Apigatewayv2RouteResponse(this, "ping-route-response", {
      apiId: this.api.id,
      routeId: pingRoute.id,
      routeResponseKey: "$default",
    });

    handler.allowToInvoke(this.role);

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

    new LambdaPermission(this, `${stage}-lambda-permission`, {
      functionName: handler.fn.functionName,
      action: "lambda:InvokeFunction",
      principal: API_GATEWAY_SERVICE_PRINCIPAL,
      sourceArn: `${this.api.executionArn}/*/*`,
    });

    // Outputs the WebSocket URL
    new TerraformOutput(this, "url", {
      value: stage.invokeUrl,
    });

    return this;
  }
}
