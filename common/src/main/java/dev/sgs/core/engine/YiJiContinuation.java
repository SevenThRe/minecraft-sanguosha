package dev.sgs.core.engine;

import dev.sgs.core.card.*;
import java.util.*;

/** One damage event: accepted points draw once; only those new physical cards may be assigned. */
public final class YiJiContinuation {
  public enum Step { POINT, INVOKE, GIVE, DONE }
  public record Snapshot(int owner,int amount,int point,Step step,List<Long> retained){public Snapshot{retained=List.copyOf(retained);}}
  private final int owner,amount;private final String skill;private int point;private Step step=Step.POINT;private final List<Long> retained=new ArrayList<>();
  YiJiContinuation(int owner,int amount){this(owner,amount,"yiji");}
  YiJiContinuation(int owner,int amount,String skill){this.owner=owner;this.amount=amount;this.skill=Objects.requireNonNull(skill);}
  YiJiContinuation(Snapshot s){this(s,"yiji");}
  YiJiContinuation(Snapshot s,String skill){this(s.owner(),s.amount(),skill);point=s.point();step=s.step();retained.addAll(s.retained());}
  public Snapshot snapshot(){return new Snapshot(owner,amount,point,step,retained);}
  boolean done(){return step==Step.DONE;}
  private void nextPoint(){retained.clear();point++;step=Step.POINT;}
  TrickContinuation.Pending advance(SgsGameEngine e,long revision){
    var who=e.state().players().get(owner);
    while(true){
      if(point>=amount || !who.alive() || who.hp()<1){step=Step.DONE;return null;}
      if(step==Step.POINT){step=Step.INVOKE;return new TrickContinuation.Pending(e.nextPromptId(),PromptType.SKILL_INVOKE,owner,owner,e.responseDeadlineMillis(),List.of(),false,skill,revision);}
      if(step==Step.GIVE){
        retained.removeIf(id->!CardZone.hand(owner).equals(e.state().cards().locate(id)));
        if(retained.isEmpty()){nextPoint();continue;}
        return new TrickContinuation.Pending(e.nextPromptId(),PromptType.YIJI_GIVE,owner,owner,e.responseDeadlineMillis(),retained,false,skill,revision);
      }
      throw new IllegalStateException("yiji invoke is missing its pending request");
    }
  }
  void invoke(SgsGameEngine e,boolean accepted){
    if(step!=Step.INVOKE)throw new IllegalStateException("yiji not awaiting invoke");
    if(!accepted){nextPoint();return;}
    retained.addAll(e.drawCards(e.state().players().get(owner),2).stream().map(CardInstance::instanceId).toList());
    e.state().publishSkillActivation(owner,skill);step=Step.GIVE;
  }
  boolean choose(SgsGameEngine e,Optional<SkillChoice> answer){
    if(step!=Step.GIVE)return false;
    if(answer.isEmpty()){nextPoint();return true;}
    var choice=answer.get();if(!retained.contains(choice.cardId()) || choice.targetSeat()<0 || choice.targetSeat()>=e.state().players().size())return false;
    var who=e.state().players().get(owner);var to=e.state().players().get(choice.targetSeat());
    var card=who.handView().stream().filter(c->c.instanceId()==choice.cardId()).findFirst().orElse(null);
    if(card==null || !to.alive())return false;
    retained.remove(choice.cardId());if(to!=who)e.transferCard(who,to,card);return true;
  }
}
