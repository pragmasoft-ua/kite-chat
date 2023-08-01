import { PolicyStatement } from "iam-floyd/lib/shared/policy-statement";

export interface Grantable {
  grant(name: string, policy: PolicyStatement): this;
}
