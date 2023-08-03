package ua.com.pragmasoft.kite.handler;

public class OutputObject {

  private String result;

  private String requestId;

  public String getResult() {
    return result;
  }

  public String getRequestId() {
    return requestId;
  }

  public OutputObject setResult(String result) {
    this.result = result;
    return this;
  }

  public OutputObject setRequestId(String requestId) {
    this.requestId = requestId;
    return this;
  }

  @Override
  public String toString() {
    return "OutputObject [result=" + result + ", requestId=" + requestId + "]";
  }

}
