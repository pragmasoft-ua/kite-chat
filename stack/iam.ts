import { IamRole } from "@cdktf/provider-aws/lib/iam-role";
import * as iam from "iam-floyd";

import { IamRolePolicyAttachment } from "@cdktf/provider-aws/lib/iam-role-policy-attachment";
import { Construct } from "constructs";
import { Sts } from "iam-floyd/lib/generated";
import { Grantable } from "./grantable";

export type RoleProps = {
  forService: string;
};

export class Role extends Construct implements Grantable {
  readonly role: IamRole;

  constructor(scope: Construct, id: string, props: Readonly<RoleProps>) {
    super(scope, id);

    const { forService } = props;

    const assumeRoleStatement = new Sts()
      .allow()
      .toAssumeRole()
      .forService(forService)
      .toJSON();

    const assumeRolePolicy = JSON.stringify({
      Version: "2012-10-17",
      Statement: [assumeRoleStatement],
    });

    this.role = new IamRole(this, this.node.id, {
      assumeRolePolicy,
    });
  }

  attachManagedPolicyArn(policyArn: string): this {
    const policyName = policyArn.split("/").pop();

    new IamRolePolicyAttachment(
      this,
      `${this.node.id}-managed-${policyName}-attachment`,
      {
        policyArn,
        role: this.role.name,
      }
    );
    return this;
  }

  grant(policyStatement: iam.PolicyStatement): this {
    const statement = policyStatement.toJSON();
    const policy = JSON.stringify({
      Version: "2012-10-17",
      Statement: [statement],
    });
    this.role.putInlinePolicy([{ policy }]);
    return this;
  }

  get arn() {
    return this.role.arn;
  }
}
