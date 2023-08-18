import { ApiGatewayAccount } from "@cdktf/provider-aws/lib/api-gateway-account";
import { CloudwatchLogGroup } from "@cdktf/provider-aws/lib/cloudwatch-log-group";
import { Construct } from "constructs";
import { Role } from "./iam";

export const API_GATEWAY_SERVICE_PRINCIPAL = "apigateway.amazonaws.com";

export class ApiGatewayPrincipal extends Construct {
  readonly role: Role;

  constructor(scope: Construct, id: string) {
    super(scope, id);

    this.role = new Role(this, "api-gateway-execution-role", {
      forService: API_GATEWAY_SERVICE_PRINCIPAL,
    });

    this.role.attachManagedPolicyArn(
      "arn:aws:iam::aws:policy/service-role/AmazonAPIGatewayPushToCloudWatchLogs"
    );

    new ApiGatewayAccount(this, "account", {
      cloudwatchRoleArn: this.role.arn,
    });

    new CloudwatchLogGroup(this, "welcome-logs", {
      name: "/aws/apigateway/welcome",
      retentionInDays: 1,
    });
  }
}
