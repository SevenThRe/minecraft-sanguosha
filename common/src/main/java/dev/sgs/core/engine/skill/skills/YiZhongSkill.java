package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
/** Character immunity is independent of Qinggang's equipment-armor bypass. */
public final class YiZhongSkill implements RuleModifier {
  private final SgsPlayer owner;
  public YiZhongSkill(SgsPlayer owner){this.owner=owner;}
  public String id(){return "yizhong";}public SgsPlayer owner(){return owner;}
  public RuleId rule(){return RuleId.SHA_EFFECT_ALLOWED;}public int priority(){return Priority.RULE_MODIFIER;}
  public boolean applies(SkillContext c){return c.target()==owner && owner.alive() && owner.equipment().armor()==null && c.card()!=null && c.card().isBlack();}
  public int modifyInt(SkillContext c,int value){c.state().publishSkillActivation(owner.seat(),id());return 0;}
}
