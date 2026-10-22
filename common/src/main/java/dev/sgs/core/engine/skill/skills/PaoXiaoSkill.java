package dev.sgs.core.engine.skill.skills;

import dev.sgs.core.Phase;
import dev.sgs.core.engine.skill.Priority;
import dev.sgs.core.engine.skill.RuleId;
import dev.sgs.core.engine.skill.RuleModifier;
import dev.sgs.core.engine.skill.SkillContext;
import dev.sgs.core.player.SgsPlayer;

/**
 * Paoxiao (Zhang Fei): unlimited Sha during the play phase.
 *
 * <p>This is a rule modifier, not a listener: it rewrites the Sha usage limit queried by the
 * engine, so every later validation and skill sees the changed rule. It runs in the
 * highest-priority rule band.
 */
public final class PaoXiaoSkill implements RuleModifier {

  public static final String ID = "paoxiao";
  static final int UNLIMITED = Integer.MAX_VALUE;

  private final SgsPlayer owner;

  public PaoXiaoSkill(SgsPlayer owner) {
    this.owner = owner;
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public RuleId rule() {
    return RuleId.SHA_USAGE_LIMIT;
  }

  @Override
  public int priority() {
    return Priority.RULE_MODIFIER;
  }

  @Override
  public SgsPlayer owner() {
    return owner;
  }

  @Override
  public boolean applies(SkillContext ctx) {
    return ctx.ruleId() == RuleId.SHA_USAGE_LIMIT
        && ctx.actor() == owner
        && owner.alive()
        && ctx.state().phase() == Phase.PLAY;
  }

  @Override
  public int modifyInt(SkillContext ctx, int current) {
    return UNLIMITED;
  }
}
