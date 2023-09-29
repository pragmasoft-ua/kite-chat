package ua.com.pragmasoft.k1te.serverless.handler;

public record Lifecycle(Tf tf) {

  public record Tf(String action) {
  }

  public enum Action {
    create, update, delete
  }

  @Override
  public String toString() {
    return "Lifecycle:" + tf.action;
  }

}
