package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
/** Optional end-phase draw, strictly after mandatory hand-limit discard. */
public final class BiYueSkill implements AwaitablePhaseEffect {
  private final SgsPlayer owner;private final SkillEngine api;
  public BiYueSkill(SgsPlayer owner,SkillEngine api){this.owner=owner;this.api=api;}
  public String id(){return "biyue";}public SgsPlayer owner(){return owner;}
  public Timing timing(){return Timing.PHASE_TURN_END;}public int priority(){return Priority.ACTIVE_CHOICE;}
  public boolean applies(SkillContext c){return c.actor()==owner && owner.alive() && owner.hp()>0;}
  public void execute(SkillContext c){if(api.requestSkillInvoke(owner,id(),owner)){api.drawCards(owner,1);c.confirmPublicActivation();}}
}
