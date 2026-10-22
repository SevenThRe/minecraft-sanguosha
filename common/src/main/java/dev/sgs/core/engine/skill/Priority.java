package dev.sgs.core.engine.skill;

/**
 * Static registration-priority bands for one timing on one owner. Within the same timing and the
 * same owning seat, a smaller value fires first.
 *
 * <p>Core principle: skills that change the rules must run before skills that rely on those rules.
 * Equipment-provided effects live in the highest band so general skills always settle first.
 */
public final class Priority {

  /** 0-99: changes the base rules of the game (Paoxiao, Zhuge Nu's unlimited Sha is equipment). */
  public static final int RULE_MODIFIER = 50;

  /**
   * 100-199: inspects or alters state before choices are made (Kongcheng, Qianxun-style effects).
   */
  public static final int STATE_DETECTION = 150;

  /** 200-299: requires a player decision (Rende, Zhiheng-style effects). */
  public static final int ACTIVE_CHOICE = 200;

  /** 300-399: passive response once a condition holds (Longdan, Wusheng-style effects). */
  public static final int PASSIVE_RESPONSE = 300;

  /** 400+: effects granted by equipped cards (Bagua, Renwang, Zhuge Nu). */
  public static final int EQUIPMENT = 400;

  private Priority() {}
}
