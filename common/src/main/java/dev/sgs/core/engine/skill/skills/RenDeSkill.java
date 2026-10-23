package dev.sgs.core.engine.skill.skills;

import java.util.List;

import dev.sgs.core.card.CardInstance;
import dev.sgs.core.engine.skill.ActiveSkill;
import dev.sgs.core.engine.skill.SkillEngine;
import dev.sgs.core.engine.skill.SkillResult;
import dev.sgs.core.player.SgsPlayer;

/**
 * Rende (Liu Bei): during the play phase, hand any number of cards to another seat. Once per turn,
 * when the cumulative number of cards given this turn reaches two, recover one hp. Giving at full
 * hp still works but does not heal. State is keyed by turn number, so the counters reset
 * automatically when the owner's next turn arrives.
 */
public final class RenDeSkill implements ActiveSkill,dev.sgs.core.engine.skill.PersistentSkillMemory {

  public static final String ID = "rende";
  private static final int CARDS_PER_HEAL = 2;

  private final SgsPlayer owner;
  private int turnTracked = -1;
  private int givenThisTurn;
  private boolean healedThisTurn;

  public RenDeSkill(SgsPlayer owner) {
    this.owner = owner;
  }

  @Override public java.util.Map<String,Long> saveMemory(){
    return java.util.Map.of("turnTracked",(long)turnTracked,"givenThisTurn",(long)givenThisTurn,
        "healedThisTurn",healedThisTurn?1L:0L);
  }
  @Override public void validateMemory(java.util.Map<String,Long> values,int turnNumber){
    if(values==null || !values.keySet().equals(java.util.Set.of("turnTracked","givenThisTurn","healedThisTurn")))
      throw new IllegalArgumentException("invalid rende memory keys");
    Long turn=values.get("turnTracked"),given=values.get("givenThisTurn"),healed=values.get("healedThisTurn");
    if(turnNumber<0 || turn==null || given==null || healed==null || turn< -1 || turn>turnNumber
        || given<0 || given>Integer.MAX_VALUE || healed<0 || healed>1
        || turn== -1 && (given!=0 || healed!=0) || (given>=CARDS_PER_HEAL)!=(healed==1))
      throw new IllegalArgumentException("invalid rende memory values");
  }
  @Override public void restoreMemory(java.util.Map<String,Long> values,int turnNumber){
    validateMemory(values,turnNumber);
    turnTracked=values.get("turnTracked").intValue();givenThisTurn=values.get("givenThisTurn").intValue();
    healedThisTurn=values.get("healedThisTurn")==1;
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public SgsPlayer owner() {
    return owner;
  }

  @Override
  public SkillResult preview(
      SkillEngine api, SgsPlayer from, SgsPlayer target, List<CardInstance> cards) {
    if (from != owner) {
      return SkillResult.reject("not_skill_owner");
    }
    if (target == null || !target.alive()) {
      return SkillResult.reject("invalid_target");
    }
    if (target == owner) {
      return SkillResult.reject("target_self");
    }
    if (cards == null || cards.isEmpty()) {
      return SkillResult.reject("no_cards");
    }

    return SkillResult.ok();
  }
  public SkillResult activate(
      SkillEngine api, SgsPlayer from, SgsPlayer target, List<CardInstance> cards) {
    var validation=preview(api,from,target,cards);if(!validation.accepted())return validation;
    int turn = api.gameState().turnNumber();
    if (turn != turnTracked) {
      turnTracked = turn;
      givenThisTurn = 0;
      healedThisTurn = false;
    }

    for (CardInstance card : cards) {
      api.transferCard(owner, target, card);
      givenThisTurn++;
    }

    int healed = 0;
    if (!healedThisTurn && givenThisTurn >= CARDS_PER_HEAL) {
      healed = api.heal(owner, 1);
      healedThisTurn = true;
    }

    api.gameState()
        .log(
            "seat {} gave {} cards to seat {} via rende (healed {})",
            owner.seat(),
            cards.size(),
            target.seat(),
            healed);
    return SkillResult.ok();
  }
}
