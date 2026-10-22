package dev.sgs.core.engine;

import dev.sgs.core.card.*;
import java.util.*;

/** One Ganglie body, including an unpaid first discard proposal and suspended card-less damage. */
public final class GangLieContinuation {
  public enum Step { INVOKE, JUDGE, COST, RETALIATE, WAIT_DAMAGE, DONE }
  public record Snapshot(int owner,int source,Step step,boolean accepted,List<Long> costs,JudgmentContinuation.Snapshot judgment,AttackContinuation.PendingSnapshot pending,long revision){public Snapshot{costs=List.copyOf(costs);}}
  private final int owner,source;private final String skill;private Step step=Step.INVOKE;private boolean accepted;private CardCostSelection costs;private JudgmentContinuation judgment;private AttackContinuation.PendingSnapshot pending;private long revision;
  GangLieContinuation(int owner,int source){this(owner,source,"ganglie");}
  GangLieContinuation(int owner,int source,String skill){this.owner=owner;this.source=source;this.skill=Objects.requireNonNull(skill);costs=new CardCostSelection(source,2,CardCostSelection.Scope.HAND,List.of());}
  GangLieContinuation(Snapshot s,long captured,long now){this(s,captured,now,"ganglie");}
  GangLieContinuation(Snapshot s,long captured,long now,String skill){this(s.owner(),s.source(),skill);step=s.step();accepted=s.accepted();costs=new CardCostSelection(source,2,CardCostSelection.Scope.HAND,s.costs());revision=s.revision();if(s.judgment()!=null)judgment=new JudgmentContinuation(s.judgment(),captured,now);var p=s.pending();pending=p==null?null:new AttackContinuation.PendingSnapshot(p.id(),p.type(),p.askerSeat(),p.subjectSeat(),p.allowedTypes(),DecisionBoundary.restoredDeadline(p.deadlineMillis(),captured,now),p.stateRevision(),p.skillId());}
  public Snapshot snapshot(){return new Snapshot(owner,source,step,accepted,costs.selected(),judgment==null?null:judgment.snapshot(),pending,revision);}
  boolean done(){return step==Step.DONE;}boolean needsDamage(){return step==Step.RETALIATE;}boolean accepted(){return accepted;}
  private void ask(SgsGameEngine e,PromptType type,int actor,int subject){revision++;pending=new AttackContinuation.PendingSnapshot(e.nextPromptId(),type,actor,subject,List.of(),e.responseDeadlineMillis(),revision,skill);}
  void advance(SgsGameEngine e){
    if(pending!=null||done()||needsDamage()||step==Step.WAIT_DAMAGE)return;
    for(int budget=0;budget<32;budget++)switch(step){
      case INVOKE->{ask(e,PromptType.SKILL_INVOKE,owner,source);return;}
      case JUDGE->{
        judgment.advance(e,++revision);pending=judgment.pending();if(pending!=null)return;
        if(!judgment.done())throw new IllegalStateException("ganglie judgment did not yield");long card=judgment.snapshot().effective();judgment=null;
        if(card==0||e.trickCardSuit(card)==Suit.HEART||!e.state().players().get(source).alive()){step=Step.DONE;return;}step=Step.COST;
      }
      case COST->{
        var who=e.state().players().get(source);
        if(costs.complete()){
          var payment=costs.resolve(e.state(),Set.of());
          if(payment.isPresent()){e.discardCards(who,payment.get());step=Step.DONE;}else step=Step.RETALIATE;
          return;
        }
        if(!who.alive()){step=Step.DONE;return;}
        if(costs.selected().isEmpty()&&who.handCount()<2){step=Step.RETALIATE;return;}
        ask(e,PromptType.GANGLIE_DISCARD,source,owner);return;
      }
      default->throw new IllegalStateException("invalid ganglie atomic step");
    }
    throw new IllegalStateException("ganglie did not yield");
  }
  PromptRequest request(SgsGameEngine e){
    if(pending==null)throw new IllegalStateException("ganglie has no pending request");if(judgment!=null)return judgment.request(e);
    var asker=e.state().players().get(pending.askerSeat());return new PromptRequest(pending.id(),pending.type(),asker,e.state().players().get(pending.subjectSeat()),List.of(),pending.type()==PromptType.GANGLIE_DISCARD?costs.options(e.state(),Set.of()):List.of(),pending.deadlineMillis(),pending.skillId());
  }
  boolean invoke(SgsGameEngine e,boolean answer){
    if(pending==null||pending.type()!=PromptType.SKILL_INVOKE)return false;
    boolean yes=answer&&e.continuationNow()<=pending.deadlineMillis();
    if(judgment!=null){if(!judgment.invoke(e,yes))return false;pending=null;revision++;return true;}
    if(step!=Step.INVOKE)return false;pending=null;revision++;accepted=yes;if(yes){judgment=new JudgmentContinuation(owner,0);step=Step.JUDGE;}else step=Step.DONE;return true;
  }
  boolean choose(SgsGameEngine e,Optional<SkillChoice> answer){
    if(pending==null)return false;if(e.continuationNow()>pending.deadlineMillis())answer=Optional.empty();
    if(judgment!=null){if(!judgment.choose(e,answer))return false;pending=null;revision++;return true;}
    if(step!=Step.COST)return false;
    if(answer.isPresent()){var choice=answer.get();if(choice.targetSeat()!=-1)return false;var proposal=costs.select(e.state(),choice.cardId(),Set.of());if(proposal.isEmpty())return false;costs=proposal.get();}
    else step=Step.RETALIATE;pending=null;revision++;return true;
  }
  void suspendForDamage(){if(!needsDamage())throw new IllegalStateException("ganglie is not retaliating");costs=new CardCostSelection(source,2,CardCostSelection.Scope.HAND,List.of());step=Step.WAIT_DAMAGE;}
  void damageCompleted(){if(step!=Step.WAIT_DAMAGE)throw new IllegalStateException("ganglie has no suspended damage");step=Step.DONE;}
}
