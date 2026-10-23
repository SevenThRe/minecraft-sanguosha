package dev.sgs.core.match;

/** Shared lifetime for public table scenes, in milliseconds. */
public final class TablePresentationTiming {
  private TablePresentationTiming() {}
  public static long durationMillis(String kind){return "JUDGMENT".equals(kind)?3600L:2800L;}
  public static long settlementHoldMillis(String kind){return durationMillis(kind)+200L;}
}
