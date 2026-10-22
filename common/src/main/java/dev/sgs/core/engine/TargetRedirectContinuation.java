package dev.sgs.core.engine;

import dev.sgs.core.card.*;
import java.util.*;

/** Select one real cost and replacement, finish its movement listeners, then return the target. */
public final class TargetRedirectContinuation {
  public enum Step { SELECT, PAYMENT, DONE }
  public record Snapshot(int source,int owner,String skill,List<Integer> originalTargets,List<Long> costs,List<Integer> targets,
      Step step,long chosenCost,int chosenTarget,DiscardCostContinuation.Snapshot payment,
      AttackContinuation.PendingSnapshot pending,long revision,boolean activated){
    public Snapshot{
      originalTargets=List.copyOf(originalTargets);costs=List.copyOf(costs);targets=List.copyOf(targets);
      new dev.sgs.core.engine.skill.AwaitableTargetRedirect.Request(owner,skill);
      if(source<0||source>7||source==owner||originalTargets.isEmpty()||originalTargets.size()>3||!originalTargets.contains(owner)
          ||originalTargets.contains(source)||new HashSet<>(originalTargets).size()!=originalTargets.size()
          ||originalTargets.stream().anyMatch(v->v<0||v>7)||costs.size()>160||new HashSet<>(costs).size()!=costs.size()
          ||costs.stream().anyMatch(v->v<1)||targets.size()>6||new HashSet<>(targets).size()!=targets.size()
          ||targets.stream().anyMatch(v->v<0||v>7||v==source)||!Collections.disjoint(targets,originalTargets)||step==null||revision<0||revision==Long.MAX_VALUE)
        throw new IllegalArgumentException("invalid target redirect shape");
      if(step==Step.SELECT&&(chosenCost!=0||chosenTarget!=-1||payment!=null||activated)
          ||step==Step.PAYMENT&&(chosenCost<1||!costs.contains(chosenCost)||!targets.contains(chosenTarget)||payment==null||activated)
          ||step==Step.DONE&&(pending!=null||payment!=null||(chosenCost==0)!=(chosenTarget==-1)||activated!=(chosenCost>0))
          ||chosenCost>0&&(!costs.contains(chosenCost)||!targets.contains(chosenTarget)))throw new IllegalArgumentException("invalid redirect return shape");
    }
  }
  private final int source,owner;private final String skill;private final List<Integer> originalTargets,targets;private final List<Long> costs;
  private Step step=Step.SELECT;private long chosenCost,revision;private int chosenTarget=-1;private boolean activated;
  private DiscardCostContinuation payment;private AttackContinuation.PendingSnapshot pending;
  TargetRedirectContinuation(SgsGameEngine e,int source,int owner,String skill,GameCard card,List<Integer> group){
    this.source=source;this.owner=owner;this.skill=skill;originalTargets=List.copyOf(group);
    var player=e.state().players().get(owner);var offered=AttackRedirectRules.costs(player);costs=offered.stream().map(CardInstance::instanceId).toList();
    targets=AttackRedirectRules.targets(e,e.state().players().get(source),player,card,offered,group);snapshot();
  }
  public TargetRedirectContinuation(Snapshot s,long captured,long now){
    source=s.source();owner=s.owner();skill=s.skill();originalTargets=s.originalTargets();costs=s.costs();targets=s.targets();step=s.step();
    chosenCost=s.chosenCost();chosenTarget=s.chosenTarget();revision=s.revision();activated=s.activated();
    if(s.payment()!=null)payment=new DiscardCostContinuation(s.payment(),captured,now);
    var p=s.pending();pending=p==null?null:new AttackContinuation.PendingSnapshot(p.id(),p.type(),p.askerSeat(),p.subjectSeat(),p.allowedTypes(),DecisionBoundary.restoredDeadline(p.deadlineMillis(),captured,now),p.stateRevision(),p.skillId());
  }
  public Snapshot snapshot(){return new Snapshot(source,owner,skill,originalTargets,costs,targets,step,chosenCost,chosenTarget,payment==null?null:payment.snapshot(),pending,revision,activated);}
  boolean done(){return step==Step.DONE;}int target(){if(!done())throw new IllegalStateException("redirect has not returned");return activated?chosenTarget:owner;}
  AttackContinuation.PendingSnapshot pending(){return pending;}
  PromptRequest request(SgsGameEngine e){
    if(pending==null)throw new IllegalStateException("redirect has no decision");
    var choices=step==Step.SELECT?AttackRedirectRules.costs(e.state().players().get(owner)).stream().filter(c->costs.contains(c.instanceId())).toList():List.<CardInstance>of();
    return new PromptRequest(pending.id(),pending.type(),e.state().players().get(pending.askerSeat()),e.state().players().get(pending.subjectSeat()),List.of(),choices,pending.deadlineMillis(),pending.skillId(),step==Step.SELECT?targets:List.of(),false);
  }
  void advance(SgsGameEngine e){
    if(done()||pending!=null)return;
    if(step==Step.SELECT){
      if(targets.isEmpty()){finish(e,false);return;}
      revision++;pending=new AttackContinuation.PendingSnapshot(e.nextPromptId(),PromptType.LIULI_TARGET,owner,source,List.of(),e.responseDeadlineMillis(),revision,skill);return;
    }
    payment.advance(e);
    if(payment.done()){payment=null;finish(e,e.state().players().get(chosenTarget).alive());return;}
    var p=payment.pending();if(p==null)throw new IllegalStateException("redirect payment failed to yield");
    revision++;pending=new AttackContinuation.PendingSnapshot(p.id(),p.type(),p.asker(),p.subject(),List.of(),p.deadline(),revision,p.skill());
  }
  private void finish(SgsGameEngine e,boolean yes){
    activated=yes;if(!yes){chosenCost=0;chosenTarget=-1;}step=Step.DONE;pending=null;revision++;
    e.continuationRedirectCompleted(owner,skill,yes);
  }
  boolean choose(SgsGameEngine e,GameCard attack,Optional<SkillChoice> answer){
    if(step!=Step.SELECT||pending==null)return false;
    if(e.continuationNow()>pending.deadlineMillis())answer=Optional.empty();
    if(answer.isEmpty()){pending=null;finish(e,false);return true;}
    var selected=AttackRedirectRules.payment(e,e.state().players().get(source),e.state().players().get(owner),attack,answer.get(),costs,targets,originalTargets);
    if(selected.isEmpty())return false;
    chosenCost=selected.get().instanceId();chosenTarget=answer.get().targetSeat();payment=new DiscardCostContinuation(owner,List.of(selected.get()));
    pending=null;step=Step.PAYMENT;revision++;return true;
  }
  boolean invoke(SgsGameEngine e,boolean yes){
    if(step!=Step.PAYMENT||pending==null||!payment.invoke(e,yes&&e.continuationNow()<=pending.deadlineMillis()))return false;
    pending=null;revision++;return true;
  }
  void validate(SgsGameEngine e,GameCard attack){
    if(source>=e.state().players().size()||owner>=e.state().players().size()||!e.continuationRedirectBound(owner,skill))throw new IllegalArgumentException("redirect binding changed");
    if(step==Step.DONE)return;
    if(pending==null||pending.stateRevision()!=revision||pending.id()<1||pending.deadlineMillis()<0||!pending.allowedTypes().isEmpty())throw new IllegalArgumentException("redirect has no saved decision");
    if(step==Step.SELECT){
      var player=e.state().players().get(owner);var available=AttackRedirectRules.costs(player);
      if(pending.type()!=PromptType.LIULI_TARGET||pending.askerSeat()!=owner||pending.subjectSeat()!=source||!pending.skillId().equals(skill)
          ||!available.stream().map(CardInstance::instanceId).toList().equals(costs)
          ||!AttackRedirectRules.targets(e,e.state().players().get(source),player,attack,available,originalTargets).equals(targets))throw new IllegalArgumentException("redirect offer changed");
    }else{
      payment.validate(e);var p=payment.pending();var paid=payment.snapshot();
      if(paid.actor()!=owner||paid.owner()!=owner||!paid.cards().equals(List.of(chosenCost))||pending.id()!=p.id()||pending.askerSeat()!=p.asker()||pending.subjectSeat()!=p.subject()
          ||pending.type()!=p.type()||pending.deadlineMillis()!=p.deadline()||!pending.skillId().equals(p.skill()))throw new IllegalArgumentException("redirect payment differs");
    }
  }
}
