package dev.sgs.core.util;

/** Injectable time source so prompt deadlines are testable on a plain JVM. */
public interface Clock {

  long nowMillis();
}
