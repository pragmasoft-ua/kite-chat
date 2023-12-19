import { Construct } from "constructs";
import { CloudflareDnsZone } from "./dns-zone";
import { TlsCertificate } from "./tls-certificate";
import { WebsocketApi, WebSocketApiProps } from "./websocket-api";
import { RestApi, RestApiProps } from "./rest-api";

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

  createRecord(
    api: WebsocketApi | RestApi,
    apiProps: WebSocketApiProps | RestApiProps,
  ) {
    if (api.domainName && apiProps?.domainName && this.dnsZone) {
      this.dnsZone.createRecord(apiProps.domainName, {
        type: "CNAME",
        name: api.domainName.domainName,
        value: api.domainName.domainNameConfiguration.targetDomainName,
      });
    }
  }
}
