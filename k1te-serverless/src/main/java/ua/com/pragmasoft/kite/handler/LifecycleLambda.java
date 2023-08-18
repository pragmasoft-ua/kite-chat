package ua.com.pragmasoft.kite.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import io.quarkus.logging.Log;
import jakarta.inject.Named;

@Named("lifecycle")
public class LifecycleLambda implements RequestHandler<Lifecycle, OutputObject> {

  @Override
  public OutputObject handleRequest(Lifecycle input, Context context) {
    OutputObject result = new OutputObject().setRequestId(context.getAwsRequestId());
    var action = switch (input.tf.action) {
      case "create" -> "CREATE";
      default -> "UNKNOWN:" + input.tf.action;
    };
    Log.infof("lifecycle %s -> %s", action, result.toString());
    return result;
  }
}
