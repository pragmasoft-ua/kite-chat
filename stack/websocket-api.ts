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

export type WebsocketApiProps = {
  handler: Lambda;
  stage?: string;
};

export const API_GATEWAY_SERVICE_PRINCIPAL = "apigateway.amazonaws.com";
export class WebsocketApi extends Construct {
  readonly gw: Apigatewayv2Api;

  constructor(
    scope: Construct,
    id: string,
    props: Readonly<WebsocketApiProps>
  ) {
    super(scope, id);

    const { handler, stage = "prod" } = props;

    const wsApiGatewayRole = new Role(this, `${id}-execution-role`, {
      forService: API_GATEWAY_SERVICE_PRINCIPAL,
    });

    handler.allowToInvoke(wsApiGatewayRole);

    wsApiGatewayRole.attachManagedPolicyArn(
      "arn:aws:iam::aws:policy/service-role/AmazonAPIGatewayPushToCloudWatchLogs"
    );

    const account = new ApiGatewayAccount(this, `${id}-account`, {
      cloudwatchRoleArn: wsApiGatewayRole.arn,
    });

    this.gw = new Apigatewayv2Api(this, id, {
      name: id,
      protocolType: "WEBSOCKET",
      routeSelectionExpression: "\\$default",
      dependsOn: [account],
    });

    const accessLogGroup = new CloudwatchLogGroup(
      this,
      `${id}-${stage}-access-logs`,
      {
        name: `/aws/apigateway/${this.gw.id}/${stage}/access-logs`,
        retentionInDays: 7,
      }
    );

    new CloudwatchLogGroup(this, `${id}-execution-logs`, {
      name: `/aws/apigateway/${this.gw.id}/${stage}`,
      retentionInDays: 7,
    });

    new CloudwatchLogGroup(this, `${id}-welcome-logs`, {
      name: "/aws/apigateway/welcome",
      retentionInDays: 1,
    });

    const defaultStage = new Apigatewayv2Stage(this, `${id}-${stage}-stage`, {
      apiId: this.gw.id,
      name: stage,
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
        apiId: this.gw.id,
        integrationType: "AWS_PROXY",
        integrationUri: handler.fn.arn,
        credentialsArn: wsApiGatewayRole.arn,
        contentHandlingStrategy: "CONVERT_TO_TEXT",
        passthroughBehavior: "WHEN_NO_MATCH",
      }
    );

    new Apigatewayv2IntegrationResponse(this, "default-integration-response", {
      apiId: this.gw.id,
      integrationId: integration.id,
      integrationResponseKey: "/200/",
    });

    const defaultRoute = new Apigatewayv2Route(this, "default-route", {
      apiId: this.gw.id,
      routeKey: "$default",
      target: "integrations/" + integration.id,
    });

    new Apigatewayv2RouteResponse(this, "default-route-response", {
      apiId: this.gw.id,
      routeId: defaultRoute.id,
      routeResponseKey: "$default",
    });

    const connectRoute = new Apigatewayv2Route(this, "connect-route", {
      apiId: this.gw.id,
      routeKey: "$connect",
      target: "integrations/" + integration.id,
    });

    new Apigatewayv2RouteResponse(this, "connect-route-response", {
      apiId: this.gw.id,
      routeId: connectRoute.id,
      routeResponseKey: "$default",
    });

    const disconnectRoute = new Apigatewayv2Route(this, "disconnect-route", {
      apiId: this.gw.id,
      routeKey: "$disconnect",
      target: "integrations/" + integration.id,
    });

    new Apigatewayv2RouteResponse(this, "disconnect-route-response", {
      apiId: this.gw.id,
      routeId: disconnectRoute.id,
      routeResponseKey: "$default",
    });

    new LambdaPermission(this, `${id}-lambda-permission`, {
      functionName: handler.fn.functionName,
      action: "lambda:InvokeFunction",
      principal: API_GATEWAY_SERVICE_PRINCIPAL,
      sourceArn: `${this.gw.executionArn}/*/*`,
    });

    // Outputs the WebSocket URL
    new TerraformOutput(this, "url", {
      value: defaultStage.invokeUrl,
    });
  }
}
