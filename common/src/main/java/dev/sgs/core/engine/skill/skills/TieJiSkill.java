package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
/** Historical red judgment removes dodge permission, not passive armor nullification. */
public final class TieJiSkill implements AwaitableIntModifier {
  private final SgsPlayer owner;private final SkillEngine api;
  public TieJiSkill(SgsPlayer owner,SkillEngine api){this.owner=owner;this.api=api;}
  public String id(){return "tieji";}public SgsPlayer owner(){return owner;}
  public RuleId rule(){return RuleId.SHA_DODGE_COUNT;}public int priority(){return Priority.ACTIVE_CHOICE;}
  public boolean applies(SkillContext c){return c.source()==owner && owner.alive();}
  public Request awaitedEffect(SkillContext c,int current){return new Request(owner.seat(),id(),Effect.INVOKE_RED_JUDGMENT_SET,0);}
  public int modifyInt(SkillContext c,int count){
    if(!api.requestSkillInvoke(owner,id(),c.target()))return count;
    c.state().publishSkillActivation(owner.seat(),id());var judged=api.judgeForSkill(owner,id(),false);
    return judged!=null && judged.isRed()?0:count;
  }
}
