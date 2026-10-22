package dev.sgs.core.engine.skill;

/**
 * One passive skill/equipment hook bound to an owner seat and a single {@link Timing}. The bus asks
 * {@link #applies(SkillContext)} first and only calls {@link #execute(SkillContext)} when it holds.
 */
public interface SkillListener {

  /** Stable skill id, matching the hero JSON / registry (e.g. {@code longdan}). */
  String id();

  Timing timing();

  /** Static priority within {@link Priority} bands; smaller fires earlier on the same owner. */
  int priority();

  /** The seat this skill belongs to; null for engine-global effects. */
  dev.sgs.core.player.SgsPlayer owner();

  boolean applies(SkillContext ctx);

  /** Pure view-as capability; execute must confirm the same recast permission. */
  default boolean allowsTransformRecast(SkillContext ctx){return false;}

  void execute(SkillContext ctx);
}
