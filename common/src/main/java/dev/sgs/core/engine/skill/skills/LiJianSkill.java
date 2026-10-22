package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.PromptType;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
import java.util.*;
/** Primary target answers Sha first; the second chosen male is the duel's source. */
public final class LiJianSkill implements PlannedActiveSkill,PersistentSkillMemory {
  private final SgsPlayer owner;private int usedTurn=-1;
  public LiJianSkill(SgsPlayer owner){this.owner=owner;}
  public String id(){return "lijian";}public SgsPlayer owner(){return owner;}
  public String unavailableReason(SkillEngine api){return usedTurn==api.gameState().turnNumber()?"SKILL_USED_THIS_TURN":"";}
  public Preparation prepare(SkillEngine api,SgsPlayer actor,SgsPlayer target,List<CardInstance> costs){
    var g=api.gameState();
    if(actor!=owner || !owner.alive())return Preparation.reject("not_skill_owner");
    if(usedTurn==g.turnNumber())return Preparation.reject("already_used");
    if(target==null || target==owner || !g.players().contains(target) || !target.alive() || target.hero()==null || !g.male(target))return Preparation.reject("other_male_required");
    if(costs.size()!=1)return Preparation.reject("one_card_required");
    var cost=costs.getFirst();var zone=g.cards().locate(cost.instanceId());
    if(!CardZone.hand(owner.seat()).equals(zone) && !CardZone.equipment(owner.seat()).equals(zone))return Preparation.reject("unowned_cost");
    var candidates=g.players().stream().filter(p->p!=owner && p!=target && p.alive() && p.hero()!=null && g.male(p) && api.canSkillDuel(p,target)).map(SgsPlayer::seat).toList();
    if(candidates.isEmpty())return Preparation.reject("no_second_male");
    return Preparation.ready(new ActiveSkillPlan(owner.seat(),g.turnNumber(),id(),target.seat(),
        PromptType.LIJIAN_TARGET,candidates,List.of(cost.instanceId()),ActiveSkillPlan.Effect.UNCOUNTERABLE_DUEL));
  }
  public void commitUse(SkillEngine api,ActiveSkillPlan plan){
    if(!stillPrepared(api,plan))throw new IllegalArgumentException("stale lijian preparation");
    usedTurn=plan.turn();
  }
  public boolean committedPlan(SkillEngine api,ActiveSkillPlan plan,int selectedSource){
    var g=api.gameState();
    if(plan.actor()!=owner.seat()||!id().equals(plan.skillId())||plan.turn()!=g.turnNumber()||usedTurn!=plan.turn()
        ||plan.costs().size()!=1||plan.primaryTarget()>=g.players().size()||selectedSource<0||selectedSource>=g.players().size()
        ||!plan.candidates().contains(selectedSource))return false;
    var target=g.players().get(plan.primaryTarget());var source=g.players().get(selectedSource);
    return target!=owner&&source!=owner&&target!=source&&g.male(target)&&g.male(source)
        &&plan.candidates().stream().allMatch(seat->seat<g.players().size()&&g.male(g.players().get(seat)));
  }
  public Map<String,Long> saveMemory(){return Map.of("usedTurn",(long)usedTurn);}
  public void validateMemory(Map<String,Long> v,int turn){if(v==null || !v.keySet().equals(Set.of("usedTurn")) || v.get("usedTurn")==null
      || turn<0 || v.get("usedTurn")< -1 || v.get("usedTurn")>turn)throw new IllegalArgumentException("invalid lijian memory");}
  public void restoreMemory(Map<String,Long> v,int turn){validateMemory(v,turn);usedTurn=v.get("usedTurn").intValue();}
}
