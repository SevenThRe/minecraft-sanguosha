package dev.sgs.core.engine.skill;

/**
 * Structured record of one listener execution. The trigger log is the replay/debug backbone: every
 * fired listener leaves one row with timing, owner and priority.
 */
public record SkillTriggerRecord(
    long timestampMillis,
    int turnNumber,
    Timing timing,
    int ownerSeat,
    String skillId,
    int priority,
    OrderStrategy strategy) {

  /** Seat value for engine-global listeners (no owning seat). */
  public static final int GLOBAL_SEAT = -1;

  public String toLogLine() {
    return "[skill] turn="
        + turnNumber
        + " timing="
        + timing
        + " seat="
        + ownerSeat
        + " skill="
        + skillId
        + " priority="
        + priority
        + " order="
        + strategy;
  }
}
