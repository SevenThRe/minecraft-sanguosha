package dev.sgs.core.engine.skill.skills;

import dev.sgs.core.card.*;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
import java.util.*;

/** Standard Jieyin costs two HAND cards; the male recipient must already be wounded. */
public final class JieYinSkill implements ActiveSkill,PersistentSkillMemory {
  private final SgsPlayer owner;private int usedTurn=-1;
  public JieYinSkill(SgsPlayer owner){this.owner=owner;}
  public String id(){return "jieyin";}public SgsPlayer owner(){return owner;}
  public String unavailableReason(SkillEngine api){return usedTurn==api.gameState().turnNumber()?"SKILL_USED_THIS_TURN":"";}
  public SkillResult activate(SkillEngine api,SgsPlayer actor,SgsPlayer target,List<CardInstance> cards){
    if(actor!=owner || !owner.alive())return SkillResult.reject("not_skill_owner");
    if(usedTurn==api.gameState().turnNumber())return SkillResult.reject("already_used");
    if(target==null || target==owner || !api.gameState().players().contains(target) || !target.alive()
        || target.hero()==null || !api.gameState().male(target) || target.hp()>=target.maxHp())return SkillResult.reject("wounded_male_required");
    if(cards==null || cards.size()!=2 || cards.get(0).instanceId()==cards.get(1).instanceId()
        || cards.stream().anyMatch(card->!CardZone.hand(owner.seat()).equals(api.gameState().cards().locate(card.instanceId()))))
      return SkillResult.reject("two_distinct_hand_cards_required");
    usedTurn=api.gameState().turnNumber();api.discardCards(owner,cards);
    if(owner.alive())api.heal(owner,1);if(target.alive())api.heal(target,1);return SkillResult.ok();
  }
  public Map<String,Long> saveMemory(){return Map.of("usedTurn",(long)usedTurn);}
  public void validateMemory(Map<String,Long> v,int turn){if(v==null || !v.keySet().equals(Set.of("usedTurn"))
      || v.get("usedTurn")==null || turn<0 || v.get("usedTurn")< -1 || v.get("usedTurn")>turn)
    throw new IllegalArgumentException("invalid jieyin memory");}
  public void restoreMemory(Map<String,Long> v,int turn){validateMemory(v,turn);usedTurn=v.get("usedTurn").intValue();}
}
