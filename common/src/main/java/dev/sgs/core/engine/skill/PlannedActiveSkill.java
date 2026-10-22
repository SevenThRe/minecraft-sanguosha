package dev.sgs.core.engine.skill;

import dev.sgs.core.card.CardInstance;
import dev.sgs.core.player.SgsPlayer;
import java.util.*;

/** Skills which declare effects as data so synchronous and resumable parents share their rules. */
public interface PlannedActiveSkill extends ActiveSkill {
  record Preparation(ActiveSkillPlan plan, String rejection) {
    public Preparation {
      if((plan==null)==(rejection==null)||rejection!=null&&rejection.isBlank())
        throw new IllegalArgumentException("preparation needs a plan or a rejection");
    }
    public static Preparation ready(ActiveSkillPlan plan){return new Preparation(Objects.requireNonNull(plan),null);}
    public static Preparation reject(String reason){return new Preparation(null,Objects.requireNonNull(reason));}
    public boolean accepted(){return plan!=null;}
  }

  /** Pure: no prompt allocation, RNG, cost movement, use-limit mutation or public activation. */
  Preparation prepare(SkillEngine api,SgsPlayer actor,SgsPlayer target,List<CardInstance> costs);
  /** A parent can only promise persistence when every effect family it invokes is supported. */
  default boolean requiresAttackContinuation(){return false;}

  /** Commit the use limit after selection is accepted and the original preparation is revalidated. */
  void commitUse(SkillEngine api,ActiveSkillPlan plan);

  /** Skill-owned memory/role contract after payment; current hand sizes may have changed. */
  boolean committedPlan(SkillEngine api,ActiveSkillPlan plan,int selectedSource);
  /** Runs once after the complete effect and its child movements return. */
  default void effectCompleted(SkillEngine api,ActiveSkillPlan plan){}

  /** Shared revalidation at the selection/payment boundary. */
  default boolean stillPrepared(SkillEngine api,ActiveSkillPlan plan) {
    var state=api.gameState();
    if(plan.actor()!=owner().seat()||!id().equals(plan.skillId())||plan.turn()!=state.turnNumber()
        ||plan.primaryTarget()>=state.players().size())return false;
    var costs=plan.ownedCosts(api);
    if(costs.isEmpty())return false;
    var current=prepare(api,owner(),plan.effect()==ActiveSkillPlan.Effect.LOSE_HP_DRAW?null:state.players().get(plan.primaryTarget()),costs.get());
    return current.accepted()&&plan.equals(current.plan());
  }

  @Override default SkillResult activate(SkillEngine api,SgsPlayer actor,SgsPlayer target,List<CardInstance> costs) {
    var preparation=prepare(api,actor,target,costs);
    if(!preparation.accepted())return SkillResult.reject(preparation.rejection());
    var plan=preparation.plan();
    if(plan.effect()==ActiveSkillPlan.Effect.REVEALED_GIFT){
      commitUse(api,plan);var guess=api.requestFanJianSuit(target,actor);
      var chosen=dev.sgs.core.engine.RevealedGiftRules.reveal(api.gameState(),actor,target,id(),guess);
      if(chosen!=null){
        if(dev.sgs.core.engine.RevealedGiftRules.physical(api.gameState(),chosen.card()).suit()!=guess)api.dealSkillDamage(actor,target,1);
        dev.sgs.core.engine.RevealedGiftRules.deliver(api.gameState(),actor.seat(),target.seat(),chosen.card(),id());
      }
      effectCompleted(api,plan);return SkillResult.ok();
    }
    if(plan.effect()==ActiveSkillPlan.Effect.PROVIDED_ATTACK)return api.useJijiangAttack(actor,target);
    if(plan.effect()==ActiveSkillPlan.Effect.LOSE_HP_DRAW){
      if(!stillPrepared(api,plan))return SkillResult.reject("stale_preparation");
      commitUse(api,plan);api.loseHp(actor,plan.hpLoss());if(actor.alive())api.drawCards(actor,plan.drawCount());
      effectCompleted(api,plan);return SkillResult.ok();
    }
    var choice=api.requestSkillChoice(actor,target,plan.selection(),id(),List.of(),plan.candidates(),true);
    if(choice.isEmpty())return SkillResult.reject("selection_cancelled");
    if(!plan.allows(choice.get())||!stillPrepared(api,plan))return SkillResult.reject("stale_selection");
    var source=api.gameState().players().get(choice.get().targetSeat());
    if(!api.canSkillDuel(source,target))return SkillResult.reject("illegal_duel_target");
    var payment=plan.ownedCosts(api).orElseThrow();
    commitUse(api,plan);
    api.discardCards(actor,payment);
    if(source.alive()&&target.alive()) {
      switch(plan.effect()) {case UNCOUNTERABLE_DUEL->api.useSkillDuel(source,target);case LOSE_HP_DRAW,PROVIDED_ATTACK,REVEALED_GIFT->throw new IllegalStateException("wrong active effect branch");}
    }
    effectCompleted(api,plan);
    return SkillResult.ok();
  }
}
