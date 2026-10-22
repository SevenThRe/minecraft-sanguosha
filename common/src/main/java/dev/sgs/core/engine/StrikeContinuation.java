package dev.sgs.core.engine;

import dev.sgs.core.card.*;
import java.util.*;

/** Paid attack supply and one returnable attack program. The enclosing action owns its own cost. */
public final class StrikeContinuation {
  public enum Step { SUPPLY, ATTACK, DONE, DECLINED }
  public record Snapshot(Step step,AttackSupplyContinuation.Snapshot supply,AttackContinuation.Snapshot attack){}
  private Step step=Step.SUPPLY;private final AttackSupplyContinuation supply;private AttackContinuation attack;
  StrikeContinuation(int user,int target){supply=new AttackSupplyContinuation(user,target,PromptType.JIEDAO_RESPONSE,true,true);}
  static StrikeContinuation activeProvided(int user,int target){return new StrikeContinuation(new AttackSupplyContinuation(user,target,PromptType.JIJIANG_SHA,false,false));}
  private StrikeContinuation(AttackSupplyContinuation supply){this.supply=supply;}
  StrikeContinuation(Snapshot s,long captured,long now){validate(s);step=s.step();supply=new AttackSupplyContinuation(s.supply(),captured,now);if(s.attack()!=null){attack=new AttackContinuation(s.attack());attack.restoreDeadline(captured,now);}}
  public Snapshot snapshot(){return new Snapshot(step,supply.snapshot(),attack==null?null:attack.snapshot());}
  public static void validate(Snapshot s){
    Objects.requireNonNull(s.step());AttackSupplyContinuation.validate(s.supply());
    boolean active=s.supply().ownType()==PromptType.JIJIANG_SHA;
    if((active?(s.supply().allowOwn()||s.supply().offerProviders()):(s.supply().ownType()!=PromptType.JIEDAO_RESPONSE||!s.supply().allowOwn()||!s.supply().offerProviders()))||(s.step()==Step.ATTACK)!=(s.attack()!=null)
        ||s.step()==Step.ATTACK&&s.supply().receipt()==null||s.step()==Step.DECLINED&&!Set.of(AttackSupplyContinuation.Step.DECLINED,AttackSupplyContinuation.Step.START).contains(s.supply().step())||s.step()==Step.DONE&&s.supply().receipt()==null)
      throw new IllegalArgumentException("invalid strike phase");
    if(s.attack()!=null&&((s.attack().version()!=14&&s.attack().version()!=21)||s.attack().sourceSeat()!=s.supply().user()||!s.attack().targets().equals(List.of(s.supply().target()))||s.attack().targetCursor()!=0||s.attack().baseDamage()<1||s.attack().baseDamage()>(active?2:1)))throw new IllegalArgumentException("strike actor or return context differs");
  }
  AttackContinuation attackProgram(){return attack;}
  boolean done(){return step==Step.DONE||step==Step.DECLINED;}
  boolean declined(){return step==Step.DECLINED;}
  Optional<PromptRequest> prompt(SgsGameEngine e){return attack!=null?attack.pending().map(p->attack.request(e)):supply.prompt(e);}
  void advance(SgsGameEngine e){
    if(done())return;
    if(step==Step.SUPPLY){
      var s=supply.snapshot();var user=e.state().players().get(s.user());var target=e.state().players().get(s.target());
      if(s.step()==AttackSupplyContinuation.Step.START&&(!target.alive()||!e.cardTargetAllowed(user,target,new VirtualCard(CardType.SHA,List.of())))){step=Step.DECLINED;return;}
      supply.advance(e);if(!supply.done())return;
      if(supply.snapshot().receipt()==null){step=Step.DECLINED;return;}
      if(e.trickMovementsPending())return;
      var card=supply.card(e);
      if(supply.supplied()&&!e.borrowedStrikeAllowed(user,target,card)){e.state().cards().finish(card,user.seat());step=Step.DONE;return;}
      int damage=s.ownType()==PromptType.JIJIANG_SHA?e.continuationCommitActiveStrike(user,card):1;
      attack=AttackContinuation.committed(user.seat(),target.seat(),card,damage);step=Step.ATTACK;
    }
    e.inAttackProgram(attack,()->{attack.advance(e);return null;});
    if(attack.done()){attack=null;step=Step.DONE;}
  }
  boolean invoke(SgsGameEngine e,boolean yes){var p=prompt(e).orElseThrow();if(attack==null)return supply.invoke(e,p.asker().id(),p.id(),supply.snapshot().revision(),yes);
    return e.inAttackProgram(attack,()->{attack.answer(e,Optional.empty(),yes);return true;});}
  boolean respond(SgsGameEngine e,Optional<PlayDecision> answer){var p=prompt(e).orElseThrow();if(attack==null)return supply.answer(e,p.asker().id(),p.id(),supply.snapshot().revision(),answer);
    return e.inAttackProgram(attack,()->{var canonical=answer.isEmpty()?answer:e.trickValidateResponse(p,answer.get());if(answer.isPresent()&&canonical.isEmpty())return false;attack.answer(e,canonical,false);return true;});}
  boolean choose(SgsGameEngine e,Optional<SkillChoice> choice){if(attack==null)return false;
    var p=attack.request(e);if(!attack.childChoice()&&!Set.of(PromptType.CARD_ZONE_SELECT,PromptType.WEAPON_DISCARD,PromptType.WEAPON_COST_FIRST,PromptType.WEAPON_COST_SECOND).contains(p.type()))return false;
    var answer=e.continuationNow()>p.deadlineMillis()?Optional.<SkillChoice>empty():choice;
    return e.inAttackProgram(attack,()->{if(attack.childChoice())return attack.chooseChild(e,answer);if(answer.isPresent()&&!e.strikeWeaponChoice(attack.request(e),answer.get()))return false;attack.chooseWeapon(e,answer);return true;});}
  void validateAgainst(SgsGameEngine e,Set<Long> retained){supply.validateAgainst(e);if(attack!=null)e.inAttackProgram(attack,()->{e.validateNestedAttack(attack,retained);return null;});}
}
