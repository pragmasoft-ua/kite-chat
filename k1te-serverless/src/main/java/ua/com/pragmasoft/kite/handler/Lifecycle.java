package ua.com.pragmasoft.kite.handler;

public class Lifecycle {

  public static class Tf {
    public String action;
  }

  public Tf tf;

  @Override
  public String toString() {
    return tf.action;
  }

}
