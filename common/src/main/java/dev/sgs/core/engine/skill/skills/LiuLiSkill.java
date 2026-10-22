package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.PromptType;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
import java.util.*;
/** Actual hand/equipment discard, then change the Sha's target before Tieji/dodge/armor. */
public final class LiuLiSkill implements AwaitableTargetRedirect {
  private final SgsPlayer owner;private final SkillEngine api;
  public LiuLiSkill(SgsPlayer owner,SkillEngine api){this.owner=owner;this.api=api;}
  public String id(){return "liuli";}public SgsPlayer owner(){return owner;}
  public Timing timing(){return Timing.SHA_TARGET_BEFORE;}public int priority(){return Priority.ACTIVE_CHOICE;}
  public boolean applies(SkillContext c){return c.target()==owner && owner.alive() && c.source()!=null && c.card()!=null
      && (owner.handCount()>0 || !owner.equipment().all().isEmpty());}
  public void execute(SkillContext c){
    var costs=dev.sgs.core.engine.AttackRedirectRules.costs(owner);
    var targets=dev.sgs.core.engine.AttackRedirectRules.targets(api,c.source(),owner,c.card(),costs,List.of());
    if(targets.isEmpty())return;
    var choice=api.requestSkillChoice(owner,c.source(),PromptType.LIULI_TARGET,id(),costs,targets,false);if(choice.isEmpty())return;
    var cost=dev.sgs.core.engine.AttackRedirectRules.payment(api,c.source(),owner,c.card(),choice.get(),costs.stream().map(CardInstance::instanceId).toList(),targets,List.of()).orElse(null);
    if(cost==null)return;
    var target=c.state().players().get(choice.get().targetSeat());
    if(!api.canRedirectAttack(c.source(),owner,target,c.card(),cost))return;
    api.discardCards(owner,List.of(cost));if(target.alive()){c.redirectAttackTarget(target);c.confirmPublicActivation();}
  }
}
