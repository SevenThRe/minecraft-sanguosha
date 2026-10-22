package dev.sgs.core.engine;

import dev.sgs.core.card.*;
import java.util.*;

/** Cause-independent dying/rescue program. HP loss has source -1 and never creates damage. */
public final class DyingContinuation {
  public enum Step { START, RESCUE, COST, DONE }
  public record Snapshot(int victim,int source,Step step,RescueRound round,TrickContinuation.Pending pending,
      long revision,CardUseCostContinuation.Snapshot payment,long acceptedInput,int hpBeforeRecovery,int shaBefore,boolean wineBefore) {
    public Snapshot {
      Objects.requireNonNull(step);Objects.requireNonNull(round);
      if(victim<0||victim>7||source< -1||source>7||revision<0||revision==Long.MAX_VALUE||shaBefore<0)
        throw new IllegalArgumentException("invalid dying program header");
      if((step==Step.COST)!=(payment!=null)||step==Step.START&&(!round.ring().isEmpty()||pending!=null)
          ||step==Step.DONE&&pending!=null)throw new IllegalArgumentException("invalid dying child phase");
      if(payment!=null&&(acceptedInput<1||payment.reason()!=CardMove.Reason.USE||payment.cards().size()!=1
          ||!Set.of(CardType.TAO,CardType.JIU).contains(payment.type())||payment.recast()
          ||round.exhausted()||round.asker()!=payment.actor()||payment.type()==CardType.JIU&&payment.actor()!=victim))
        throw new IllegalArgumentException("invalid rescue payment owner or card");
      if(pending!=null) {
        if(pending.id()<1||pending.deadline()<0)throw new IllegalArgumentException("invalid rescue input");
        if(step==Step.RESCUE&&(round.exhausted()||pending.type()!=PromptType.RESCUE_TAO||pending.asker()!=round.asker()
            ||pending.subject()!=victim||!pending.choices().isEmpty()||pending.hidden()||!pending.skill().isEmpty()
            ||pending.revision()!=revision))throw new IllegalArgumentException("rescue input differs from cursor");
        if(step==Step.COST&&(!pending.equals(payment.pending())||payment.step()!=CardUseCostContinuation.Step.MOVEMENTS
            ||pending.id()<=acceptedInput))throw new IllegalArgumentException("rescue cost input differs");
      }
    }
  }
  private final int victim,source;private Step step=Step.START;
  private RescueRound round=RescueRound.start(List.of());private TrickContinuation.Pending pending;
  private long revision,acceptedInput;private CardUseCostContinuation payment;
  private int hpBeforeRecovery,shaBefore;private boolean wineBefore;
  DyingContinuation(int victim,int source){this.victim=victim;this.source=source;}
  DyingContinuation(Snapshot s,long captured,long now){
    victim=s.victim();source=s.source();step=s.step();round=s.round();revision=s.revision();acceptedInput=s.acceptedInput();
    hpBeforeRecovery=s.hpBeforeRecovery();shaBefore=s.shaBefore();wineBefore=s.wineBefore();
    if(s.payment()!=null)payment=new CardUseCostContinuation(s.payment(),captured,now);
    var p=s.pending();pending=p==null?null:new TrickContinuation.Pending(p.id(),p.type(),p.asker(),p.subject(),
        DecisionBoundary.restoredDeadline(p.deadline(),captured,now),p.choices(),p.hidden(),p.skill(),p.revision());
  }
  public Snapshot snapshot(){return new Snapshot(victim,source,step,round,pending,revision,
      payment==null?null:payment.snapshot(),acceptedInput,hpBeforeRecovery,shaBefore,wineBefore);}
  boolean done(){return step==Step.DONE;}
  Optional<PromptRequest> prompt(SgsGameEngine e){
    if(pending==null)return Optional.empty();var p=pending;
    return Optional.of(new PromptRequest(p.id(),p.type(),e.state().players().get(p.asker()),e.state().players().get(p.subject()),
        p.type()==PromptType.RESCUE_TAO?(p.asker()==victim?List.of(CardType.TAO,CardType.JIU):List.of(CardType.TAO)):List.of(),
        List.of(),p.deadline(),p.skill(),List.of(),false));
  }
  void advanceOne(SgsGameEngine e){
    if(pending!=null||done())return;
    var target=e.state().players().get(victim);var origin=source<0?null:e.state().players().get(source);
    switch(step) {
      case START->{e.continuationStartDying(target,origin);round=RescueRound.start(e.state().alivePlayersFromCurrent().stream().map(p->p.seat()).toList());step=Step.RESCUE;}
      case RESCUE->{
        if(target.hp()>=1||!target.alive()||e.mythic().survivesBuqu(target)) {
          e.continuationCompleteDying(target,origin);step=Step.DONE;
        }else if(round.exhausted()) {
          if(!round.played()){e.continuationCompleteDying(target,origin);step=Step.DONE;}
          else round=round.next(e.state().alivePlayersFromCurrent().stream().map(p->p.seat()).toList());
        }else {
          var asker=e.state().players().get(round.asker());
          if(!asker.alive()||!e.mythic().rescueAllowed(asker,target)){round=round.completed(false);return;}
          revision++;pending=new TrickContinuation.Pending(e.nextPromptId(),PromptType.RESCUE_TAO,asker.seat(),victim,
              e.responseDeadlineMillis(),List.of(),false,"",revision);
        }
      }
      case COST->{
        payment.advance(e);
        if(payment.done()) {
          e.continuationFinishRescue(e.state().players().get(round.asker()),target,PlayDecision.asIs(payment.card(e)));
          payment=null;round=round.completed(true);step=Step.RESCUE;
        }else {
          pending=payment.pending();if(pending==null)throw new IllegalStateException("rescue payment did not yield");
          hpBeforeRecovery=target.hp();
        }
      }
      case DONE->{}
    }
  }
  boolean respond(SgsGameEngine e,Optional<PlayDecision> answer){
    if(step!=Step.RESCUE||pending==null)return false;
    if(e.continuationNow()>pending.deadline())answer=Optional.empty();
    acceptedInput=pending.id();pending=null;revision++;
    if(answer.isEmpty()){round=round.completed(false);return true;}
    hpBeforeRecovery=e.state().players().get(victim).hp();shaBefore=e.state().shaPlayedThisTurn();wineBefore=e.state().jiuBoostPending();
    payment=CardUseCostContinuation.accepted(e,round.asker(),answer.get().card(),CardMove.Reason.USE);step=Step.COST;return true;
  }
  boolean invoke(SgsGameEngine e,boolean accepted){
    if(step!=Step.COST||pending==null||!payment.invoke(e,accepted))return false;
    pending=null;revision++;return true;
  }
  void validate(SgsGameEngine e){
    snapshot();int n=e.state().players().size();
    if(victim>=n||source>=n||round.ring().stream().anyMatch(s->s>=n))throw new IllegalArgumentException("dying role outside roster");
    var target=e.state().players().get(victim);
    if(pending==null||!target.alive()||target.hp()>=1||!e.state().players().get(round.asker()).alive()
        ||!round.ring().equals(e.state().alivePlayersFromCurrent().stream().map(p->p.seat()).toList()))
      throw new IllegalArgumentException("dying decision no longer applies");
    if(step==Step.COST) {
      if(target.hp()!=hpBeforeRecovery||e.state().shaPlayedThisTurn()!=shaBefore||e.state().jiuBoostPending()!=wineBefore)
        throw new IllegalArgumentException("rescue recovery or counters advanced before payment finished");
      payment.validate(e);
    }else if(step!=Step.RESCUE)throw new IllegalArgumentException("dying program is not awaiting");
  }
}
