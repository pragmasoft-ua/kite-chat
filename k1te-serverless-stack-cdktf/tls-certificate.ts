import { AcmCertificate } from "@cdktf/provider-aws/lib/acm-certificate";
import { AcmCertificateValidation } from "@cdktf/provider-aws/lib/acm-certificate-validation";
import { Construct } from "constructs";
import { DnsZone } from "./dns-zone";

export class TlsCertificate extends Construct {
  readonly cert: AcmCertificate;
  readonly validation: AcmCertificateValidation;

  constructor(scope: Construct, id: string, dnsZone: DnsZone) {
    super(scope, id);

    this.node.addDependency(dnsZone);

    const { domainName } = dnsZone;
    const wildcardDomain = "*." + domainName;

    this.cert = new AcmCertificate(this, "certificate", {
      domainName,
      subjectAlternativeNames: [wildcardDomain],
      validationMethod: "DNS",
      lifecycle: {
        createBeforeDestroy: true,
      },
    });

    /*
    If cert is requested only for apex and wildcard domains, both validation records 
    appear to be identical. There exists an error related to that:
    https://github.com/hashicorp/terraform-provider-aws/issues/16913
    As a workaround, we only create one of them (second).
    */

    const validationOption = this.cert.domainValidationOptions.get(1);

    const validationRecord = dnsZone.createRecord(`${id}-validation-record`, {
      name: validationOption.resourceRecordName,
      type: validationOption.resourceRecordType,
      value: validationOption.resourceRecordValue,
    });

    this.validation = new AcmCertificateValidation(this, "validation", {
      certificateArn: this.cert.arn,
      dependsOn: [validationRecord],
    });
  }
}
