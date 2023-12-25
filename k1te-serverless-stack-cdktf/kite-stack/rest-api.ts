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
import { TlsCertificate } from "./tls-certificate";
import { Lambda } from "./lambda";

export class RestApiStage extends Construct {
  readonly stage: Apigatewayv2Stage;
  readonly api: RestApi;

  constructor(scope: RestApi, id: string, props: Readonly<RestApiStageConfig>) {
    super(scope, id);

    this.api = scope;

    const {
      stage,
      functionStageVariable,
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
      stageVariables: {
        function: functionStageVariable,
      },
      defaultRouteSettings: {
        dataTraceEnabled: false,
        loggingLevel,
        detailedMetricsEnabled: false,
        throttlingRateLimit: 10,
        throttlingBurstLimit: 5,
      },
      lifecycle: {
        ignoreChanges: ["default_route_settings[0].logging_level"],
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

  allowInvocation(handler: Lambda) {
    handler.allowInvocationForService(this.api);
  }

  get invokeUrl() {
    if (this.api.domainName) {
      return `https://${this.api.domainName.domainName}/${this.stage.name}`;
    }
    return this.stage.invokeUrl;
  }
}

export class RestApi extends Construct implements ServicePrincipal {
  readonly api: Apigatewayv2Api;
  readonly domainName?: Apigatewayv2DomainName;

  constructor(scope: Construct, id: string, props: Readonly<RestApiProps>) {
    super(scope, id);
    const {
      handlerArn,
      method = "GET",
      route,
      domainName,
      certificate,
    } = props;
    const routeKey = `${method} ${route}`;

    const api = new Apigatewayv2Api(this, "api", {
      name: this.node.id,
      protocolType: "HTTP",
    });
    this.api = api;

    const integration = new Apigatewayv2Integration(
      this,
      "default-integration",
      {
        apiId: api.id,
        integrationType: "AWS_PROXY",
        integrationUri: handlerArn,
        payloadFormatVersion: "2.0",
      },
    );

    new Apigatewayv2Route(this, "route", {
      apiId: api.id,
      routeKey,
      target: "integrations/" + integration.id,
    });

    if (domainName && certificate) {
      this.domainName = new Apigatewayv2DomainName(this, "domain-name", {
        domainName,
        domainNameConfiguration: {
          certificateArn: certificate.cert.arn,
          endpointType: "REGIONAL",
          securityPolicy: "TLS_1_2",
        },
        dependsOn: [certificate.validation],
      });
    }
  }

  addStage(props: Readonly<RestApiStageConfig>) {
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

  public getDomainName() {
    return this.domainName?.domainName;
  }

  public getTargetDomainName() {
    return this.domainName?.domainNameConfiguration.targetDomainName;
  }
}

export type RestApiProps = {
  handlerArn: string;
  method?: string;
  route: string;
  domainName?: string;
  certificate?: TlsCertificate;
};

export type RestApiStageConfig = {
  stage: string;
  functionStageVariable: string;
  logRetentionDays?: number;
  loggingLevel?: "ERROR" | "INFO" | "OFF";
};
