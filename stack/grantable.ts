import { PolicyStatement } from "iam-floyd/lib/shared/policy-statement";

export interface Grantable {
  grant(policy: PolicyStatement): this;
}
