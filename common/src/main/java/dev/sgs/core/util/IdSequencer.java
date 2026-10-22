package dev.sgs.core.util;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** Monotonic unique id generator for card instances within one match. */
public final class IdSequencer implements LongSupplier {

  private final AtomicLong next;

  public IdSequencer() {
    this(1);
  }

  public IdSequencer(long start) {
    this.next = new AtomicLong(start);
  }

  @Override
  public long getAsLong() {
    return next.getAndIncrement();
  }
}
