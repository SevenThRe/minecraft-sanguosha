package dev.sgs.core.persistence;

import dev.sgs.core.*;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.*;
import dev.sgs.core.game.GameState;
import java.util.*;

/** Versioned active parent and real children; no fictitious physical card represents its effect. */
public record ActiveSkillCheckpoint(int version,StateCheckpoint state,long promptSequence,int responseTimeoutSeconds,
    Map<String,Map<String,Long>> skillMemory,List<String> bindings,long skillSequence,
    List<GameState.PublicSkillActivation> activations,ActiveSkillContinuation.Snapshot continuation,long capturedAtMillis){
  public static final int VERSION=4;
  public static final String LEGACY_RULES_VERSION="standard-active-parent-v1";
  public static final String HP_LOSS_RULES_VERSION="standard-active-hp-loss-v2";
  public static final String SUPPLIED_RULES_VERSION="standard-active-supplied-attack-v3";
  public static final String RULES_VERSION="standard-active-revealed-gift-v4";
  public ActiveSkillCheckpoint {
    Objects.requireNonNull(state);Objects.requireNonNull(continuation);
    var memory=new TreeMap<String,Map<String,Long>>();skillMemory.forEach((key,value)->memory.put(key,Map.copyOf(value)));
    skillMemory=Collections.unmodifiableMap(memory);bindings=List.copyOf(bindings);activations=List.copyOf(activations);
  }
  public static AttackContinuation.PendingSnapshot pending(ActiveSkillContinuation.Snapshot c){
    if(c.movements().pending()!=null)return converted(c.movements().pending());
    if(c.selection()!=null)return converted(c.selection());
    if(c.payment()!=null&&c.payment().queue().pending()!=null)return converted(c.payment().queue().pending());
    if(c.response()!=null)return c.response().pending();
    if(c.revealedGift()!=null){var g=c.revealedGift();return g.pending()!=null?converted(g.pending()):damagePending(g.damage());}
    if(c.providedAttack()!=null){var s=c.providedAttack().strike();return s.attack()!=null?s.attack().pending():s.supply().pending();}
    if(c.hpLoss()!=null&&c.hpLoss().dying()!=null&&c.hpLoss().dying().pending()!=null)return converted(c.hpLoss().dying().pending());
    return damagePending(c.damage());
  }
  private static AttackContinuation.PendingSnapshot damagePending(DamageProgramContinuation.Snapshot d){
    if(d==null)return null;if(d.pending()!=null)return converted(d.pending());
    if(d.reaction()!=null&&d.reaction().pending()!=null)return d.reaction().pending();return d.damage().pending();
  }
  private static AttackContinuation.PendingSnapshot converted(TrickContinuation.Pending p){
    return new AttackContinuation.PendingSnapshot(p.id(),p.type(),p.asker(),p.subject(),List.of(),p.deadline(),p.revision(),p.skill());
  }
  private static void seat(int seat,int n){if(seat<0||seat>=n)throw new IllegalArgumentException("active seat outside roster");}
  public void validate(){
    state.validate();var c=continuation;var plan=c.plan();int n=state.players().size();
    if(version<1||version>VERSION||state.turn().status()!=GameStatus.RUNNING||state.turn().phase()!=Phase.PLAY
        ||plan.actor()!=state.turn().currentSeat()||plan.turn()!=state.turn().turnNumber()
        ||promptSequence<1||promptSequence==Long.MAX_VALUE||responseTimeoutSeconds<1||responseTimeoutSeconds>3600
        ||capturedAtMillis<0||skillSequence<0||skillSequence==Long.MAX_VALUE||skillMemory.size()>SkillMemoryLayout.MAX_RECORDS||bindings.size()>256||activations.size()>32
        ||c.revision()<1||c.revision()==Long.MAX_VALUE||c.published()||c.step()==ActiveSkillContinuation.Step.DONE)
      throw new IllegalArgumentException("invalid active checkpoint header");
    seat(plan.actor(),n);seat(plan.primaryTarget(),n);plan.candidates().forEach(s->seat(s,n));
    var ids=new HashSet<Long>();state.zones().forEach(z->z.cards().forEach(card->ids.add(card.id())));
    if(!ids.containsAll(plan.costs()))throw new IllegalArgumentException("missing active fee entity");
    skillMemory.forEach((key,value)->{
      SkillMemoryLayout.validate(key,value,n,false);
    });
    bindings.forEach(b->StateCheckpoint.text(b,256));long previous=0;
    for(var a:activations){if(a.sequence()<=previous||a.sequence()>skillSequence)throw new IllegalArgumentException("active activation order differs");
      seat(a.seat(),n);StateCheckpoint.text(a.skillId(),128);previous=a.sequence();}
    if(plan.effect()==dev.sgs.core.engine.skill.ActiveSkillPlan.Effect.REVEALED_GIFT){
      var g=c.revealedGift();
      if(version!=4||c.step()!=ActiveSkillContinuation.Step.REVEALED_GIFT||!c.accepted()||c.selectedSource()!=-1||c.duel().started()
          ||c.selection()!=null||c.payment()!=null||c.response()!=null||c.damage()!=null||c.hpLoss()!=null||c.providedAttack()!=null||g==null
          ||g.actor()!=plan.actor()||g.target()!=plan.primaryTarget()||!g.skill().equals(plan.skillId())
          ||!Set.of(RevealedGiftContinuation.Step.SUIT,RevealedGiftContinuation.Step.DAMAGE).contains(g.step()))throw new IllegalArgumentException("active revealed gift linkage differs");
      RevealedGiftContinuation.validate(g);
      if(g.step()==RevealedGiftContinuation.Step.SUIT&&g.pending().revision()!=c.revision())throw new IllegalArgumentException("suit decision revision differs");
      var entityIds=new HashSet<Long>();state.zones().forEach(z->z.cards().forEach(card->entityIds.add(card.id())));
      if(!entityIds.containsAll(g.hand())||g.selected()>0&&!entityIds.contains(g.selected()))throw new IllegalArgumentException("revealed gift entity missing");
      validateDepartureAndInput(c,DamageProgramContinuation.processingCosts(g.damage()),n);return;
    }
    if(c.revealedGift()!=null)throw new IllegalArgumentException("unexpected revealed gift child");
    if(plan.effect()==dev.sgs.core.engine.skill.ActiveSkillPlan.Effect.PROVIDED_ATTACK){
      var attack=c.providedAttack();
      if(version!=3||c.step()!=ActiveSkillContinuation.Step.PROVIDED_ATTACK||!c.accepted()||c.selectedSource()!=-1||c.duel().started()
          ||c.selection()!=null||c.payment()!=null||c.response()!=null||c.damage()!=null||c.hpLoss()!=null||attack==null)
        throw new IllegalArgumentException("active supplied attack child or phase differs");
      ActiveAttackContinuation.validate(attack);var strike=attack.strike();var supply=strike.supply();
      if(supply.user()!=plan.actor()||supply.target()!=plan.primaryTarget()||supply.providers().stream().anyMatch(s->s>=n))
        throw new IllegalArgumentException("active supplier role linkage differs");
      var a=strike.attack();
      if(a!=null){
        new ContinuationCheckpoint(a.version(),state,promptSequence,responseTimeoutSeconds,skillMemory,bindings,skillSequence,activations,a,capturedAtMillis).validate();
        var paid=supply.receipt().costs().stream().map(AttackSupplyContinuation.Cost::id).toList();
        var original=new ArrayList<Long>();
        if(a.parents().isEmpty()){original.add(a.cardId());original.addAll(a.extraCosts());}
        else original.addAll(a.parents().subList(0,a.parentSizes().getFirst()));
        if(!paid.equals(original))throw new IllegalArgumentException("active supplied physical root changed");
      }
      validateDepartureAndInput(c,StrikeFrames.processing(strike,state.restore()),n);return;
    }
    if(c.providedAttack()!=null)throw new IllegalArgumentException("unexpected supplied attack child");
    if(plan.effect()==dev.sgs.core.engine.skill.ActiveSkillPlan.Effect.LOSE_HP_DRAW){
      var h=c.hpLoss();
      if(version!=2||c.step()!=ActiveSkillContinuation.Step.HP_LOSS||!c.accepted()||c.selectedSource()!=-1||c.duel().started()
          ||c.selection()!=null||c.payment()!=null||c.response()!=null||c.damage()!=null||h==null||h.actor()!=plan.actor()
          ||h.amount()!=plan.hpLoss()||h.drawCount()!=plan.drawCount()||h.step()!=HpLossContinuation.Step.RESCUE
          ||h.dying()==null||h.dying().pending()==null||h.dying().step()==DyingContinuation.Step.DONE)
        throw new IllegalArgumentException("active HP-loss child or phase differs");
      var d=h.dying();seat(d.victim(),n);d.round().ring().forEach(s->seat(s,n));
      var processing=d.payment()==null?Set.<Long>of():Set.copyOf(d.payment().cards());
      validateDepartureAndInput(c,processing,n);return;
    }
    if(version!=1||c.hpLoss()!=null)throw new IllegalArgumentException("legacy active effect changed format");
    boolean selecting=c.step()==ActiveSkillContinuation.Step.SELECT;
    if(selecting!=(!c.accepted())||selecting!=(c.selection()!=null)
        ||(c.step()==ActiveSkillContinuation.Step.COST)!=(c.payment()!=null)
        ||(c.step()==ActiveSkillContinuation.Step.DAMAGE)!=(c.damage()!=null)
        ||c.response()!=null&&c.step()!=ActiveSkillContinuation.Step.DUEL)
      throw new IllegalArgumentException("active child/phase differs");
    if(selecting){var p=c.selection();
      if(c.selectedSource()!=-1||c.duel().started()||c.response()!=null||!c.movements().movements().isEmpty()
          ||p.type()!=plan.selection()||p.asker()!=plan.actor()||p.subject()!=plan.primaryTarget()||!p.skill().equals(plan.skillId())
          ||p.revision()!=c.revision()||!p.choices().isEmpty()||!p.hidden())throw new IllegalArgumentException("invalid active selection");
    }else{
      seat(c.selectedSource(),n);if(!plan.candidates().contains(c.selectedSource()))throw new IllegalArgumentException("unselected active source");
      if(c.step()==ActiveSkillContinuation.Step.COST){var fee=c.payment();
        if(c.duel().started()||c.response()!=null||fee.actor()!=plan.actor()||fee.owner()!=plan.actor()||!fee.cards().equals(plan.costs())
            ||fee.step()!=DiscardCostContinuation.Step.MOVEMENTS||fee.queue().pending()==null)throw new IllegalArgumentException("active payment differs");
      }else{
        var duel=c.duel();if(!duel.started()||!Set.of(duel.responder(),duel.other()).equals(Set.of(c.selectedSource(),plan.primaryTarget()))
            ||duel.required()<1||duel.required()>8||duel.paid()>=duel.required())throw new IllegalArgumentException("invalid active duel exchange");
        if(c.step()==ActiveSkillContinuation.Step.DUEL){var r=c.response();
          if(r==null||r.type()!=PromptType.DUEL_SHA||r.actor()!=duel.responder()||r.subject()!=duel.other()
              ||r.pending()==null||r.success()||r.supplied()||r.ownCardPaid()||r.judgment()!=null||r.judgmentCard()!=0
              ||r.revision()<1||r.pending().stateRevision()!=r.revision()||r.cursor()<0||r.cursor()>r.providers().size()
              ||new HashSet<>(r.providers()).size()!=r.providers().size()||r.own()==null
              ||!Set.of(TrickResponseContinuation.Step.LORD,TrickResponseContinuation.Step.PROVIDER_CARD,TrickResponseContinuation.Step.OWN_CARD,TrickResponseContinuation.Step.COST).contains(r.step())
              ||(r.step()==TrickResponseContinuation.Step.COST)!=(r.cost()!=null))throw new IllegalArgumentException("invalid active response");
          r.providers().forEach(s->seat(s,n));
          var own=r.own();if(own.askerSeat()!=r.actor()||own.subjectSeat()!=r.subject()||own.type()!=PromptType.DUEL_SHA
              ||own.id()<1||own.id()>promptSequence||own.deadlineMillis()<0||own.stateRevision()<1)throw new IllegalArgumentException("invalid allocated duel response");
        }
      }
    }
    if(c.damage()!=null){DamageProgramContinuation.validate(c.damage());
      if(!c.damage().ordered()||!c.damage().awaitRescueCosts())throw new IllegalArgumentException("active damage lost shared capability");}
    var expectedProcessing=new HashSet<Long>();
    if(c.response()!=null&&c.response().cost()!=null)expectedProcessing.addAll(c.response().cost().cards());
    expectedProcessing.addAll(DamageProgramContinuation.processingCosts(c.damage()));
    validateDepartureAndInput(c,expectedProcessing,n);
  }
  private void validateDepartureAndInput(ActiveSkillContinuation.Snapshot c,Set<Long> expectedProcessing,int n){
    var actualProcessing=new HashSet<Long>();state.zones().stream().filter(z->z.location().equals(CardZone.PROCESSING)).forEach(z->z.cards().forEach(card->actualProcessing.add(card.id())));
    if(!expectedProcessing.equals(actualProcessing))throw new IllegalArgumentException("active processing has no real child owner");
    if(c.movements().pending()==null&&!c.movements().movements().isEmpty()
        ||(c.penaltyLogPending()||c.outcomePending())&&c.movements().pending()==null)throw new IllegalArgumentException("active departure has no return boundary");
    var p=pending(c);if(p==null||p.id()<1||p.id()>promptSequence||p.deadlineMillis()<0||p.deadlineMillis()-capturedAtMillis>responseTimeoutSeconds*1000L)throw new IllegalArgumentException("active has no valid bounded pending input");
    seat(p.askerSeat(),n);seat(p.subjectSeat(),n);
  }
}
