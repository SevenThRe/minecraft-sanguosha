package dev.sgs.core.engine;

import dev.sgs.core.card.*;
import java.util.*;

/** One real judgment: each modifier pays once, followed by the final card's optional Tiandu. */
public final class JudgmentContinuation {
  public enum Step { REVEAL, MODIFY, EFFECT, TIANDU, DONE }
  public record Snapshot(int owner,long cause,long revealed,long effective,List<Integer> modifiers,int cursor,Step step,AttackContinuation.PendingSnapshot pending){public Snapshot{modifiers=List.copyOf(modifiers);}}
  private final int owner;private final long cause;private long revealed,effective;private List<Integer> modifiers=List.of();private int cursor;private Step step=Step.REVEAL;private AttackContinuation.PendingSnapshot pending;private boolean red;
  JudgmentContinuation(int owner,long cause){this.owner=owner;this.cause=cause;}
  JudgmentContinuation(Snapshot s,long captured,long now){owner=s.owner();cause=s.cause();revealed=s.revealed();effective=s.effective();modifiers=s.modifiers();cursor=s.cursor();step=s.step();var p=s.pending();pending=p==null?null:new AttackContinuation.PendingSnapshot(p.id(),p.type(),p.askerSeat(),p.subjectSeat(),p.allowedTypes(),DecisionBoundary.restoredDeadline(p.deadlineMillis(),captured,now),p.stateRevision(),p.skillId());}
  public Snapshot snapshot(){return new Snapshot(owner,cause,revealed,effective,modifiers,cursor,step,pending);}
  boolean done(){return step==Step.DONE;}boolean red(){return red;}AttackContinuation.PendingSnapshot pending(){return pending;}
  private void ask(SgsGameEngine e,PromptType type,int actor,String skill,long revision){pending=new AttackContinuation.PendingSnapshot(e.nextPromptId(),type,actor,owner,List.of(),e.responseDeadlineMillis(),revision,skill);}
  void advance(SgsGameEngine e,long revision){
    if(pending!=null||done())return;
    for(int limit=0;limit<32;limit++)switch(step){
      case REVEAL->{revealed=effective=e.trickRevealJudgment(owner,cause);modifiers=effective==0?List.of():e.trickJudgmentModifiers();step=Step.MODIFY;}
      case MODIFY->{
        if(cursor>=modifiers.size()){e.trickFinalizeJudgment(owner,effective);step=Step.EFFECT;continue;}
        int actor=modifiers.get(cursor);var who=e.state().players().get(actor);
        if(!who.alive()||who.hp()<1||who.handCount()==0){cursor++;continue;}
        ask(e,PromptType.GUI_CAI_REPLACE,actor,"guicai",revision);return;
      }
      case EFFECT->{if(e.trickTianduApplies(owner,cause,effective)){step=Step.TIANDU;ask(e,PromptType.SKILL_INVOKE,owner,"tiandu",revision);return;}finish(e,false);return;}
      case TIANDU->throw new IllegalStateException("tiandu lost its pending request");
      case DONE->{return;}
    }
    throw new IllegalStateException("judgment did not yield");
  }
  PromptRequest request(SgsGameEngine e){
    if(pending==null)throw new IllegalStateException("judgment has no pending request");
    var actor=e.state().players().get(pending.askerSeat());return new PromptRequest(pending.id(),pending.type(),actor,e.state().players().get(owner),List.of(),pending.type()==PromptType.GUI_CAI_REPLACE?actor.handView():List.of(),pending.deadlineMillis(),pending.skillId());
  }
  boolean choose(SgsGameEngine e,Optional<SkillChoice> answer){
    if(pending==null||step!=Step.MODIFY)return false;int actor=modifiers.get(cursor);
    if(e.continuationNow()>pending.deadlineMillis())answer=Optional.empty();
    if(answer.isPresent()){var choice=answer.get();if(choice.targetSeat()!=-1 || e.state().players().get(actor).handView().stream().noneMatch(c->c.instanceId()==choice.cardId()))return false;}
    effective=e.trickModifyJudgment(owner,actor,cause,effective,answer);pending=null;cursor++;return true;
  }
  boolean invoke(SgsGameEngine e,boolean accepted){if(pending==null||step!=Step.TIANDU)return false;boolean answer=accepted&&e.continuationNow()<=pending.deadlineMillis();pending=null;finish(e,answer);return true;}
  private void finish(SgsGameEngine e,boolean accepted){red=e.trickCompleteJudgment(owner,cause,effective,accepted);e.trickFinishJudgment(revealed,owner);step=Step.DONE;}
}
