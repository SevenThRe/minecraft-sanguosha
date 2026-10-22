package dev.sgs.core.engine;

import dev.sgs.core.card.*;
import java.util.*;

/** Turn-use contract around the shared supplier/strike program; never pays the lord's hand. */
public final class ActiveAttackContinuation {
  public record Snapshot(StrikeContinuation.Snapshot strike,int shaBefore,boolean wineBefore){}
  private final StrikeContinuation strike;
  private final int shaBefore;
  private final boolean wineBefore;
  ActiveAttackContinuation(SgsGameEngine e,int user,int target){
    strike=StrikeContinuation.activeProvided(user,target);shaBefore=e.state().shaPlayedThisTurn();wineBefore=e.state().jiuBoostPending();
  }
  ActiveAttackContinuation(Snapshot s,long captured,long now){
    validate(s);strike=new StrikeContinuation(s.strike(),captured,now);shaBefore=s.shaBefore();wineBefore=s.wineBefore();
  }
  public Snapshot snapshot(){return new Snapshot(strike.snapshot(),shaBefore,wineBefore);}
  public static void validate(Snapshot s){
    Objects.requireNonNull(s);StrikeContinuation.validate(s.strike());
    if(s.shaBefore()<0||s.shaBefore()==Integer.MAX_VALUE||s.strike().supply().ownType()!=PromptType.JIJIANG_SHA)
      throw new IllegalArgumentException("invalid active attack contract");
    var a=s.strike().attack();
    if(a!=null&&a.baseDamage()!=(a.parents().isEmpty()?(s.wineBefore()?2:1):1))throw new IllegalArgumentException("active attack wine linkage differs");
  }
  AttackContinuation attackProgram(){return strike.attackProgram();}
  boolean done(){return strike.done();}
  Optional<PromptRequest> prompt(SgsGameEngine e){return strike.prompt(e);}
  void advance(SgsGameEngine e){strike.advance(e);}
  boolean invoke(SgsGameEngine e,boolean answer){return strike.invoke(e,answer);}
  boolean respond(SgsGameEngine e,Optional<PlayDecision> answer){return strike.respond(e,answer);}
  boolean choose(SgsGameEngine e,Optional<SkillChoice> answer){return strike.choose(e,answer);}
  void validateAgainst(SgsGameEngine e){
    validate(snapshot());var s=strike.snapshot();
    int expected=shaBefore+(s.step()==StrikeContinuation.Step.ATTACK||s.step()==StrikeContinuation.Step.DONE?1:0);
    if(s.attack()!=null)expected+=s.attack().parentSizes().size();
    boolean expectedWine=s.step()==StrikeContinuation.Step.SUPPLY||s.step()==StrikeContinuation.Step.DECLINED?wineBefore:false;
    if(e.state().shaPlayedThisTurn()!=expected||e.state().jiuBoostPending()!=expectedWine)throw new IllegalArgumentException("active attack quota or wine changed");
    strike.validateAgainst(e,Set.of());
  }
}
