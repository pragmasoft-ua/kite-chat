import { Construct } from "constructs";
import { SsmParameter as SmmParam } from "@cdktf/provider-aws/lib/ssm-parameter";
import { Role } from "./iam";
import { Ssm } from "iam-floyd/lib/generated";

export type SsmParameterProp = {
  type: "String" | "StringList" | "SecureString";
  value: string;
  description?: string;
};

export class SsmParameter extends Construct {
  private param: SmmParam;

  constructor(scope: Construct, id: string, props: Readonly<SsmParameterProp>) {
    super(scope, id);
    const { type, value, description } = props;

    this.param = new SmmParam(this, `ssm-parameter-${id}`, {
      name: id,
      type,
      value,
      description,
    });
  }

  public allowGetRequest(role: Role): void {
    const allowToGetParameter = new Ssm()
      .allow()
      .toGetParameter()
      .toGetParameters()
      .onParameter(this.param.name);
    role.grant(`allow-to-get-${this.param.name}`, allowToGetParameter);
  }
}
