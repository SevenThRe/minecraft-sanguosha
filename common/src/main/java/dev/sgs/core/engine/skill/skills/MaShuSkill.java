package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
/** Outgoing distance only, applied before the shared distance floor of one. */
public final class MaShuSkill implements RuleModifier {
  private final SgsPlayer owner;public MaShuSkill(SgsPlayer owner){this.owner=owner;}
  public String id(){return "mashu";}public SgsPlayer owner(){return owner;}
  public RuleId rule(){return RuleId.DISTANCE_FROM_CORRECTION;}public int priority(){return Priority.RULE_MODIFIER;}
  public boolean applies(SkillContext c){return owner.alive() && (c.source()==owner || c.actor()==owner);}
  public int modifyInt(SkillContext c,int value){return value-1;}
}
