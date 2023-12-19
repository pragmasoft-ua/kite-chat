import { Construct } from "constructs";
import { CloudflareDnsZone } from "./dns-zone";
import { TlsCertificate } from "./tls-certificate";
import { WebsocketApi } from "./websocket-api";
import { RestApi } from "./rest-api";

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

  createRecord(api: WebsocketApi | RestApi) {
    if (api.domainName && this.dnsZone) {
      this.dnsZone.createRecord(api.domainName.domainName, {
        type: "CNAME",
        name: api.domainName.domainName,
        value: api.domainName.domainNameConfiguration.targetDomainName,
      });
    }
  }
}
