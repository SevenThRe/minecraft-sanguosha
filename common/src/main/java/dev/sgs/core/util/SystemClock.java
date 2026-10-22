package dev.sgs.core.util;

/** Production wall-clock time source. Tests use ManualClock instead. */
public final class SystemClock implements Clock {

  public static final SystemClock INSTANCE = new SystemClock();

  private SystemClock() {}

  @Override
  public long nowMillis() {
    return System.currentTimeMillis();
  }
}
