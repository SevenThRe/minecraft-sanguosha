package dev.sgs.core.engine.skill.equipment;

import dev.sgs.core.Phase;
import dev.sgs.core.card.CardInstance;
import dev.sgs.core.card.CardType;
import dev.sgs.core.engine.skill.Priority;
import dev.sgs.core.engine.skill.RuleId;
import dev.sgs.core.engine.skill.RuleModifier;
import dev.sgs.core.engine.skill.SkillContext;
import dev.sgs.core.player.SgsPlayer;

/**
 * Zhuge Nu equipment effect: unlimited Sha while the weapon is equipped. Equipment effects live on
 * the same bus as hero skills, in the equipment priority band (400+), so hero rule modifiers always
 * settle first. The modifier is engine-global and inspects the queried seat's weapon slot, which
 * makes equipping/unequipping automatic without registration churn.
 */
public final class ZhugeNuShaLimit implements RuleModifier {

  public static final String ID = "zhugenu_sha_limit";
  private static final int UNLIMITED = Integer.MAX_VALUE;

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
    return Priority.EQUIPMENT;
  }

  @Override
  public SgsPlayer owner() {
    return null;
  }

  @Override
  public boolean applies(SkillContext ctx) {
    if (ctx.ruleId() != RuleId.SHA_USAGE_LIMIT || ctx.state().phase() != Phase.PLAY) {
      return false;
    }
    SgsPlayer player = ctx.actor();
    CardInstance weapon = player.equipment().weapon();
    return weapon != null && weapon.type() == CardType.ZHUGE_NU;
  }

  @Override
  public int modifyInt(SkillContext ctx, int current) {
    return UNLIMITED;
  }
}
