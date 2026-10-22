package dev.sgs.core.engine;

import dev.sgs.core.card.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Suit decision -> one random reveal in hand -> complete cardless damage -> same physical gift. */
public final class RevealedGiftContinuation {
  public enum Step { SUIT, REVEAL, DAMAGE, GIFT, DONE }
  public record Snapshot(int actor,int target,String skill,Step step,Suit guess,long selected,List<Long> hand,
      TrickContinuation.Pending pending,DamageProgramContinuation.Snapshot damage){
    public Snapshot{hand=List.copyOf(hand);}
  }
  private final int actor,target;private final String skill;
  private Step step=Step.SUIT;private Suit guess;private long selected;private List<Long> hand=List.of();
  private TrickContinuation.Pending pending;private DamageProgramContinuation damage;
  RevealedGiftContinuation(int actor,int target,String skill){this.actor=actor;this.target=target;this.skill=skill;}
  RevealedGiftContinuation(Snapshot s,long captured,long now){
    validate(s);actor=s.actor();target=s.target();skill=s.skill();step=s.step();guess=s.guess();selected=s.selected();hand=s.hand();
    var p=s.pending();if(p!=null)pending=new TrickContinuation.Pending(p.id(),p.type(),p.asker(),p.subject(),DecisionBoundary.restoredDeadline(p.deadline(),captured,now),p.choices(),p.hidden(),p.skill(),p.revision());
    if(s.damage()!=null)damage=new DamageProgramContinuation(s.damage(),captured,now);
  }
  public Snapshot snapshot(){return new Snapshot(actor,target,skill,step,guess,selected,hand,pending,damage==null?null:damage.snapshot());}
  public static void validate(Snapshot s){
    Objects.requireNonNull(s);Objects.requireNonNull(s.step());
    if(s.actor()<0||s.actor()>7||s.target()<0||s.target()>7||s.actor()==s.target()||s.skill()==null||!s.skill().matches("(?:[a-z][a-z0-9_]{1,31}:)?[a-z][a-z0-9_]{0,63}")
        ||s.selected()<0||s.hand().size()>160||new HashSet<>(s.hand()).size()!=s.hand().size()||s.hand().stream().anyMatch(id->id<1)
        ||s.guess()==Suit.NONE||(s.step()==Step.SUIT)!=(s.guess()==null)||(s.step()==Step.DAMAGE)!=(s.damage()!=null))throw new IllegalArgumentException("invalid revealed gift contract");
    if(s.step()==Step.SUIT){var p=s.pending();
      if(s.selected()!=0||!s.hand().isEmpty()||p==null||p.type()!=PromptType.FANJIAN_SUIT||p.asker()!=s.target()||p.subject()!=s.actor()
          ||!p.skill().equals(s.skill())||!p.choices().isEmpty()||p.hidden()||p.id()<1||p.revision()<1||p.deadline()<0)throw new IllegalArgumentException("invalid hidden suit decision");
    }else if(s.pending()!=null||s.step()!=Step.REVEAL&&(s.selected()<1||!s.hand().contains(s.selected())))throw new IllegalArgumentException("revealed entity or phase differs");
    if(s.damage()!=null){DamageProgramContinuation.validate(s.damage());if(!s.damage().ordered()||!s.damage().awaitRescueCosts())throw new IllegalArgumentException("revealed gift lost shared damage capabilities");}
  }
  boolean done(){return step==Step.DONE;}
  Optional<PromptRequest> prompt(SgsGameEngine e){
    if(pending!=null)return Optional.of(new PromptRequest(pending.id(),pending.type(),e.state().players().get(target),e.state().players().get(actor),List.of(),List.of(),pending.deadline(),skill));
    return damage==null?Optional.empty():damage.prompt(e);
  }
  void advanceOne(SgsGameEngine e,long revision,BooleanSupplier movementsPending){
    var state=e.state();
    switch(step){
      case SUIT->{if(pending==null)pending=new TrickContinuation.Pending(e.nextPromptId(),PromptType.FANJIAN_SUIT,target,actor,e.responseDeadlineMillis(),List.of(),false,skill,revision);}
      case REVEAL->{
        var chosen=RevealedGiftRules.reveal(state,state.players().get(actor),state.players().get(target),skill,guess);
        if(chosen==null){step=Step.DONE;return;}selected=chosen.card();hand=chosen.hand();
        if(RevealedGiftRules.physical(state,selected).suit()!=guess){damage=new DamageProgramContinuation(AttackContinuation.skillDamage(actor,target,0,null),true,true);step=Step.DAMAGE;}
        else step=Step.GIFT;
      }
      case DAMAGE->{damage.advanceOne(e,revision,movementsPending);if(damage.done()){damage=null;step=Step.GIFT;}}
      case GIFT->{RevealedGiftRules.deliver(state,actor,target,selected,skill);step=Step.DONE;}
      case DONE->{}
    }
  }
  boolean chooseSuit(SgsGameEngine e,Optional<Suit> answer){
    if(step!=Step.SUIT||pending==null||answer.orElse(Suit.SPADE)==Suit.NONE)return false;
    guess=e.continuationNow()>pending.deadline()?Suit.SPADE:answer.orElse(Suit.SPADE);pending=null;step=Step.REVEAL;return true;
  }
  boolean choose(SgsGameEngine e,Optional<SkillChoice> answer){return damage!=null&&damage.choose(e,answer);}
  boolean invoke(SgsGameEngine e,boolean answer){return damage!=null&&damage.invoke(e,answer);}
  boolean respond(SgsGameEngine e,Optional<PlayDecision> answer){return damage!=null&&damage.respond(e,answer);}
  void validateAgainst(SgsGameEngine e,AttackContinuation.PendingSnapshot p,boolean movementPaused){
    validate(snapshot());var state=e.state();if(actor>=state.players().size()||target>=state.players().size())throw new IllegalArgumentException("revealed gift actor outside game");
    if(step==Step.SUIT){if(!state.players().get(actor).alive()||!state.players().get(target).alive()||state.players().get(actor).handCount()==0)throw new IllegalArgumentException("suit decision no longer available");}
    else{
      for(long id:hand)RevealedGiftRules.physical(state,id);
      if(damage!=null){
        if(RevealedGiftRules.physical(state,selected).suit()==guess)throw new IllegalArgumentException("matching reveal incorrectly caused damage");
        e.validateRevealedGiftDamage(damage.snapshot(),actor,target,p,movementPaused);
      }
    }
  }
}
