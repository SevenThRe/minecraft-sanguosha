package dev.sgs.core.engine.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import dev.sgs.core.player.SgsPlayer;

/**
 * The skill arbitration core. Each {@link Timing} owns an independent list of registrations kept in
 * static-priority order; rules have their own per-{@link RuleId} lists. Three arbitration rules are
 * supported (see {@link OrderStrategy}).
 *
 * <p>Every executed listener produces one {@link SkillTriggerRecord}; the structured log is
 * retained in a ring buffer so a settlement chain can be reconstructed for debugging or future
 * replay.
 */
public final class SkillEventBus {

  private record Registration<T>(T hook, SgsPlayer owner, int priority, long sequence) {
    String id() {
      if (hook instanceof SkillListener l) {
        return l.id();
      }
      if (hook instanceof RuleModifier r) {
        return r.id();
      }
      return hook.getClass().getSimpleName();
    }
  }

  private static final int LOG_LIMIT = 500;

  private final Map<Timing, List<Registration<SkillListener>>> listeners =
      new EnumMap<>(Timing.class);
  private final Map<RuleId, List<Registration<RuleModifier>>> rules = new EnumMap<>(RuleId.class);
  private final List<SkillTriggerRecord> triggerLog = new ArrayList<>();
  private final java.util.LinkedHashMap<dev.sgs.core.card.GameCard,SkillTriggerRecord> pendingTransforms=new java.util.LinkedHashMap<>();
  private final AtomicLong sequence = new AtomicLong();
  private final LongSupplier clockMillis;
  private int dispatchDepth;
  private boolean faulted;
  private java.util.function.BiPredicate<SgsPlayer,String> skillEnabled=(owner,id)->true;
  public void setSkillEnabled(java.util.function.BiPredicate<SgsPlayer,String> predicate){skillEnabled=java.util.Objects.requireNonNull(predicate);}
  private boolean enabled(Registration<?> r){return skillEnabled.test(r.owner(),r.id());}
  public boolean checkpointIdle(){return dispatchDepth==0 && !faulted && pendingTransforms.isEmpty();}
  /** Probe predicates only: never confirm a transform, record an activation or consume a cost. */
  public boolean canTransform(SkillContext context){
    return listeners.getOrDefault(Timing.CARD_TRANSFORM,List.of()).stream().anyMatch(reg->enabled(reg)&&reg.hook().applies(context));
  }
  public boolean canTransformRecast(SkillContext context){
    return listeners.getOrDefault(Timing.CARD_TRANSFORM,List.of()).stream().filter(reg->enabled(reg)&&reg.hook().applies(context))
        .findFirst().map(reg->reg.hook().allowsTransformRecast(context)).orElse(false);
  }
  /** Deterministic binding order; snapshots cannot silently drop an added callback or modifier. */
  public List<String> checkpointBindings(){
    var result=new ArrayList<String>();
    listeners.forEach((timing,values)->values.forEach(r->result.add("L/"+timing+"/"+r.id()+"/"+(r.owner()==null?-1:r.owner().seat())+"/"+r.priority()+"/"+r.sequence())));
    rules.forEach((rule,values)->values.forEach(r->result.add("R/"+rule+"/"+r.id()+"/"+(r.owner()==null?-1:r.owner().seat())+"/"+r.priority()+"/"+r.sequence())));
    return List.copyOf(result);
  }

  public SkillEventBus(LongSupplier clockMillis) {
    this.clockMillis = clockMillis;
  }

  public void register(SkillListener listener) {
    register(
        listeners.computeIfAbsent(listener.timing(), t -> new ArrayList<>()),
        listener,
        listener.owner(),
        listener.priority());
  }

  public void registerRule(RuleModifier modifier) {
    register(
        rules.computeIfAbsent(modifier.rule(), r -> new ArrayList<>()),
        modifier,
        modifier.owner(),
        modifier.priority());
  }

  private <T> void register(List<Registration<T>> bucket, T hook, SgsPlayer owner, int priority) {
    bucket.add(new Registration<>(hook, owner, priority, sequence.incrementAndGet()));
    bucket.sort(
        Comparator.comparingInt((Registration<T> r) -> r.priority())
            .thenComparingLong(Registration::sequence));
  }

  /** Removes every hook bound to this seat (used when skills are rebound). */
  public void unregisterOwner(SgsPlayer owner) {
    listeners.values().forEach(list -> list.removeIf(r -> r.owner() == owner));
    rules.values().forEach(list -> list.removeIf(r -> r.owner() == owner));
  }

  /** Fires a timing with the default static-priority arbitration. */
  public void trigger(Timing timing, SkillContext ctx) {
    dispatchDepth++;
    try {
    trigger(timing, ctx, OrderStrategy.REGISTRATION_PRIORITY);

    } catch(RuntimeException|Error failure){faulted=true;throw failure;}
    finally{dispatchDepth--;}
  }

  /** Selects applicable hooks, orders them per the strategy and executes them in sequence. */
  public void trigger(Timing timing, SkillContext ctx, OrderStrategy strategy) {
    triggerExcept(timing,ctx,strategy,-1,"");
  }
  /** An audited data-owned hook completes separately; all other registered hooks still dispatch. */
  public void triggerExcept(Timing timing,SkillContext ctx,OrderStrategy strategy,int deferredOwner,String deferredSkill){
    var program=beginListeners(timing,ctx,strategy,deferredOwner,deferredSkill);
    program.advance(this,ctx,false);
  }
  /** Capture initial eligibility once, then recheck each planned listener immediately before execution. */
  public ListenerProgram beginListeners(Timing timing,SkillContext ctx,OrderStrategy strategy){
    return beginListeners(timing,ctx,strategy,-1,"");
  }
  private ListenerProgram beginListeners(Timing timing,SkillContext ctx,OrderStrategy strategy,int excludedOwner,String excludedSkill){
    dispatchDepth++;
    try {
    if(faulted)throw new IllegalStateException("listener bus is faulted");
    if(timing==null||timing!=ctx.timing())throw new IllegalArgumentException("listener timing mismatch");
    List<Registration<SkillListener>> applicable = new ArrayList<>();
    for (Registration<SkillListener> reg : listeners.getOrDefault(timing, List.of())) {
      if (enabled(reg) && !(reg.owner()!=null && reg.owner().seat()==excludedOwner && reg.id().equals(excludedSkill)) && reg.hook().applies(ctx)) {
        applicable.add(reg);
      }
    }
    return new ListenerProgram(new ListenerProgram.Snapshot(timing,strategy,listenerRegistry(timing),order(applicable,ctx,strategy).stream().map(Registration::sequence).toList(),0,null));
    } catch(RuntimeException|Error failure){faulted=true;throw failure;}
    finally{dispatchDepth--;}
  }
  private List<ListenerProgram.Binding> listenerRegistry(Timing timing){
    return listeners.getOrDefault(timing,List.of()).stream().map(r->new ListenerProgram.Binding(r.id(),r.owner()==null?-1:r.owner().seat(),r.priority(),r.sequence())).toList();
  }
  private void validateListenerProgram(ListenerProgram.Snapshot program,SkillContext context){
    if(faulted)throw new IllegalStateException("listener bus is faulted");
    if(program.timing()!=context.timing()||!program.registry().equals(listenerRegistry(program.timing())))throw new IllegalArgumentException("listener program registry or timing changed");
    if(program.damageEvent()!=null&&!program.damageEvent().equals(ListenerProgram.DamageEvent.of(context)))throw new IllegalArgumentException("listener damage event changed");
    if(program.awaiting()!=null){
      var reg=plannedListener(program,program.cursor());
      if(!(reg.hook() instanceof AwaitableDamageEffect effect)||effect.damageEffect()!=program.awaiting().kind())throw new IllegalArgumentException("awaited listener body changed");
    }
    if(program.strategy()==OrderStrategy.MULTI_ENTITY_ORDER){
      int n=context.state().players().size(),start=context.state().currentSeat();
      var expected=listeners.getOrDefault(program.timing(),List.of()).stream().filter(r->program.candidates().contains(r.sequence()))
          .sorted(Comparator.comparingInt((Registration<SkillListener> r)->r.owner()==null?-1:Math.floorMod(r.owner().seat()-start,n)).thenComparingInt(Registration::priority).thenComparingLong(Registration::sequence)).map(Registration::sequence).toList();
      if(!expected.equals(program.candidates()))throw new IllegalArgumentException("listener turn order changed");
    }
  }
  /** Read-only restore audit; never executes a planned listener. */
  public void validateListeners(ListenerProgram.Snapshot program,SkillContext context){validateListenerProgram(program,context);}
  public boolean hasAwaitableDamage(SkillContext context){return listeners.getOrDefault(Timing.DAMAGE_TAKE_AFTER,List.of()).stream().anyMatch(r->r.owner()==context.target()&&r.hook() instanceof AwaitableDamageEffect&&r.hook().applies(context));}
  public boolean hasTargetRedirectBinding(int owner,String skill){return listeners.getOrDefault(Timing.SHA_TARGET_BEFORE,List.of()).stream()
      .anyMatch(r->enabled(r)&&r.owner()!=null&&r.owner().seat()==owner&&r.id().equals(skill)&&r.hook() instanceof AwaitableTargetRedirect);}
  public java.util.Optional<AwaitableTargetRedirect.Request> targetRedirect(SkillContext context){
    if(context.timing()!=Timing.SHA_TARGET_BEFORE)throw new IllegalArgumentException("attack target context required");
    var matches=listeners.getOrDefault(Timing.SHA_TARGET_BEFORE,List.of()).stream()
        .filter(r->enabled(r)&&r.hook() instanceof AwaitableTargetRedirect&&r.hook().applies(context)).toList();
    if(matches.isEmpty())return java.util.Optional.empty();
    if(matches.size()!=1||matches.getFirst().owner()!=context.target())throw new IllegalStateException("redirect requires one owning target effect");
    var r=matches.getFirst();return java.util.Optional.of(new AwaitableTargetRedirect.Request(r.owner().seat(),r.id()));
  }
  /** Read-only departure capability lookup. Never constructs or executes a replacement listener. */
  public java.util.Optional<AwaitableMovementEffect.Request> movementEffect(SkillContext context){
    if(context.timing()!=Timing.CARD_MOVE_AFTER || context.movement()==null)
      throw new IllegalArgumentException("movement context required");
    var matches=listeners.getOrDefault(Timing.CARD_MOVE_AFTER,List.of()).stream()
        .filter(r->enabled(r)&&r.hook() instanceof AwaitableMovementEffect&&r.hook().applies(context)).toList();
    if(matches.isEmpty())return java.util.Optional.empty();
    if(matches.size()!=1)throw new IllegalStateException("multiple movement effects require an ordered movement program");
    var binding=matches.getFirst();
    if(binding.owner()==null || binding.owner().seat()!=context.movement().from().seat())
      throw new IllegalStateException("movement invocation requires the departure owner");
    return java.util.Optional.of(new AwaitableMovementEffect.Request(binding.owner().seat(),binding.id()));
  }
  public MovementListenerProgram beginMovement(SkillContext context){
    var event=MovementListenerProgram.Event.of(context);
    return new MovementListenerProgram(new MovementListenerProgram.Snapshot(event,
        beginListeners(Timing.CARD_MOVE_AFTER,context,OrderStrategy.MULTI_ENTITY_ORDER).snapshot(),null));
  }
  public void validateMovement(MovementListenerProgram.Snapshot program,SkillContext context){
    if(!program.event().equals(MovementListenerProgram.Event.of(context)))throw new IllegalArgumentException("movement event changed");
    validateListenerProgram(program.listeners(),context);
    if(program.awaiting()!=null){
      var reg=plannedListener(program.listeners(),program.listeners().cursor());
      if(!(reg.hook() instanceof AwaitableMovementEffect)||reg.owner()==null
          ||reg.owner().seat()!=program.awaiting().owner()||!reg.id().equals(program.awaiting().skill()))
        throw new IllegalArgumentException("awaited movement body changed");
    }
  }
  private ListenerProgram.Snapshot movementCursor(ListenerProgram.Snapshot p,int cursor){
    return new ListenerProgram.Snapshot(p.timing(),p.strategy(),p.registry(),p.candidates(),cursor,null);
  }
  private void executeMovement(Registration<SkillListener> reg,SkillContext context){
    reg.hook().execute(context);
    var receipt=record(Timing.CARD_MOVE_AFTER,reg,OrderStrategy.MULTI_ENTITY_ORDER,context.state().turnNumber());
    if(context.consumePublicActivation()&&receipt.ownerSeat()>=0)context.state().publishSkillActivation(receipt.ownerSeat(),receipt.skillId());
  }
  MovementListenerProgram.Snapshot advanceMovement(MovementListenerProgram.Snapshot program,SkillContext context){
    dispatchDepth++;
    try{
      validateMovement(program,context);if(program.awaiting()!=null)return program;
      var p=program.listeners();int cursor=p.cursor();
      for(;cursor<p.candidates().size();cursor++){
        var reg=plannedListener(p,cursor);if(!enabled(reg)||!reg.hook().applies(context))continue;
        if(reg.hook() instanceof AwaitableMovementEffect){
          if(reg.owner()==null)throw new IllegalArgumentException("movement invocation requires an owner");
          return new MovementListenerProgram.Snapshot(program.event(),movementCursor(p,cursor),new AwaitableMovementEffect.Request(reg.owner().seat(),reg.id()));
        }
        executeMovement(reg,context);
      }
      return new MovementListenerProgram.Snapshot(program.event(),movementCursor(p,cursor),null);
    }catch(RuntimeException|Error failure){faulted=true;throw failure;}finally{dispatchDepth--;}
  }
  MovementListenerProgram.Snapshot completeMovement(MovementListenerProgram.Snapshot program,SkillContext context){
    dispatchDepth++;
    try{
      validateMovement(program,context);if(program.awaiting()==null)throw new IllegalStateException("movement body not awaiting");
      var p=program.listeners();var reg=plannedListener(p,p.cursor());
      if(!enabled(reg)||!reg.hook().applies(context))throw new IllegalArgumentException("movement body no longer applicable");
      executeMovement(reg,context);
      return new MovementListenerProgram.Snapshot(program.event(),movementCursor(p,p.cursor()+1),null);
    }catch(RuntimeException|Error failure){faulted=true;throw failure;}finally{dispatchDepth--;}
  }
  /** Unknown phase bodies remain on their existing synchronous route until explicitly migrated. */
  public boolean supportsPreparationProgram(){return listeners.getOrDefault(Timing.PHASE_PREPARE_START,List.of()).stream().allMatch(r->r.hook() instanceof AwaitableDeckOrderEffect);}
  public PreparationProgram beginPreparation(SkillContext c){
    if(!supportsPreparationProgram()||c.timing()!=Timing.PHASE_PREPARE_START)throw new IllegalArgumentException("preparation body not migrated");
    return new PreparationProgram(new PreparationProgram.Snapshot(c.actor().seat(),c.state().turnNumber(),beginListeners(c.timing(),c,OrderStrategy.REGISTRATION_PRIORITY).snapshot(),PreparationProgram.Step.NEXT,null));
  }
  public void validatePreparation(PreparationProgram.Snapshot p,SkillContext c){
    if(!supportsPreparationProgram()||p.actor()!=c.actor().seat()||p.turn()!=c.state().turnNumber()||c.state().phase()!=dev.sgs.core.Phase.PREPARE)
      throw new IllegalArgumentException("preparation context differs");
    validateListenerProgram(p.listeners(),c);
    if(p.step()!=PreparationProgram.Step.NEXT){var reg=plannedListener(p.listeners(),p.listeners().cursor());
      if(!enabled(reg)||!reg.hook().applies(c)||reg.owner()!=c.actor()||!(reg.hook() instanceof AwaitableDeckOrderEffect))throw new IllegalArgumentException("preparation binding differs");
    }
    if(p.order()!=null&&p.step()!=PreparationProgram.Step.COMPLETE){
      var effect=(AwaitableDeckOrderEffect)plannedListener(p.listeners(),p.listeners().cursor()).hook();
      var cards=c.state().cards();
      if(!cards.processingCards().stream().map(dev.sgs.core.card.CardInstance::instanceId).toList().equals(p.order().original())||!cards.publicProcessingCards().isEmpty()
          ||p.order().original().size()!=Math.min(effect.privateDeckCount(c),c.state().deck().drawPileCount()+c.state().deck().discardCount()+p.order().original().size()))
        throw new IllegalArgumentException("private preparation cards differ");
    }
  }
  private List<dev.sgs.core.card.CardInstance> privateOrderCards(SkillContext c,List<Long> ids){return ids.stream()
      .map(id->c.state().cards().processingCards().stream().filter(card->card.instanceId()==id).findFirst().orElseThrow()).toList();}
  PreparationProgram.Snapshot advancePreparation(PreparationProgram.Snapshot p,SkillContext c){
    dispatchDepth++;
    try{
      validatePreparation(p,c);if(p.done())return p;var plan=p.listeners();var stage=p.step();var order=p.order();
      if(stage==PreparationProgram.Step.NEXT){
        int cursor=plan.cursor();for(;cursor<plan.candidates().size();cursor++){var reg=plannedListener(plan,cursor);if(enabled(reg)&&reg.hook().applies(c))break;}
        plan=movementCursor(plan,cursor);stage=plan.done()?PreparationProgram.Step.NEXT:PreparationProgram.Step.INVOKE;
      }else if(stage==PreparationProgram.Step.ORDER&&order.pending().isEmpty())stage=PreparationProgram.Step.RETURN;
      else if(stage==PreparationProgram.Step.RETURN){
        var effect=(AwaitableDeckOrderEffect)plannedListener(plan,plan.cursor()).hook();effect.returnPrivateDeck(c,privateOrderCards(c,order.returnedTop()),privateOrderCards(c,order.bottom()));stage=PreparationProgram.Step.COMPLETE;
      }else if(stage==PreparationProgram.Step.COMPLETE){
        var reg=plannedListener(plan,plan.cursor());if(order!=null)((AwaitableDeckOrderEffect)reg.hook()).finishPrivateDeck(c);
        var receipt=record(c.timing(),reg,OrderStrategy.REGISTRATION_PRIORITY,c.state().turnNumber());
        if(c.consumePublicActivation())c.state().publishSkillActivation(receipt.ownerSeat(),receipt.skillId());
        plan=movementCursor(plan,plan.cursor()+1);stage=PreparationProgram.Step.NEXT;order=null;
      }
      return new PreparationProgram.Snapshot(p.actor(),p.turn(),plan,stage,order);
    }catch(RuntimeException|Error failure){faulted=true;throw failure;}finally{dispatchDepth--;}
  }
  PreparationProgram.Snapshot answerPreparation(PreparationProgram.Snapshot p,SkillContext c,boolean accepted,java.util.Optional<dev.sgs.core.engine.SkillChoice> choice){
    dispatchDepth++;
    try{
      validatePreparation(p,c);var stage=p.step();var order=p.order();
      if(stage==PreparationProgram.Step.INVOKE){
        if(accepted){var effect=(AwaitableDeckOrderEffect)plannedListener(p.listeners(),p.listeners().cursor()).hook();var ids=effect.revealPrivateDeck(c).stream().map(dev.sgs.core.card.CardInstance::instanceId).toList();order=new PreparationProgram.Order(ids,ids,List.of(),List.of());stage=PreparationProgram.Step.ORDER;}
        else stage=PreparationProgram.Step.COMPLETE;
      }else if(stage==PreparationProgram.Step.ORDER){
        if(choice.isEmpty())stage=PreparationProgram.Step.RETURN;
        else{
          var answer=choice.get();if(!order.pending().contains(answer.cardId())||answer.targetSeat()<0||answer.targetSeat()>1)throw new IllegalArgumentException("private order choice differs");
          var remaining=new ArrayList<>(order.pending());remaining.remove(answer.cardId());var top=new ArrayList<>(order.top());var bottom=new ArrayList<>(order.bottom());
          (answer.targetSeat()==0?top:bottom).add(answer.cardId());order=new PreparationProgram.Order(order.original(),remaining,top,bottom);
        }
      }else throw new IllegalStateException("preparation is not awaiting");
      return new PreparationProgram.Snapshot(p.actor(),p.turn(),p.listeners(),stage,order);
    }catch(RuntimeException|Error failure){faulted=true;throw failure;}finally{dispatchDepth--;}
  }
  public boolean supportsPhase(Timing timing){
    return listeners.getOrDefault(timing,List.of()).stream().allMatch(r->r.hook() instanceof AwaitablePhaseEffect);
  }
  public boolean supportsMovementProgram(){
    return listeners.getOrDefault(Timing.CARD_MOVE_AFTER,List.of()).stream()
        .allMatch(r->r.hook() instanceof AwaitableMovementEffect||r.hook() instanceof ImmediateMovementEffect);
  }
  public PhaseListenerProgram beginPhase(SkillContext context){
    if(!supportsPhase(context.timing()))throw new IllegalArgumentException("phase listener has not been migrated");
    return new PhaseListenerProgram(new PhaseListenerProgram.Snapshot(PhaseListenerProgram.Event.of(context),
        beginListeners(context.timing(),context,OrderStrategy.REGISTRATION_PRIORITY).snapshot(),context.phaseSkipped(),null));
  }
  public void validatePhase(PhaseListenerProgram.Snapshot p,SkillContext context){
    if(!p.event().equals(PhaseListenerProgram.Event.of(context))||p.skipped()!=context.phaseSkipped()||!supportsPhase(context.timing()))
      throw new IllegalArgumentException("phase event changed");
    validateListenerProgram(p.listeners(),context);
    if(p.awaiting()!=null){var reg=plannedListener(p.listeners(),p.listeners().cursor());
      if(!(reg.hook() instanceof AwaitablePhaseEffect)||!enabled(reg)||!reg.hook().applies(context))
        throw new IllegalArgumentException("phase body no longer applies");
    }
  }
  PhaseListenerProgram.Snapshot advancePhase(PhaseListenerProgram.Snapshot p,SkillContext context){
    dispatchDepth++;
    try{
      validatePhase(p,context);if(p.awaiting()!=null)return p;
      var listeners=p.listeners();int cursor=listeners.cursor();
      for(;cursor<listeners.candidates().size();cursor++){
        var reg=plannedListener(listeners,cursor);if(!enabled(reg)||!reg.hook().applies(context))continue;
        if(reg.owner()==null||reg.owner()!=context.actor())throw new IllegalArgumentException("phase invocation owner differs");
        return new PhaseListenerProgram.Snapshot(p.event(),movementCursor(listeners,cursor),p.skipped(),
            new AwaitablePhaseEffect.Request(reg.owner().seat(),reg.id()));
      }
      return new PhaseListenerProgram.Snapshot(p.event(),movementCursor(listeners,cursor),p.skipped(),null);
    }catch(RuntimeException|Error failure){faulted=true;throw failure;}finally{dispatchDepth--;}
  }
  PhaseListenerProgram.Snapshot completePhase(PhaseListenerProgram.Snapshot p,SkillContext context){
    dispatchDepth++;
    try{
      validatePhase(p,context);if(p.awaiting()==null)throw new IllegalStateException("phase body not awaiting");
      var listeners=p.listeners();var reg=plannedListener(listeners,listeners.cursor());reg.hook().execute(context);
      var receipt=record(context.timing(),reg,OrderStrategy.REGISTRATION_PRIORITY,context.state().turnNumber());
      if(context.consumePublicActivation())context.state().publishSkillActivation(receipt.ownerSeat(),receipt.skillId());
      return new PhaseListenerProgram.Snapshot(p.event(),movementCursor(listeners,listeners.cursor()+1),context.phaseSkipped(),null);
    }catch(RuntimeException|Error failure){faulted=true;throw failure;}finally{dispatchDepth--;}
  }
  private Registration<SkillListener> plannedListener(ListenerProgram.Snapshot program,int cursor){
    long sequence=program.candidates().get(cursor);
    return listeners.getOrDefault(program.timing(),List.of()).stream().filter(r->r.sequence()==sequence).findFirst().orElseThrow();
  }
  ListenerProgram.Snapshot advanceListeners(ListenerProgram.Snapshot program,SkillContext ctx,boolean awaitDamageBodies){
    dispatchDepth++;
    try {
    validateListenerProgram(program,ctx);
    if(program.awaiting()!=null)return program;
    var event=program.damageEvent();if(awaitDamageBodies&&event==null)event=ListenerProgram.DamageEvent.of(ctx);
    int cursor=program.cursor();
    for(;cursor<program.candidates().size();cursor++){
      var reg=plannedListener(program,cursor);
      // Earlier hooks may have changed life/skills/card state during this same timing.
      if (!enabled(reg) || !reg.hook().applies(ctx)) continue;
      if(awaitDamageBodies&&program.timing()==Timing.DAMAGE_TAKE_AFTER&&reg.hook() instanceof AwaitableDamageEffect effect){
        if(reg.owner()==null)throw new IllegalStateException("awaited damage body requires an owner");
        return new ListenerProgram.Snapshot(program.timing(),program.strategy(),program.registry(),program.candidates(),cursor,
            new AwaitableDamageEffect.Request(reg.owner().seat(),reg.id(),effect.damageEffect()),event);
      }
      reg.hook().execute(ctx);
      var receipt=record(program.timing(), reg, program.strategy(), ctx.state().turnNumber());
      if(ctx.consumePublicActivation() && receipt.ownerSeat()>=0){
        if(program.timing()==Timing.CARD_TRANSFORM){
          pendingTransforms.put(ctx.card(),receipt);
          while(pendingTransforms.size()>32)pendingTransforms.pollFirstEntry();
        }else ctx.state().publishSkillActivation(receipt.ownerSeat(),receipt.skillId());
      }
    }
    return new ListenerProgram.Snapshot(program.timing(),program.strategy(),program.registry(),program.candidates(),cursor,null,event);
    } catch(RuntimeException|Error failure){faulted=true;throw failure;}
    finally{dispatchDepth--;}
  }
  ListenerProgram.Snapshot completeListener(ListenerProgram.Snapshot program,SkillContext ctx,boolean activated){
    dispatchDepth++;
    try{
      validateListenerProgram(program,ctx);
      if(program.awaiting()==null)throw new IllegalStateException("listener program is not awaiting a body");
      var reg=plannedListener(program,program.cursor());
      if(!(reg.hook() instanceof AwaitableDamageEffect effect)||effect.damageEffect()!=program.awaiting().kind())throw new IllegalArgumentException("awaited listener body changed");
      var receipt=record(program.timing(),reg,program.strategy(),ctx.state().turnNumber());
      // The caller supplies the completed body's activation result. Do not let a residual flag
      // attribute that activation again to the next ordinary listener on the same context.
      ctx.consumePublicActivation();
      if(activated)ctx.state().publishSkillActivation(receipt.ownerSeat(),receipt.skillId());
      return new ListenerProgram.Snapshot(program.timing(),program.strategy(),program.registry(),program.candidates(),program.cursor()+1,null,program.damageEvent());
    }catch(RuntimeException|Error failure){faulted=true;throw failure;}finally{dispatchDepth--;}
  }

  public void recordDeferred(Timing timing,int owner,String skill,int turn){
    recordDeferred(timing,owner,skill,turn,OrderStrategy.REGISTRATION_PRIORITY);
  }
  public void recordDeferred(Timing timing,int owner,String skill,int turn,OrderStrategy strategy){
    var matches=listeners.getOrDefault(timing,List.of()).stream().filter(r->r.owner()!=null&&r.owner().seat()==owner&&r.id().equals(skill)).toList();
    if(matches.size()!=1)throw new IllegalStateException("deferred hook binding changed");
    record(timing,matches.getFirst(),strategy,turn);
  }
  /** Execute the original body now; a data-owned parent completes its receipt after nested moves. */
  public boolean executeDeferred(Timing timing,SkillContext ctx,int owner,String skill){
    dispatchDepth++;
    try{
      var matches=listeners.getOrDefault(timing,List.of()).stream().filter(r->r.owner()!=null&&r.owner().seat()==owner&&r.id().equals(skill)).toList();
      if(matches.size()!=1||!matches.getFirst().hook().applies(ctx))throw new IllegalStateException("deferred body no longer applies");
      matches.getFirst().hook().execute(ctx);return ctx.consumePublicActivation();
    }catch(RuntimeException|Error failure){faulted=true;throw failure;}finally{dispatchDepth--;}
  }

  /** Execute the original listener after its data-owned question has already received an answer. */
  public void dispatchDeferred(Timing timing,SkillContext ctx,int owner,String skill,OrderStrategy strategy){
    dispatchDepth++;
    try{
      var matches=listeners.getOrDefault(timing,List.of()).stream().filter(r->r.owner()!=null&&r.owner().seat()==owner&&r.id().equals(skill)).toList();
      if(matches.size()!=1 || !matches.getFirst().hook().applies(ctx))throw new IllegalStateException("deferred hook no longer applies");
      var reg=matches.getFirst();reg.hook().execute(ctx);var receipt=record(timing,reg,strategy,ctx.state().turnNumber());
      if(ctx.consumePublicActivation())ctx.state().publishSkillActivation(receipt.ownerSeat(),receipt.skillId());
    }catch(RuntimeException|Error failure){faulted=true;throw failure;}finally{dispatchDepth--;}
  }

  private List<Registration<SkillListener>> order(
      List<Registration<SkillListener>> candidates, SkillContext ctx, OrderStrategy strategy) {
    return switch (strategy) {
      case REGISTRATION_PRIORITY -> candidates; // bucket is already sorted this way
      case INSERTION -> candidates; // insertion order is the settlement order
      case MULTI_ENTITY_ORDER -> orderByTurnPlayer(candidates, ctx);
    };
  }

  /**
   * Rule two: global hooks first, then one owning seat after another, starting at the current turn
   * player and walking the alive ring. Hooks owned by the same seat still follow static priority.
   */
  private List<Registration<SkillListener>> orderByTurnPlayer(
      List<Registration<SkillListener>> candidates, SkillContext ctx) {
    List<Registration<SkillListener>> globals = new ArrayList<>();
    List<Registration<SkillListener>> seated = new ArrayList<>();
    for (Registration<SkillListener> reg : candidates) {
      (reg.owner() == null ? globals : seated).add(reg);
    }
    globals.sort(
        Comparator.comparingInt((Registration<SkillListener> r) -> r.priority())
            .thenComparingLong(Registration::sequence));

    List<Registration<SkillListener>> ordered = new ArrayList<>(globals);
    for (SgsPlayer player : ctx.state().alivePlayersFromCurrent()) {
      for (Registration<SkillListener> reg : seated) {
        if (reg.owner() == player) {
          ordered.add(reg);
        }
      }
    }
    return ordered;
  }

  /** Folds every applicable rule modifier over the base value in static-priority order. */
  public record IntProgress(int cursor,int value,AwaitableIntModifier.Request request) {}
  /** Stops before executing an awaited modifier. Cursor includes inapplicable bindings so restore
   * uses exactly the same registration order as the synchronous fold.
   */
  public IntProgress advanceInt(RuleId rule,SkillContext context,int cursor,int value){
    dispatchDepth++;
    try{
      var registrations=rules.getOrDefault(rule,List.of());
      if(cursor<0 || cursor>registrations.size())throw new IllegalArgumentException("invalid rule cursor");
      for(;cursor<registrations.size();cursor++){
        var modifier=registrations.get(cursor).hook();if(!modifier.applies(context))continue;
        if(modifier instanceof AwaitableIntModifier awaited)return new IntProgress(cursor,value,awaited.awaitedEffect(context,value));
        value=modifier.modifyInt(context,value);
      }
      return new IntProgress(cursor,value,null);
    }catch(RuntimeException|Error failure){faulted=true;throw failure;}finally{dispatchDepth--;}
  }
  public boolean matchesAwaitedInt(RuleId rule,SkillContext context,int cursor,int value,AwaitableIntModifier.Request request,boolean requireApplicable){
    var registrations=rules.getOrDefault(rule,List.of());if(cursor<0 || cursor>=registrations.size())return false;
    var modifier=registrations.get(cursor).hook();return modifier instanceof AwaitableIntModifier awaited
        && (!requireApplicable || modifier.applies(context)) && awaited.awaitedEffect(context,value).equals(request);
  }
  public boolean hasAwaitableInt(RuleId rule,SgsPlayer owner){return rules.getOrDefault(rule,List.of()).stream().anyMatch(r->r.owner()==owner && r.hook() instanceof AwaitableIntModifier);}
  /** Binding lookup is separate from execution; a suspended parent never replays a listener. */
  public java.util.Optional<AwaitableDamageEffect.Request> damageEffect(SkillContext context,boolean requireApplicable){
    var matches=listeners.getOrDefault(Timing.DAMAGE_TAKE_AFTER,List.of()).stream().filter(r->r.owner()==context.target()
        && r.hook() instanceof AwaitableDamageEffect && (!requireApplicable || r.hook().applies(context))).toList();
    if(matches.size()>1)throw new IllegalStateException("multiple damage effect bodies require an ordered listener program");
    if(matches.isEmpty())return java.util.Optional.empty();var r=matches.getFirst();
    return java.util.Optional.of(new AwaitableDamageEffect.Request(r.owner().seat(),r.id(),((AwaitableDamageEffect)r.hook()).damageEffect()));
  }

  /** Folds every applicable rule modifier over the base value in static-priority order. */
  public boolean hasPhaseListeners(Timing timing){return !listeners.getOrDefault(timing,List.of()).isEmpty();}
  public boolean supportsRuleProgram(RuleId rule){return rules.getOrDefault(rule,List.of()).stream().allMatch(r->r.hook() instanceof AwaitableRuleEffect);}
  private List<ListenerProgram.Binding> ruleBindings(RuleId rule){return rules.getOrDefault(rule,List.of()).stream()
      .map(r->new ListenerProgram.Binding(r.id(),r.owner()==null?-1:r.owner().seat(),r.priority(),r.sequence())).toList();}
  public RuleFoldProgram beginRule(RuleId rule,SkillContext c,int value){
    if(!supportsRuleProgram(rule))throw new IllegalArgumentException("rule body has not been migrated");
    return new RuleFoldProgram(new RuleFoldProgram.Snapshot(rule,c.actor().seat(),c.state().turnNumber(),ruleBindings(rule),0,value,null,null));
  }
  public void validateRule(RuleFoldProgram.Snapshot p,SkillContext c){
    if(!supportsRuleProgram(p.rule())||p.rule()!=c.ruleId()||p.actor()!=c.actor().seat()||p.turn()!=c.state().turnNumber()||!p.registry().equals(ruleBindings(p.rule())))
      throw new IllegalArgumentException("rule fold context differs");
    if(p.progress()!=null){
      if(!p.progress().complete()&&p.progress().value()!=p.value())throw new IllegalArgumentException("unfinished rule changed fold value");
      var reg=rules.get(p.rule()).get(p.cursor());if(!enabled(reg)||!reg.hook().applies(c))throw new IllegalArgumentException("rule body no longer applies");
      var effect=(AwaitableRuleEffect)reg.hook();effect.validateRuleProgress(c,p.progress());
      if(p.awaiting()!=null&&!p.awaiting().equals(effect.nextRule(c,p.progress())))throw new IllegalArgumentException("rule request differs");
    }
  }
  RuleFoldProgram.Snapshot advanceRule(RuleFoldProgram.Snapshot p,SkillContext c){
    dispatchDepth++;
    try{
      validateRule(p,c);if(p.awaiting()!=null)return p;int cursor=p.cursor(),value=p.value();var progress=p.progress();
      var registry=rules.getOrDefault(p.rule(),List.of());
      for(;cursor<registry.size();cursor++){
        var reg=registry.get(cursor);if(!enabled(reg)||!reg.hook().applies(c)){progress=null;continue;}
        var effect=(AwaitableRuleEffect)reg.hook();if(progress==null)progress=effect.beginRule(value);
        var request=effect.nextRule(c,progress);
        if(request!=null)return new RuleFoldProgram.Snapshot(p.rule(),p.actor(),p.turn(),p.registry(),cursor,value,progress,request);
        value=effect.finishRule(c,progress);progress=null;
      }
      return new RuleFoldProgram.Snapshot(p.rule(),p.actor(),p.turn(),p.registry(),cursor,value,null,null);
    }catch(RuntimeException|Error failure){faulted=true;throw failure;}finally{dispatchDepth--;}
  }
  RuleFoldProgram.Snapshot completeRule(RuleFoldProgram.Snapshot p,SkillContext c,java.util.Optional<dev.sgs.core.engine.SkillChoice> choice){
    dispatchDepth++;
    try{
      validateRule(p,c);if(p.awaiting()==null)throw new IllegalStateException("rule fold is not awaiting");
      var effect=(AwaitableRuleEffect)rules.get(p.rule()).get(p.cursor()).hook();
      var progress=effect.completeRule(c,p.progress(),choice);
      return new RuleFoldProgram.Snapshot(p.rule(),p.actor(),p.turn(),p.registry(),p.cursor(),p.value(),progress,null);
    }catch(RuntimeException|Error failure){faulted=true;throw failure;}finally{dispatchDepth--;}
  }
  public int queryInt(RuleId rule, SkillContext ctx, int base) {
    dispatchDepth++;
    try {
    int value = base;
    for (Registration<RuleModifier> reg : rules.getOrDefault(rule, List.of())) {
      if (enabled(reg) && reg.hook().applies(ctx)) {
        value = reg.hook().modifyInt(ctx, value);
      }
    }
    return value;

    } catch(RuntimeException|Error failure){faulted=true;throw failure;}
    finally{dispatchDepth--;}
  }

  private SkillTriggerRecord record(
      Timing timing, Registration<SkillListener> reg, OrderStrategy strategy, int turnNumber) {
    SkillTriggerRecord record =
        new SkillTriggerRecord(
            clockMillis.getAsLong(),
            turnNumber,
            timing,
            reg.owner() == null ? SkillTriggerRecord.GLOBAL_SEAT : reg.owner().seat(),
            reg.id(),
            reg.priority(),
            strategy);
    triggerLog.add(record);
    if (triggerLog.size() > LOG_LIMIT) {
      triggerLog.remove(0);
    }
    return record;
  }

  /** A confirmed view-as is public only once its physical costs were actually committed. */
  public String takeTransformReceipt(SgsPlayer actor,dev.sgs.core.card.GameCard card){
    var receipt=pendingTransforms.remove(card);if(receipt==null)return "";
    if(receipt.ownerSeat()!=actor.seat()||receipt.timing()!=Timing.CARD_TRANSFORM)throw new IllegalStateException("transform receipt ownership differs");
    return receipt.skillId();
  }
  public boolean hasTransformReceiptBinding(SgsPlayer actor,String skill){return listeners.getOrDefault(Timing.CARD_TRANSFORM,List.of()).stream().anyMatch(r->r.owner()==actor&&r.id().equals(skill));}
  public void commitTransformReceipt(dev.sgs.core.game.GameState state,SgsPlayer actor,String skill){
    if(!hasTransformReceiptBinding(actor,skill))throw new IllegalArgumentException("transform receipt binding missing");state.publishSkillActivation(actor.seat(),skill);
  }
  public void commitTransform(dev.sgs.core.game.GameState state,SgsPlayer actor,dev.sgs.core.card.GameCard card){
    var receipt=pendingTransforms.remove(card);
    if(receipt!=null && receipt.ownerSeat()==actor.seat())state.publishSkillActivation(receipt.ownerSeat(),receipt.skillId());
  }

  public List<SkillTriggerRecord> triggerLog() {
    return List.copyOf(triggerLog);
  }
}
