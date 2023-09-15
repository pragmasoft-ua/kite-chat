package ua.com.pragmasoft.k1te.serverless.handler;

class Lifecycle {

  static class Tf {
    public String action;
  }

  @SuppressWarnings("java:S115")
  enum Action {
    create, update, delete
  }

  Tf tf;

  @Override
  public String toString() {
    return tf.action;
  }

}
