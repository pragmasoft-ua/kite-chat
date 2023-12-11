import { Lambda } from "./lambda";
import { ApiGatewayPrincipal } from "./apigateway-principal";

export interface Api {
  addStage(stageConfig: ApiStageConfig): ApiStage;
  attachDefaultIntegration(
    integrationConfig: IntegrationConfig
  ): ApiIntegration;
}

export interface ApiStage {
  get invokeUrl(): string;
}

export interface ApiIntegration {
  addRouteDefaultRoutes(props?: any): ApiIntegration;
  allowInvocation(invocationConfig: InvocationConfig): ApiIntegration;
}

export type ApiStageConfig = {
  stage: string;
  functionStageVariable: string;
  logRetentionDays?: number;
  loggingLevel?: "ERROR" | "INFO" | "OFF";
};

export type IntegrationConfig = {
  region: string;
  accountId: string;
  integrationName: string;
  principal?: ApiGatewayPrincipal;
};

export type InvocationConfig = {
  handler: Lambda | undefined;
  stage: string;
};
