package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
/** Trick distance only; Borrow Sword's forced attack retains the holder's range. */
public final class QiCaiSkill implements RuleModifier {
  public static final String ID="qicai";private final SgsPlayer owner;
  public QiCaiSkill(SgsPlayer owner){this.owner=owner;}
  public String id(){return ID;}
  public SgsPlayer owner(){return owner;}
  public RuleId rule(){return RuleId.TRICK_DISTANCE_LIMIT;}
  public int priority(){return Priority.RULE_MODIFIER;}
  public boolean applies(SkillContext ctx){return ctx.actor()==owner && owner.alive();}
  public int modifyInt(SkillContext ctx,int current){return Integer.MAX_VALUE;}
}
