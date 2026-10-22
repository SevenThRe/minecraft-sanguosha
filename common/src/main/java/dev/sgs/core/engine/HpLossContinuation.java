package dev.sgs.core.engine;

import java.util.*;

/** HP loss -> real dying/rescue -> HP-loss notification -> survivor draw. No damage timings. */
public final class HpLossContinuation {
  public enum Step { START, RESCUE, AFTER, DRAW, DONE }
  public record Snapshot(int actor,int amount,int drawCount,Step step,int hpBefore,DyingContinuation.Snapshot dying) {
    public Snapshot {
      Objects.requireNonNull(step);
      if(actor<0||actor>7||amount<1||amount>1_000_000||drawCount<0||drawCount>160
          ||(step==Step.RESCUE)!=(dying!=null)||dying!=null&&(dying.victim()!=actor||dying.source()!=-1))
        throw new IllegalArgumentException("invalid HP loss program");
    }
  }
  private final int actor,amount,drawCount;private Step step=Step.START;private int hpBefore;private DyingContinuation dying;
  HpLossContinuation(int actor,int amount,int drawCount){this.actor=actor;this.amount=amount;this.drawCount=drawCount;}
  HpLossContinuation(Snapshot s,long captured,long now){
    actor=s.actor();amount=s.amount();drawCount=s.drawCount();step=s.step();hpBefore=s.hpBefore();
    if(s.dying()!=null)dying=new DyingContinuation(s.dying(),captured,now);
  }
  public Snapshot snapshot(){return new Snapshot(actor,amount,drawCount,step,hpBefore,dying==null?null:dying.snapshot());}
  boolean done(){return step==Step.DONE;}
  Optional<PromptRequest> prompt(SgsGameEngine e){return dying==null?Optional.empty():dying.prompt(e);}
  void advanceOne(SgsGameEngine e){
    var target=e.state().players().get(actor);
    switch(step) {
      case START->{hpBefore=target.hp();e.continuationApplyHpLoss(target,amount);
        if(target.hp()<1&&target.alive()){dying=new DyingContinuation(actor,-1);step=Step.RESCUE;}else step=Step.AFTER;}
      case RESCUE->{dying.advanceOne(e);if(dying.done()){dying=null;step=Step.AFTER;}}
      case AFTER->{e.continuationHpLossAfter(target,amount);step=Step.DRAW;}
      case DRAW->{if(target.alive())e.drawCards(target,drawCount);step=Step.DONE;}
      case DONE->{}
    }
  }
  boolean respond(SgsGameEngine e,Optional<PlayDecision> answer){return dying!=null&&dying.respond(e,answer);}
  boolean invoke(SgsGameEngine e,boolean accepted){return dying!=null&&dying.invoke(e,accepted);}
  void validate(SgsGameEngine e){
    snapshot();if(actor>=e.state().players().size()||dying==null)throw new IllegalArgumentException("HP loss has no awaiting rescue");
    var target=e.state().players().get(actor);
    if(hpBefore<1||hpBefore>target.maxHp()||target.hp()<hpBefore-amount)throw new IllegalArgumentException("HP loss baseline differs");
    dying.validate(e);
  }
}
