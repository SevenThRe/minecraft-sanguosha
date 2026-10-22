package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
/** Outgoing modifier mirrors the state's shared distance calculation, as does Mashu. */
public final class YiCongSkill implements RuleModifier {
  private final SgsPlayer owner;
  public YiCongSkill(SgsPlayer owner){this.owner=owner;}
  public String id(){return "yicong";}public SgsPlayer owner(){return owner;}
  public RuleId rule(){return RuleId.DISTANCE_FROM_CORRECTION;}public int priority(){return Priority.RULE_MODIFIER;}
  public boolean applies(SkillContext c){return owner.alive() && owner.hp()>2 && (c.source()==owner || c.actor()==owner);}
  public int modifyInt(SkillContext c,int value){return value-1;}
}
