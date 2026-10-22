package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
/** Separate optional invocations; final modified black judgments are obtained before cleanup. */
public final class LuoShenSkill implements SkillListener {
  private final SgsPlayer owner;private final SkillEngine api;
  public LuoShenSkill(SgsPlayer owner,SkillEngine api){this.owner=owner;this.api=api;}
  public String id(){return "luoshen";}public SgsPlayer owner(){return owner;}
  public Timing timing(){return Timing.PHASE_PREPARE_START;}public int priority(){return Priority.ACTIVE_CHOICE;}
  public boolean applies(SkillContext c){return c.actor()==owner && owner.alive() && owner.hp()>0;}
  public void execute(SkillContext c){while(owner.alive() && owner.hp()>0 && api.requestSkillInvoke(owner,id(),owner)){
    c.state().publishSkillActivation(owner.seat(),id());var card=api.judgeForSkill(owner,id(),true);
    if(card==null || !card.isBlack())break;
  }}
}
