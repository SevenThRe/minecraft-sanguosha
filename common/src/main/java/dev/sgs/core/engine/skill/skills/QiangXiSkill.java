package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
import java.util.*;
/** Classic once-per-play-phase skill: lose one HP or discard one owned weapon. */
public final class QiangXiSkill implements ActiveSkill,PersistentSkillMemory {
  private final SgsPlayer owner;private int usedTurn=-1;
  public QiangXiSkill(SgsPlayer owner){this.owner=owner;}
  public String id(){return "qiangxi";}public SgsPlayer owner(){return owner;}
  public String unavailableReason(SkillEngine api){return usedTurn==api.gameState().turnNumber()?"SKILL_USED_THIS_TURN":"";}
  public SkillResult preview(SkillEngine api,SgsPlayer actor,SgsPlayer target,List<CardInstance> costs){
    var g=api.gameState();
    if(actor!=owner || !owner.alive())return SkillResult.reject("not_skill_owner");
    if(usedTurn==g.turnNumber())return SkillResult.reject("already_used");
    if(target==null || target==owner || !g.players().contains(target) || !target.alive())return SkillResult.reject("other_living_target_required");
    if(costs==null || costs.size()>1)return SkillResult.reject("one_weapon_or_hp_required");
    int range=g.attackRange(owner);
    if(!costs.isEmpty()){
      var card=costs.getFirst();var zone=g.cards().locate(card.instanceId());
      if(card.type().category()!=CardCategory.WEAPON || (!CardZone.hand(owner.seat()).equals(zone) && !CardZone.equipment(owner.seat()).equals(zone)))return SkillResult.reject("owned_weapon_required");
      if(CardZone.equipment(owner.seat()).equals(zone))range=1;
    }
    if(g.distance(owner,target)>range)return SkillResult.reject("out_of_range_after_cost");
    return SkillResult.ok();
  }
  public SkillResult activate(SkillEngine api,SgsPlayer actor,SgsPlayer target,List<CardInstance> costs){
    var validation=preview(api,actor,target,costs);if(!validation.accepted())return validation;
    var g=api.gameState();
    usedTurn=g.turnNumber();
    if(costs.isEmpty())api.loseHp(owner,1);else api.discardCards(owner,costs);
    // The committed effect remains valid even when its HP cost kills the source.
    if(target.alive())api.dealSkillDamage(owner,target,1);
    return SkillResult.ok();
  }
  public Map<String,Long> saveMemory(){return Map.of("usedTurn",(long)usedTurn);}
  public void validateMemory(Map<String,Long> v,int turn){if(v==null || !v.keySet().equals(Set.of("usedTurn")) || v.get("usedTurn")==null || turn<0 || v.get("usedTurn")< -1 || v.get("usedTurn")>turn)throw new IllegalArgumentException("invalid qiangxi memory");}
  public void restoreMemory(Map<String,Long> v,int turn){validateMemory(v,turn);usedTurn=v.get("usedTurn").intValue();}
}
