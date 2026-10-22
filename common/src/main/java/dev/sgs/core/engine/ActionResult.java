package dev.sgs.core.engine;

/** Result of an engine operation. A rejected result never mutated any state. */
public record ActionResult(boolean accepted, RejectReason reason) {

  private static final ActionResult OK = new ActionResult(true, null);

  public static ActionResult ok() {
    return OK;
  }

  public static ActionResult rejected(RejectReason reason) {
    return new ActionResult(false, reason);
  }
}
