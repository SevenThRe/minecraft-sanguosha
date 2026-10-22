package dev.sgs.core.engine.skill;

/**
 * Base-game rules that can be overridden by {@link RuleModifier}s instead of being hard-coded in
 * the engine. Append-only, like {@link Timing}.
 */
public enum RuleId {
  /**
   * How many Sha a seat may play during one play phase; an effectively unlimited value lifts it.
   */
  SHA_USAGE_LIMIT,
  DRAW_COUNT,
  TRICK_DISTANCE_LIMIT,
  RESCUE_RECOVERY,
  CARD_TARGET_ALLOWED,
  SHA_DODGE_COUNT,
  DUEL_SHA_COUNT,
  DISTANCE_FROM_CORRECTION,
  SHA_EFFECT_ALLOWED
}
