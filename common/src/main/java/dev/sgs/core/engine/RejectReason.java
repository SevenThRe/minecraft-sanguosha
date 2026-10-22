package dev.sgs.core.engine;

/** Why an attempted action was rejected. Names double as i18n key suffixes. */
public enum RejectReason {
  GAME_NOT_RUNNING,
  NOT_YOUR_TURN,
  WRONG_PHASE,
  CARD_NOT_IN_HAND,
  TARGET_REQUIRED,
  TARGET_SELF,
  TARGET_DEAD,
  OUT_OF_RANGE,
  SHA_LIMIT,
  FULL_HP,
  JIU_LIMIT,
  SHAN_NOT_PLAYABLE,
  DISCARD_COUNT,
  UNKNOWN_CARD,
  NOT_YET_IMPLEMENTED,
  RESPONSE_ONLY,
  TARGET_NO_CARDS,
  TARGET_NO_WEAPON,
  /** The seat does not own an active skill with this id in the current phase. */
  SKILL_UNAVAILABLE,
  /** The skill's own conditions rejected the launch; details are in the game log. */
  SKILL_REJECTED,
  /** No listener confirmed the declared card alternation. */
  TRANSFORM_NOT_AVAILABLE,
  DUPLICATE_TARGET,
  TARGET_NOT_IN_GAME,
  RECAST_NOT_AVAILABLE,
  DUPLICATE_DELAYED_TRICK,
  TARGET_PROHIBITED,
  TARGET_COUNT
}
