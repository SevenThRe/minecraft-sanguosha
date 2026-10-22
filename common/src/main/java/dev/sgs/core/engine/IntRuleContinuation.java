package dev.sgs.core.engine;

import dev.sgs.core.engine.skill.*;
import java.util.*;

/** Ordered rule fold with data-owned optional choices and a judgment child. No blocked responder. */
public final class IntRuleContinuation {
  public enum Step { FOLD, INVOKE, JUDGMENT, DONE }
  public record Snapshot(RuleId rule,int cursor,int value,Step step,AwaitableIntModifier.Request effect,
      JudgmentContinuation.Snapshot judgment,AttackContinuation.PendingSnapshot pending,long revision) {}
  private final RuleId rule;private int cursor,value;private Step step=Step.FOLD;
  private AwaitableIntModifier.Request effect;private JudgmentContinuation judgment;
  private AttackContinuation.PendingSnapshot pending;private long revision;
  IntRuleContinuation(RuleId rule,int base){this.rule=Objects.requireNonNull(rule);value=base;}
  IntRuleContinuation(Snapshot s,long captured,long now){rule=s.rule();cursor=s.cursor();value=s.value();step=s.step();effect=s.effect();revision=s.revision();
    if(s.judgment()!=null)judgment=new JudgmentContinuation(s.judgment(),captured,now);
    var p=s.pending();pending=p==null?null:new AttackContinuation.PendingSnapshot(p.id(),p.type(),p.askerSeat(),p.subjectSeat(),p.allowedTypes(),DecisionBoundary.restoredDeadline(p.deadlineMillis(),captured,now),p.stateRevision(),p.skillId());
  }
  public Snapshot snapshot(){return new Snapshot(rule,cursor,value,step,effect,judgment==null?null:judgment.snapshot(),pending,revision);}
  public static void validate(Snapshot s,AttackContinuation.PendingSnapshot outer,int target,int seats){
    var p=s.pending();var effect=s.effect();
    if(s.rule()!=RuleId.SHA_DODGE_COUNT || s.cursor()<0 || s.cursor()>256 || s.value()<0 || s.value()>2 || s.revision()<1 || s.revision()==Long.MAX_VALUE
        || effect==null || effect.owner()>=seats || effect.value()<0 || effect.value()>2 || p==null || outer==null || p.id()<1 || p.deadlineMillis()<0
        || p.askerSeat()<0 || p.askerSeat()>=seats || p.subjectSeat()<0 || p.subjectSeat()>=seats || !p.allowedTypes().isEmpty()
        || !outer.allowedTypes().isEmpty() || p.stateRevision()!=s.revision() || p.id()!=outer.id() || p.type()!=outer.type() || p.askerSeat()!=outer.askerSeat() || p.subjectSeat()!=outer.subjectSeat()
        || p.deadlineMillis()!=outer.deadlineMillis() || !p.skillId().equals(outer.skillId()))throw new IllegalArgumentException("invalid saved rule child");
    if(s.step()==Step.INVOKE){
      if(s.judgment()!=null || p.type()!=PromptType.SKILL_INVOKE || p.askerSeat()!=effect.owner() || p.subjectSeat()!=target || !p.skillId().equals(effect.skill()))throw new IllegalArgumentException("invalid rule invocation");
    }else if(s.step()==Step.JUDGMENT){
      var j=s.judgment();if(effect.effect()!=AwaitableIntModifier.Effect.INVOKE_RED_JUDGMENT_SET || j==null || j.owner()!=effect.owner() || j.cause()!=0
          || j.revealed()<1 || j.effective()<1 || j.cursor()<0 || j.cursor()>j.modifiers().size() || j.modifiers().size()>seats || new HashSet<>(j.modifiers()).size()!=j.modifiers().size() || !p.equals(j.pending())
          || !(j.step()==JudgmentContinuation.Step.MODIFY && p.type()==PromptType.GUI_CAI_REPLACE && j.cursor()<j.modifiers().size() && p.askerSeat()==j.modifiers().get(j.cursor()) && p.skillId().equals("guicai")
              || j.step()==JudgmentContinuation.Step.TIANDU && p.type()==PromptType.SKILL_INVOKE && p.askerSeat()==j.owner() && p.skillId().equals("tiandu"))
          || p.subjectSeat()!=j.owner())throw new IllegalArgumentException("invalid rule judgment child");
      for(int owner:j.modifiers())if(owner<0 || owner>=seats)throw new IllegalArgumentException("invalid rule judgment actor");
    }else throw new IllegalArgumentException("rule checkpoint is not yielded");
  }
  boolean done(){return step==Step.DONE;}int value(){if(!done())throw new IllegalStateException("unfinished rule query");return value;}
  AttackContinuation.PendingSnapshot pending(){return pending;}
  void advance(SgsGameEngine e,SkillContext context){
    if(pending!=null || done())return;
    for(int budget=0;budget<256;budget++){
      if(step==Step.FOLD){var next=e.continuationAdvanceInt(rule,context,cursor,value);cursor=next.cursor();value=next.value();effect=next.request();
        if(effect==null){step=Step.DONE;return;}
        step=Step.INVOKE;revision++;pending=new AttackContinuation.PendingSnapshot(e.nextPromptId(),PromptType.SKILL_INVOKE,effect.owner(),context.target().seat(),List.of(),e.responseDeadlineMillis(),revision,effect.skill());return;
      }
      if(step==Step.JUDGMENT){judgment.advance(e,++revision);pending=judgment.pending();if(pending!=null)return;
        if(!judgment.done())throw new IllegalStateException("rule judgment failed to yield");if(judgment.red())value=effect.value();judgment=null;effect=null;cursor++;step=Step.FOLD;continue;
      }
      throw new IllegalStateException("rule query lost its decision");
    }
    throw new IllegalStateException("rule query exceeded binding budget");
  }
  PromptRequest request(SgsGameEngine e){
    if(pending==null)throw new IllegalStateException("rule query has no pending decision");if(judgment!=null)return judgment.request(e);
    return new PromptRequest(pending.id(),pending.type(),e.state().players().get(pending.askerSeat()),e.state().players().get(pending.subjectSeat()),List.of(),List.of(),pending.deadlineMillis(),pending.skillId());
  }
  boolean invoke(SgsGameEngine e,boolean accepted){
    if(pending==null || pending.type()!=PromptType.SKILL_INVOKE)return false;boolean yes=accepted&&e.continuationNow()<=pending.deadlineMillis();
    if(judgment!=null){if(!judgment.invoke(e,yes))return false;pending=null;revision++;return true;}
    if(step!=Step.INVOKE)return false;pending=null;revision++;
    if(!yes){effect=null;cursor++;step=Step.FOLD;return true;}
    e.state().publishSkillActivation(effect.owner(),effect.skill());
    switch(effect.effect()){
      case INVOKE_SET->{value=effect.value();effect=null;cursor++;step=Step.FOLD;}
      case INVOKE_RED_JUDGMENT_SET->{judgment=new JudgmentContinuation(effect.owner(),0);step=Step.JUDGMENT;}
    }
    return true;
  }
  boolean choose(SgsGameEngine e,Optional<SkillChoice> answer){if(judgment==null || !judgment.choose(e,answer))return false;pending=null;revision++;return true;}
}
