import { DataCloudflareZone } from "@cdktf/provider-cloudflare/lib/data-cloudflare-zone";
import { CloudflareProvider } from "@cdktf/provider-cloudflare/lib/provider";
import { Record } from "@cdktf/provider-cloudflare/lib/record";
import { ITerraformDependable } from "cdktf";
import { Construct, IDependable } from "constructs";
import assert = require("node:assert");
import { ALLOW_TAGS } from "./tags";

export interface DnsRecord extends ITerraformDependable {}

export type DnsRecordProps = {
  name: string;
  type: string;
  value: string;
};

export interface DnsZone extends IDependable {
  zoneId: string;
  domainName: string;
  createRecord(id: string, props: DnsRecordProps): DnsRecord;
}

class CloudflareDnsRecord extends Construct implements DnsRecord {
  readonly cloudflareRecord: Record;

  constructor(scope: Construct, id: string, props: DnsRecordProps) {
    super(scope, id);

    //Cloudflare free tier does not allow tags
    this.node.setContext(ALLOW_TAGS, false);

    this.cloudflareRecord = new Record(this, "record", {
      ...props,
      zoneId: (scope as CloudflareDnsZone).zoneId,
      proxied: false, // CNAME records cannot be proxied
      ttl: 1, // 1 means Auto
      /* 
        Cloudflare free tier does not allow tags, so we need empty array to prevent the 
        tagging aspect from adding invalid tag. 
         
        In the case tags will be added sometimes, keep in mind, that unlike AWS, 
        Cloudflare tags are simple strings, not objects. 
          
        Convention is to have keys and values separated by colon, like ["app:k1te-chat"]
        */
      tags: [],
    });
  }

  get fqn(): string {
    return this.cloudflareRecord.fqn;
  }
}

export class CloudflareDnsZone extends Construct implements DnsZone {
  private cloudflareZone: DataCloudflareZone;

  constructor(scope: Construct, domainName: string) {
    super(scope, domainName);

    const apiToken = process.env.CLOUDFLARE_API_TOKEN;

    assert(
      apiToken,
      "CLOUDFLARE_API_TOKEN env variable is not configured in the .env file"
    );

    new CloudflareProvider(this, "cloudflare-provider", {
      apiToken,
    });

    this.cloudflareZone = new DataCloudflareZone(this, "zone", {
      name: domainName,
    });
  }

  get zoneId() {
    return this.cloudflareZone.zoneId;
  }

  get domainName() {
    return this.cloudflareZone.name;
  }

  createRecord(id: string, props: DnsRecordProps): DnsRecord {
    return new CloudflareDnsRecord(this, id, props);
  }
}
