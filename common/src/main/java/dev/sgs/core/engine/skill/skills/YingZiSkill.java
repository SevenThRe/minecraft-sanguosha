package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
/** Standard optional draw modifier; no max-HP hand-limit effect. */
public final class YingZiSkill implements AwaitableRuleInvoke {
  private final SgsPlayer owner;private final SkillEngine api;
  public YingZiSkill(SgsPlayer owner,SkillEngine api){this.owner=owner;this.api=api;}
  public String id(){return "yingzi";}public SgsPlayer owner(){return owner;}
  public RuleId rule(){return RuleId.DRAW_COUNT;}public int priority(){return Priority.ACTIVE_CHOICE;}
  public boolean applies(SkillContext c){return owner.alive() && c.actor()==owner;}
  public int modifyInt(SkillContext c,int value){if(!api.requestSkillInvoke(owner,id(),owner))return value;
    c.state().publishSkillActivation(owner.seat(),id());return value+1;}
}
