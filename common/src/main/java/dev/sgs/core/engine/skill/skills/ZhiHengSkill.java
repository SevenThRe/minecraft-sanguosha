package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
import java.util.*;
/** Historical standard version: once per turn, discard hand/equipment and draw exactly that count. */
public final class ZhiHengSkill implements ActiveSkill,PersistentSkillMemory {
  private final SgsPlayer owner;private int usedTurn=-1;
  public ZhiHengSkill(SgsPlayer owner){this.owner=owner;}
  public java.util.Map<String,Long> saveMemory(){return java.util.Map.of("usedTurn",(long)usedTurn);}
  public void validateMemory(java.util.Map<String,Long> values,int turnNumber){
    if(values==null || !values.keySet().equals(java.util.Set.of("usedTurn")) || values.get("usedTurn")==null
        || turnNumber<0 || values.get("usedTurn")< -1 || values.get("usedTurn")>turnNumber)
      throw new IllegalArgumentException("invalid zhiheng memory");
  }
  public void restoreMemory(java.util.Map<String,Long> values,int turnNumber){validateMemory(values,turnNumber);usedTurn=values.get("usedTurn").intValue();}
  public String id(){return "zhiheng";}public SgsPlayer owner(){return owner;}
  public String unavailableReason(SkillEngine api){return usedTurn==api.gameState().turnNumber()?"SKILL_USED_THIS_TURN":"";}
  public SkillResult preview(SkillEngine api,SgsPlayer actor,SgsPlayer target,List<CardInstance> cards){
    if(actor!=owner || !owner.alive())return SkillResult.reject("not_skill_owner");
    if(target!=null || cards.isEmpty())return SkillResult.reject("invalid_target_or_cost");
    if(usedTurn==api.gameState().turnNumber())return SkillResult.reject("already_used_this_turn");
    var seen=new HashSet<Long>();
    for(var card:cards){var at=api.gameState().cards().locate(card.instanceId());
      if(!seen.add(card.instanceId()) || (!CardZone.hand(owner.seat()).equals(at) && !CardZone.equipment(owner.seat()).equals(at)))return SkillResult.reject("unowned_cost");}
    return SkillResult.ok();
  }
  public SkillResult activate(SkillEngine api,SgsPlayer actor,SgsPlayer target,List<CardInstance> cards){
    var validation=preview(api,actor,target,cards);if(!validation.accepted())return validation;
    usedTurn=api.gameState().turnNumber();api.discardCards(owner,cards);if(owner.alive())api.drawCards(owner,cards.size());
    api.gameState().log("seat {} used zhiheng count {}",owner.seat(),cards.size());return SkillResult.ok();
  }
}
