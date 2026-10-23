package dev.sgs.core.engine.skill.skills;

import java.util.List;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;

/** Standard Hua Tuo: once per turn, one discarded hand card heals a living wounded character. */
public final class QingNangSkill implements ActiveSkill,PersistentSkillMemory {
  public static final String ID="qingnang";
  private final SgsPlayer owner;
  private int usedTurn=-1;
  public QingNangSkill(SgsPlayer owner){this.owner=owner;}
  public java.util.Map<String,Long> saveMemory(){return java.util.Map.of("usedTurn",(long)usedTurn);}
  public void validateMemory(java.util.Map<String,Long> values,int turnNumber){
    if(values==null || !values.keySet().equals(java.util.Set.of("usedTurn")) || values.get("usedTurn")==null
        || turnNumber<0 || values.get("usedTurn")< -1 || values.get("usedTurn")>turnNumber)
      throw new IllegalArgumentException("invalid qingnang memory");
  }
  public void restoreMemory(java.util.Map<String,Long> values,int turnNumber){validateMemory(values,turnNumber);usedTurn=values.get("usedTurn").intValue();}
  public String id(){return ID;}
  public SgsPlayer owner(){return owner;}
  public String unavailableReason(SkillEngine api){return usedTurn==api.gameState().turnNumber()?"SKILL_USED_THIS_TURN":"";}
  public SkillResult preview(SkillEngine api,SgsPlayer actor,SgsPlayer target,List<CardInstance> cards){
    if(actor!=owner || !owner.alive())return SkillResult.reject("not_skill_owner");
    if(usedTurn==api.gameState().turnNumber())return SkillResult.reject("already_used");
    if(target==null || !api.gameState().players().contains(target) || !target.alive() || target.hp()>=target.maxHp())return SkillResult.reject("target_not_wounded");
    if(cards==null || cards.size()!=1 || !CardZone.hand(owner.seat()).equals(api.gameState().cards().locate(cards.getFirst().instanceId())))return SkillResult.reject("one_hand_card_required");
    return SkillResult.ok();
  }
  public SkillResult activate(SkillEngine api,SgsPlayer actor,SgsPlayer target,List<CardInstance> cards){
    var validation=preview(api,actor,target,cards);if(!validation.accepted())return validation;
    usedTurn=api.gameState().turnNumber();api.discardCards(owner,cards);
    if(target.alive())api.heal(target,1);
    return SkillResult.ok();
  }
}
