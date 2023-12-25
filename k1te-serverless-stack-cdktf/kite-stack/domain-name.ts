import { Construct } from "constructs";
import { CloudflareDnsZone } from "./dns-zone";
import { TlsCertificate } from "./tls-certificate";

export class DomainName extends Construct {
  private readonly dnsZone?: CloudflareDnsZone;
  readonly certificate?: TlsCertificate;
  constructor(scope: Construct, id: string, domainName?: string) {
    super(scope, id);

    this.dnsZone = domainName
      ? new CloudflareDnsZone(scope, domainName)
      : undefined;

    this.certificate = this.dnsZone
      ? new TlsCertificate(scope, `${domainName}-cert`, this.dnsZone)
      : undefined;
  }

  createCname(domainName?: string, targetDomainName?: string) {
    if (domainName && targetDomainName && this.dnsZone) {
      this.dnsZone.createRecord(domainName, {
        type: "CNAME",
        name: domainName,
        value: targetDomainName,
      });
    }
  }
}
