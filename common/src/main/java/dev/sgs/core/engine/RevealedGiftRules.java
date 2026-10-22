package dev.sgs.core.engine;

import dev.sgs.core.card.*;
import dev.sgs.core.game.GameState;
import dev.sgs.core.player.SgsPlayer;
import java.util.*;

/** Shared historical reveal/deliver rules; revealing is not a payment or a move. */
public final class RevealedGiftRules {
  private RevealedGiftRules(){}
  public record Selection(long card,List<Long> hand){public Selection{hand=List.copyOf(hand);}}
  public static Selection reveal(GameState state,SgsPlayer source,SgsPlayer target,String skill,Suit guess){
    if(!source.alive()||!target.alive()||source.handCount()==0)return null;
    var hand=List.copyOf(source.handView());var card=hand.get(state.random().nextInt(hand.size()));
    state.log("seat {} {} revealed {} {} {} to seat {} guessed {}",source.seat(),skill,card.cardId(),card.suit(),card.rank(),target.seat(),guess);
    state.publishCardReveal(skill,source.seat(),target.seat(),card);
    return new Selection(card.instanceId(),hand.stream().map(CardInstance::instanceId).toList());
  }
  public static CardInstance physical(GameState state,long id){return state.cards().zones().stream().flatMap(z->state.cards().cards(z).stream()).filter(c->c.instanceId()==id).findFirst().orElseThrow(()->new IllegalArgumentException("revealed entity missing"));}
  public static void deliver(GameState state,int source,int target,long id){
    deliver(state,source,target,id,"fanjian");
  }
  public static void deliver(GameState state,int source,int target,long id,String reason){
    try{
    if(!state.players().get(target).alive())return;
    var from=state.cards().locate(id);
    if(from!=null&&!from.equals(CardZone.hand(target)))state.cards().move(List.of(physical(state,id)),from,CardZone.hand(target),CardMove.Reason.SKILL_GIVE,source);
    }finally{state.completeCardReveal(reason,source,target,id);}
  }
}
