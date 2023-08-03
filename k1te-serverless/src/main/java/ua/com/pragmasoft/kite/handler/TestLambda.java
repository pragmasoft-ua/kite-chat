package ua.com.pragmasoft.kite.handler;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import io.quarkus.logging.Log;

@Named("test")
public class TestLambda implements RequestHandler<InputObject, OutputObject> {

  @Inject
  ProcessingService service;

  @Override
  public OutputObject handleRequest(InputObject input, Context context) {
    var result = service.process(input).setRequestId(context.getAwsRequestId());
    Log.infof("test %s -> %s", input.toString(), result.toString());
    return result;
  }
}
