package dev.sgs.core.engine;

import java.util.*;
import dev.sgs.core.card.*;
import dev.sgs.core.player.SgsPlayer;

/** A real reveal decision followed by a separate optional, suit-matching hand discard. */
final class FireAttackRule {
  private FireAttackRule(){}
  static ActionResult validate(SgsGameEngine engine,SgsPlayer source,SgsPlayer target,GameCard card){
    if(target==null)return ActionResult.rejected(RejectReason.TARGET_REQUIRED);
    if(!engine.state().players().contains(target))return ActionResult.rejected(RejectReason.TARGET_NOT_IN_GAME);
    if(!target.alive())return ActionResult.rejected(RejectReason.TARGET_DEAD);
    if(target.handCount()==0 || (target==source && target.handCount()==card.sourceCards().size()))
      return ActionResult.rejected(RejectReason.TARGET_NO_CARDS);
    return ActionResult.ok();
  }
  static void resolve(SgsGameEngine engine,SgsPlayer source,SgsPlayer target,GameCard cause){
    var state=engine.state();if(engine.isCounteredByWuxie(target,dev.sgs.core.card.CardType.HUOGONG) || !source.alive() || !target.alive() || target.handCount()==0)return;
    var allTypes=List.of(CardType.values());
    var reveal=new PromptRequest(engine.nextPromptId(),PromptType.HUOGONG_REVEAL,target,source,allTypes,List.of(),engine.responseDeadlineMillis(),"huogong");
    var choice=engine.ask(reveal);
    if(!source.alive() || !target.alive() || target.handCount()==0)return;
    var shown=choice.isPresent()?choice.get().card().sourceCards().getFirst():target.handView().getFirst();
    if(!CardZone.hand(target.seat()).equals(state.cards().locate(shown.instanceId())))shown=target.handView().getFirst();
    state.log("seat {} revealed {} for huogong",target.seat(),shown.cardId());
    state.publishCardReveal("huogong",target.seat(),source.seat(),shown);
    try{
    var payment=new PromptRequest(engine.nextPromptId(),PromptType.HUOGONG_PAY,source,target,allTypes,List.of(shown),engine.responseDeadlineMillis(),"huogong");
    var paid=engine.ask(payment);if(paid.isEmpty() || !source.alive() || !target.alive())return;
    var cost=paid.get().card().sourceCards().getFirst();
    if(engine.mythic().suit(source,cost)!=engine.mythic().suit(target,shown) || !CardZone.hand(source.seat()).equals(state.cards().locate(cost.instanceId())))return;
    state.cards().move(List.of(cost),CardZone.hand(source.seat()),CardZone.DISCARD,CardMove.Reason.DISCARD,source.seat());
    engine.applyDamage(target,1,source,cause,DamageNature.FIRE,false);
    }finally{state.completeCardReveal("huogong",target.seat(),source.seat(),shown.instanceId());}
  }
}
