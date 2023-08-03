package ua.com.pragmasoft.kite.handler;

public class InputObject {

  static class TfData {
    public String action;

    @Override
    public String toString() {
      return action;
    }
  }

  private String name;
  private String greeting;
  private TfData tf;

  public String getName() {
    return name;
  }

  public InputObject setName(String name) {
    this.name = name;
    return this;
  }

  public String getGreeting() {
    return greeting;
  }

  public InputObject setGreeting(String greeting) {
    this.greeting = greeting;
    return this;
  }

  /**
   * @return the tf
   */
  public TfData getTf() {
    return tf;
  }

  /**
   * @param tf the tf to set
   */
  public void setTf(TfData tf) {
    this.tf = tf;
  }

  @Override
  public String toString() {
    return "InputObject [name=" + name + ", greeting=" + greeting + ", tf=" + tf + "]";
  }

}
