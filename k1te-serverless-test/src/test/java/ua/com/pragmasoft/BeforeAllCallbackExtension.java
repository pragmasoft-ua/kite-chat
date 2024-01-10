/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft;

import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Abstract class that implements {@link BeforeAllCallback} and {@link
 * ExtensionContext.Store.CloseableResource}. It runs the setup() method before all tests and
 * teardown() after all tests. In order to use it, utilize {@linkplain ExtendWith}.
 */
public abstract class BeforeAllCallbackExtension
    implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {

  private boolean successFlag = false;

  /**
   * Callback that is invoked exactly once before the start of all tests.
   *
   * @param context The ExtensionContext for the current test.
   */
  @Override
  public void beforeAll(ExtensionContext context) {
    // We need to use a unique key here, across all usages of this particular extension.
    String uniqueKey = this.getClass().getName();
    Object value = context.getRoot().getStore(GLOBAL).get(uniqueKey);
    if (value == null) {
      context.getRoot().getStore(GLOBAL).put(uniqueKey, this);
      this.setup();
      this.successFlag = true;
    }
  }

  /**
   * Callback that is invoked exactly once before the start of all tests. Override this method to
   * perform the setup actions needed for the tests.
   */
  abstract void setup();

  /**
   * Callback that is invoked exactly once after the end of all tests. Inherited from
   * CloseableResource.
   */
  @Override
  public void close() {
    if (!successFlag) throw new IllegalStateException("Init phase has not finished successfully");
    this.teardown();
  }

  /**
   * Callback that is invoked exactly once after the end of all tests. Override this method to
   * perform the teardown actions needed after the tests.
   */
  abstract void teardown();
}
