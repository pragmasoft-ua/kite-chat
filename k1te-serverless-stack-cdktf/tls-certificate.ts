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

    this.cert = new AcmCertificate(this, id, {
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
    As a workaround, we only create one of them.
    */

    const validationOption = this.cert.domainValidationOptions.get(1);

    const validationRecord = dnsZone.createRecord(`${id}-validation-record`, {
      name: validationOption.resourceRecordName,
      type: validationOption.resourceRecordType,
      value: validationOption.resourceRecordValue,
    });

    // Iterators currently fail for sets: https://github.com/hashicorp/terraform-cdk/issues/2001
    // const validationOptions: ListTerraformIterator = TerraformIterator.fromList(
    //   this.cert.domainValidationOptions
    // );
    // Workaround was taken from https://github.com/hashicorp/terraform-cdk/issues/430#issuecomment-1288006312
    // and slightly modified
    // But it still does not work due to the bug with duplicated validation records mentioned above.
    // I decided to keep workaround here commented out in the case we will need to add domain names
    // To the certificate sometimes.
    // NOSONAR
    // validationRecord.addOverride(
    //   "for_each",
    //   `\${{for dvo in ${this.cert.fqn}.domain_validation_options : dvo.domain_name => {
    //   name=dvo.resource_record_name
    //   type=dvo.resource_record_type
    //   value=dvo.resource_record_value
    //   }}}`
    // );

    this.validation = new AcmCertificateValidation(this, `${id}-validation`, {
      certificateArn: this.cert.arn,
      dependsOn: [validationRecord],
    });

    // Part of commented out workaround explained above
    // certValidation.addOverride(
    //   "validation_record_fqdns",
    //   `\${[for record in ${validationRecords.fqn} : record.hostname]}`
    // );
  }
}
