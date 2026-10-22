package dev.sgs.core.engine.skill;

/**
 * A rule-changing hook. Unlike {@link SkillListener}s, modifiers do not fire on a timing: the
 * engine queries them whenever a base rule is evaluated, so rule changes are visible to every later
 * listener and validation. Paoxiao is the prototype: it rewrites the per-turn Sha limit instead of
 * reacting to a Sha being played.
 */
public interface RuleModifier {

  String id();

  RuleId rule();

  int priority();

  dev.sgs.core.player.SgsPlayer owner();

  boolean applies(SkillContext ctx);

  /** Folds this modifier over the current value; modifiers are applied in priority order. */
  int modifyInt(SkillContext ctx, int current);
}
