package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
/** The engine queries this once per Sha or per opposing Duel response, consuming each real card. */
public final class WuShuangSkill implements RuleModifier {
  private final SgsPlayer owner;private final RuleId rule;
  public WuShuangSkill(SgsPlayer owner,RuleId rule){
    if(rule!=RuleId.SHA_DODGE_COUNT && rule!=RuleId.DUEL_SHA_COUNT)throw new IllegalArgumentException("invalid wushuang rule");
    this.owner=owner;this.rule=rule;
  }
  public String id(){return "wushuang";}public SgsPlayer owner(){return owner;}
  public RuleId rule(){return rule;}public int priority(){return Priority.RULE_MODIFIER;}
  public boolean applies(SkillContext c){return c.source()==owner && owner.alive();}
  public int modifyInt(SkillContext c,int count){return Math.max(count,2);}
}
