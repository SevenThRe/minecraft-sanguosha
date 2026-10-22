package dev.sgs.core.engine;

import dev.sgs.core.card.GameCard;
import dev.sgs.core.card.CardMove;
import dev.sgs.core.card.CardZone;
import dev.sgs.core.game.GameState;
import dev.sgs.core.player.SgsPlayer;

/** Iron Chain owns its targeting/recast/effect rules; loader callbacks never implement them. */
final class TiesuoRule {
  private TiesuoRule(){}

  static ActionResult play(SgsGameEngine engine,SgsPlayer source,GameCard card,SgsPlayer first,SgsPlayer second){
    var state=engine.gameState();var result=validate(state,card,first,second);
    if(!result.accepted())return result;
    for(var target:new SgsPlayer[]{first,second})if(target!=null&&!engine.cardTargetAllowed(source,target,card))return ActionResult.rejected(RejectReason.SKILL_REJECTED);
    if(first==null){
      // Recasting is not card use. The discard is complete before drawing/recycling.
      state.cards().move(card.sourceCards(),CardZone.hand(source.seat()),CardZone.DISCARD,CardMove.Reason.RECAST,source.seat());
      engine.drawCards(source,1);
      state.log("seat {} recast {}",source.seat(),card.type().id());return ActionResult.ok();
    }
    engine.beginCounterspellScope();try{
    engine.beginOwnedUse(source,card,CardMove.Reason.USE);
    engine.cardUseCommitted(source,card);
    try{for(var recipient:state.alivePlayersFromCurrent()){
      if((recipient==first || recipient==second) && !engine.isCounteredByWuxie(recipient)){
        state.setChained(recipient.seat(),!state.chained(recipient.seat()));
        state.log("seat {} chain state {}",recipient.seat(),state.chained(recipient.seat()));
      }
    }}finally{state.cards().finish(card,source.seat());}
    }finally{engine.endCounterspellScope();}
    state.log("seat {} played {}",source.seat(),card.sourceCards().size()==1?card.sourceCards().getFirst().cardId():card.type().id());
    return ActionResult.ok();
  }

  static ActionResult validate(GameState state,GameCard card,SgsPlayer first,SgsPlayer second){
    if(first==null)return second!=null?ActionResult.rejected(RejectReason.TARGET_REQUIRED)
        :!card.canRecast()?ActionResult.rejected(RejectReason.RECAST_NOT_AVAILABLE):ActionResult.ok();
    if(first==second)return ActionResult.rejected(RejectReason.DUPLICATE_TARGET);
    for(var target:new SgsPlayer[]{first,second})if(target!=null){
      if(!state.players().contains(target))return ActionResult.rejected(RejectReason.TARGET_NOT_IN_GAME);
      if(!target.alive())return ActionResult.rejected(RejectReason.TARGET_DEAD);
    }
    return ActionResult.ok();
  }
}
