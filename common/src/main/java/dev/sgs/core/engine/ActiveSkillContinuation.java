package dev.sgs.core.engine;

import dev.sgs.core.GameStatus;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.skill.*;
import java.util.*;

/** Data-owned active invocation: selection -> atomic discard -> virtual effect -> caller return. */
public final class ActiveSkillContinuation {
  public enum Step { SELECT, COST, DUEL, DAMAGE, DONE, HP_LOSS, PROVIDED_ATTACK, REVEALED_GIFT }
  public record Snapshot(ActiveSkillPlan plan,Step step,int selectedSource,long revision,
      TrickContinuation.Pending selection,DiscardCostContinuation.Snapshot payment,DuelExchange duel,
      TrickResponseContinuation.Snapshot response,DamageProgramContinuation.Snapshot damage,
      CostMovementQueue.Snapshot movements,boolean accepted,boolean published,
      boolean penaltyLogPending,boolean outcomePending,HpLossContinuation.Snapshot hpLoss,ActiveAttackContinuation.Snapshot providedAttack,RevealedGiftContinuation.Snapshot revealedGift) {
    public Snapshot(ActiveSkillPlan plan,Step step,int selectedSource,long revision,TrickContinuation.Pending selection,
        DiscardCostContinuation.Snapshot payment,DuelExchange duel,TrickResponseContinuation.Snapshot response,
        DamageProgramContinuation.Snapshot damage,CostMovementQueue.Snapshot movements,boolean accepted,boolean published,
        boolean penaltyLogPending,boolean outcomePending,HpLossContinuation.Snapshot hpLoss,ActiveAttackContinuation.Snapshot providedAttack){
      this(plan,step,selectedSource,revision,selection,payment,duel,response,damage,movements,accepted,published,penaltyLogPending,outcomePending,hpLoss,providedAttack,null);
    }
    public Snapshot(ActiveSkillPlan plan,Step step,int selectedSource,long revision,TrickContinuation.Pending selection,
        DiscardCostContinuation.Snapshot payment,DuelExchange duel,TrickResponseContinuation.Snapshot response,
        DamageProgramContinuation.Snapshot damage,CostMovementQueue.Snapshot movements,boolean accepted,boolean published,
        boolean penaltyLogPending,boolean outcomePending,HpLossContinuation.Snapshot hpLoss){
      this(plan,step,selectedSource,revision,selection,payment,duel,response,damage,movements,accepted,published,penaltyLogPending,outcomePending,hpLoss,null);
    }
    public Snapshot(ActiveSkillPlan plan,Step step,int selectedSource,long revision,TrickContinuation.Pending selection,
        DiscardCostContinuation.Snapshot payment,DuelExchange duel,TrickResponseContinuation.Snapshot response,
        DamageProgramContinuation.Snapshot damage,CostMovementQueue.Snapshot movements,boolean accepted,boolean published,
        boolean penaltyLogPending,boolean outcomePending){
      this(plan,step,selectedSource,revision,selection,payment,duel,response,damage,movements,accepted,published,penaltyLogPending,outcomePending,null);
    }
  }

  private final ActiveSkillPlan plan;
  private Step step=Step.SELECT;private int selectedSource=-1;private long revision;
  private TrickContinuation.Pending selection;private DiscardCostContinuation payment;
  private DuelExchange duel=DuelExchange.inactive();private TrickResponseContinuation response;
  private DamageProgramContinuation damage;private final CostMovementQueue movements;
  private boolean accepted,published,penaltyLogPending,outcomePending;
  private HpLossContinuation hpLoss;
  private ActiveAttackContinuation providedAttack;
  private RevealedGiftContinuation revealedGift;

  ActiveSkillContinuation(ActiveSkillPlan plan){this.plan=Objects.requireNonNull(plan);movements=new CostMovementQueue();
    if(plan.effect()==ActiveSkillPlan.Effect.LOSE_HP_DRAW)step=Step.HP_LOSS;
    if(plan.effect()==ActiveSkillPlan.Effect.PROVIDED_ATTACK)step=Step.PROVIDED_ATTACK;
    if(plan.effect()==ActiveSkillPlan.Effect.REVEALED_GIFT)step=Step.REVEALED_GIFT;}
  ActiveSkillContinuation(Snapshot s,long captured,long now){
    plan=s.plan();step=s.step();selectedSource=s.selectedSource();revision=s.revision();
    var p=s.selection();selection=p==null?null:new TrickContinuation.Pending(p.id(),p.type(),p.asker(),p.subject(),
        DecisionBoundary.restoredDeadline(p.deadline(),captured,now),p.choices(),p.hidden(),p.skill(),p.revision());
    if(s.payment()!=null)payment=new DiscardCostContinuation(s.payment(),captured,now);
    duel=s.duel();if(s.response()!=null)response=new TrickResponseContinuation(s.response(),captured,now,false,true);
    if(s.damage()!=null)damage=new DamageProgramContinuation(s.damage(),captured,now);
    movements=new CostMovementQueue(s.movements(),captured,now);accepted=s.accepted();published=s.published();
    penaltyLogPending=s.penaltyLogPending();outcomePending=s.outcomePending();
    if(s.hpLoss()!=null)hpLoss=new HpLossContinuation(s.hpLoss(),captured,now);
    if(s.providedAttack()!=null)providedAttack=new ActiveAttackContinuation(s.providedAttack(),captured,now);
    if(s.revealedGift()!=null)revealedGift=new RevealedGiftContinuation(s.revealedGift(),captured,now);
  }
  public Snapshot snapshot(){return new Snapshot(plan,step,selectedSource,revision,selection,
      payment==null?null:payment.snapshot(),duel,response==null?null:response.snapshot(),
      damage==null?null:damage.snapshot(),movements.snapshot(),accepted,published,penaltyLogPending,outcomePending,hpLoss==null?null:hpLoss.snapshot(),providedAttack==null?null:providedAttack.snapshot(),revealedGift==null?null:revealedGift.snapshot());}
  long revision(){return revision;}
  boolean done(){return step==Step.DONE&&(!accepted||published);}
  GameCard card(){return switch(plan.effect()){case UNCOUNTERABLE_DUEL->new VirtualCard(CardType.JUEDOU,List.of());case LOSE_HP_DRAW,PROVIDED_ATTACK,REVEALED_GIFT->null;};}
  AttackContinuation attackProgram(){return providedAttack==null?null:providedAttack.attackProgram();}
  boolean strikeInput(){return providedAttack!=null;}
  void enqueue(SgsGameEngine e,CardMove move){movements.enqueue(e,move);}
  boolean movementsPending(){return !movements.snapshot().movements().isEmpty();}
  void deferLordPenaltyLog(){penaltyLogPending=true;}
  void deferOutcome(){outcomePending=true;}

  private PromptRequest request(SgsGameEngine e,TrickContinuation.Pending p,List<Integer> targets){
    return new PromptRequest(p.id(),p.type(),e.state().players().get(p.asker()),
        e.state().players().get(p.subject()),List.of(),List.of(),p.deadline(),p.skill(),targets,p.hidden());
  }
  Optional<PromptRequest> prompt(SgsGameEngine e){
    if(movements.pending()!=null)return Optional.of(request(e,movements.pending(),List.of()));
    if(selection!=null)return Optional.of(request(e,selection,plan.candidates()));
    if(payment!=null&&payment.pending()!=null)return Optional.of(request(e,payment.pending(),List.of()));
    if(response!=null)return response.prompt(e);
    if(hpLoss!=null)return hpLoss.prompt(e);
    if(providedAttack!=null)return providedAttack.prompt(e);
    if(revealedGift!=null)return revealedGift.prompt(e);
    return damage==null?Optional.empty():damage.prompt(e);
  }

  void advance(SgsGameEngine e){
    for(int budget=0;budget<10000;budget++) {
      if(!movements.advance(e))return;
      if(penaltyLogPending){e.state().log("lord accidentally killed a loyalist and discarded the whole hand");penaltyLogPending=false;}
      if(outcomePending){e.continuationFinishOutcome();outcomePending=false;}
      switch(step) {
        case REVEALED_GIFT->{
          if(!accepted){e.plannedSkill(plan).commitUse(e,plan);accepted=true;revision++;revealedGift=new RevealedGiftContinuation(plan.actor(),plan.primaryTarget(),plan.skillId());}
          revealedGift.advanceOne(e,revision,()->!movements.snapshot().movements().isEmpty());
          if(revealedGift.done()){revealedGift=null;step=Step.DONE;}
          else if(revealedGift.prompt(e).isPresent())return;
        }
        case PROVIDED_ATTACK->{
          if(!accepted){var skill=e.plannedSkill(plan);skill.commitUse(e,plan);accepted=true;revision++;
            providedAttack=new ActiveAttackContinuation(e,plan.actor(),plan.primaryTarget());}
          providedAttack.advance(e);
          if(providedAttack.done()){providedAttack=null;step=Step.DONE;}
          else if(providedAttack.prompt(e).isPresent())return;
        }
        case HP_LOSS->{
          if(!accepted){
            var skill=e.plannedSkill(plan);if(!skill.stillPrepared(e,plan))throw new IllegalStateException("active HP-loss preparation changed");
            skill.commitUse(e,plan);accepted=true;revision++;hpLoss=new HpLossContinuation(plan.actor(),plan.hpLoss(),plan.drawCount());
          }
          hpLoss.advanceOne(e);
          if(hpLoss.done()){hpLoss=null;step=Step.DONE;}
          else if(hpLoss.prompt(e).isPresent())return;
        }
        case SELECT->{
          if(selection==null){revision++;selection=new TrickContinuation.Pending(e.nextPromptId(),plan.selection(),
              plan.actor(),plan.primaryTarget(),e.responseDeadlineMillis(),List.of(),true,plan.skillId(),revision);}
          return;
        }
        case COST->{
          payment.advance(e);if(!payment.done())return;payment=null;
          duel=DuelExchange.start(selectedSource,plan.primaryTarget());step=Step.DUEL;
        }
        case DUEL->{
          var actor=e.state().players().get(duel.responder());var other=e.state().players().get(duel.other());
          if(response==null){
            if(e.state().status()!=GameStatus.RUNNING||!actor.alive()||!other.alive()){step=Step.DONE;continue;}
            if(duel.needsRequirement())duel=duel.requiring(e.trickDuelRequired(other,actor,card()));
            response=TrickResponseContinuation.forSkillDuel(duel.responder(),duel.other());
          }
          response.advance(e);if(!response.done())return;
          if(response.success())duel=duel.committed();
          else{
            e.state().log("seat {} lost the duel against seat {}",duel.responder(),duel.other());
            damage=new DamageProgramContinuation(AttackContinuation.damage(duel.other(),duel.responder(),0,CardType.JUEDOU,1),true,true);
            step=Step.DAMAGE;
          }
          response=null;
        }
        case DAMAGE->{
          damage.advanceOne(e,revision,()->!movements.snapshot().movements().isEmpty());
          if(!movements.snapshot().movements().isEmpty())continue;
          if(damage.done()){damage=null;step=Step.DONE;}
          else if(damage.prompt(e).isPresent())return;
        }
        case DONE->{
          if(accepted&&!published){e.plannedSkill(plan).effectCompleted(e,plan);
            if(plan.effect()!=ActiveSkillPlan.Effect.PROVIDED_ATTACK)e.state().publishSkillActivation(plan.actor(),plan.skillId());published=true;}
          return;
        }
      }
    }
    throw new IllegalStateException("active skill failed to yield");
  }

  boolean choose(SgsGameEngine e,Optional<SkillChoice> choice){
    if(selection!=null){
      if(e.continuationNow()>selection.deadline())choice=Optional.empty();
      if(choice.isEmpty()){selection=null;step=Step.DONE;revision++;return true;}
      var skill=e.plannedSkill(plan);
      if(!plan.allows(choice.get())||!skill.stillPrepared(e,plan))return false;
      var source=e.state().players().get(choice.get().targetSeat());var target=e.state().players().get(plan.primaryTarget());
      if(!e.canSkillDuel(source,target))return false;
      var costs=plan.ownedCosts(e).orElseThrow();
      skill.commitUse(e,plan);accepted=true;selectedSource=source.seat();
      payment=new DiscardCostContinuation(plan.actor(),costs);selection=null;step=Step.COST;revision++;return true;
    }
    boolean consumed=revealedGift!=null?revealedGift.choose(e,choice):providedAttack!=null?providedAttack.choose(e,choice):response!=null?response.choose(e,choice):damage!=null&&damage.choose(e,choice);
    if(consumed)revision++;return consumed;
  }
  boolean invoke(SgsGameEngine e,boolean answer){
    boolean consumed=movements.pending()!=null?movements.invoke(e,answer):payment!=null?payment.invoke(e,answer)
        :revealedGift!=null?revealedGift.invoke(e,answer):providedAttack!=null?providedAttack.invoke(e,answer):response!=null?response.invoke(e,answer):hpLoss!=null?hpLoss.invoke(e,answer):damage!=null&&damage.invoke(e,answer);
    if(consumed)revision++;return consumed;
  }
  boolean respond(SgsGameEngine e,Optional<PlayDecision> answer){
    boolean consumed=revealedGift!=null?revealedGift.respond(e,answer):providedAttack!=null?providedAttack.respond(e,answer):response!=null?response.answer(e,answer):hpLoss!=null?hpLoss.respond(e,answer):damage!=null&&damage.respond(e,answer);
    if(consumed)revision++;return consumed;
  }
  boolean chooseSuit(SgsGameEngine e,Optional<Suit> answer){boolean consumed=revealedGift!=null&&revealedGift.chooseSuit(e,answer);if(consumed)revision++;return consumed;}
}
