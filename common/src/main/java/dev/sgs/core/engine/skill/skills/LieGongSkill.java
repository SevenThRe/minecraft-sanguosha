package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.Phase;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
/** Classic Huang Zhong: target hand count is compared when this Sha specifies its target. */
public final class LieGongSkill implements AwaitableIntModifier {
  private final SgsPlayer owner;private final SkillEngine api;
  public LieGongSkill(SgsPlayer owner,SkillEngine api){this.owner=owner;this.api=api;}
  public String id(){return "liegong";}public SgsPlayer owner(){return owner;}
  public RuleId rule(){return RuleId.SHA_DODGE_COUNT;}public int priority(){return Priority.ACTIVE_CHOICE;}
  public boolean applies(SkillContext c){return c.source()==owner && owner.alive() && c.target()!=null
      && c.state().currentPlayer()==owner && c.state().phase()==Phase.PLAY
      && (c.target().handCount()>=owner.hp() || c.target().handCount()<=c.state().attackRange(owner));}
  public int modifyInt(SkillContext c,int value){
    if(!api.requestSkillInvoke(owner,id(),c.target()))return value;
    c.state().publishSkillActivation(owner.seat(),id());return 0;
  }
  public Request awaitedEffect(SkillContext c,int current){return new Request(owner.seat(),id(),Effect.INVOKE_SET,0);}
}
