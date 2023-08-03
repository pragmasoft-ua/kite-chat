import { Container } from "@cdktf/provider-docker/lib/container";
import { Image } from "@cdktf/provider-docker/lib/image";
import { DockerProvider } from "@cdktf/provider-docker/lib/provider";
import { Construct } from "constructs";
import { join } from "path";

export class DynamodbLocalContainer extends Construct {
  readonly container: Container;

  constructor(scope: Construct, name: string) {
    super(scope, name);

    new DockerProvider(this, "docker", {});

    const dockerImage = new Image(this, "dynamodb-local-image", {
      name: "amazon/dynamodb-local",
    });

    const hostPath = join(__dirname, "docker/dynamodb");

    this.container = new Container(this, "dynamodb-local-container", {
      name: "dynamodb-local",
      image: dockerImage.imageId,
      command: "-jar DynamoDBLocal.jar -sharedDb -dbPath ./data".split(" "),
      volumes: [
        {
          hostPath,
          containerPath: "/home/dynamodblocal/data",
        },
      ],
      workingDir: "/home/dynamodblocal",
      ports: [
        {
          internal: 8000,
          external: 8000,
        },
      ],
    });
  }
}

// aws dynamodb list-tables --endpoint-url http://localhost:8000
