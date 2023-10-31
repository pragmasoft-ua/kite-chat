/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.serverless.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Named("stream")
public class StreamLambda implements RequestStreamHandler {

  @Override
  public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context)
      throws IOException {
    int letter;
    while ((letter = inputStream.read()) != -1) {
      int character = Character.toUpperCase(letter);
      outputStream.write(character);
    }
  }
}
