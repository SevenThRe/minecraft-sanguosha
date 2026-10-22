package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.*;
import dev.sgs.core.card.CardType;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
/** Standard lord-only rescue: another Wu character's Peach restores one extra; self Peach does not. */
public record JiuYuanSkill(SgsPlayer owner) implements RuleModifier {
  public String id(){return "jiuyuan";}public RuleId rule(){return RuleId.RESCUE_RECOVERY;}public int priority(){return Priority.RULE_MODIFIER;}
  public boolean applies(SkillContext c){return owner.alive() && owner.identity()==Identity.LORD && c.target()==owner && owner.hp()<1
    && c.source()!=null && c.source()!=owner && c.source().hero()!=null && c.state().kingdom(c.source())==Kingdom.WU
    && c.card()!=null && c.card().type()==CardType.TAO;}
  public int modifyInt(SkillContext c,int value){c.state().publishSkillActivation(owner.seat(),id());return value+1;}
}
