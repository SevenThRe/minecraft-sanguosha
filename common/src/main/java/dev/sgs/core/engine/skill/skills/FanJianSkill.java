package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.card.*;
import dev.sgs.core.player.SgsPlayer;
import java.util.List;
/** Historical standard (2011): reveal without leaving hand, damage fully settles, then give. */
public final class FanJianSkill implements PlannedActiveSkill,PersistentSkillMemory {
  private final SgsPlayer owner;private int usedTurn=-1;
  public FanJianSkill(SgsPlayer owner){this.owner=owner;}
  public java.util.Map<String,Long> saveMemory(){return java.util.Map.of("usedTurn",(long)usedTurn);}
  public void validateMemory(java.util.Map<String,Long> values,int turnNumber){
    if(values==null || !values.keySet().equals(java.util.Set.of("usedTurn")) || values.get("usedTurn")==null
        || turnNumber<0 || values.get("usedTurn")< -1 || values.get("usedTurn")>turnNumber)
      throw new IllegalArgumentException("invalid fanjian memory");
  }
  public void restoreMemory(java.util.Map<String,Long> values,int turnNumber){validateMemory(values,turnNumber);usedTurn=values.get("usedTurn").intValue();}
  public String id(){return "fanjian";}public SgsPlayer owner(){return owner;}
  public String unavailableReason(SkillEngine api){return usedTurn==api.gameState().turnNumber()?"SKILL_USED_THIS_TURN":"";}
  public Preparation prepare(SkillEngine api,SgsPlayer actor,SgsPlayer target,List<CardInstance> costs){
    if(actor!=owner || !owner.alive() || target==null || target==owner || !target.alive() || !costs.isEmpty() || owner.handCount()==0)
      return Preparation.reject("invalid_target_or_cost");
    if(usedTurn==api.gameState().turnNumber())return Preparation.reject("already_used_this_turn");
    return Preparation.ready(new ActiveSkillPlan(owner.seat(),api.gameState().turnNumber(),id(),target.seat(),dev.sgs.core.engine.PromptType.FANJIAN_SUIT,List.of(),List.of(),ActiveSkillPlan.Effect.REVEALED_GIFT));
  }
  public void commitUse(SkillEngine api,ActiveSkillPlan plan){
    if(!stillPrepared(api,plan))throw new IllegalArgumentException("stale revealed gift preparation");usedTurn=plan.turn();
  }
  public boolean committedPlan(SkillEngine api,ActiveSkillPlan plan,int selectedSource){
    return selectedSource==-1&&plan.actor()==owner.seat()&&plan.skillId().equals(id())&&plan.turn()==api.gameState().turnNumber()
        &&usedTurn==plan.turn()&&plan.effect()==ActiveSkillPlan.Effect.REVEALED_GIFT;
  }
}
