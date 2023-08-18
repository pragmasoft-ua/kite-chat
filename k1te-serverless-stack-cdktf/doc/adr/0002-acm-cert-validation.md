# 1. ACM certificate validation problems and solutions

Date: 2023-08-17

## Status

Accepted

## Context

ACM managed certificates support automatic renewal if the DNS validation method is used.

DNS validation method requires to add certain CNAME records to DNS zone, one record per validated domain name in the TLS certificate.

Most documented and tested is the combination of ACM certificate validation paired with Route53 zone.

Though, we have our domain **k1te.chat** hosted on CloudFlare, and for a number of reasons I don't want to transfer this domain to AWS.

Terraform supports CloudFlare provider as well, but this is more buggy and less documented path.

This decision record holds references to existing bugs, workarounds and solutions, in case when some of them are fixed or configuration changed, this could require to re-assess the solution.

## Facing

We issue certificate for two domain records: apex **k1te.chat** and wildcard **\*.k1te.chat** Thus, we will need to add two validation records to our DNS zone.

It appears, that if the cert is requested only for apex and wildcard domains, both validation records appear to be identical. There exists an error related to that:
https://github.com/hashicorp/terraform-provider-aws/issues/16913

Also terraform cdk seems has problems with terraform's native approach of managing collections.
In the case we'll sometimes have to add more domain names to the certificate, I'll left workaround below.

```ts
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
validationRecord.addOverride(
  "for_each",
  `\${{for dvo in ${this.cert.fqn}.domain_validation_options : dvo.domain_name => {
      name=dvo.resource_record_name
      type=dvo.resource_record_type
      value=dvo.resource_record_value
      }}}`
);

// Part of commented out workaround explained above
certValidation.addOverride(
  "validation_record_fqdns",
  `\${[for record in ${validationRecords.fqn} : record.hostname]}`
);
```

[Phind AI](https://www.phind.com/agent?cache=cllci2th9001ql208t4m9it5n) recommends the following code. I didn't test it, but added it here for the completeness sake.

Notice how resource collections are handled here without overrides and iterators:

```ts
const cert = new aws.AcmCertificate(this, "Certificate", {
  domainName: "example.com",
  validationMethod: "DNS",
});

cert.certificateDomainValidationOptions.forEach((options, index) => {
  new cloudflare.Record(this, `DnsValidationRecord${index}`, {
    zoneId: "your-zone-id",
    name: options.resourceRecordName,
    value: options.resourceRecordValue,
    type: "CNAME",
  });
});

new aws.AcmCertificateValidation(this, "CertificateValidation", {
  certificateArn: cert.certificateArn,
  validationRecordFqdns: cert.certificateDomainValidationOptions.map(
    (options) => options.resourceRecordFqdns
  ),
});
```

Additional problem was caused by the CloudFlare provider, which rejected creation of the CNAME record with the obscure error message.

It required me to use `curl` to recreate the record creation API request and get more detailed rejection reason. It turned out that free CloudFlare account does not allow resources to have
tags for some reason, whereas we have all our resources automatically tagged by the cdktf aspect
(see tags.ts). Additionally, unlike AWS provider, Cloudflare tags are simple strings rather than key-value maps, so the code had to be modified to prevent aspect from adding tags to cloudflare resources.

## Decision

As a workaround, we only created one validation record for now so this solves all of the problems above, except tags.

To solve problem with tags we changed aspect to add tags only if resource doesn't already have ones, and added empty array `[]` tags value to all cloudflare resources.

## Consequences

Current workaround is the simplest working one but is only possible because we have just one validation record.

There are few documented issues related to ACM validation flow. Hopefully some of them will be fixed sooner or later, as well as CDKTF support of collections will be fixed.
