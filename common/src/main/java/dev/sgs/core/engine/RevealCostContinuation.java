package dev.sgs.core.engine;

import dev.sgs.core.card.*;
import java.util.*;

/** Data-owned reveal, then optional matching-suit hand cost. Revealing never moves the card. */
public final class RevealCostContinuation {
  public enum Step { REVEAL, PAY, COMMITTED, DECLINED }
  public record Snapshot(int source,int target,Step step,long shown,long cost,TrickContinuation.Pending pending){}
  private final int source,target;private Step step=Step.REVEAL;private long shown,cost;private TrickContinuation.Pending pending;
  RevealCostContinuation(int source,int target){this.source=source;this.target=target;}
  RevealCostContinuation(Snapshot saved,long captured,long now){source=saved.source();target=saved.target();step=saved.step();shown=saved.shown();cost=saved.cost();var p=saved.pending();pending=p==null?null:new TrickContinuation.Pending(p.id(),p.type(),p.asker(),p.subject(),DecisionBoundary.restoredDeadline(p.deadline(),captured,now),p.choices(),p.hidden(),p.skill(),p.revision());}
  public Snapshot snapshot(){return new Snapshot(source,target,step,shown,cost,pending);}
  boolean committed(){return step==Step.COMMITTED;}boolean declined(){return step==Step.DECLINED;}
  Optional<PromptRequest> prompt(SgsGameEngine e){if(pending==null)return Optional.empty();var players=e.state().players();
    var choices=pending.choices().stream().map(id->physical(e,id)).toList();
    return Optional.of(new PromptRequest(pending.id(),pending.type(),players.get(pending.asker()),players.get(pending.subject()),List.of(CardType.values()),choices,pending.deadline(),pending.skill()));
  }
  void advance(SgsGameEngine e,long revision){
    if(pending!=null||committed()||declined())return;
    if(!e.state().players().get(source).alive()||!e.state().players().get(target).alive()||e.state().players().get(target).handCount()==0){step=Step.DECLINED;return;}
    boolean reveal=step==Step.REVEAL;
    pending=new TrickContinuation.Pending(e.nextPromptId(),reveal?PromptType.HUOGONG_REVEAL:PromptType.HUOGONG_PAY,reveal?target:source,reveal?source:target,e.responseDeadlineMillis(),reveal?List.of():List.of(shown),false,"huogong",revision);
  }
  boolean answer(SgsGameEngine e,Optional<PlayDecision> answer){
    if(pending==null)return false;var state=e.state();var actor=state.players().get(source);var victim=state.players().get(target);
    if(!actor.alive()||!victim.alive()||victim.handCount()==0){pending=null;step=Step.DECLINED;return true;}
    if(step==Step.REVEAL){
      var card=answer.isPresent()?answer.get().card().sourceCards().getFirst():victim.handView().getFirst();
      if(!CardZone.hand(target).equals(state.cards().locate(card.instanceId())))return false;
      shown=card.instanceId();state.log("seat {} revealed {} for huogong",target,card.cardId());state.publishCardReveal("huogong",target,source,card);pending=null;step=Step.PAY;return true;
    }
    if(step!=Step.PAY)return false;
    if(answer.isEmpty()){pending=null;step=Step.DECLINED;return true;}
    var card=answer.get().card().sourceCards().getFirst();
    if(!CardZone.hand(source).equals(state.cards().locate(card.instanceId()))||e.mythic().suit(actor,card)!=e.mythic().suit(victim,physical(e,shown)))return false;
    cost=card.instanceId();pending=null;step=Step.COMMITTED;
    state.cards().move(List.of(card),CardZone.hand(source),CardZone.DISCARD,CardMove.Reason.DISCARD,source);return true;
  }
  private static CardInstance physical(SgsGameEngine e,long id){return e.state().cards().zones().stream().flatMap(z->e.state().cards().cards(z).stream()).filter(c->c.instanceId()==id).findFirst().orElseThrow();}
}
