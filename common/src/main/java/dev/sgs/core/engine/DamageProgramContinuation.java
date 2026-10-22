package dev.sgs.core.engine;

import dev.sgs.core.card.*;
import dev.sgs.core.engine.skill.AwaitableDamageEffect;
import dev.sgs.core.engine.skill.ListenerProgram;
import java.util.*;

/** Shared damage/rescue and after-damage bodies, including bounded calls which return after retaliation. */
public final class DamageProgramContinuation {
  public enum Step { DAMAGE, DISTRIBUTE, TAKE, TAKE_FINISH, REACTION, DONE, RESCUE_COST }
  public record RescueCostSnapshot(CardUseCostContinuation.Snapshot program,long inputId,long inputRevision,int hpBeforeRecovery,int shaBefore,boolean wineBefore){
    public RescueCostSnapshot{Objects.requireNonNull(program);if(inputId<1||inputRevision<0||inputRevision==Long.MAX_VALUE||shaBefore<0)throw new IllegalArgumentException("invalid accepted rescue input");}
  }
  public record ReturnSnapshot(AttackContinuation.Snapshot damage,GangLieContinuation.Snapshot reaction,AwaitableDamageEffect.Request effect,ListenerProgram.Snapshot listeners) {
    public ReturnSnapshot(AttackContinuation.Snapshot damage,GangLieContinuation.Snapshot reaction,AwaitableDamageEffect.Request effect){this(damage,reaction,effect,null);}
  }
  private record ReturnFrame(AttackContinuation damage,GangLieContinuation reaction,AwaitableDamageEffect.Request effect,ListenerProgram listeners) {}
  public record Snapshot(Step step,AttackContinuation.Snapshot damage,YiJiContinuation.Snapshot distribution,
      GangLieContinuation.Snapshot reaction,AwaitableDamageEffect.Request effect,List<ReturnSnapshot> parents,
      TrickContinuation.Pending pending,boolean taken,ListenerProgram.Snapshot listeners,boolean ordered,boolean awaitRescueCosts,RescueCostSnapshot rescueCost){
    public Snapshot(Step step,AttackContinuation.Snapshot damage,YiJiContinuation.Snapshot distribution,GangLieContinuation.Snapshot reaction,AwaitableDamageEffect.Request effect,List<ReturnSnapshot> parents,TrickContinuation.Pending pending,boolean taken,ListenerProgram.Snapshot listeners,boolean ordered){this(step,damage,distribution,reaction,effect,parents,pending,taken,listeners,ordered,false,null);}
    public Snapshot(Step step,AttackContinuation.Snapshot damage,YiJiContinuation.Snapshot distribution,GangLieContinuation.Snapshot reaction,AwaitableDamageEffect.Request effect,List<ReturnSnapshot> parents,TrickContinuation.Pending pending,boolean taken){this(step,damage,distribution,reaction,effect,parents,pending,taken,null,false);}
    public Snapshot{parents=List.copyOf(parents);Objects.requireNonNull(step);Objects.requireNonNull(damage);if(parents.size()>64)throw new IllegalArgumentException("too many damage returns");}
  }
  private Step step=Step.DAMAGE;private AttackContinuation damage;private YiJiContinuation distribution;private GangLieContinuation reaction;
  private AwaitableDamageEffect.Request effect;private final Deque<ReturnFrame> parents=new ArrayDeque<>();
  private TrickContinuation.Pending pending;private boolean taken;
  private ListenerProgram listeners;private final boolean ordered;
  private final boolean awaitRescueCosts;private CardUseCostContinuation rescueCost;
  private long rescueInput,rescueRevision;private int rescueHp,rescueSha;private boolean rescueWine;
  DamageProgramContinuation(AttackContinuation damage){this(damage,false);}
  DamageProgramContinuation(AttackContinuation damage,boolean ordered){this(damage,ordered,false);}
  DamageProgramContinuation(AttackContinuation damage,boolean ordered,boolean awaitRescueCosts){this.damage=Objects.requireNonNull(damage);this.ordered=ordered;this.awaitRescueCosts=awaitRescueCosts;if(ordered)damage.useOrderedDamageListeners();}
  DamageProgramContinuation(Snapshot s,long captured,long now){
    step=s.step();damage=AttackContinuation.restoreDamage(s.damage(),captured,now);effect=s.effect();taken=s.taken();ordered=s.ordered();awaitRescueCosts=s.awaitRescueCosts();
    if(s.rescueCost()!=null){var c=s.rescueCost();rescueCost=new CardUseCostContinuation(c.program(),captured,now);rescueInput=c.inputId();rescueRevision=c.inputRevision();rescueHp=c.hpBeforeRecovery();rescueSha=c.shaBefore();rescueWine=c.wineBefore();}
    if(ordered)damage.useOrderedDamageListeners();if(s.listeners()!=null)listeners=new ListenerProgram(s.listeners());
    if(s.distribution()!=null)distribution=new YiJiContinuation(s.distribution(),effect.skill());
    if(s.reaction()!=null)reaction=new GangLieContinuation(s.reaction(),captured,now,effect.skill());
    for(var parent:s.parents()){var leaf=AttackContinuation.restoreDamage(parent.damage(),captured,now);if(ordered)leaf.useOrderedDamageListeners();parents.addLast(new ReturnFrame(leaf,new GangLieContinuation(parent.reaction(),captured,now,parent.effect().skill()),parent.effect(),parent.listeners()==null?null:new ListenerProgram(parent.listeners())));}
    var p=s.pending();pending=p==null?null:new TrickContinuation.Pending(p.id(),p.type(),p.asker(),p.subject(),DecisionBoundary.restoredDeadline(p.deadline(),captured,now),p.choices(),p.hidden(),p.skill(),p.revision());
  }
  public Snapshot snapshot(){return new Snapshot(step,damage.snapshot(),distribution==null?null:distribution.snapshot(),reaction==null?null:reaction.snapshot(),effect,
      parents.stream().map(p->new ReturnSnapshot(p.damage().snapshot(),p.reaction().snapshot(),p.effect(),p.listeners()==null?null:p.listeners().snapshot())).toList(),pending,taken,listeners==null?null:listeners.snapshot(),ordered,awaitRescueCosts,rescueCost==null?null:new RescueCostSnapshot(rescueCost.snapshot(),rescueInput,rescueRevision,rescueHp,rescueSha,rescueWine));}
  public static void validate(Snapshot s){
    validateLeaf(s.damage());
    for(var p:s.parents()){
      if(p.damage()==null||p.reaction()==null||p.effect()==null)throw new IllegalArgumentException("incomplete damage return frame");
      validateLeaf(p.damage());
    }
    boolean body=Set.of(Step.DISTRIBUTE,Step.TAKE,Step.TAKE_FINISH,Step.REACTION).contains(s.step());
    if(body!=(s.effect()!=null)||(s.step()==Step.DISTRIBUTE)!=(s.distribution()!=null)||(s.step()==Step.REACTION)!=(s.reaction()!=null)
        ||s.pending()!=null&&!Set.of(Step.DISTRIBUTE,Step.TAKE,Step.RESCUE_COST).contains(s.step())||s.taken()&&s.step()!=Step.TAKE_FINISH)throw new IllegalArgumentException("invalid shared damage body phase");
    if((s.step()==Step.RESCUE_COST)!=(s.rescueCost()!=null)||!s.awaitRescueCosts()&&s.rescueCost()!=null)throw new IllegalArgumentException("invalid rescue cost phase or capability");
    if(s.rescueCost()!=null){
      var c=s.rescueCost();var q=c.program().pending();var p=s.pending();var d=s.damage();
      if(d.damageFrames().isEmpty()||d.pending()!=null||d.revision()!=c.inputRevision()+1||p==null||q==null||c.program().step()!=CardUseCostContinuation.Step.MOVEMENTS
          ||c.program().reason()!=CardMove.Reason.USE||!Set.of(CardType.TAO,CardType.JIU).contains(c.program().type())||c.program().cards().size()!=1||c.inputId()>=q.id()
          ||p.id()!=q.id()||p.type()!=q.type()||p.asker()!=q.asker()||p.subject()!=q.subject()||p.deadline()!=q.deadline()||!p.skill().equals(q.skill())||!p.choices().isEmpty()||p.hidden())throw new IllegalArgumentException("accepted rescue cost differs");
      var f=d.damageFrames().getFirst();
      if(f.step()!=AttackContinuation.DamageStep.RESCUE||f.rescueCursor()>=f.rescueRing().size()||f.rescueRing().get(f.rescueCursor())!=c.program().actor()||c.program().type()==CardType.JIU&&c.program().actor()!=f.targetSeat())throw new IllegalArgumentException("rescue payer or return cursor differs");
    }
    if(body){
      if(s.damage().damageFrames().isEmpty()||s.effect().owner()!=s.damage().damageFrames().getFirst().targetSeat())throw new IllegalArgumentException("damage effect owner differs");
      if(s.damage().pending()!=null||s.damage().damageFrames().getFirst().step()!=AttackContinuation.DamageStep.AFTER_DEFERRED)throw new IllegalArgumentException("damage body overlaps a leaf decision");
      var kind=s.step()==Step.DISTRIBUTE?AwaitableDamageEffect.Kind.DISTRIBUTE_NEW_CARDS:s.step()==Step.REACTION?AwaitableDamageEffect.Kind.JUDGMENT_OR_RETALIATION:AwaitableDamageEffect.Kind.TAKE_SOURCE_CARD;
      if(s.effect().kind()!=kind)throw new IllegalArgumentException("damage effect kind differs");
      if(s.distribution()!=null&&s.distribution().owner()!=s.effect().owner()||s.reaction()!=null&&s.reaction().owner()!=s.effect().owner())throw new IllegalArgumentException("damage child owner differs");
    }
    if(s.ordered()){
      if(body&&(s.listeners()==null||!s.effect().equals(s.listeners().awaiting()))||!body&&s.listeners()!=null&&s.listeners().awaiting()!=null)throw new IllegalArgumentException("damage listener cursor differs from body");
      for(var p:s.parents())if(p.listeners()==null||!p.effect().equals(p.listeners().awaiting()))throw new IllegalArgumentException("damage parent lost listener cursor");
    }else if(s.listeners()!=null||s.parents().stream().anyMatch(p->p.listeners()!=null))throw new IllegalArgumentException("ordered fields on legacy damage program");
    for(var p:s.parents())if(p.reaction().step()!=GangLieContinuation.Step.WAIT_DAMAGE||!p.reaction().accepted()||p.reaction().judgment()!=null||!p.reaction().costs().isEmpty()||p.reaction().source()!=p.damage().sourceSeat()||p.reaction().pending()!=null||p.damage().pending()!=null||p.damage().damageFrames().isEmpty()||p.damage().damageFrames().getFirst().step()!=AttackContinuation.DamageStep.AFTER_DEFERRED||p.effect().kind()!=AwaitableDamageEffect.Kind.JUDGMENT_OR_RETALIATION||p.effect().owner()!=p.reaction().owner()||p.effect().owner()!=p.damage().damageFrames().getFirst().targetSeat())throw new IllegalArgumentException("invalid damage return frame");
    if(s.step()==Step.DONE&&(s.damage().step()!=AttackContinuation.AttackStep.DONE||!s.parents().isEmpty()||s.listeners()!=null))throw new IllegalArgumentException("unfinished damage return program");
  }
  private static void validateLeaf(AttackContinuation.Snapshot d){
    boolean rootSkill=d.cardless()&&d.cardId()==0&&d.effectiveType()==null;
    if(d.version()!=(rootSkill?5:d.cardless()?4:3)||!Set.of(AttackContinuation.AttackStep.DAMAGE,AttackContinuation.AttackStep.DONE).contains(d.step())
        ||d.sourceSeat()<0||d.sourceSeat()>7||d.targetSeat()<0||d.targetSeat()>7||d.effectiveType()==null&&!rootSkill||(d.cardId()<0||d.cardId()==0&&d.effectiveType()!=CardType.JUEDOU&&!rootSkill)||d.baseDamage()<1||d.baseDamage()>1_000_000||d.damageFrames().size()>8
        ||!d.providers().isEmpty()||d.providerCursor()!=0||d.weaponCost()!=0||!d.parents().isEmpty()||!d.extraCosts().isEmpty()||!d.parentSizes().isEmpty()||d.ruleQuery()!=null
        ||!d.targets().equals(List.of(d.targetSeat()))||d.targetCursor()!=0)throw new IllegalArgumentException("invalid shared damage leaf");
    for(var f:d.damageFrames())if(f.targetSeat()<0||f.targetSeat()>7||f.amount()<0||f.amount()>1_000_000||f.nature()==null||f.step()==null||f.rescueRing().size()>8||new HashSet<>(f.rescueRing()).size()!=f.rescueRing().size()||f.rescueCursor()<0||f.rescueCursor()>f.rescueRing().size())throw new IllegalArgumentException("invalid shared damage instruction");
  }
  private void completeEffect(SgsGameEngine e,boolean activated){
    if(ordered){if(listeners==null)throw new IllegalStateException("missing damage listener cursor");e.continuationCompleteListener(listeners,damage.snapshot(),activated);}
    else{e.continuationCompleteDamageEffect(effect,activated);damage.completeDeferredAfterDamage();}
    effect=null;
  }
  /** Only a live reaction judgment can retain an additional processing entity. */
  public static Set<Long> processingCosts(Snapshot s){
    if(s!=null&&s.rescueCost()!=null)return Set.copyOf(s.rescueCost().program().cards());
    if(s==null||s.reaction()==null||s.reaction().step()!=GangLieContinuation.Step.JUDGE||s.reaction().judgment()==null)return Set.of();
    return Set.of(s.reaction().judgment().effective());
  }
  boolean done(){return step==Step.DONE;}Step step(){return step;}TrickContinuation.Pending ownPending(){return pending;}
  Optional<PromptRequest> prompt(SgsGameEngine e){
    if(pending!=null){var who=e.state().players().get(pending.asker());var choices=pending.choices().stream().map(id->who.handView().stream().filter(c->c.instanceId()==id).findFirst().orElseGet(()->e.state().cards().zones().stream().flatMap(z->e.state().cards().cards(z).stream()).filter(c->c.instanceId()==id).findFirst().orElseThrow())).toList();
      var recipients=pending.type()==PromptType.YIJI_GIVE?e.state().players().stream().filter(p->p.alive()).map(p->p.seat()).toList():List.<Integer>of();
      return Optional.of(new PromptRequest(pending.id(),pending.type(),who,e.state().players().get(pending.subject()),List.of(),choices,pending.deadline(),pending.skill(),recipients,pending.hidden()));
    }
    if(reaction!=null&&reaction.snapshot().pending()!=null)return Optional.of(reaction.request(e));
    return damage.pending().isEmpty()?Optional.empty():Optional.of(damage.request(e));
  }
  /** One parent scheduling turn, so movement hooks can run before the next child instruction. */
  void advanceOne(SgsGameEngine e,long revision,boolean movementsPending){advanceOne(e,revision,()->movementsPending);}
  void advanceOne(SgsGameEngine e,long revision,java.util.function.BooleanSupplier movementsPending){
    if(pending!=null||done())return;
    switch(step){
      case RESCUE_COST->{
        rescueCost.advance(e);
        if(rescueCost.done()){
          var c=rescueCost.snapshot();var victim=e.state().players().get(damage.snapshot().damageFrames().getFirst().targetSeat());
          e.continuationFinishRescue(e.state().players().get(c.actor()),victim,PlayDecision.asIs(rescueCost.card(e)));damage.completeRescuePayment();rescueCost=null;step=Step.DAMAGE;
        }else{
          var q=rescueCost.pending();if(q==null)throw new IllegalStateException("rescue cost failed to yield");rescueHp=e.state().players().get(damage.snapshot().damageFrames().getFirst().targetSeat()).hp();
          pending=new TrickContinuation.Pending(q.id(),q.type(),q.asker(),q.subject(),q.deadline(),q.choices(),q.hidden(),q.skill(),revision);
        }
      }
      case DAMAGE->{
        if(damage.pending().isPresent()&&!movementsPending.getAsBoolean())return;
        if(!ordered||damage.deferredAfterDamage().isEmpty())damage.advance(e);
        if(damage.deferredAfterDamage().isPresent()){
          var frame=damage.deferredAfterDamage().orElseThrow();
          if(ordered){
            if(listeners==null)listeners=e.continuationBeginListeners(damage.snapshot());
            var next=e.continuationAdvanceListeners(listeners,damage.snapshot());
            if(next.isEmpty()){listeners=null;damage.completeDeferredAfterDamage();return;}effect=next.get();
          }else effect=e.continuationDamageEffect(damage.snapshot(),false).orElseThrow(()->new IllegalStateException("deferred damage binding vanished"));
          switch(effect.kind()){
            case DISTRIBUTE_NEW_CARDS->{distribution=new YiJiContinuation(frame.targetSeat(),frame.amount(),effect.skill());step=Step.DISTRIBUTE;}
            case TAKE_SOURCE_CARD->step=Step.TAKE;
            case JUDGMENT_OR_RETALIATION->{reaction=new GangLieContinuation(frame.targetSeat(),damage.snapshot().sourceSeat(),effect.skill());step=Step.REACTION;}
          }
        }else if(damage.done()){
          if(parents.isEmpty())step=Step.DONE;
          else{var parent=parents.pop();damage=parent.damage();reaction=parent.reaction();effect=parent.effect();listeners=parent.listeners();reaction.damageCompleted();step=Step.REACTION;}
        }
      }
      case DISTRIBUTE->{pending=distribution.advance(e,revision);if(distribution.done()){completeEffect(e,false);distribution=null;step=Step.DAMAGE;}}
      case TAKE->{var d=damage.snapshot();var owner=e.state().players().get(d.damageFrames().getFirst().targetSeat());var source=e.state().players().get(d.sourceSeat());
        pending=new TrickContinuation.Pending(e.nextPromptId(),PromptType.FANKUI_TAKE,owner.seat(),source.seat(),e.responseDeadlineMillis(),source.equipment().all().stream().map(CardInstance::instanceId).toList(),source!=owner&&source.handCount()>0,effect.skill(),revision);
      }
      case TAKE_FINISH->{completeEffect(e,taken);taken=false;step=Step.DAMAGE;}
      case REACTION->{
        reaction.advance(e);
        if(reaction.needsDamage()){
          if(parents.size()>=64)throw new IllegalStateException("damage call nesting limit");var g=reaction.snapshot();reaction.suspendForDamage();parents.push(new ReturnFrame(damage,reaction,effect,listeners));
          var root=damage.snapshot();damage=AttackContinuation.skillDamage(g.owner(),g.source(),root.cardId(),root.effectiveType());if(ordered)damage.useOrderedDamageListeners();reaction=null;effect=null;listeners=null;step=Step.DAMAGE;
        }else if(reaction.done()&&!movementsPending.getAsBoolean()){completeEffect(e,reaction.accepted());reaction=null;step=Step.DAMAGE;}
      }
      case DONE->{}
    }
  }
  boolean invoke(SgsGameEngine e,boolean yes){
    if(step==Step.RESCUE_COST){if(pending==null||!rescueCost.invoke(e,yes))return false;pending=null;return true;}
    if(pending!=null){if(step!=Step.DISTRIBUTE||pending.type()!=PromptType.SKILL_INVOKE)return false;distribution.invoke(e,yes&&e.continuationNow()<=pending.deadline());pending=null;return true;}
    if(reaction!=null)return reaction.invoke(e,yes);if(damage.pending().isEmpty())return false;damage.answer(e,Optional.empty(),yes);return true;
  }
  boolean respond(SgsGameEngine e,Optional<PlayDecision> answer){
    if(pending!=null||damage.pending().isEmpty())return false;
    if(awaitRescueCosts&&damage.pending().orElseThrow().type()==PromptType.RESCUE_TAO&&answer.isPresent()){
      var accepted=damage.suspendRescuePayment();rescueInput=accepted.id();rescueRevision=accepted.stateRevision();rescueSha=e.state().shaPlayedThisTurn();rescueWine=e.state().jiuBoostPending();
      rescueCost=CardUseCostContinuation.accepted(e,accepted.askerSeat(),answer.get().card(),CardMove.Reason.USE);step=Step.RESCUE_COST;return true;
    }
    damage.answer(e,answer,false);return true;
  }
  void validateRescueCost(SgsGameEngine e){
    validate(snapshot());var victim=e.state().players().get(damage.snapshot().damageFrames().getFirst().targetSeat());
    if(!victim.alive()||victim.hp()!=rescueHp||e.state().shaPlayedThisTurn()!=rescueSha||e.state().jiuBoostPending()!=rescueWine)throw new IllegalArgumentException("rescue recovery or counters advanced during cost");
    rescueCost.validate(e);
  }
  boolean choose(SgsGameEngine e,Optional<SkillChoice> answer){
    if(pending==null)return reaction!=null&&reaction.choose(e,answer);
    if(e.continuationNow()>pending.deadline())answer=Optional.empty();
    if(step==Step.TAKE){
      if(answer.isPresent()){
        long id=answer.get().cardId();var source=e.state().players().get(pending.subject());
        boolean hidden=HandSelection.allowsHiddenChoice(pending.type(),pending.hidden(),source.handCount(),id);
        if(answer.get().targetSeat()!=-1 || !(hidden || pending.choices().contains(id)))return false;
      }
      taken=e.continuationExecuteDamageTake(damage.snapshot(),effect,answer);pending=null;step=Step.TAKE_FINISH;return true;
    }
    if(step==Step.DISTRIBUTE&&pending.type()==PromptType.YIJI_GIVE){if(!distribution.choose(e,answer))return false;pending=null;return true;}
    return false;
  }
}
