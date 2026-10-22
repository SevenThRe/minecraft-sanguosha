package dev.sgs.core.engine;

import dev.sgs.core.engine.skill.equipment.EquipmentDamageRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import dev.sgs.core.card.VirtualCard;

import dev.sgs.core.GameStatus;
import dev.sgs.core.Identity;
import dev.sgs.core.Phase;
import dev.sgs.core.card.CardInstance;
import dev.sgs.core.card.CardType;
import dev.sgs.core.card.Suit;
import dev.sgs.core.card.GameCard;
import dev.sgs.core.card.CardMove;
import dev.sgs.core.card.CardMovement;
import dev.sgs.core.card.CardZone;
import static dev.sgs.core.card.CardMove.Reason.*;
import dev.sgs.core.engine.skill.ActiveSkill;
import dev.sgs.core.engine.skill.OrderStrategy;
import dev.sgs.core.engine.skill.ResolutionStack;
import dev.sgs.core.engine.skill.ResolutionTask;
import dev.sgs.core.engine.skill.RuleId;
import dev.sgs.core.engine.skill.RuleFoldProgram;
import dev.sgs.core.engine.skill.SkillContext;
import dev.sgs.core.engine.skill.SkillEngine;
import dev.sgs.core.engine.skill.SkillEventBus;
import dev.sgs.core.engine.skill.SkillRegistry;
import dev.sgs.core.engine.skill.SkillResult;
import dev.sgs.core.engine.skill.SkillTriggerRecord;
import dev.sgs.core.engine.skill.Timing;
import dev.sgs.core.game.GameState;
import dev.sgs.core.player.SgsPlayer;
import dev.sgs.core.util.Clock;

/**
 * Synchronous settlement engine for the MVP basic cards and the ask/response queue. Every public
 * action validates fully before mutating state, so a rejected action leaves the game byte-for-byte
 * unchanged.
 *
 * <p>Skills, equipment effects and delayed tricks arbitrate through the {@link SkillEventBus}
 * (timing + priority), while nested settlement (damage &rarr; dying &rarr; after-damage) runs on
 * the explicit {@link ResolutionStack} instead of recursion.
 */
public final class SgsGameEngine implements SkillEngine {

  private final GameState state;
  private final PromptResponder responder;
  private final Clock clock;
  private final int responseTimeoutMillis;
  private final List<GameEventListener> listeners = new ArrayList<>();
  private final List<GameEventListener> presentationListeners = new ArrayList<>();
  /** Transient UI notifications must not mutate rules; reattach these after restore. */
  public void addPresentationListener(GameEventListener listener){presentationListeners.add(java.util.Objects.requireNonNull(listener));}
  private List<GameEventListener> eventListeners(){var all=new ArrayList<>(listeners);all.addAll(presentationListeners);return all;}


  private final SkillEventBus bus;
  private final SkillRegistry skills;
  private final ResolutionStack<SgsGameEngine> stack = new ResolutionStack<>();
  private long promptSequence;
  private GameOutcome outcome = GameOutcome.ONGOING;
  private BattleReport report;
  private AttackContinuation activeAttack;
  // Synchronous adapter context; not a resumable root or a license to save its caller stack.
  private AttackContinuation executingAttack;
  private TrickContinuation activeTrick;
  private ActiveSkillContinuation activeSkill;
  private TurnEndContinuation activeTurnEnd;
  private TurnStartContinuation activeTurnStart;
  private boolean hasActiveContinuation(){return activeAttack!=null||activeTrick!=null||activeSkill!=null||activeTurnEnd!=null||activeTurnStart!=null;}
  private final java.util.Deque<java.util.Set<java.util.UUID>> counterspellScopes=new java.util.ArrayDeque<>();
  // Scope is rule-worker private and never inferred from a client's supplied identifiers.
  void beginCounterspellScope(){counterspellScopes.push(new java.util.HashSet<>());}
  void endCounterspellScope(){if(counterspellScopes.isEmpty())throw new IllegalStateException("missing counterspell scope");counterspellScopes.pop();}

  private boolean legacyAttackOracle;
  private Set<Integer> attackGroupTargets=Set.of();
  // A yielded hook resumes through its real registered listener, consuming one prevalidated input.
  private DeferredInvoke deferredInvoke;
  private DeferredChoice deferredChoice;
  private static final class DeferredChoice {
    final int actor,subject;final PromptType type;final String skill;final Optional<SkillChoice> answer;boolean consumed;
    DeferredChoice(int actor,int subject,PromptType type,String skill,Optional<SkillChoice> answer){this.actor=actor;this.subject=subject;this.type=type;this.skill=skill;this.answer=answer;}
  }
  private static final class DeferredInvoke {
    final int actor,subject;final String skill;final boolean accepted;boolean consumed;
    DeferredInvoke(int actor,int subject,String skill,boolean accepted){this.actor=actor;this.subject=subject;this.skill=skill;this.accepted=accepted;}
  }
  private final Thread checkpointOwner=Thread.currentThread();
  private int checkpointOperations;
  private boolean checkpointFaulted;
  private List<String> checkpointInitialBindings;
  private List<String> checkpointInitialHeroes;
  private long checkpointMovementRevision;
  private List<String> checkpointHeroDefinitions(){return state.players().stream().map(p->{var h=p.hero();return h==null?"":h.id()+"/"+h.kingdom()+"/"+h.maxHp()+"/"+h.skillIds();}).toList();}

  /** Transitional safe-point API; it deliberately refuses pending legacy continuations. */
  public dev.sgs.core.persistence.IdleEngineCheckpoint checkpoint(String contentVersion){
    if(Thread.currentThread()!=checkpointOwner)throw new IllegalStateException("checkpoint must run on engine owner");
    if(hasActiveContinuation() || executingAttack!=null || checkpointOperations!=0 || checkpointFaulted || !stack.checkpointIdle() || !bus.checkpointIdle()
        || !state.cards().cards(CardZone.PROCESSING).isEmpty())throw new IllegalStateException("engine is not at a safe point");
    if(!bus.checkpointBindings().equals(checkpointInitialBindings))throw new IllegalStateException("unversioned skill registrations changed");
    if(!checkpointHeroDefinitions().equals(checkpointInitialHeroes))throw new IllegalStateException("hero changed without versioned rebinding");
    if(!listeners.isEmpty() || state.cards().observerCount()!=0 || state.logObserverCount()!=0
        || state.cards().ruleListenerRevision()!=checkpointMovementRevision)throw new IllegalStateException("unversioned observers cannot be restored");
    if(state.status()!=GameStatus.RUNNING || (state.phase()!=dev.sgs.core.Phase.PLAY && state.phase()!=dev.sgs.core.Phase.DISCARD))
      throw new IllegalStateException("idle checkpoint requires a play or discard window");
    var missingHeroContract=unsupportedCheckpointHero();
    if(missingHeroContract!=null)throw new IllegalStateException("hero lacks checkpoint contract: "+missingHeroContract);
    var saved=new dev.sgs.core.persistence.IdleEngineCheckpoint(1,
        dev.sgs.core.persistence.StateCheckpoint.capture(state,contentVersion),promptSequence,responseTimeoutMillis/1000,
        skills.saveMemory(),bus.checkpointBindings(),state.publicSkillSequence(),state.publicSkillActivations());
    saved.validate();return saved;
  }

  private String unsupportedCheckpointHero(){
    for(var player:state.players()){
      var h=player.hero();
      boolean known=dev.sgs.core.hero.StandardHeroes.CATALOG.stream().anyMatch(c->c.id().equals(h.id())
          && c.kingdom()==h.kingdom() && c.maxHp()==h.maxHp() && c.male()==h.male() && c.skillIds().equals(h.skillIds()));
      if(!known && !skills.supportsExternalIdleCheckpoint(player))return h.id();
    }
    return null;
  }

  /** Bootstrap a new engine, not the old stack or old network connection. */
  public static SgsGameEngine restoreIdle(dev.sgs.core.persistence.IdleEngineCheckpoint saved,String expectedContent,
      PromptResponder responder,Clock clock){
    saved.validate();
    if(!saved.state().contentVersion().equals(expectedContent))throw new IllegalArgumentException("content version mismatch");
    var restored=saved.state().restore();
    var engine=new SgsGameEngine(restored,responder,clock,saved.responseTimeoutSeconds());
    engine.skills.restoreMemory(saved.skillMemory(),restored.turnNumber());
    if(!engine.bus.checkpointBindings().equals(saved.bindings()))throw new IllegalArgumentException("skill binding order mismatch");
    engine.promptSequence=saved.promptSequence();
    restored.restoreSkillActivations(saved.skillSequence(),saved.activations());
    // Reuse capture validation to reject modified definitions/unsupported phase and prove a
    // complete current contract. No dealing, opening a turn, or public callbacks happen here.
    engine.checkpoint(expectedContent);
    return engine;
  }

  public SgsGameEngine(
      GameState state, PromptResponder responder, Clock clock, int responseTimeoutSeconds) {
    this.state = state;
    this.responder = responder;
    this.clock = clock;
    this.responseTimeoutMillis = responseTimeoutSeconds * 1000;
    this.bus = new SkillEventBus(clock::nowMillis);
    this.skills = new SkillRegistry(bus,this);
    state.ruleHeroTraits(skills.mythic()::has,skills.mythic()::kingdom,skills.mythic()::male);
    state.rulePublicStatuses(skills.mythic()::statuses);
    state.ruleHandLimit(skills.mythic()::handLimit);
    state.ruleNextTurn(skills.mythic()::nextTurnSeat);
    state.ruleSkillDistance(p->skills.mythic().has(p,"tuntian")?-state.cards().cards(CardZone.special(p.seat())).size():0);
    state.cards().setRuleListener(move->{
      if(capturingCostMovements!=null&&!movementDrawDispatch){capturingCostMovements.enqueue(this,move);return;}
      if(activeSkill!=null&&!movementDrawDispatch){activeSkill.enqueue(this,move);return;}
      if(activeTrick!=null&&!movementDrawDispatch){activeTrick.enqueue(move);return;}
      trickDispatchMovement(move);
    });
    bindSkills();
    checkpointInitialBindings=bus.checkpointBindings();
    checkpointInitialHeroes=checkpointHeroDefinitions();
    checkpointMovementRevision=state.cards().ruleListenerRevision();
  }

  /** Registers the hero skill hooks of every seat; safe to call again after hero reassignment. */
  public void bindSkills() {
    checkpointOperations++;
    try {
    skills.registerBuiltIns();
    for (SgsPlayer player : state.players()) {
      List<String> unknown = skills.bindHero(player);
      if (!unknown.isEmpty()) {
        state.log("seat {} references unknown skill ids, skipped: {}", player.seat(), unknown);
      }
    }

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  @Override public dev.sgs.core.card.Suit requestFanJianSuit(SgsPlayer target,SgsPlayer source){
    checkpointOperations++;
    try {
    var prompt=new PromptRequest(nextPromptId(),PromptType.FANJIAN_SUIT,target,source,List.of(),List.of(),clock.nowMillis()+responseTimeoutMillis,"fanjian");
    return responder.chooseSuit(state,prompt).filter(s->s!=dev.sgs.core.card.Suit.NONE).orElse(dev.sgs.core.card.Suit.SPADE);

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }
  @Override public void dealSkillDamage(SgsPlayer source,SgsPlayer target,int amount){
    checkpointOperations++;
    try {applyDamage(target,amount,source,null);
    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  public dev.sgs.core.engine.skill.skills.MythicRules mythic(){return skills.mythic();}
  @Override public void versionedSkillLayoutChanged(){checkpointInitialBindings=bus.checkpointBindings();}
  @Override public boolean faceDown(SgsPlayer player){return skills.mythic().faceDown(player);}
  public List<String> effectiveSkillIds(SgsPlayer p){return skills.mythic().effectiveSkills(p);}
  @Override public void turnOver(SgsPlayer p){skills.mythic().turnOver(p);state.log("seat {} turns over: {}",p.seat(),skills.mythic().faceDown(p));}
  @Override public void dealElementalSkillDamage(SgsPlayer source,SgsPlayer target,int amount,DamageNature nature){applyDamage(target,amount,source,null,nature,false);}
  @Override public void transferSkillDamage(SgsPlayer source,SgsPlayer target,int amount,GameCard cause,DamageNature nature){applyDamage(target,amount,source,cause,nature,false,true);}
  @Override public boolean useVirtualAttack(SgsPlayer source,SgsPlayer target,boolean ignoreDistance){
    var card=new VirtualCard(CardType.SHA,List.of());
    if(target==null||target==source||!target.alive()||!cardTargetAllowed(source,target,card)||(!ignoreDistance&&!state.inBasicAttackRange(source,target)))return false;
    cardUseCommitted(source,card);resolveAttack(source,target,1,card);bus.trigger(Timing.CARD_FINISHED,SkillContext.cardUse(state,source,card).at(Timing.CARD_FINISHED));return true;
  }
  @Override public boolean useVirtualCard(SgsPlayer source,GameCard card,SgsPlayer target,SgsPlayer secondary){
    return dispatch(source,card,card.type(),target,secondary).accepted();
  }
  @Override public boolean useProvidedSkillAttack(SgsPlayer source,SgsPlayer target,GameCard card){
    if(!EquipmentDamageRules.attack(card)||target==null||!target.alive()||target==source||!cardTargetAllowed(source,target,card)||!validAttackRangeAfterCost(source,target,card))return false;
    cardUseCommitted(source,card);resolveAttack(source,target,1,card);return true;
  }
  /** Legality probe for a Guhuo declaration; never transforms, pays, or triggers skills. */
  public boolean mythicDeclarationLegal(SgsPlayer source,GameCard card,SgsPlayer target){
    return mythicDeclarationLegal(source,card,target,null);
  }
  public boolean mythicDeclarationLegal(SgsPlayer source,GameCard card,SgsPlayer target,SgsPlayer secondary){
    if(state.status()!=GameStatus.RUNNING||!source.alive())return false;
    var type=card.type();
    if(EquipmentDamageRules.attack(card))return validateSha(source,target).accepted()&&cardTargetAllowed(source,target,card);
    if(type==CardType.TAO)return validateTao(source).accepted();
    if(type==CardType.JIU)return validateJiu().accepted();
    if(type==CardType.HUOGONG)return FireAttackRule.validate(this,source,target,card).accepted()&&cardTargetAllowed(source,target,card);
    if(type==CardType.JIEDAO)return validateJiedao(source,target,secondary,card).accepted()&&cardTargetAllowed(source,target,card);
    if(type==CardType.GUOHE||type==CardType.SHUNSHOU||type==CardType.JUEDOU)
      return validateSingleTarget(source,target,type).accepted()&&cardTargetAllowed(source,target,card);
    return Set.of(CardType.WUZHONG,CardType.NANMAN,CardType.WANJIAN,CardType.TAOYUAN,CardType.WUGU).contains(type)
        ||type==CardType.TIESUO&&target!=null&&target.alive()&&cardTargetAllowed(source,target,card);
  }

  @Override public boolean requestSkillInvoke(SgsPlayer actor,String skillId,SgsPlayer subject){
    checkpointOperations++;
    try {
    if(!state.players().contains(actor) || !actor.alive())return false;
    if(deferredInvoke!=null){
      var input=deferredInvoke;
      if(input.consumed || input.actor!=actor.seat() || input.subject!=seatOf(subject) || !input.skill.equals(skillId))
        throw new IllegalStateException("deferred invocation does not match resumed hook");
      input.consumed=true;return input.accepted;
    }
    var prompt=new PromptRequest(nextPromptId(),PromptType.SKILL_INVOKE,actor,subject,
        List.of(),List.of(),clock.nowMillis()+responseTimeoutMillis,skillId);
    try{return responder.invoke(state,prompt) && clock.nowMillis()<=prompt.deadlineMillis() && actor.alive();}
    catch(RuntimeException e){state.log("skill responder error for seat {}: {}",actor.seat(),e.toString());return false;}

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  private record ChoiceResult(Optional<SkillChoice> choice,boolean invalid){}
  @Override public Optional<SkillChoice> requestSkillChoice(SgsPlayer actor,SgsPlayer subject,
      PromptType type,String skill,List<CardInstance> cards,List<Integer> targets,boolean hiddenHand){
    return requestChoice(actor,subject,type,skill,cards,targets,hiddenHand).choice();
  }
  /** Same request/validation path as skills, retaining invalid versus declined for card effects. */
  private ChoiceResult requestChoice(SgsPlayer actor,SgsPlayer subject,
      PromptType type,String skill,List<CardInstance> cards,List<Integer> targets,boolean hiddenHand){
    return requestChoice(actor,subject,type,skill,cards,targets,hiddenHand,false);
  }
  @Override public Optional<SkillChoice> requestDyingSkillChoice(SgsPlayer actor,SgsPlayer victim,
      PromptType type,String skill,List<CardInstance> cards,boolean hiddenHand){
    if(victim==null||!state.players().contains(victim)||!victim.alive()||victim.hp()>0)return Optional.empty();
    return requestChoice(actor,victim,type,skill,cards,List.of(),hiddenHand,actor==victim).choice();
  }
  private ChoiceResult requestChoice(SgsPlayer actor,SgsPlayer subject,
      PromptType type,String skill,List<CardInstance> cards,List<Integer> targets,boolean hiddenHand,boolean dyingSelf){
    checkpointOperations++;
    try {
      if(!state.players().contains(actor) || !actor.alive() || actor.hp()<=0&&!dyingSelf&&!skills.mythic().survivesBuqu(actor))return new ChoiceResult(Optional.empty(),false);
      if(deferredChoice!=null){
        var input=deferredChoice;
        if(input.consumed || input.actor!=actor.seat() || input.subject!=seatOf(subject) || input.type!=type || !input.skill.equals(skill))throw new IllegalStateException("deferred choice does not match resumed hook");
        input.consumed=true;return new ChoiceResult(input.answer,false);
      }
      var prompt=new PromptRequest(nextPromptId(),type,actor,subject,List.of(),cards,
          clock.nowMillis()+responseTimeoutMillis,skill,targets,hiddenHand);
      try{
        var selected=responder.choose(state,prompt);
        if(clock.nowMillis()>prompt.deadlineMillis() || !actor.alive() || actor.hp()<=0&&!dyingSelf&&!skills.mythic().survivesBuqu(actor) || selected==null || selected.isEmpty())
          return new ChoiceResult(Optional.empty(),false);
        var choice=selected.get();
        boolean valid=(prompt.allowsHiddenChoice(choice.cardId()) || cards.stream().anyMatch(card->card.instanceId()==choice.cardId()))
            && (targets.isEmpty()?choice.targetSeat()==-1:targets.contains(choice.targetSeat()));
        return new ChoiceResult(valid?selected:Optional.empty(),!valid);
      }catch(RuntimeException e){state.log("skill choice failed for seat {}: {}",actor.seat(),e.toString());return new ChoiceResult(Optional.empty(),false);}
    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  @Override public boolean takeFeedbackCard(SgsPlayer actor,SgsPlayer source,SkillChoice choice){
    checkpointOperations++;
    try {
    if(!state.players().contains(actor) || !state.players().contains(source) || !actor.alive())return false;
    CardInstance card;
    if(choice.cardId()==0){
      if(source==actor || source.handView().isEmpty())return false;
      card=source.handView().get(state.random().nextInt(source.handCount()));
    }else if(choice.cardId()<=-2){
      int index=HandSelection.positionIndex(choice.cardId(),source.handCount());
      if(source==actor || index<0)return false;
      card=source.handView().get(index);
    }else card=source.equipment().all().stream().filter(c->c.instanceId()==choice.cardId()).findFirst().orElse(null);
    if(card==null)return false;
    var zone=state.cards().locate(card.instanceId());
    state.cards().move(List.of(card),zone,CardZone.hand(actor.seat()),EFFECT_GAIN,actor.seat());return true;

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  @Override public boolean requestProvidedDodge(SgsPlayer helper,SgsPlayer lord){
    checkpointOperations++;
    try {
    if(!state.players().contains(helper) || !helper.alive())return false;
    var armor=helper.equipment().armor();
    if(armor!=null && armor.type()==CardType.BAGUA && requestSkillInvoke(helper,"bagua",lord)){
      var judged=judgeFor(helper,armor);
      if(judged!=null && judged.isRed())return true;
      // A failed optional armor judgment does not consume the right to supply a hand Shan.
    }
    var prompt=new PromptRequest(nextPromptId(),PromptType.HUJIA_SHAN,helper,lord,
        List.of(CardType.SHAN),List.of(),clock.nowMillis()+responseTimeoutMillis,"hujia");
    var decision=ask(prompt);
    if(decision.isPresent()){spendDecision(helper,decision.get());return true;}
    return false;

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  public java.util.Map<String,String> activeSkillAvailability(SgsPlayer player){return skills.activeAvailability(player);}

  public SkillEventBus skillBus() {
    return bus;
  }

  private SgsPlayer providedAttackTarget;
  private Optional<GameCard> provideJijiangAgainst(SgsPlayer lord,boolean offer,SgsPlayer target){
    if(target!=null&&!legacyAttackOracle&&!skills.mythic().active()){
      var supply=runAttackSupply(lord,target,PromptType.QINGLONG_SHA,offer,false);
      return supply.snapshot().receipt()==null?Optional.empty():Optional.of(supply.card(this));
    }
    var previous=providedAttackTarget;providedAttackTarget=target;
    try{return skills.provideJijiang(lord,offer);}finally{providedAttackTarget=previous;}
  }
  private boolean emptiesKongcheng(SgsPlayer player,GameCard cost){return state.hasSkill(player,"kongcheng")
      && player.handView().stream().allMatch(c->cost.sourceCards().stream().anyMatch(x->x.instanceId()==c.instanceId()));}
  @Override public Optional<GameCard> requestProvidedAttack(SgsPlayer helper,SgsPlayer lord){
    checkpointOperations++;
    try {
    if(!state.players().contains(helper) || !helper.alive())return Optional.empty();
    var prompt=new PromptRequest(nextPromptId(),PromptType.JIJIANG_SHA,helper,lord,
        List.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA),List.of(),
        clock.nowMillis()+responseTimeoutMillis,"jijiang");
    var decision=ask(prompt);
    if(decision.isEmpty())return Optional.empty();
    if(providedAttackTarget!=null && (!cardTargetAllowed(lord,providedAttackTarget,decision.get().card())
        || helper==providedAttackTarget && emptiesKongcheng(helper,decision.get().card())))return Optional.empty();
    beginOwnedUse(helper,decision.get().card(),RESPONSE);
    return Optional.of(decision.get().card());

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  @Override public SkillResult useJijiangAttack(SgsPlayer lord,SgsPlayer target){
    checkpointOperations++;
    try {
    var validation=validateSha(lord,target);
    if(!validation.accepted())return SkillResult.reject(validation.reason().name());
    var supplied=provideJijiangAgainst(lord,false,target);
    if(supplied.isEmpty())return SkillResult.reject("no_provider");
    commitProcessedAttack(lord,supplied.get(),target);
    return SkillResult.ok();

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  private boolean answerJijiang(SgsPlayer lord){
    var supplied=skills.provideJijiang(lord,true);
    if(supplied.isEmpty())return false;
    state.cards().finish(supplied.get(),lord.seat());return true;
  }

  /** Structured skill trigger log, used for debugging and future replay. */
  public List<SkillTriggerRecord> skillTriggerLog() {
    return bus.triggerLog();
  }

  @Override
  public GameState gameState() {
    return state;
  }

  @Override
  public void transferCard(SgsPlayer from, SgsPlayer to, CardInstance card) {
    checkpointOperations++;
    try {
    if(!state.players().contains(from) || !state.players().contains(to))throw new IllegalArgumentException("unknown player");
    if(from!=to && CardZone.hand(from.seat()).equals(state.cards().locate(card.instanceId())))
      state.cards().move(List.of(card),CardZone.hand(from.seat()),CardZone.hand(to.seat()),SKILL_GIVE,from.seat());

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  @Override
  public int heal(SgsPlayer player, int amount) {
    checkpointOperations++;
    try {
    int healed = Math.min(amount, player.maxHp() - player.hp());
    if (healed > 0) {
      player.setHp(player.hp() + healed);
      skills.mythic().healed(player);
    }
    return Math.max(healed, 0);

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  @Override public void discardCards(SgsPlayer owner,List<CardInstance> costs){
    discardOwnedCards(owner,owner,costs);
  }
  private void discardOwnedCards(SgsPlayer owner,SgsPlayer actor,List<CardInstance> costs){
    checkpointOperations++;
    try {
    if(!state.players().contains(owner) || !state.players().contains(actor) || costs==null || costs.stream().map(CardInstance::instanceId).distinct().count()!=costs.size())throw new IllegalArgumentException("invalid discard costs");
    var changes=new ArrayList<CardMovement.Transfer>();
    var grouped=new java.util.LinkedHashMap<CardZone,List<CardInstance>>();
    for(var cost:costs){
      var zone=state.cards().locate(cost.instanceId());
      if(!CardZone.hand(owner.seat()).equals(zone) && !CardZone.equipment(owner.seat()).equals(zone))throw new IllegalArgumentException("unowned discard cost");
      grouped.computeIfAbsent(zone,ignored->new ArrayList<>()).add(cost);
    }
    grouped.forEach((zone,cards)->changes.add(new CardMovement.Transfer(cards,zone,CardZone.DISCARD,DISCARD,actor.seat(),true)));
    state.cards().moveBatch(changes);

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  @Override
  public List<CardInstance> drawCards(SgsPlayer recipient,int count) {
    checkpointOperations++;
    try {
    if(!state.players().contains(recipient))throw new IllegalArgumentException("unknown recipient");
    return state.cards().drawTo(CardZone.hand(recipient.seat()),count,DRAW,recipient.seat(),false);

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }
  @Override public int obtainProcessingCards(SgsPlayer recipient,GameCard card){
    checkpointOperations++;
    try {
    if(!state.players().contains(recipient))throw new IllegalArgumentException("unknown recipient");
    return recipient.alive()?state.cards().obtainProcessing(recipient,card):0;

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  public GameState state() {
    return state;
  }

  public GameOutcome outcome() {
    return outcome;
  }

  public Optional<BattleReport> report() {
    return Optional.ofNullable(report);
  }

  public void addListener(GameEventListener listener) {
    listeners.add(listener);
  }

  /** Plays a card from the player's hand. Target is null for self-only cards. */
  public ActionResult playCard(SgsPlayer player, long cardInstanceId, SgsPlayer target) {
    checkpointOperations++;
    try {
    return playCard(player, cardInstanceId, target, null);

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  /**
   * Plays a card; {@code secondaryTarget} is only used by Borrow Sword (the victim the weapon
   * holder is told to attack).
   */
  public ActionResult playCard(
      SgsPlayer player, long cardInstanceId, SgsPlayer target, SgsPlayer secondaryTarget) {
    checkpointOperations++;
    try {
    return playCardAs(player, cardInstanceId, null, target, secondaryTarget);

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  /**
   * Plays a card while declaring it as another type (view-as skills). A null {@code declaredAs}
   * means the physical type. Any alternation must be confirmed by a CARD_TRANSFORM listener
   * (Longdan), otherwise the action is rejected before any state change.
   */
  public ActionResult playCardAs(
      SgsPlayer player,
      long cardInstanceId,
      CardType declaredAs,
      SgsPlayer target,
      SgsPlayer secondaryTarget) {
    checkpointOperations++;
    try {
    if(hasActiveContinuation())return ActionResult.rejected(RejectReason.WRONG_PHASE);
    if (state.status() != GameStatus.RUNNING) {
      return ActionResult.rejected(RejectReason.GAME_NOT_RUNNING);
    }
    if (player != state.currentPlayer()) {
      return ActionResult.rejected(RejectReason.NOT_YOUR_TURN);
    }
    if (state.phase() != Phase.PLAY) {
      return ActionResult.rejected(RejectReason.WRONG_PHASE);
    }
    CardInstance card = findOwnedCost(player, cardInstanceId);
    if (card == null) {
      return ActionResult.rejected(RejectReason.CARD_NOT_IN_HAND);
    }
    CardType asType = declaredAs == null ? card.type() : declaredAs;
    boolean equipped=CardZone.equipment(player.seat()).equals(state.cards().locate(cardInstanceId));
    if(equipped && ((asType!=CardType.SHA && asType!=CardType.GUOHE && asType!=CardType.LEBU && asType!=CardType.BINGLIANG) || card.type()==asType))return ActionResult.rejected(RejectReason.CARD_NOT_IN_HAND);
    GameCard settled = card;
    if (asType != card.type()) {
      // CARD_TRANSFORM sits between the declared action's legality check and its settlement:
      // validate first so an illegal view-as play never asks skills to confirm a transform.
      var legality=validateTransformPlay(player,card,asType,target,secondaryTarget);
      if(!legality.accepted())return legality;
      SkillContext ctx = SkillContext.transform(state, player, card, asType, null);
      bus.trigger(Timing.CARD_TRANSFORM, ctx);
      if (!ctx.transformConfirmed()) {
        return ActionResult.rejected(RejectReason.TRANSFORM_NOT_AVAILABLE);
      }
      // confirmTransform() swapped the context card to the virtual counterpart.
      settled = ctx.card();
    }
    return dispatch(player, settled, asType, target, secondaryTarget);

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  /** Validate the declared use before a transform hook can charge or publish its activation. */
  private ActionResult validateTransformPlay(SgsPlayer source,CardInstance physical,CardType type,SgsPlayer target,SgsPlayer secondary){
    GameCard card=new VirtualCard(type,List.of(physical),type==CardType.TIESUO && bus.canTransformRecast(SkillContext.transform(state,source,physical,type,null)));
    if(type.isEquipment())return ActionResult.rejected(RejectReason.TRANSFORM_NOT_AVAILABLE);
    if(target!=null && !state.players().contains(target) || secondary!=null && !state.players().contains(secondary))
      return ActionResult.rejected(RejectReason.TARGET_NOT_IN_GAME);
    ActionResult result;
    if(EquipmentDamageRules.attack(card))result=validateShaCost(source,target,physical);
    else if(type.category()==dev.sgs.core.card.CardCategory.DELAYED_TRICK)result=validateDelayedTrick(source,card,target);
    else result=switch(type){
      case TAO->validateTao(source);
      case JIU->validateJiu();
      case SHAN->ActionResult.rejected(RejectReason.SHAN_NOT_PLAYABLE);
      case WUXIE->ActionResult.rejected(RejectReason.RESPONSE_ONLY);
      case GUOHE,SHUNSHOU,JUEDOU->validateSingleTarget(source,target,type);
      case HUOGONG->FireAttackRule.validate(this,source,target,card);
      case JIEDAO->validateJiedao(source,target,secondary,card);
      case TIESUO->TiesuoRule.validate(state,card,target,secondary);
      case WUZHONG,TAOYUAN,WUGU,NANMAN,WANJIAN->ActionResult.ok();
      default->ActionResult.rejected(RejectReason.UNKNOWN_CARD);
    };
    if(!result.accepted())return result;
    if(target!=null && !cardTargetAllowed(source,target,card) || type==CardType.TIESUO && secondary!=null && !cardTargetAllowed(source,secondary,card))
      return ActionResult.rejected(RejectReason.TARGET_PROHIBITED);
    return result;
  }

  private ActionResult dispatch(
      SgsPlayer player,
      GameCard card,
      CardType asType,
      SgsPlayer target,
      SgsPlayer secondaryTarget) {
    card=skills.mythic().effectiveCard(player,card);
    return switch (asType.category()) {
      case BASIC -> switch (asType) {
        case SHA, HUOSHA, LEISHA -> playAttack(player, card, target);
        case TAO -> playTao(player, card);
        case JIU -> playJiu(player, card);
        default -> ActionResult.rejected(RejectReason.SHAN_NOT_PLAYABLE);
      };
        // Equipment and delayed tricks are never transformed, so the single physical source is
        // safe.
      case WEAPON, ARMOR, HORSE_PLUS, HORSE_MINUS -> equipCard(player, card.sourceCards().get(0));
      case TRICK -> asType == CardType.WUXIE
          ? ActionResult.rejected(RejectReason.RESPONSE_ONLY)
          : playTrick(player, card, target, secondaryTarget);
      case DELAYED_TRICK -> playDelayedTrick(player, card, target);
    };
  }

  /** Lightning hangs on self; Acedia hangs on another alive player. No Negation at play time. */
  private ActionResult playDelayedTrick(SgsPlayer source, GameCard card, SgsPlayer target) {
    var verdict=validateDelayedTrick(source,card,target);if(!verdict.accepted())return verdict;
    var destination=card.type()==CardType.SHANDIAN?source:target;
    beginOwnedUse(source,card,USE);
    state.cards().moveBatch(List.of(new CardMovement.Transfer(card.sourceCards(),CardZone.PROCESSING,CardZone.judgment(destination.seat()),DELAYED_USE,source.seat(),true,card.type())));
    state.log("seat {} placed {} on seat {}", source.seat(), card.type().id(), destination.seat());
    return ActionResult.ok();
  }
  private ActionResult validateDelayedTrick(SgsPlayer source,GameCard card,SgsPlayer target){
    SgsPlayer destination;
    if (card.type() == CardType.SHANDIAN) {
      destination = source;
    } else {
      if (target == null) {
        return ActionResult.rejected(RejectReason.TARGET_REQUIRED);
      }
      if (target == source) {
        return ActionResult.rejected(RejectReason.TARGET_SELF);
      }
      if (!target.alive()) {
        return ActionResult.rejected(RejectReason.TARGET_DEAD);
      }
      destination = target;
    }
    if(!cardTargetAllowed(source,destination,card))return ActionResult.rejected(RejectReason.TARGET_PROHIBITED);
    if(destination.delayZone().cards().stream().anyMatch(c->destination.delayZone().effectiveType(c)==card.type()))return ActionResult.rejected(RejectReason.DUPLICATE_DELAYED_TRICK);
    if(card.type()==CardType.BINGLIANG && state.distanceExcluding(source,destination,card.sourceCards().size()==1?card.sourceCards().getFirst():null)>bus.queryInt(RuleId.TRICK_DISTANCE_LIMIT,
        SkillContext.cardTarget(state,source,destination,card),1))return ActionResult.rejected(RejectReason.OUT_OF_RANGE);
    return ActionResult.ok();
  }

  /**
   * Runs the current player's whole turn: phase timing hooks, judgment resolution on entering
   * JUDGMENT, draw, play (skipped when Acedia hits), discard and handover to the next seat.
   */
  public ActionResult advanceTurn(SgsPlayer player) {
    checkpointOperations++;
    try {
    if(hasActiveContinuation())return ActionResult.rejected(RejectReason.WRONG_PHASE);
    if (state.status() != GameStatus.RUNNING) {
      return ActionResult.rejected(RejectReason.GAME_NOT_RUNNING);
    }
    if (player != state.currentPlayer()) {
      return ActionResult.rejected(RejectReason.NOT_YOUR_TURN);
    }
    SgsPlayer turnPlayer = player;
    if(state.phase()==Phase.PREPARE) {
      skills.mythic().initializeGame();
      if(skills.mythic().faceDown(player)){turnOver(player);state.skipTurn();return ActionResult.ok();}
      fireTiming(Timing.TURN_START, turnPlayer);
      firePhaseEntered();
    }
    for (int steps = 0; steps < 8; steps++) {
      if(state.phase()==Phase.PLAY && state.currentPlayer()==turnPlayer){enterDiscardWindow(turnPlayer);if(state.currentPlayer()!=turnPlayer)return ActionResult.ok();continue;}
      if(state.phase()==Phase.JUDGMENT && state.currentPlayer()==turnPlayer) enterDrawPhase(turnPlayer);
      else state.advancePhase();
      if (state.status() != GameStatus.RUNNING) {
        return ActionResult.ok();
      }
      if (state.phase() == Phase.JUDGMENT && state.currentPlayer() == turnPlayer) {
        firePhaseEntered();
        resolveJudgments(turnPlayer);
        continue;
      }
      if (state.phase() == Phase.DRAW
          && state.currentPlayer() == turnPlayer
          && state.skipPlayPhase()) {
        // Acedia hit: burn the play phase but still draw and discard.
        state.advancePhase(); // DRAW -> skipped PLAY; no play-start trigger
        enterDiscardWindow(turnPlayer);
        if(state.currentPlayer()!=turnPlayer)return ActionResult.ok();
        continue;
      }
      if (state.phase() == Phase.PREPARE && state.currentPlayer() != turnPlayer) {
        // The next seat's turn-start hooks belong to its own advanceTurn() call.
        return ActionResult.ok();
      }
      if(state.phase()!=Phase.DRAW) firePhaseEntered();
    }
    return ActionResult.ok();

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  /**
   * Drives the current player's turn from PREPARE through judgment and draw, parking in the PLAY
   * window (or DISCARD when an acedia judgment skipped play). The live match driver then services
   * player actions in the window and finishes the turn via {@link #endPlayPhase}/ {@link
   * #confirmDiscard}/{@link #timeoutTurn}. Unlike {@link #advanceTurn} this never fast- forwards
   * the play/discard windows.
   */
  public ActionResult openTurn(SgsPlayer player) {
    checkpointOperations++;
    try {
    if(hasActiveContinuation())return ActionResult.rejected(RejectReason.WRONG_PHASE);
    if (state.status() != GameStatus.RUNNING) {
      return ActionResult.rejected(RejectReason.GAME_NOT_RUNNING);
    }
    if (player != state.currentPlayer()) {
      return ActionResult.rejected(RejectReason.NOT_YOUR_TURN);
    }
    if (state.phase() != Phase.PREPARE) {
      return ActionResult.rejected(RejectReason.WRONG_PHASE);
    }
    skills.mythic().initializeGame();
    if(skills.mythic().faceDown(player)){turnOver(player);state.skipTurn();return ActionResult.ok();}
    fireTiming(Timing.TURN_START, player);
    firePhaseEntered(); // PHASE_PREPARE_START

    state.advancePhase(); // PREPARE -> JUDGMENT
    firePhaseEntered(); // PHASE_JUDGMENT_START
    resolveJudgments(player);
    if (state.status() != GameStatus.RUNNING) {
      return ActionResult.ok();
    }

    enterDrawPhase(player);
    if(state.status()!=GameStatus.RUNNING || state.phase()!=Phase.DRAW || state.currentPlayer()!=player) return ActionResult.ok();
    if (state.skipPlayPhase()) {
      // Acedia: the play window is burned but discard still runs.
      state.advancePhase(); // DRAW -> PLAY
      enterDiscardWindow(player);
    } else {
      state.advancePhase(); // DRAW -> PLAY
      firePhaseEntered(); // PHASE_PLAY_START
    }
    return ActionResult.ok();

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  private void enterDrawPhase(SgsPlayer player) {
    var before=SkillContext.phase(state,Timing.PHASE_DRAW_BEFORE,player);
    if(!state.skipDrawPhase())bus.trigger(Timing.PHASE_DRAW_BEFORE,before);
    if(before.phaseSkipped())state.setSkipDrawPhase(true);
    state.advancePhase(false);
    if(state.skipDrawPhase()){state.log("seat {} skips draw phase due to bingliang",player.seat());return;}
    firePhaseEntered();
    if(state.status()!=GameStatus.RUNNING || !player.alive() || state.phase()!=Phase.DRAW || state.currentPlayer()!=player) return;
    int count=Math.max(0,bus.queryInt(RuleId.DRAW_COUNT,SkillContext.forPlayer(state,RuleId.DRAW_COUNT,player),state.normalDrawCount()));
    List<CardInstance> drawn=drawCards(player,count);
    state.log("seat {} drew {} cards",player.seat(),drawn.size());
    fireTiming(Timing.PHASE_DRAW_END,player);
  }

  private void fireTiming(Timing timing, SgsPlayer owner) {
    bus.trigger(timing, SkillContext.phase(state, timing, owner));
  }

  private void firePhaseEntered() {
    if(state.phase()==Phase.PLAY){
      var before=SkillContext.phase(state,Timing.PHASE_PLAY_BEFORE,state.currentPlayer());bus.trigger(Timing.PHASE_PLAY_BEFORE,before);
      if(state.status()!=GameStatus.RUNNING)return;
      if(before.phaseSkipped()){enterDiscardWindow(state.currentPlayer());return;}
    }
    if(state.phase()==Phase.END)fireTiming(Timing.PHASE_DISCARD_END,state.currentPlayer());
    Timing timing =
        switch (state.phase()) {
          case PREPARE -> Timing.PHASE_PREPARE_START;
          case JUDGMENT -> Timing.PHASE_JUDGMENT_START;
          case DRAW -> Timing.PHASE_DRAW_START;
          case PLAY -> Timing.PHASE_PLAY_START;
          case DISCARD -> Timing.PHASE_DISCARD_START;
          case END -> Timing.PHASE_TURN_END;
          default -> null;
        };
    if (timing != null) {
      fireTiming(timing, state.currentPlayer());
    }
  }

  /**
   * Settles the delay zone in reverse placement order (the later a delayed trick was attached, the
   * earlier it judges). Each card runs the judge pipeline: reveal &rarr; multi-entity modification
   * &rarr; effect.
   */
  void resolveJudgments(SgsPlayer player) {
    checkpointOperations++;
    try {
    var before=SkillContext.phase(state,Timing.PHASE_JUDGMENT_BEFORE,player);bus.trigger(Timing.PHASE_JUDGMENT_BEFORE,before);
    if(before.phaseSkipped())return;
    List<CardInstance> pending = player.delayZone().judgmentOrder();
    for (CardInstance trick : pending) {
      if (state.status() != GameStatus.RUNNING || !player.alive()) {
        return;
      }
      if(!CardZone.judgment(player.seat()).equals(state.cards().locate(trick.instanceId())))continue;
      var delayedType=player.delayZone().effectiveType(trick);
      beginCounterspellScope();try{
      state.cards().move(List.of(trick),CardZone.judgment(player.seat()),CardZone.PROCESSING,USE,player.seat());
      if (isCounteredByWuxie(player)) {
        state.cards().finish(trick,player.seat());
        state.log("seat {}'s {} was negated before judgment", player.seat(), trick.cardId());
        continue;
      }
      CardInstance judged = judgeFor(player,trick);
      try{applyJudgment(player,trick,judged,delayedType);}finally{state.cards().finish(trick,player.seat());}
      }finally{endCounterspellScope();}
    }

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  private void applyJudgment(SgsPlayer player, CardInstance trick, CardInstance judged,CardType delayedType) {
    switch (delayedType) {
      case BINGLIANG -> {
        if(judged!=null && skills.mythic().suit(player,judged)!=dev.sgs.core.card.Suit.CLUB)state.setSkipDrawPhase(true);
        state.log("seat {} bingliang skips_draw={}",player.seat(),state.skipDrawPhase());
      }
      case LEBU -> {
        if (judged != null && skills.mythic().suit(player,judged) != dev.sgs.core.card.Suit.HEART) {
          state.setSkipPlayPhase(true);
          state.log("seat {} is acedia-bound and skips the play phase", player.seat());
        } else {
          state.log("seat {} shakes off acedia", player.seat());
        }
      }
      case SHANDIAN -> {
        boolean struck =
            judged != null
                && skills.mythic().suit(player,judged) == dev.sgs.core.card.Suit.SPADE
                && judged.rank().value() >= 2
                && judged.rank().value() <= 9;
        if (struck) {
          state.log("lightning struck seat {} for 3", player.seat());
          applyDamage(player,3,null,trick,DamageNature.THUNDER,false);
        } else {
          SgsPlayer next=state.alivePlayersAfter(player.seat()).stream()
              .filter(p->p.delayZone().cards().stream().noneMatch(c->c.type()==trick.type())).findFirst().orElse(player);
          state.cards().move(List.of(trick),CardZone.PROCESSING,CardZone.judgment(next.seat()),DELAY_PASS,player.seat());
          state.log("lightning passes to seat {}", next.seat());
        }
      }
      default -> {}
    }
  }

  /** Equips a weapon/armor/horse; the replaced card (if any) goes to the discard pile. */
  private ActionResult equipCard(SgsPlayer player, CardInstance card) {
    state.cards().beginUse(player,card,USE);
    var replaced=player.equipment().all().stream().filter(c->c.type().category()==card.type().category()).findFirst();
    var changes=new ArrayList<CardMovement.Transfer>();
    replaced.ifPresent(old->changes.add(new CardMovement.Transfer(List.of(old),CardZone.equipment(player.seat()),CardZone.DISCARD,EQUIPMENT_REPLACED,player.seat(),true)));
    changes.add(new CardMovement.Transfer(List.of(card),CardZone.PROCESSING,CardZone.equipment(player.seat()),EQUIP,player.seat(),true));
    state.cards().moveBatch(changes);
    state.log("seat {} equipped {}", player.seat(), card.cardId());
    return ActionResult.ok();
  }

  // ---------------------------------------------------------------------------------------------
  // Trick cards
  // ---------------------------------------------------------------------------------------------

  private ActionResult playTrick(
      SgsPlayer source, GameCard card, SgsPlayer target, SgsPlayer secondaryTarget) {
    if(target!=null && !cardTargetAllowed(source,target,card))return ActionResult.rejected(RejectReason.TARGET_PROHIBITED);
    if(card.type()==CardType.TIESUO)return TiesuoRule.play(this,source,card,target,secondaryTarget);
    ActionResult validation =
        switch (card.type()) {
          case WUZHONG, TAOYUAN, WUGU, NANMAN, WANJIAN -> ActionResult.ok();
          case JUEDOU, GUOHE, SHUNSHOU -> validateSingleTarget(source, target, card.type());
          case HUOGONG -> FireAttackRule.validate(this,source,target,card);
          case JIEDAO -> validateJiedao(source, target, secondaryTarget,card);
          default -> ActionResult.rejected(RejectReason.UNKNOWN_CARD);
        };
    if (!validation.accepted()) {
      return validation;
    }

    // Confirmed view-as costs can come from equipment; all costs share the same atomic path.
    beginCounterspellScope();
    try{
    beginOwnedUse(source,card,USE);
    cardUseCommitted(source,card);
    try{switch (card.type()) {
      case WUZHONG -> resolveWuzhong(source);
      case GUOHE -> resolveGuohe(source, target);
      case SHUNSHOU -> resolveShunshou(source, target);
      case JUEDOU -> resolveJuedou(source, target, card);
      case NANMAN -> resolveAoeSha(source, true, card);
      case WANJIAN -> resolveAoeSha(source, false, card);
      case TAOYUAN -> resolveTaoyuan(source);
      case WUGU -> resolveWugu(source);
      case JIEDAO -> resolveJiedao(source, target, secondaryTarget);
      case HUOGONG -> FireAttackRule.resolve(this,source,target,card);
      default -> throw new IllegalStateException("unhandled trick " + card.type());
    }
    }finally{bus.trigger(Timing.CARD_FINISHED,SkillContext.cardUse(state,source,card).at(Timing.CARD_FINISHED));state.cards().finish(card,source.seat());}
    }finally{endCounterspellScope();}
    state.log("seat {} played {}", source.seat(), describe(card));
    return ActionResult.ok();
  }

  public boolean cardTargetAllowed(SgsPlayer source,SgsPlayer target,GameCard card){
    card=skills.mythic().effectiveCard(source,card);
    return bus.queryInt(RuleId.CARD_TARGET_ALLOWED,SkillContext.cardTarget(state,source,target,card),1)>0;
  }

  /** Audited simple-trick branches use explicit parents for every built-in standard hero. */
  public boolean supportsTrickContinuation(){
    if(skills.mythic().active())return false;
    // Expansion hooks run atomically on the full legacy path until their pending prompts are migrated.
    if(state.players().stream().anyMatch(p->p.hero()!=null && p.hero().skillIds().stream().anyMatch(java.util.Set.of("liegong","kuanggu","duanliang","qiangxi","yizhong","yaowu","yicong")::contains)))return false;
    if(!bus.checkpointBindings().equals(checkpointInitialBindings) || !checkpointHeroDefinitions().equals(checkpointInitialHeroes))return false;
    return state.players().stream().allMatch(p->{var h=p.hero();return h!=null && (skills.supportsExternalProgrammedCheckpoint(p)
        ||dev.sgs.core.hero.StandardHeroes.CATALOG.stream().anyMatch(c->c.id().equals(h.id())&&c.kingdom()==h.kingdom()&&c.maxHp()==h.maxHp()&&c.male()==h.male()&&c.skillIds().equals(h.skillIds())));});
  }
  public static boolean resumableTrickType(CardType type){return type!=null && java.util.Set.of(CardType.WUZHONG,CardType.GUOHE,CardType.SHUNSHOU,CardType.TAOYUAN,CardType.WUGU,CardType.NANMAN,CardType.WANJIAN,CardType.JUEDOU,CardType.TIESUO,CardType.HUOGONG,CardType.JIEDAO).contains(type);}
  public boolean supportsTrickContinuation(CardType type){
    return supportsTrickContinuation()&&(type!=CardType.JIEDAO||supportsAttackContinuation());
  }
  public boolean hasTrickContinuation(){return activeTrick!=null;}
  public long trickDecisionRevision(){return activeTrick==null?-1:activeTrick.revision();}
  public Optional<CounterspellWindow> pendingTrickWindow(){return activeTrick==null?Optional.empty():activeTrick.window();}
  public Optional<PromptRequest> pendingTrickPrompt(){return activeTrick==null?Optional.empty():activeTrick.prompt(this);}
  public ActionResult beginResumableTrick(SgsPlayer source,long cardId,CardType declared,SgsPlayer target){
    return beginResumableTrick(source,cardId,declared,target,null);
  }
  public ActionResult beginResumableTrick(SgsPlayer source,long cardId,CardType declared,SgsPlayer target,SgsPlayer secondaryTarget){
    assertContinuationIngress();
    if(hasActiveContinuation())return ActionResult.rejected(RejectReason.WRONG_PHASE);
    if(!supportsTrickContinuation())return ActionResult.rejected(RejectReason.NOT_YET_IMPLEMENTED);
    if(state.status()!=GameStatus.RUNNING)return ActionResult.rejected(RejectReason.GAME_NOT_RUNNING);
    if(source!=state.currentPlayer())return ActionResult.rejected(RejectReason.NOT_YOUR_TURN);
    if(state.phase()!=Phase.PLAY)return ActionResult.rejected(RejectReason.WRONG_PHASE);
    if(target!=null && !state.players().contains(target))return ActionResult.rejected(RejectReason.TARGET_NOT_IN_GAME);
    var cost=findOwnedCost(source,cardId);if(cost==null)return ActionResult.rejected(RejectReason.CARD_NOT_IN_HAND);
    CardType type=declared==null?cost.type():declared;if(!resumableTrickType(type))return ActionResult.rejected(RejectReason.UNKNOWN_CARD);
    if(!supportsTrickContinuation(type))return ActionResult.rejected(RejectReason.NOT_YET_IMPLEMENTED);
    if(type!=CardType.TIESUO && type!=CardType.JIEDAO && secondaryTarget!=null)return ActionResult.rejected(RejectReason.TARGET_PROHIBITED);
    if(type==CardType.JIEDAO){var valid=validateJiedao(source,target,secondaryTarget,cost);if(!valid.accepted())return valid;}
    if(type==CardType.HUOGONG){var valid=FireAttackRule.validate(this,source,target,cost);if(!valid.accepted())return valid;}
    if(type==CardType.TIESUO){
      if(target==null)return ActionResult.rejected(RejectReason.TARGET_REQUIRED);
      var valid=TiesuoRule.validate(state,cost,target,secondaryTarget);if(!valid.accepted())return valid;
      if(secondaryTarget!=null&&!cardTargetAllowed(source,secondaryTarget,cost))return ActionResult.rejected(RejectReason.TARGET_PROHIBITED);
    }
    GameCard used=cost;boolean equipped=CardZone.equipment(source.seat()).equals(state.cards().locate(cardId));
    if(equipped && (type!=CardType.GUOHE || cost.type()==type))return ActionResult.rejected(RejectReason.CARD_NOT_IN_HAND);
    if(target!=null && !cardTargetAllowed(source,target,type==cost.type()?cost:new VirtualCard(type,List.of(cost))))return ActionResult.rejected(RejectReason.TARGET_PROHIBITED);
    if(type==CardType.GUOHE || type==CardType.SHUNSHOU || type==CardType.JUEDOU){var valid=validateSingleTarget(source,target,type);if(!valid.accepted())return valid;}
    if(type!=cost.type()){
      var ctx=SkillContext.transform(state,source,cost,type,null);bus.trigger(Timing.CARD_TRANSFORM,ctx);
      if(!ctx.transformConfirmed())return ActionResult.rejected(RejectReason.TRANSFORM_NOT_AVAILABLE);used=ctx.card();
    }
    checkpointOperations++;
    try{var selected=type==CardType.TIESUO?state.alivePlayersFromCurrent().stream().filter(p->p==target||p==secondaryTarget).map(SgsPlayer::seat).toList():null;
      activeTrick=new TrickContinuation(this,source.seat(),type==CardType.GUOHE||type==CardType.SHUNSHOU||type==CardType.JUEDOU||type==CardType.HUOGONG||type==CardType.JIEDAO?target.seat():source.seat(),cardId,type,selected);if(type==CardType.JIEDAO)activeTrick.attachStrike(target.seat(),secondaryTarget.seat());beginOwnedUse(source,used,USE);return ActionResult.ok();}
    catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}finally{checkpointOperations--;}
  }
  public void pollResumableTrick(){
    assertContinuationIngress();checkpointOperations++;
    try{if(activeTrick!=null){activeTrick.advance(this);if(activeTrick.done())activeTrick=null;}}
    catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}finally{checkpointOperations--;}
  }
  public void resumeTrickWindow(long windowId,long revision,CounterspellResult result){
    assertContinuationIngress();if(activeTrick==null || revision!=activeTrick.revision())throw new IllegalArgumentException("stale trick window");
    checkpointOperations++;try{activeTrick.counterResult(this,windowId,result);}catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}finally{checkpointOperations--;}
  }
  public ActionResult resumeTrickChoice(UUID actor,long promptId,long revision,Optional<SkillChoice> choice){
    assertContinuationIngress();if(activeTrick==null)return ActionResult.rejected(RejectReason.WRONG_PHASE);
    checkpointOperations++;try{return activeTrick.choose(this,actor,promptId,revision,choice)?ActionResult.ok():ActionResult.rejected(RejectReason.RESPONSE_ONLY);}finally{checkpointOperations--;}
  }
  public ActionResult resumeTrickInvoke(UUID actor,long promptId,long revision,boolean answer){
    assertContinuationIngress();if(activeTrick==null)return ActionResult.rejected(RejectReason.WRONG_PHASE);
    checkpointOperations++;try{return activeTrick.invoke(this,actor,promptId,revision,answer)?ActionResult.ok():ActionResult.rejected(RejectReason.RESPONSE_ONLY);}finally{checkpointOperations--;}
  }
  public ActionResult resumeTrickResponse(UUID actor,long promptId,long revision,Optional<PlayDecision> answer){
    assertContinuationIngress();if(activeTrick==null)return ActionResult.rejected(RejectReason.WRONG_PHASE);
    var prompt=activeTrick.prompt(this).orElse(null);
    if(prompt==null || !DecisionBoundary.of(prompt,activeTrick.revision()).matches(actor,promptId,revision) || java.util.Set.of(PromptType.WEAPON_DISCARD,PromptType.WEAPON_COST_FIRST,PromptType.WEAPON_COST_SECOND,PromptType.SKILL_INVOKE,PromptType.CARD_ZONE_SELECT,PromptType.YIJI_GIVE,PromptType.FANKUI_TAKE,PromptType.GUI_CAI_REPLACE,PromptType.GANGLIE_DISCARD,PromptType.LIULI_TARGET).contains(prompt.type()))return ActionResult.rejected(RejectReason.RESPONSE_ONLY);
    checkpointOperations++;try{
      if(DecisionBoundary.of(prompt,activeTrick.revision()).expired(clock.nowMillis()))answer=Optional.empty();
      else if(answer.isPresent()&&!activeTrick.strikeInput()){var canonical=validateContinuationDecision(prompt,answer.get());if(canonical.isEmpty())return ActionResult.rejected(RejectReason.RESPONSE_ONLY);answer=canonical;}
      return activeTrick.respond(this,actor,promptId,revision,answer)?ActionResult.ok():ActionResult.rejected(RejectReason.RESPONSE_ONLY);
    }catch(RuntimeException|Error error){checkpointFaulted=true;throw error;}finally{checkpointOperations--;}
  }
  int trickDuelRequired(SgsPlayer other,SgsPlayer actor,GameCard card){return Math.max(1,bus.queryInt(RuleId.DUEL_SHA_COUNT,SkillContext.cardTarget(state,other,actor,card),1));}
  List<Integer> trickLordProviders(SgsPlayer lord,boolean slash){
    String skill=slash?"jijiang":"hujia";var faction=slash?dev.sgs.core.Kingdom.SHU:dev.sgs.core.Kingdom.WEI;
    if(!lord.alive() || lord.identity()!=Identity.LORD || !lord.hero().skillIds().contains(skill))return List.of();
    return state.alivePlayersFromCurrent().stream().filter(p->p!=lord&&p.hero()!=null&&p.hero().kingdom()==faction).map(SgsPlayer::seat).toList();
  }
  void trickBeginResponse(SgsPlayer actor,GameCard card){beginOwnedUse(actor,card,RESPONSE);}
  Optional<PlayDecision> trickValidateResponse(PromptRequest prompt,PlayDecision decision){return validateContinuationDecision(prompt,decision);}
  String trickMovementInvoke(CardMove move){
    return bus.movementEffect(SkillContext.movement(state,move))
        .map(dev.sgs.core.engine.skill.AwaitableMovementEffect.Request::skill).orElse("");
  }
  void trickDispatchMovement(CardMove move){
    checkpointOperations++;try{bus.trigger(Timing.CARD_MOVE_AFTER,SkillContext.movement(state,move),OrderStrategy.MULTI_ENTITY_ORDER);eventListeners().forEach(l->l.onCardMoved(move));}
    catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}finally{checkpointOperations--;}
  }
  void trickPrepareMovement(CardMove move,String skill){bus.triggerExcept(Timing.CARD_MOVE_AFTER,SkillContext.movement(state,move),OrderStrategy.MULTI_ENTITY_ORDER,move.from().seat(),skill);}
  void trickMovementWithInvoke(CardMove move,SgsPlayer actor,String skill,boolean answer,boolean prepared){
    if(deferredInvoke!=null || !trickMovementInvoke(move).equals(skill))throw new IllegalStateException("invalid movement hook");var input=new DeferredInvoke(actor.seat(),actor.seat(),skill,answer);deferredInvoke=input;
    try{if(!prepared)trickPrepareMovement(move,skill);movementDrawDispatch=true;bus.dispatchDeferred(Timing.CARD_MOVE_AFTER,SkillContext.movement(state,move),actor.seat(),skill,OrderStrategy.MULTI_ENTITY_ORDER);eventListeners().forEach(l->l.onCardMoved(move));if(!input.consumed)throw new IllegalStateException("movement hook not dispatched");}finally{deferredInvoke=null;movementDrawDispatch=false;}
  }
  private boolean movementDrawDispatch;
  private CostMovementQueue capturingCostMovements;
  void commitUseCost(CardUseCostContinuation program,SgsPlayer actor,GameCard card,CardMove.Reason reason){
    if(capturingCostMovements!=null)throw new IllegalStateException("nested cost capture");capturingCostMovements=program.movementQueue();
    try{moveOwnedUse(actor,card,reason);}finally{capturingCostMovements=null;}
  }
  void commitDiscardCost(CostMovementQueue queue,SgsPlayer owner,SgsPlayer actor,List<CardInstance> cards){
    if(capturingCostMovements!=null)throw new IllegalStateException("nested cost capture");capturingCostMovements=queue;
    try{discardOwnedCards(owner,actor,cards);}finally{capturingCostMovements=null;}
  }
  void movementEventFinished(CardMove move){eventListeners().forEach(listener->listener.onCardMoved(move));}
  void completeMovementInvoke(dev.sgs.core.engine.skill.MovementListenerProgram program,CardMove move,boolean answer){
    if(program==null||program.snapshot().awaiting()==null||deferredInvoke!=null)throw new IllegalStateException("missing movement invocation");
    var request=program.snapshot().awaiting();var input=new DeferredInvoke(request.owner(),request.owner(),request.skill(),answer);deferredInvoke=input;
    try{
      // Built-in awaitable bodies only draw; their nested draw notifications retain synchronous order.
      movementDrawDispatch=true;program.complete(bus,SkillContext.movement(state,move));
      if(!input.consumed)throw new IllegalStateException("movement invocation not consumed");
    }finally{deferredInvoke=null;movementDrawDispatch=false;}
  }
  void completePhaseInvoke(dev.sgs.core.engine.skill.PhaseListenerProgram program,SkillContext context,boolean answer){
    if(program==null||program.snapshot().awaiting()==null||deferredInvoke!=null)throw new IllegalStateException("missing phase invocation");
    var request=program.snapshot().awaiting();var input=new DeferredInvoke(request.owner(),context.actor().seat(),request.skill(),answer);deferredInvoke=input;
    try{
      movementDrawDispatch=true;program.complete(bus,context);
      if(!input.consumed)throw new IllegalStateException("phase invocation not consumed");
    }finally{deferredInvoke=null;movementDrawDispatch=false;}
  }
  void trickBeginOwned(SgsPlayer actor,GameCard card){beginOwnedUse(actor,card,USE);}
  void trickCommit(SgsPlayer actor,GameCard card){cardUseCommitted(actor,card);}
  void trickCommitWithInvoke(SgsPlayer actor,GameCard card,boolean answer){
    if(deferredInvoke!=null)throw new IllegalStateException("nested trick invoke");var input=new DeferredInvoke(actor.seat(),actor.seat(),"jizhi",answer);deferredInvoke=input;
    try{cardUseCommitted(actor,card);if(!input.consumed)throw new IllegalStateException("trick hook not dispatched");}finally{deferredInvoke=null;}
  }
  void trickPlayedLog(int seat,GameCard card){state.log("seat {} played {}",seat,describe(card));}
  public dev.sgs.core.persistence.TrickCheckpoint checkpointTrick(String content){
    assertContinuationIngress();
    if(activeTrick==null || checkpointFaulted || !supportsTrickContinuation() || !counterspellScopes.isEmpty() || !listeners.isEmpty()
        || state.cards().observerCount()!=0 || state.logObserverCount()!=0 || state.cards().ruleListenerRevision()!=checkpointMovementRevision)
      throw new IllegalStateException("trick is not at a versioned safe boundary");
    validateTrickProgram();
    var saved=new dev.sgs.core.persistence.TrickCheckpoint(activeTrick.snapshot().version(),dev.sgs.core.persistence.StateCheckpoint.capture(state,content),promptSequence,responseTimeoutMillis/1000,
        skills.saveMemory(),bus.checkpointBindings(),state.publicSkillSequence(),state.publicSkillActivations(),activeTrick.snapshot(),clock.nowMillis());saved.validate();return saved;
  }
  public static SgsGameEngine restoreTrick(dev.sgs.core.persistence.TrickCheckpoint saved,String content,PromptResponder responder,Clock clock){
    saved.validate();if(!saved.state().contentVersion().equals(content))throw new IllegalArgumentException("trick content mismatch");
    var e=new SgsGameEngine(saved.state().restore(),responder,clock,saved.responseTimeoutSeconds());
    if(!e.bus.checkpointBindings().equals(saved.bindings()))throw new IllegalArgumentException("trick binding mismatch");
    e.skills.restoreMemory(saved.skillMemory(),e.state.turnNumber());e.promptSequence=saved.promptSequence();e.state.restoreSkillActivations(saved.skillSequence(),saved.activations());
    e.activeTrick=new TrickContinuation(e,saved.continuation(),saved.capturedAtMillis(),clock.nowMillis());e.validateTrickProgram();return e;
  }
  private void validateTrickProgram(){
    if(activeTrick==null || !supportsTrickContinuation(activeTrick.snapshot().effectiveType()) || state.status()!=GameStatus.RUNNING || state.phase()!=Phase.PLAY)throw new IllegalArgumentException("unsupported trick context");
    var c=activeTrick.snapshot();
    if(c.family().strike()!=null){var retained=new HashSet<Long>();retained.add(c.cardId());c.counters().forEach(v->retained.add(v.cardId()));activeTrick.strike().validateAgainst(this,retained);}
    if(c.family().program()!=null){var program=c.family().program();if(program.listeners()!=null)continuationValidateListener(program.listeners(),program.damage());for(var parent:program.parents())continuationValidateListener(parent.listeners(),parent.damage());}
    var processing=state.cards().processingCards();var ids=new HashSet<Long>();
    var physical=state.cards().zones().stream().flatMap(z->state.cards().cards(z).stream()).filter(card->card.instanceId()==c.cardId()).findFirst().orElseThrow();
    var location=state.cards().locate(c.cardId());
    if(CardZone.PROCESSING.equals(location))ids.add(c.cardId());
    else if(!java.util.Set.of(CardType.NANMAN,CardType.WANJIAN,CardType.JUEDOU,CardType.HUOGONG).contains(c.effectiveType()) || location.kind()!=CardZone.Kind.HAND || !state.players().get(location.seat()).hero().skillIds().contains("jianxiong"))throw new IllegalArgumentException("primary trick cost missing");
    for(var cost:c.counters())if(!ids.add(cost.cardId()))throw new IllegalArgumentException("aliased counter cost");
    for(long id:c.family().harvest())if(id==c.cardId() || !ids.add(id))throw new IllegalArgumentException("aliased harvest cost");
    if(c.family().response()!=null && c.family().response().judgmentCard()!=0 && !ids.add(c.family().response().judgmentCard()))throw new IllegalArgumentException("aliased judgment cost");
    if(c.family().ganglie()!=null&&c.family().ganglie().judgment()!=null&&!ids.add(c.family().ganglie().judgment().effective()))throw new IllegalArgumentException("aliased ganglie judgment");
    if(c.family().strike()!=null)ids.addAll(dev.sgs.core.persistence.StrikeFrames.processing(c.family().strike(),state));
    ids.addAll(DamageProgramContinuation.processingCosts(c.family().program()));
    if(c.source()!=state.currentSeat() || processing.size()!=ids.size() || !processing.stream().map(CardInstance::instanceId).collect(java.util.stream.Collectors.toSet()).equals(ids))throw new IllegalArgumentException("invalid trick physical ownership");
    if(physical.type()!=c.effectiveType() && !(c.effectiveType()==CardType.GUOHE && physical.isBlack() && state.players().get(c.source()).hero().skillIds().contains("qixi")))throw new IllegalArgumentException("invalid trick transformation");
    for(var cost:c.counters())if(processing.stream().noneMatch(card->card.instanceId()==cost.cardId() && card.type()==CardType.WUXIE))throw new IllegalArgumentException("invalid counter physical cost");
    if(c.window()!=null){var w=c.window();var expected=state.alivePlayersFromSeat(c.target()).stream().map(SgsPlayer::seat).filter(seat->!c.scopePassed().contains(seat)).toList();
      var invited=w.tokens().stream().map(TrickContinuation.Token::seat).toList();
      // Persist the full historical ring; the session filters eligibility before publication.
      if(!invited.equals(expected))throw new IllegalArgumentException("invalid trick invitation ring");
    }else if(c.pending()!=null){var p=c.pending();
      if(p.type()==PromptType.SKILL_INVOKE){
        if(c.family().program()!=null&&c.family().program().rescueCost()!=null)new DamageProgramContinuation(c.family().program(),0,0).validateRescueCost(this);
        else if(p.skill().equals("yiji"))validateTrickYiJi(c);
        else if(c.family().movementProgram()!=null){
          var m=c.movements().getFirst();var move=new CardMove(m.sequence(),m.from(),m.to(),m.cards().stream().map(id->state.cards().zones().stream().flatMap(z->state.cards().cards(z).stream()).filter(card->card.instanceId()==id).findFirst().orElseThrow()).toList(),m.reason(),m.actor(),m.publicCards());
          bus.validateMovement(c.family().movementProgram(),SkillContext.movement(state,move));
        }
        else if(p.skill().equals("jizhi")){if(!state.players().get(p.asker()).hero().skillIds().contains("jizhi") || !c.movements().isEmpty())throw new IllegalArgumentException("missing trick invoke skill");}
        else{var m=c.movements().isEmpty()?null:c.movements().getFirst();
          if(m==null || p.asker()!=m.from().seat())throw new IllegalArgumentException("missing movement program");
          var move=new CardMove(m.sequence(),m.from(),m.to(),m.cards().stream().map(id->state.cards().zones().stream().flatMap(z->state.cards().cards(z).stream()).filter(card->card.instanceId()==id).findFirst().orElseThrow()).toList(),m.reason(),m.actor(),m.publicCards());
          if(!trickMovementInvoke(move).equals(p.skill()))throw new IllegalArgumentException("movement hook mismatch");
        }
      }
      if(p.type()==PromptType.CARD_ZONE_SELECT){var target=state.players().get(c.target());var choices=new ArrayList<CardInstance>(target.equipment().all());choices.addAll(target.delayZone().cards());
        if(!p.choices().equals(choices.stream().map(CardInstance::instanceId).toList()) || p.hidden()!=(target.handCount()>0))throw new IllegalArgumentException("trick private choice mismatch");}
      if(p.type()==PromptType.YIJI_GIVE)validateTrickYiJi(c);
      if(p.type()==PromptType.FANKUI_TAKE){
        var d=c.family().damage();var f=d.damageFrames().getFirst();var who=state.players().get(p.asker());var from=state.players().get(p.subject());
        if(d.pending()!=null || f.step()!=AttackContinuation.DamageStep.AFTER_DEFERRED || !trickFankuiApplies(who,from,f.amount(),damageCause(d),f.nature(),f.chainDamage()) || !p.choices().equals(from.equipment().all().stream().map(CardInstance::instanceId).toList()) || p.hidden()!=(who!=from&&from.handCount()>0))throw new IllegalArgumentException("invalid fankui damage boundary");
      }
    }else if(c.family().strike()!=null&&dev.sgs.core.persistence.StrikeFrames.pending(c)!=null){
      // The nested attack and supply are validated above in their actual parent context.
    }else if(c.family().revealCost()!=null&&c.family().revealCost().pending()!=null){
      // The data-owned reveal/cost frame is validated against physical zones by TrickCheckpoint.
    }else if(c.family().ganglie()!=null)validateDamageReaction(c.family().ganglie(),c.family().damage());
    else if(c.family().response()!=null)validateSharedResponse(c.family().response(),false);
    else if(c.family().damage()!=null)validateDamagePrompt(c.family().damage());
    else throw new IllegalArgumentException("trick is not pending");
  }

  private void validateSharedResponse(TrickResponseContinuation.Snapshot r,boolean attack){validateSharedResponse(r,attack,false);}
  private void validateSharedResponse(TrickResponseContinuation.Snapshot r,boolean attack,boolean slashCosts){
    var actor=state.players().get(r.actor());var p=r.pending();boolean slash=r.type()!=PromptType.DODGE_SHA;
    if(attack&&Set.of(TrickResponseContinuation.Step.OWN_BAGUA,TrickResponseContinuation.Step.OWN_JUDGMENT,TrickResponseContinuation.Step.OWN_TIANDU).contains(r.step())&&!continuationOwnBaguaAvailable(state.players().get(r.subject()),actor))throw new IllegalArgumentException("own judgment prohibited by attack context");
    var allowed=slash?List.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA):List.of(CardType.SHAN);
    if(!r.providers().equals(trickLordProviders(actor,slash)))throw new IllegalArgumentException("provider ring mismatch");
    if(r.own()!=null && (!r.own().allowedTypes().equals(allowed)||!r.own().skillId().isEmpty()))throw new IllegalArgumentException("own response mismatch");
    switch(r.step()){
      case COST->{
        var cost=r.cost();
        if((!attack&&!slashCosts)||cost==null||r.ownCardPaid()||cost.reason()!=RESPONSE
            ||(slashCosts?!Set.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA).contains(cost.type()):cost.type()!=CardType.SHAN)
            ||cost.actor()!=r.actor()&&(r.cursor()>=r.providers().size()||cost.actor()!=r.providers().get(r.cursor())))throw new IllegalArgumentException("response cost actor or type differs");
        var q=cost.pending();
        if(q==null||p.id()!=q.id()||p.type()!=q.type()||p.askerSeat()!=q.asker()||p.subjectSeat()!=q.subject()
            ||p.deadlineMillis()!=q.deadline()||!p.skillId().equals(q.skill())||!p.allowedTypes().isEmpty())throw new IllegalArgumentException("response cost input differs");
        new CardUseCostContinuation(cost,0,0).validate(this);
      }
      case OWN_JUDGMENT,PROVIDER_JUDGMENT->{
        var j=r.judgment();int owner=r.step()==TrickResponseContinuation.Step.OWN_JUDGMENT?r.actor():r.cursor()<r.providers().size()?r.providers().get(r.cursor()):-1;
        if(j==null || slash || j.owner()!=owner || owner<0 || p.subjectSeat()!=owner || !p.allowedTypes().isEmpty() || !CardZone.PROCESSING.equals(state.cards().locate(j.effective())))throw new IllegalArgumentException("invalid judgment ownership");
        var armor=state.players().get(owner).equipment().armor();if(armor==null||armor.type()!=CardType.BAGUA||armor.instanceId()!=j.cause() || j.revealed()!=j.effective()&&!CardZone.DISCARD.equals(state.cards().locate(j.revealed())))throw new IllegalArgumentException("invalid judgment physical cost");
        var ring=state.alivePlayersFromCurrent().stream().map(SgsPlayer::seat).toList();int previous=-1;for(int modifier:j.modifiers()){int index=ring.indexOf(modifier);if(index<=previous || !state.players().get(modifier).hero().skillIds().contains("guicai"))throw new IllegalArgumentException("invalid judgment modifier order");previous=index;}
        var processed=new HashSet<>(j.modifiers().subList(0,j.cursor()));var remaining=trickJudgmentModifiers().stream().filter(seat->!processed.contains(seat)).toList();
        if(!remaining.equals(j.modifiers().subList(j.cursor(),j.modifiers().size())) || j.step()==JudgmentContinuation.Step.TIANDU&&!trickTianduApplies(owner,j.effective()))throw new IllegalArgumentException("invalid judgment modifier cursor");
      }
      case OWN_TIANDU,PROVIDER_TIANDU->{
        int seat=r.step()==TrickResponseContinuation.Step.OWN_TIANDU?r.actor():r.cursor()<r.providers().size()?r.providers().get(r.cursor()):-1;
        if(seat<0 || slash || p.askerSeat()!=seat || p.subjectSeat()!=seat || p.type()!=PromptType.SKILL_INVOKE || !p.skillId().equals("tiandu") || !p.allowedTypes().isEmpty() || !CardZone.PROCESSING.equals(state.cards().locate(r.judgmentCard())) || !trickTianduApplies(seat,r.judgmentCard()))throw new IllegalArgumentException("invalid yielded judgment effect");
        var armor=state.players().get(seat).equipment().armor();if(armor==null||armor.type()!=CardType.BAGUA)throw new IllegalArgumentException("missing judgment armor");
      }
      case LORD->{if(r.providers().isEmpty()||r.cursor()!=0||p.askerSeat()!=r.actor()||p.subjectSeat()!=r.actor()||p.type()!=PromptType.SKILL_INVOKE||!p.skillId().equals(slash?"jijiang":"hujia")||!p.allowedTypes().isEmpty())throw new IllegalArgumentException("lord response mismatch");}
      case PROVIDER_BAGUA,PROVIDER_CARD->{
        if(r.cursor()>=r.providers().size()||p.askerSeat()!=r.providers().get(r.cursor())||p.subjectSeat()!=r.actor())throw new IllegalArgumentException("provider response actor mismatch");
        if(r.step()==TrickResponseContinuation.Step.PROVIDER_BAGUA){var armor=state.players().get(p.askerSeat()).equipment().armor();if(slash||armor==null||armor.type()!=CardType.BAGUA||p.type()!=PromptType.SKILL_INVOKE||!p.skillId().equals("bagua")||!p.allowedTypes().isEmpty())throw new IllegalArgumentException("provider armor mismatch");}
        else if(p.type()!=(slash?PromptType.JIJIANG_SHA:PromptType.HUJIA_SHAN)||!p.skillId().equals(slash?"jijiang":"hujia")||!p.allowedTypes().equals(allowed))throw new IllegalArgumentException("provider response type mismatch");
      }
      case OWN_BAGUA->{var armor=actor.equipment().armor();if(slash||armor==null||armor.type()!=CardType.BAGUA||p.askerSeat()!=r.actor()||p.subjectSeat()!=r.subject()||p.type()!=PromptType.SKILL_INVOKE||!p.skillId().equals("bagua")||!p.allowedTypes().isEmpty())throw new IllegalArgumentException("own armor mismatch");}
      case OWN_CARD->{if(r.own()==null||p.id()!=r.own().id()||p.deadlineMillis()!=r.own().deadlineMillis()||p.askerSeat()!=r.actor()||p.subjectSeat()!=r.subject()||p.type()!=r.type()||!p.allowedTypes().equals(allowed)||!p.skillId().isEmpty())throw new IllegalArgumentException("own response mismatch");}
      default->throw new IllegalArgumentException("response is not pending");
    }
  }
  private void validateDamageReaction(GangLieContinuation.Snapshot g,AttackContinuation.Snapshot d){
    var owner=state.players().get(g.owner());var source=state.players().get(g.source());var f=d.damageFrames().getFirst();var p=g.pending();
    if(g.owner()!=f.targetSeat()||g.source()!=d.sourceSeat()||g.owner()==g.source()||g.revision()<1||g.revision()==Long.MAX_VALUE||g.costs().size()>1||new HashSet<>(g.costs()).size()!=g.costs().size()
        ||p==null||p.id()!=promptSequence||p.stateRevision()!=g.revision()||!p.allowedTypes().isEmpty())throw new IllegalArgumentException("invalid damage reaction boundary");
    if(g.step()==GangLieContinuation.Step.INVOKE&&(g.accepted()||!g.costs().isEmpty()||g.judgment()!=null||p.type()!=PromptType.SKILL_INVOKE||p.askerSeat()!=g.owner()||p.subjectSeat()!=g.source()||!p.skillId().equals("ganglie"))
        ||g.step()==GangLieContinuation.Step.COST&&(!g.accepted()||g.judgment()!=null||p.type()!=PromptType.GANGLIE_DISCARD||p.askerSeat()!=g.source()||p.subjectSeat()!=g.owner()||!p.skillId().equals("ganglie")))throw new IllegalArgumentException("invalid damage reaction input kind");
    if(!trickGanglieApplies(owner,source,f.amount(),damageCause(d),f.nature(),f.chainDamage()))throw new IllegalArgumentException("invalid ganglie damage context");
    switch(g.step()){
      case COST->{if(!source.alive()||source.handCount()<2||g.costs().stream().anyMatch(id->!CardZone.hand(g.source()).equals(state.cards().locate(id))))throw new IllegalArgumentException("invalid unpaid ganglie costs");}
      case JUDGE->{var j=g.judgment();
        if(!g.accepted()||!g.costs().isEmpty()||j==null||j.owner()!=g.owner()||j.cause()!=0||!Objects.equals(p,j.pending())||j.step()!=JudgmentContinuation.Step.MODIFY||j.cursor()<0||j.cursor()>=j.modifiers().size()
            ||p.type()!=PromptType.GUI_CAI_REPLACE||p.askerSeat()!=j.modifiers().get(j.cursor())||p.subjectSeat()!=g.owner()||!p.skillId().equals("guicai"))throw new IllegalArgumentException("invalid reaction judgment boundary");
        if(!CardZone.PROCESSING.equals(state.cards().locate(j.effective()))||j.revealed()!=j.effective()&&!CardZone.DISCARD.equals(state.cards().locate(j.revealed())))throw new IllegalArgumentException("invalid ganglie judgment entities");
        var ring=state.alivePlayersFromCurrent().stream().map(SgsPlayer::seat).toList();int previous=-1;
        for(int actor:j.modifiers()){int index=ring.indexOf(actor);if(index<=previous||!state.players().get(actor).hero().skillIds().contains("guicai"))throw new IllegalArgumentException("invalid ganglie modifier order");previous=index;}
        var processed=new HashSet<>(j.modifiers().subList(0,j.cursor()));if(!trickJudgmentModifiers().stream().filter(seat->!processed.contains(seat)).toList().equals(j.modifiers().subList(j.cursor(),j.modifiers().size())))throw new IllegalArgumentException("invalid ganglie modifier cursor");
      }
      case INVOKE->{}
      default->throw new IllegalArgumentException("ganglie has no pending input");
    }
  }
  private void validateDamagePrompt(AttackContinuation.Snapshot d){
    var f=d.damageFrames().getFirst();var p=d.pending();var victim=state.players().get(f.targetSeat());var source=state.players().get(d.sourceSeat());
    if(p==null||p.id()!=promptSequence||p.stateRevision()!=d.revision())throw new IllegalArgumentException("damage leaf request revision differs");
    if(p.type()==PromptType.RESCUE_TAO){
      if(!victim.alive()||!f.rescueRing().equals(state.alivePlayersFromCurrent().stream().map(SgsPlayer::seat).toList())||f.step()!=AttackContinuation.DamageStep.RESCUE||f.rescueCursor()>=f.rescueRing().size()||p.askerSeat()!=f.rescueRing().get(f.rescueCursor())||p.subjectSeat()!=victim.seat()||victim.hp()>=1||!p.skillId().isEmpty()||!p.allowedTypes().equals(p.askerSeat()==victim.seat()?List.of(CardType.TAO,CardType.JIU):List.of(CardType.TAO)))throw new IllegalArgumentException("rescue cursor mismatch");
    }else if(p.type()==PromptType.SKILL_INVOKE){
      if(f.step()!=AttackContinuation.DamageStep.AFTER_CHOICE||p.askerSeat()!=victim.seat()||p.subjectSeat()!=source.seat()||!p.skillId().equals("jianxiong")||!p.allowedTypes().isEmpty()||!continuationJianxiongApplies(victim,source,f.amount(),damageCause(d),f.nature(),f.chainDamage()))throw new IllegalArgumentException("nested damage hook mismatch");
    }else throw new IllegalArgumentException("unsupported nested damage prompt");
  }
  private void validateTrickYiJi(TrickContinuation.Snapshot c){
    var y=c.family().yiji();var d=c.family().damage();
    if(y==null || d==null || d.damageFrames().isEmpty() || d.pending()!=null || d.damageFrames().getFirst().step()!=AttackContinuation.DamageStep.AFTER_DEFERRED || y.owner()!=d.damageFrames().getFirst().targetSeat() || y.amount()!=d.damageFrames().getFirst().amount())throw new IllegalArgumentException("yiji damage parent mismatch");
    var who=state.players().get(y.owner());var source=state.players().get(d.sourceSeat());var f=d.damageFrames().getFirst();
    if(!trickYiJiApplies(who,source,f.amount(),damageCause(d),f.nature(),f.chainDamage()) || y.retained().stream().anyMatch(id->!CardZone.hand(y.owner()).equals(state.cards().locate(id))))throw new IllegalArgumentException("yiji physical hand mismatch");
  }

  private ActionResult validateSingleTarget(SgsPlayer source, SgsPlayer target, CardType type) {
    if (target == null) {
      return ActionResult.rejected(RejectReason.TARGET_REQUIRED);
    }
    if (target == source) {
      return ActionResult.rejected(RejectReason.TARGET_SELF);
    }
    if (!target.alive()) {
      return ActionResult.rejected(RejectReason.TARGET_DEAD);
    }
    if (type == CardType.SHUNSHOU && state.distance(source, target) > bus.queryInt(RuleId.TRICK_DISTANCE_LIMIT,
        SkillContext.forPlayer(state,RuleId.TRICK_DISTANCE_LIMIT,source),1)) {
      return ActionResult.rejected(RejectReason.OUT_OF_RANGE);
    }
    if ((type == CardType.GUOHE || type == CardType.SHUNSHOU) && countCardsOf(target) == 0) {
      return ActionResult.rejected(RejectReason.TARGET_NO_CARDS);
    }
    return ActionResult.ok();
  }

  /** Owner-only UI hints for physical single-target tricks. Reuses actual target validators;
   * unsupported cards/view-as actions deliberately have no hint instead of a guessed verdict. */
  public java.util.Map<Long,java.util.Map<Integer,String>> singleTrickTargetAvailability(SgsPlayer source){
    if(source==null || !state.players().contains(source) || !source.alive() || source!=state.currentPlayer()
        || state.status()!=GameStatus.RUNNING || state.phase()!=Phase.PLAY || hasActiveContinuation()
        || checkpointOperations!=0 || !state.cards().cards(CardZone.PROCESSING).isEmpty())return java.util.Map.of();
    var hints=new java.util.LinkedHashMap<Long,java.util.Map<Integer,String>>();
    for(var card:source.handView()){
      if(card.type()!=CardType.SHUNSHOU && card.type()!=CardType.GUOHE && card.type()!=CardType.JUEDOU)continue;
      var targets=new java.util.LinkedHashMap<Integer,String>();
      for(var target:state.players()){
        var verdict=validateSingleTarget(source,target,card.type());
        if(verdict.accepted() && !cardTargetAllowed(source,target,card))verdict=ActionResult.rejected(RejectReason.TARGET_PROHIBITED);
        targets.put(target.seat(),verdict.accepted()?"":verdict.reason().name());
      }
      hints.put(card.instanceId(),java.util.Map.copyOf(targets));
    }
    return java.util.Map.copyOf(hints);
  }

  /** Read-only owner capability list, including real view-as hooks; never executes a skill or pays a card. */
  public java.util.Map<Long,List<dev.sgs.core.match.dto.MatchViews.CardPlayOption>> cardPlayAvailability(SgsPlayer source){
    if(source==null || !source.alive() || source!=state.currentPlayer() || state.status()!=GameStatus.RUNNING
        || state.phase()!=Phase.PLAY || hasActiveContinuation() || checkpointOperations!=0
        || !state.cards().cards(CardZone.PROCESSING).isEmpty())return java.util.Map.of();
    var cards=new ArrayList<CardInstance>(source.handView());cards.addAll(source.equipment().all());
    var result=new java.util.LinkedHashMap<Long,List<dev.sgs.core.match.dto.MatchViews.CardPlayOption>>();
    for(var physical:cards){
      var options=new ArrayList<dev.sgs.core.match.dto.MatchViews.CardPlayOption>();
      boolean equipped=CardZone.equipment(source.seat()).equals(state.cards().locate(physical.instanceId()));
      for(var declared:CardType.values()){
        if(declared!=physical.type() && !bus.canTransform(SkillContext.transform(state,source,physical,declared,null)))continue;
        if(equipped && (declared==physical.type() || (declared!=CardType.SHA && declared!=CardType.GUOHE && declared!=CardType.LEBU && declared!=CardType.BINGLIANG)))continue;
        var verdict=cardPlayVerdict(source,physical,declared);
        options.add(new dev.sgs.core.match.dto.MatchViews.CardPlayOption(declared.ordinal(),verdict.accepted(),verdict.accepted()?"":verdict.reason().name()));
      }
      result.put(physical.instanceId(),List.copyOf(options));
    }
    return java.util.Map.copyOf(result);
  }
  /** Side-effect-free eligibility; uses the same physical/view-as validator as submission. */
  public boolean hasCounterspellResponse(PromptRequest prompt){
    var actor=prompt.asker();
    if(prompt.type()!=PromptType.WUXIE_COUNTER || !actor.alive() || !state.players().contains(actor))return false;
    for(var card:actor.handView())if(validateContinuationDecision(prompt,new PlayDecision(card,CardType.WUXIE),true).isPresent())return true;
    for(var card:actor.equipment().all())if(validateContinuationDecision(prompt,new PlayDecision(card,CardType.WUXIE),true).isPresent())return true;
    return false;
  }
  /** Owner-only response hints. Uses the same canonicalization as input without confirming hooks. */
  public java.util.Map<Long,List<dev.sgs.core.match.dto.MatchViews.CardPlayOption>> cardResponseAvailability(SgsPlayer source,PromptRequest prompt){
    if(source==null || prompt==null || prompt.asker()!=source || !state.players().contains(source) || !source.alive()
        || state.status()!=GameStatus.RUNNING || clock.nowMillis()>prompt.deadlineMillis())return java.util.Map.of();
    var result=new java.util.LinkedHashMap<Long,List<dev.sgs.core.match.dto.MatchViews.CardPlayOption>>();
    var owned=new java.util.ArrayList<CardInstance>(source.handView());owned.addAll(source.equipment().all());
    for(var physical:owned){
      var options=new java.util.ArrayList<dev.sgs.core.match.dto.MatchViews.CardPlayOption>();
      for(var declared:CardType.values()){
        if(declared!=physical.type() && (!prompt.allowedTypes().contains(declared)
            || !bus.canTransform(SkillContext.transform(state,source,physical,declared,prompt.type()))))continue;
        var available=validateContinuationDecision(prompt,new PlayDecision(physical,declared),true).isPresent();
        options.add(new dev.sgs.core.match.dto.MatchViews.CardPlayOption(declared.ordinal(),available,available?"":RejectReason.RESPONSE_ONLY.name()));
      }
      result.put(physical.instanceId(),List.copyOf(options));
    }
    return java.util.Map.copyOf(result);
  }

  private ActionResult cardPlayVerdict(SgsPlayer source,CardInstance physical,CardType declared){
    GameCard card=declared==physical.type()?physical:new dev.sgs.core.card.VirtualCard(declared,List.of(physical));
    if(declared==CardType.SHAN)return ActionResult.rejected(RejectReason.SHAN_NOT_PLAYABLE);
    if(declared==CardType.WUXIE)return ActionResult.rejected(RejectReason.RESPONSE_ONLY);
    if(declared==CardType.TAO)return validateTao(source);
    if(declared==CardType.JIU)return validateJiu();
    if(declared.isEquipment())return declared==physical.type()?ActionResult.ok():ActionResult.rejected(RejectReason.TRANSFORM_NOT_AVAILABLE);
    if(declared==CardType.SHA || declared==CardType.HUOSHA || declared==CardType.LEISHA){
      var quota=shaUsageAvailability(source);if(!quota.accepted())return quota;
      return anyCardTarget(source,card,physical);
    }
    if(declared==CardType.SHANDIAN)return validateDelayedTrick(source,card,source);
    if(declared==CardType.LEBU || declared==CardType.BINGLIANG || declared==CardType.GUOHE
        || declared==CardType.SHUNSHOU || declared==CardType.JUEDOU || declared==CardType.HUOGONG || declared==CardType.JIEDAO)
      return anyCardTarget(source,card,physical);
    if(declared==CardType.TIESUO || declared==CardType.WUZHONG || declared==CardType.TAOYUAN
        || declared==CardType.WUGU || declared==CardType.NANMAN || declared==CardType.WANJIAN)return ActionResult.ok();
    return ActionResult.rejected(RejectReason.UNKNOWN_CARD);
  }
  private ActionResult anyCardTarget(SgsPlayer source,GameCard card,CardInstance physical){
    ActionResult failure=ActionResult.rejected(RejectReason.TARGET_REQUIRED);
    for(var target:state.players()){
      if(!target.alive())continue;
      ActionResult verdict;
      switch(card.type()){
        case SHA,HUOSHA,LEISHA->verdict=validateShaCost(source,target,physical);
        case LEBU,BINGLIANG->verdict=validateDelayedTrick(source,card,target);
        case HUOGONG->verdict=FireAttackRule.validate(this,source,target,card);
        case JIEDAO->{
          verdict=ActionResult.rejected(RejectReason.TARGET_REQUIRED);
          for(var victim:state.players()){
            var v=validateJiedao(source,target,victim,card);if(v.accepted()){verdict=v;break;}
          }
        }
        default->verdict=validateSingleTarget(source,target,card.type());
      }
      if(verdict.accepted() && !cardTargetAllowed(source,target,card))verdict=ActionResult.rejected(RejectReason.TARGET_PROHIBITED);
      if(verdict.accepted())return verdict;
      if(target!=source)failure=verdict;
    }
    return failure;
  }

  private ActionResult validateJiedao(SgsPlayer source, SgsPlayer holder, SgsPlayer victim,GameCard cost) {
    if (holder == null || victim == null) {
      return ActionResult.rejected(RejectReason.TARGET_REQUIRED);
    }
    if (holder == source || victim == holder) {
      return ActionResult.rejected(RejectReason.TARGET_SELF);
    }
    if (!holder.alive() || !victim.alive()) {
      return ActionResult.rejected(RejectReason.TARGET_DEAD);
    }
    if(!cardTargetAllowed(holder,victim,new dev.sgs.core.card.VirtualCard(CardType.SHA,List.of())))return ActionResult.rejected(RejectReason.TARGET_PROHIBITED);
    // Borrowing the sword consumes this trick before the requested Slash selects its target.
    if(victim==source && emptiesKongcheng(victim,cost))
      return ActionResult.rejected(RejectReason.TARGET_PROHIBITED);
    if (holder.equipment().weapon() == null) {
      return ActionResult.rejected(RejectReason.TARGET_NO_WEAPON);
    }
    if (state.distance(holder, victim) > state.attackRange(holder)) {
      return ActionResult.rejected(RejectReason.OUT_OF_RANGE);
    }
    return ActionResult.ok();
  }

  private static int countCardsOf(SgsPlayer player) {
    return player.handCount()+player.equipment().all().size()+player.delayZone().size();
  }

  /** Something from Nothing: draw 2 unless negated. */
  private void resolveWuzhong(SgsPlayer source) {
    if (isCounteredByWuxie(source)) {
      return;
    }
    List<CardInstance> drawn=drawCards(source,2);
    state.log("seat {} drew {} cards from wuzhong", source.seat(), drawn.size());
  }

  /** Dismantle: operator-private selection, with anonymous hand-position tokens. */
  private void resolveGuohe(SgsPlayer source, SgsPlayer target) {
    if (isCounteredByWuxie(target)) {
      return;
    }
    CardInstance taken=chooseTargetZoneCard(source,target,"guohe");
    if(taken!=null){
      state.cards().move(List.of(taken),state.cards().locate(taken.instanceId()),CardZone.DISCARD,DISCARD,source.seat());
      state.log("seat {} dismantled {} from seat {}", source.seat(), taken.cardId(), target.seat());
    }
  }

  /** Snatch: the operator chooses a target zone/card without learning hidden hand identities. */
  private void resolveShunshou(SgsPlayer source, SgsPlayer target) {
    if (isCounteredByWuxie(target)) {
      return;
    }
    CardInstance taken=chooseTargetZoneCard(source,target,"shunshou");
    if(taken!=null){
      state.cards().move(List.of(taken),state.cards().locate(taken.instanceId()),CardZone.hand(source.seat()),EFFECT_GAIN,source.seat());
      state.log("seat {} snatched a card from seat {}", source.seat(), target.seat());
    }
  }

  private CardInstance chooseTargetZoneCard(SgsPlayer source,SgsPlayer target,String effect){
    var visible=new ArrayList<CardInstance>(target.equipment().all());visible.addAll(target.delayZone().cards());
    var result=requestChoice(source,target,PromptType.CARD_ZONE_SELECT,effect,List.copyOf(visible),List.of(),target.handCount()>0);
    if(result.invalid())return null;
    if(result.choice().isEmpty())return chooseRandomCard(target); // Existing noninteractive responders/timeouts.
    long id=result.choice().get().cardId();
    if(id==0)return target.handCount()==0?null:target.handView().get(state.random().nextInt(target.handCount()));
    if(id<=-2){int index=HandSelection.positionIndex(id,target.handCount());return index>=0?target.handView().get(index):null;}
    var selected=visible.stream().filter(c->c.instanceId()==id).findFirst().orElse(null);
    if(selected==null)return null;
    var zone=state.cards().locate(id);
    return CardZone.equipment(target.seat()).equals(zone) || CardZone.judgment(target.seat()).equals(zone)?selected:null;
  }

  private CardInstance chooseRandomCard(SgsPlayer target){
    var available=new ArrayList<CardInstance>(target.handView());available.addAll(target.equipment().all());available.addAll(target.delayZone().cards());
    return available.isEmpty()?null:available.get(state.random().nextInt(available.size()));
  }

  /** Duel: participants alternate playing Sha; the first one without an answer takes 1 damage. */
  private void resolveJuedou(SgsPlayer source, SgsPlayer target, GameCard juedouCard) {
    bus.trigger(Timing.CARD_TARGET_CONFIRMED,SkillContext.cardTarget(state,source,target,juedouCard).at(Timing.CARD_TARGET_CONFIRMED));
    if (isCounteredByWuxie(target)) {
      return;
    }
    resolveUncounterableDuel(source,target,juedouCard);
  }
  @Override public boolean canSkillDuel(SgsPlayer source,SgsPlayer target){
    return source!=null && target!=null && source!=target && state.players().contains(source) && state.players().contains(target)
        && source.alive() && target.alive() && state.status()==GameStatus.RUNNING
        && cardTargetAllowed(source,target,new dev.sgs.core.card.VirtualCard(CardType.JUEDOU,List.of()));
  }
  @Override public void useSkillDuel(SgsPlayer source,SgsPlayer target){
    if(!canSkillDuel(source,target))throw new IllegalArgumentException("illegal virtual duel");
    resolveUncounterableDuel(source,target,new dev.sgs.core.card.VirtualCard(CardType.JUEDOU,List.of()));
  }
  private void resolveUncounterableDuel(SgsPlayer source,SgsPlayer target,GameCard juedouCard){
    var duel=DuelExchange.start(source.seat(),target.seat());
    while (true) {
      var responder=state.players().get(duel.responder());
      var other=state.players().get(duel.other());
      if(duel.needsRequirement() && (state.status()!=GameStatus.RUNNING || !responder.alive() || !other.alive()))return;
      if(duel.needsRequirement())duel=duel.requiring(Math.max(1,bus.queryInt(
          RuleId.DUEL_SHA_COUNT,SkillContext.cardTarget(state,other,responder,juedouCard),1)));
      PromptRequest prompt =
          new PromptRequest(
              nextPromptId(),
              PromptType.DUEL_SHA,
              responder,
              other,
              List.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA),
              clock.nowMillis() + responseTimeoutMillis);
      boolean provided=answerJijiang(responder);
      Optional<PlayDecision> response = provided?Optional.empty():ask(prompt);
      if (!provided && response.isEmpty()) {
        state.log("seat {} lost the duel against seat {}", responder.seat(), other.seat());
        applyDamage(responder, 1, other, juedouCard);
        return;
      }
      if(!provided)spendDecision(responder, response.get());
      duel=duel.committed();
    }
  }

  /** Barbarian Invasion (askSha) or Volley of Arrows (ask Shan) against everyone else. */
  private void resolveAoeSha(SgsPlayer source, boolean nanman, GameCard aoeCard) {
    for (SgsPlayer target : state.alivePlayersAfter(source.seat())) {
      if (state.status() != GameStatus.RUNNING || !target.alive()) {
        continue;
      }
      if(!cardTargetAllowed(source,target,aoeCard))continue;
      if(nanman&&(skills.mythic().has(target,"huoshou")||skills.mythic().has(target,"juxiang")))continue;
      if(target.equipment().armor()!=null && target.equipment().armor().type()==CardType.TENGJIA){
        state.log("seat {} tengjia nullifies aoe",target.seat());continue;
      }
      if (isCounteredByWuxie(target)) {
        continue;
      }
      if(!nanman){
        if(provideDodge(target,source,true,null))state.log("seat {} answered the aoe from seat {}",target.seat(),source.seat());
        else applyDamage(target,1,source,aoeCard);
        continue;
      }
      if(nanman && answerJijiang(target))continue;
      PromptRequest prompt =
          new PromptRequest(
              nextPromptId(),
              nanman ? PromptType.RESPOND_SHA_NANMAN : PromptType.DODGE_SHA,
              target,
              source,
              nanman?List.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA):List.of(CardType.SHAN),
              clock.nowMillis() + responseTimeoutMillis);
      Optional<PlayDecision> response = ask(prompt);
      if (response.isPresent()) {
        spendDecision(target, response.get());
        state.log("seat {} answered the aoe from seat {}", target.seat(), source.seat());
      } else {
        var damageSource=nanman?state.players().stream().filter(p->p.alive()&&skills.mythic().has(p,"huoshou")).findFirst().orElse(source):source;
        applyDamage(target, 1, damageSource, aoeCard);
      }
    }
  }

  /** Peach Garden: every alive player heals 1 unless negated individually. */
  private void resolveTaoyuan(SgsPlayer source) {
    for (SgsPlayer target : state.alivePlayersFromSeat(source.seat())) {
      if (isCounteredByWuxie(target)) {
        continue;
      }
      if (target.hp() < target.maxHp()) {
        target.setHp(target.hp() + 1);
        state.log("seat {} healed 1 from taoyuan", target.seat());
      }
    }
  }

  /** Harvest: reveal one card per alive player; in ring order each picks one, rest is discarded. */
  private void resolveWugu(SgsPlayer source) {
    List<CardInstance> pool=new ArrayList<>(state.cards().drawTo(CardZone.PROCESSING,state.alivePlayers().size(),REVEAL,source.seat(),true));
    for (SgsPlayer picker : state.alivePlayersFromSeat(source.seat())) {
      if (pool.isEmpty()) {
        break;
      }
      if (isCounteredByWuxie(picker)) {
        continue;
      }
      PromptRequest prompt =
          new PromptRequest(
              nextPromptId(),
              PromptType.WUGU_PICK,
              picker,
              source,
              List.of(),
              pool,
              clock.nowMillis() + responseTimeoutMillis);
      Optional<PlayDecision> response = ask(prompt);
      CardInstance picked;
      if (response.isPresent()) {
        picked = response.get().card().sourceCards().get(0);
      } else {
        // Timeout fallback: take the first remaining card.
        picked = pool.get(0);
      }
      pool.removeIf(c -> c.instanceId() == picked.instanceId());
      state.cards().move(List.of(picked),CardZone.PROCESSING,CardZone.hand(picker.seat()),EFFECT_GAIN,picker.seat());
      state.log("seat {} picked {} from wugu", picker.seat(), picked.cardId());
    }
    var remaining=pool.stream().filter(c->CardZone.PROCESSING.equals(state.cards().locate(c.instanceId()))).toList();
    state.cards().move(remaining,CardZone.PROCESSING,CardZone.DISCARD,FINISH,source.seat());
  }

  /** Borrow Sword: the weapon holder attacks the victim, or hands the weapon to the source. */
  private void resolveJiedao(SgsPlayer source,SgsPlayer holder,SgsPlayer victim){
    if(legacyAttackOracle||skills.mythic().active()){resolveJiedaoLegacy(source,holder,victim);return;}
    if(isCounteredByWuxie(holder))return;
    if(!victim.alive()||!cardTargetAllowed(holder,victim,new VirtualCard(CardType.SHA,List.of()))){transferBorrowedWeapon(source,holder);return;}
    var program=runAttackSupply(holder,victim,PromptType.JIEDAO_RESPONSE,true,true);
    if(program.snapshot().receipt()==null){transferBorrowedWeapon(source,holder);return;}
    var card=program.card(this);
    try{if(!program.supplied()||validAttackRangeAfterCost(holder,victim,card)&&cardTargetAllowed(holder,victim,card))settleCommittedAttack(holder,victim,1,card,false);}
    finally{state.cards().finish(card,holder.seat());}
  }
  private AttackSupplyContinuation runAttackSupply(SgsPlayer user,SgsPlayer victim,PromptType ownType,boolean offer,boolean own){
    var program=new AttackSupplyContinuation(user.seat(),victim.seat(),ownType,offer,own);
    while(!program.done()){
      program.advance(this);if(program.done())break;var p=program.prompt(this).orElseThrow();
      boolean accepted=p.type()==PromptType.SKILL_INVOKE?program.invoke(this,p.asker().id(),p.id(),program.snapshot().revision(),invokeSupplyPrompt(p)):
          program.answer(this,p.asker().id(),p.id(),program.snapshot().revision(),askSupplyPrompt(p,victim));
      if(!accepted&&p.type()!=PromptType.SKILL_INVOKE)accepted=program.answer(this,p.asker().id(),p.id(),program.snapshot().revision(),Optional.empty());
      if(!accepted)throw new IllegalStateException("attack supply transport boundary mismatch");
    }
    return program;
  }
  Optional<PlayDecision> validateAttackSupplyDecision(PromptRequest prompt,PlayDecision answer,int target){
    var old=providedAttackTarget;providedAttackTarget=state.players().get(target);
    try{return validateContinuationDecision(prompt,answer);}finally{providedAttackTarget=old;}
  }
  private Optional<PlayDecision> askSupplyPrompt(PromptRequest prompt,SgsPlayer target){
    var old=providedAttackTarget;providedAttackTarget=target;
    try{
      // The program owns canonical validation. ask() would apply transform queries a second time.
      try{var answer=responder.respond(state,prompt);return answer==null?Optional.empty():answer;}
      catch(RuntimeException error){state.log("responder error for seat {}: {}",prompt.asker().seat(),error.toString());return Optional.empty();}
    }finally{providedAttackTarget=old;}
  }
  private boolean invokeSupplyPrompt(PromptRequest prompt){
    try{return responder.invoke(state,prompt)&&clock.nowMillis()<=prompt.deadlineMillis()&&prompt.asker().alive();}
    catch(RuntimeException error){state.log("skill responder error for seat {}: {}",prompt.asker().seat(),error.toString());return false;}
  }
  void commitAttackSupply(SgsPlayer supplier,GameCard card,boolean provided,int user){
    beginOwnedUse(supplier,card,provided?RESPONSE:USE);
    if(provided){state.publishSkillActivation(user,"jijiang");state.log("seat {} used jijiang: sha supplied by seat {}",user,supplier.seat());}
    else cardUseCommitted(supplier,card);
  }
  ActionResult playJiedaoLegacyOracle(SgsPlayer source,long card,SgsPlayer holder,SgsPlayer victim){
    boolean old=legacyAttackOracle;legacyAttackOracle=true;try{return playCardAs(source,card,null,holder,victim);}finally{legacyAttackOracle=old;}
  }
  /** Independent synchronous rule path used by migration differential checks. */
  ActionResult useActiveSkillLegacyOracle(SgsPlayer actor,String skill,SgsPlayer target,List<Long> costs){
    boolean old=legacyAttackOracle;legacyAttackOracle=true;try{return useActiveSkill(actor,skill,target,costs);}finally{legacyAttackOracle=old;}
  }
  private void resolveJiedaoLegacy(SgsPlayer source, SgsPlayer holder, SgsPlayer victim) {
    if (isCounteredByWuxie(holder)) {
      return;
    }
    if(!victim.alive() || !cardTargetAllowed(holder,victim,new dev.sgs.core.card.VirtualCard(CardType.SHA,List.of()))){
      transferBorrowedWeapon(source,holder);return;
    }
    var supplied=provideJijiangAgainst(holder,true,victim);
    if(supplied.isPresent()){
      try{if(validAttackRangeAfterCost(holder,victim,supplied.get()) && cardTargetAllowed(holder,victim,supplied.get()))resolveAttack(holder,victim,1,supplied.get());}
      finally{state.cards().finish(supplied.get(),holder.seat());}return;
    }
    PromptRequest prompt =
        new PromptRequest(
            nextPromptId(),
            PromptType.JIEDAO_RESPONSE,
            holder,
            victim,
            List.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA),
            clock.nowMillis() + responseTimeoutMillis);
    Optional<PlayDecision> response = ask(prompt);
    if (response.isPresent()) {
      PlayDecision decision = response.get();
      if(!validAttackRangeAfterCost(holder,victim,decision.card()) || !cardTargetAllowed(holder,victim,decision.card())){transferBorrowedWeapon(source,holder);return;}
      beginOwnedUse(holder,decision.card(),USE);
      cardUseCommitted(holder,decision.card());
      try{resolveAttack(holder,victim,1,decision.card());}finally{state.cards().finish(decision.card(),holder.seat());}
      return;
    }
    transferBorrowedWeapon(source,holder);
  }
  private void transferBorrowedWeapon(SgsPlayer source,SgsPlayer holder){
    CardInstance weapon = holder.equipment().weapon();
    if(weapon==null || !source.alive())return;
    state.cards().move(List.of(weapon),CardZone.equipment(holder.seat()),CardZone.hand(source.seat()),EFFECT_GAIN,source.seat());
    state.log("seat {} handed {} to seat {}", holder.seat(), weapon.cardId(), source.seat());
  }

  /**
   * Negation chain: asks the ring starting at the effect's target until a full round passes without
   * a Negation. An odd number of Negations cancels the effect.
   */
  boolean isCounteredByWuxie(SgsPlayer effectTarget) {
    checkpointOperations++;
    boolean localScope=counterspellScopes.isEmpty();if(localScope)beginCounterspellScope();
    try {
    List<SgsPlayer> ring = state.alivePlayersFromSeat(effectTarget.seat());
    if (ring.isEmpty()) {
      return false;
    }
    int counters = 0;
    List<PlayDecision> pending = new ArrayList<>();
    List<SgsPlayer> users = new ArrayList<>();
    try {
    while(state.status()==GameStatus.RUNNING){
      boolean guhuoCounter=false;
      for(var actor:state.alivePlayersFromSeat(effectTarget.seat()))if(skills.mythic().has(actor,"guhuo")&&!counterspellScopes.peek().contains(actor.id())){
        var question=new PromptRequest(nextPromptId(),PromptType.WUXIE_COUNTER,actor,effectTarget,List.of(CardType.WUXIE),clock.nowMillis()+responseTimeoutMillis);
        var bluff=skills.mythic().offerGuhuo(question);
        if(bluff.answer().isPresent()){
          var counter=bluff.answer().get();pending.add(counter);users.add(actor);beginOwnedUse(actor,counter.card(),USE);cardUseCommitted(actor,counter.card());counters++;guhuoCounter=true;break;
        }
      }
      if(guhuoCounter)continue;
      long windowId=nextPromptId(),deadline=clock.nowMillis()+responseTimeoutMillis;
      var requests=new ArrayList<PromptRequest>();
      for(var asker:state.alivePlayersFromSeat(effectTarget.seat()))if(!counterspellScopes.peek().contains(asker.id()))
        {
        var question=new PromptRequest(nextPromptId(),PromptType.WUXIE_COUNTER,asker,effectTarget,List.of(CardType.WUXIE),deadline);
        if(hasCounterspellResponse(question))requests.add(question);
      }
      if(requests.isEmpty())break;
      var window=new CounterspellWindow(windowId,effectTarget.seat(),counters,deadline,requests,clock::nowMillis,this::validateContinuationDecision);
      CounterspellResult result=responder.collectCounterspell(state,window);
      // A result is authoritative only if it came from this worker's validated window.
      var authoritative=window.finish();
      if(!authoritative.equals(result))throw new IllegalStateException("foreign counterspell result");
      counterspellScopes.peek().addAll(result.scopePasses());
      if(result.winner().isEmpty())break;
      var answer=result.winner().get();var asker=state.players().stream().filter(p->p.id().equals(answer.actor())).findFirst().orElseThrow();
      var counter=answer.decision();
      pending.add(counter);users.add(asker);
      beginOwnedUse(asker,counter.card(),USE);cardUseCommitted(asker,counter.card());
      counters++;
      state.log("seat {} negated a trick (chain depth {})",asker.seat(),counters);
    }
    return counters % 2 == 1;
    } finally {
      // Each counter is itself being countered until the complete chain unwinds.
      for (int i = pending.size() - 1; i >= 0; i--) {
        state.cards().finish(pending.get(i).card(), users.get(i).seat());
      }
    }

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{if(localScope)endCounterspellScope();checkpointOperations--;}
  }

  /**
   * Validates the declared sha action (target, range, per-turn usage limit) without moving any
   * card. Shared by the normal play path and the view-as (Longdan) path.
   */
  private ActionResult validateSha(SgsPlayer source, SgsPlayer target) {
    if(target!=null && !cardTargetAllowed(source,target,new dev.sgs.core.card.VirtualCard(CardType.SHA,List.of())))return ActionResult.rejected(RejectReason.TARGET_PROHIBITED);
    if (target == null) {
      return ActionResult.rejected(RejectReason.TARGET_REQUIRED);
    }
    if (target == source) {
      return ActionResult.rejected(RejectReason.TARGET_SELF);
    }
    if (!target.alive()) {
      return ActionResult.rejected(RejectReason.TARGET_DEAD);
    }
    if (!skills.mythic().unlimitedDistance(source) && !state.inBasicAttackRange(source, target)) {
      return ActionResult.rejected(RejectReason.OUT_OF_RANGE);
    }
    return shaUsageAvailability(source);
  }
  private ActionResult shaUsageAvailability(SgsPlayer source){
    int shaLimit =
        bus.queryInt(
            RuleId.SHA_USAGE_LIMIT,
            SkillContext.forPlayer(state, RuleId.SHA_USAGE_LIMIT, source),
            1);
    shaLimit=skills.mythic().attackLimit(source,shaLimit);
    if (state.shaPlayedThisTurn() >= shaLimit) {
      return ActionResult.rejected(RejectReason.SHA_LIMIT);
    }
    return ActionResult.ok();
  }

  private ActionResult playAttack(SgsPlayer source, GameCard sha, SgsPlayer target) {
    ActionResult validation = sha.sourceCards().isEmpty()?validateSha(source,target):validateShaCost(source,target,sha.sourceCards().getFirst());
    if (!validation.accepted()) {
      return validation;
    }
    return commitAttack(source, sha, target);
  }

  /** Commits a validated attack (physical or view-as): spends the card and settles the strike. */
  private ActionResult commitAttack(SgsPlayer source, GameCard sha, SgsPlayer target) {
    if(!cardTargetAllowed(source,target,sha))return ActionResult.rejected(RejectReason.TARGET_PROHIBITED);
    beginOwnedUse(source,sha,USE);
    return commitProcessedAttack(source,sha,target);
  }

  private ActionResult commitProcessedAttack(SgsPlayer source,GameCard sha,SgsPlayer target){
    state.registerShaPlayed();
    cardUseCommitted(source,sha);

    int damage = 1;
    if (state.consumeJiuBoost()) {
      damage = 2;
    }
    try{
      settleCommittedAttack(source,target,damage,sha,true);
    }finally{state.cards().finish(sha,source.seat());}
    return ActionResult.ok();
  }

  /** Shared paid-strike execution, without paying again, consuming wine or reserving Sha quota. */
  private void settleCommittedAttack(SgsPlayer source,SgsPlayer target,int damage,GameCard card,boolean root){
    if(legacyAttackOracle||!supportsAttackContinuation()){resolveAttack(source,target,damage,card);return;}
    if(card.sourceCards().stream().anyMatch(c->!CardZone.PROCESSING.equals(state.cards().locate(c.instanceId()))))
      throw new IllegalStateException("strike cost is not committed");
    var frame=AttackContinuation.committed(source.seat(),target.seat(),card,damage);
    var previous=executingAttack;var previousRoot=activeAttack;executingAttack=frame;if(root)activeAttack=frame;
    try{
      while(!frame.done()){
        frame.advance(this);
        if(frame.pending().isPresent()){
          var prompt=frame.request(this);
          if(prompt.type()==PromptType.SKILL_INVOKE){
            boolean invoke=false;try{invoke=responder.invoke(state,prompt) && clock.nowMillis()<=prompt.deadlineMillis();}
            catch(RuntimeException error){state.log("skill responder error: {}",error.toString());}
            frame.answer(this,Optional.empty(),invoke);
          }else if(java.util.Set.of(PromptType.CARD_ZONE_SELECT,PromptType.WEAPON_DISCARD,PromptType.WEAPON_COST_FIRST,PromptType.WEAPON_COST_SECOND,PromptType.GUI_CAI_REPLACE,PromptType.YIJI_GIVE,PromptType.FANKUI_TAKE,PromptType.GANGLIE_DISCARD,PromptType.LIULI_TARGET).contains(prompt.type())){
            Optional<SkillChoice> selected=Optional.empty();
            try{selected=responder.choose(state,prompt);if(selected==null || clock.nowMillis()>prompt.deadlineMillis()
                || selected.isPresent() && !frame.childChoice() && (!validWeaponChoice(prompt,selected.get())))selected=Optional.empty();}
            catch(RuntimeException error){state.log("skill choice failed: {}",error.toString());}
            if(frame.childChoice())frame.chooseChild(this,selected);else frame.chooseWeapon(this,selected);
          }else frame.answer(this,ask(prompt),false);
        }
      }
    }finally{executingAttack=previous;if(root)activeAttack=previousRoot;}
  }

  /** Migration oracle for differential checks; the original whole attack path remains intact. */
  ActionResult playCardLegacyForDifferential(SgsPlayer source,long cardId,SgsPlayer target){
    legacyAttackOracle=true;try{return playCard(source,cardId,target);}finally{legacyAttackOracle=false;}
  }

  boolean continuationTiejiAvailable(SgsPlayer source){return source.alive() && source.hero()!=null && source.hero().skillIds().contains("tieji");}
  int continuationDodgeCount(SgsPlayer source,SgsPlayer target,GameCard card){
    return Math.max(0,bus.queryInt(RuleId.SHA_DODGE_COUNT,SkillContext.cardTarget(state,source,target,card),1));
  }
  SkillEventBus.IntProgress continuationAdvanceInt(RuleId rule,SkillContext context,int cursor,int value){return bus.advanceInt(rule,context,cursor,value);}
  /** Executes the old complete rule query only for an already-saved pre-v13 Tieji decision. */
  boolean continuationTiejiResult(SgsPlayer source,SgsPlayer target,GameCard card,boolean accepted){
    if(deferredInvoke!=null || !continuationTiejiAvailable(source))throw new IllegalStateException("invalid deferred Tieji");
    var input=new DeferredInvoke(source.seat(),target.seat(),"tieji",accepted);deferredInvoke=input;
    try{
      int count=bus.queryInt(RuleId.SHA_DODGE_COUNT,SkillContext.cardTarget(state,source,target,card),1);
      if(!input.consumed)throw new IllegalStateException("Tieji modifier not dispatched");
      return count==0;
    }finally{deferredInvoke=null;}
  }
  boolean continuationArmorBlocks(SgsPlayer source,SgsPlayer target,GameCard card){
    if(bus.queryInt(RuleId.SHA_EFFECT_ALLOWED,SkillContext.cardTarget(state,source,target,card),1)==0)return true;
    if(ignoresArmor(source)){
      if(target.equipment().armor()!=null)state.publishSkillActivation(source.seat(),"qinggang");
      return false;
    }
    if(target.equipment().armor()==null)return false;
    var armor=target.equipment().armor().type();
    if(armor==CardType.TENGJIA && card.type()==CardType.SHA){state.log("seat {} tengjia nullifies normal sha",target.seat());return true;}
    if(armor==CardType.RENWANG && card.isBlack()){
      state.log("seat {}'s renwang shield blocked the black sha from seat {}",target.seat(),source.seat());return true;
    }return false;
  }
  boolean continuationOwnBaguaAvailable(SgsPlayer source,SgsPlayer target){return !ignoresArmor(source)
      && target.equipment().armor()!=null && target.equipment().armor().type()==CardType.BAGUA;}
  void continuationSpendResponse(SgsPlayer actor,PlayDecision decision){spendDecision(actor,decision);}
  void continuationAfterDamage(SgsPlayer target,SgsPlayer source,int amount,GameCard card,DamageNature nature,boolean chain){fireAfterDamage(target,source,amount,card,nature,chain);}
  long continuationNow(){return clock.nowMillis();}
  <T> T inAttackProgram(AttackContinuation attack,java.util.function.Supplier<T> action){var previous=executingAttack;executingAttack=attack;try{return action.get();}finally{executingAttack=previous;}}
  boolean trickMovementsPending(){return activeTrick!=null&&activeTrick.movementsPending()||activeSkill!=null&&activeSkill.movementsPending();}
  boolean borrowedStrikeAllowed(SgsPlayer source,SgsPlayer target,GameCard card){return validAttackRangeAfterCost(source,target,card)&&cardTargetAllowed(source,target,card);}
  void trickBorrowWeapon(int source,int holder){transferBorrowedWeapon(state.players().get(source),state.players().get(holder));}
  boolean strikeWeaponChoice(PromptRequest request,SkillChoice choice){return validWeaponChoice(request,choice);}
  void validateNestedAttack(AttackContinuation attack,Set<Long> retained){validateAttackProgram(attack,retained,false);}
  private AttackContinuation currentAttackProgram(){return executingAttack!=null?executingAttack:activeAttack!=null?activeAttack:activeTrick!=null&&activeTrick.strike()!=null?activeTrick.strike().attackProgram():activeSkill!=null?activeSkill.attackProgram():null;}
  GameCard continuationParentCard(AttackContinuation child){
    var attack=currentAttackProgram();
    return attack!=null&&attack!=child?attack.card(this):activeTrick!=null?activeTrick.card(this):activeSkill!=null?activeSkill.card():null;
  }
  java.util.Optional<dev.sgs.core.engine.skill.AwaitableDamageEffect.Request> continuationDamageEffect(AttackContinuation.Snapshot damage,boolean requireApplicable){
    var f=damage.damageFrames().getFirst();return bus.damageEffect(SkillContext.damage(state,Timing.DAMAGE_TAKE_AFTER,state.players().get(damage.sourceSeat()),state.players().get(f.targetSeat()),f.amount(),damageCause(damage),f.nature(),f.chainDamage()),requireApplicable);
  }
  boolean continuationBeginDeferredDamage(SgsPlayer target,SgsPlayer source,int amount,GameCard card,DamageNature nature,boolean chain){
    if(state.status()!=GameStatus.RUNNING)return false;var context=SkillContext.damage(state,Timing.DAMAGE_TAKE_AFTER,source,target,amount,card,nature,chain);var effect=bus.damageEffect(context,true);
    if(effect.isEmpty())return false;
    if(source!=null&&source.alive())bus.trigger(Timing.DAMAGE_CAUSE_AFTER,SkillContext.damage(state,Timing.DAMAGE_CAUSE_AFTER,source,target,amount,card,nature,chain));
    bus.triggerExcept(Timing.DAMAGE_TAKE_AFTER,context,OrderStrategy.REGISTRATION_PRIORITY,effect.get().owner(),effect.get().skill());return true;
  }
  private SkillContext continuationDamageContext(AttackContinuation.Snapshot damage){
    var frame=damage.damageFrames().getFirst();return SkillContext.damage(state,Timing.DAMAGE_TAKE_AFTER,state.players().get(damage.sourceSeat()),state.players().get(frame.targetSeat()),frame.amount(),damageCause(damage),frame.nature(),frame.chainDamage());
  }
  boolean continuationBeginOrderedDamage(SgsPlayer target,SgsPlayer source,int amount,GameCard card,DamageNature nature,boolean chain){
    if(state.status()!=GameStatus.RUNNING||!bus.hasAwaitableDamage(SkillContext.damage(state,Timing.DAMAGE_TAKE_AFTER,source,target,amount,card,nature,chain)))return false;
    if(source!=null&&source.alive())bus.trigger(Timing.DAMAGE_CAUSE_AFTER,SkillContext.damage(state,Timing.DAMAGE_CAUSE_AFTER,source,target,amount,card,nature,chain));return true;
  }
  dev.sgs.core.engine.skill.ListenerProgram continuationBeginListeners(AttackContinuation.Snapshot damage){return bus.beginListeners(Timing.DAMAGE_TAKE_AFTER,continuationDamageContext(damage),OrderStrategy.REGISTRATION_PRIORITY);}
  java.util.Optional<dev.sgs.core.engine.skill.AwaitableDamageEffect.Request> continuationAdvanceListeners(dev.sgs.core.engine.skill.ListenerProgram listeners,AttackContinuation.Snapshot damage){return listeners.advance(bus,continuationDamageContext(damage),true);}
  void continuationCompleteListener(dev.sgs.core.engine.skill.ListenerProgram listeners,AttackContinuation.Snapshot damage,boolean activated){listeners.complete(bus,continuationDamageContext(damage),activated);}
  void continuationValidateListener(dev.sgs.core.engine.skill.ListenerProgram.Snapshot listeners,AttackContinuation.Snapshot damage){bus.validateListeners(listeners,continuationDamageContext(damage));}
  void continuationCompleteDamageEffect(dev.sgs.core.engine.skill.AwaitableDamageEffect.Request effect,boolean activated){
    bus.recordDeferred(Timing.DAMAGE_TAKE_AFTER,effect.owner(),effect.skill(),state.turnNumber());if(activated)state.publishSkillActivation(effect.owner(),effect.skill());
  }
  boolean trickYiJiApplies(SgsPlayer target,SgsPlayer source,int amount,GameCard card,DamageNature nature,boolean chain){
    return state.status()==GameStatus.RUNNING && target.hero().skillIds().contains("yiji") && new dev.sgs.core.engine.skill.skills.YiJiSkill(target,this).applies(SkillContext.damage(state,Timing.DAMAGE_TAKE_AFTER,source,target,amount,card,nature,chain));
  }
  void trickBeginYiJi(SgsPlayer target,SgsPlayer source,int amount,GameCard card,DamageNature nature,boolean chain){
    if(source!=null&&source.alive())bus.trigger(Timing.DAMAGE_CAUSE_AFTER,SkillContext.damage(state,Timing.DAMAGE_CAUSE_AFTER,source,target,amount,card,nature,chain));
    bus.triggerExcept(Timing.DAMAGE_TAKE_AFTER,SkillContext.damage(state,Timing.DAMAGE_TAKE_AFTER,source,target,amount,card,nature,chain),OrderStrategy.REGISTRATION_PRIORITY,target.seat(),"yiji");
  }
  void trickCompleteYiJi(int owner){bus.recordDeferred(Timing.DAMAGE_TAKE_AFTER,owner,"yiji",state.turnNumber());}
  boolean trickGanglieApplies(SgsPlayer target,SgsPlayer source,int amount,GameCard card,DamageNature nature,boolean chain){
    return state.status()==GameStatus.RUNNING && target.hero().skillIds().contains("ganglie") && new dev.sgs.core.engine.skill.skills.GangLieSkill(target,this).applies(SkillContext.damage(state,Timing.DAMAGE_TAKE_AFTER,source,target,amount,card,nature,chain));
  }
  void trickBeginGanglie(SgsPlayer target,SgsPlayer source,int amount,GameCard card,DamageNature nature,boolean chain){
    if(source!=null&&source.alive())bus.trigger(Timing.DAMAGE_CAUSE_AFTER,SkillContext.damage(state,Timing.DAMAGE_CAUSE_AFTER,source,target,amount,card,nature,chain));
    bus.triggerExcept(Timing.DAMAGE_TAKE_AFTER,SkillContext.damage(state,Timing.DAMAGE_TAKE_AFTER,source,target,amount,card,nature,chain),OrderStrategy.REGISTRATION_PRIORITY,target.seat(),"ganglie");
  }
  void trickCompleteGanglie(int owner,boolean accepted){bus.recordDeferred(Timing.DAMAGE_TAKE_AFTER,owner,"ganglie",state.turnNumber());if(accepted)state.publishSkillActivation(owner,"ganglie");}
  private GameCard damageCause(AttackContinuation.Snapshot damage){
    if(damage.cardless())return null;var attack=currentAttackProgram();if(attack!=null)return attack.card(this);if(activeTrick!=null)return activeTrick.card(this);if(activeSkill!=null&&damage.cardId()==0)return activeSkill.card();
    var physical=trickPhysical(damage.cardId());return physical.type()==damage.effectiveType()?physical:new VirtualCard(damage.effectiveType(),List.of(physical));
  }
  dev.sgs.core.card.Suit trickCardSuit(long id){return id==0?dev.sgs.core.card.Suit.NONE:trickPhysical(id).suit();}
  boolean trickFankuiApplies(SgsPlayer target,SgsPlayer source,int amount,GameCard card,DamageNature nature,boolean chain){
    return state.status()==GameStatus.RUNNING && target.hero().skillIds().contains("fankui") && new dev.sgs.core.engine.skill.skills.FanKuiSkill(target,this).applies(SkillContext.damage(state,Timing.DAMAGE_TAKE_AFTER,source,target,amount,card,nature,chain));
  }
  void trickBeginFankui(SgsPlayer target,SgsPlayer source,int amount,GameCard card,DamageNature nature,boolean chain){
    if(source!=null&&source.alive())bus.trigger(Timing.DAMAGE_CAUSE_AFTER,SkillContext.damage(state,Timing.DAMAGE_CAUSE_AFTER,source,target,amount,card,nature,chain));
    bus.triggerExcept(Timing.DAMAGE_TAKE_AFTER,SkillContext.damage(state,Timing.DAMAGE_TAKE_AFTER,source,target,amount,card,nature,chain),OrderStrategy.REGISTRATION_PRIORITY,target.seat(),"fankui");
  }
  boolean trickExecuteFankui(AttackContinuation.Snapshot damage,Optional<SkillChoice> answer){
    return continuationExecuteDamageTake(damage,new dev.sgs.core.engine.skill.AwaitableDamageEffect.Request(damage.targetSeat(),"fankui",dev.sgs.core.engine.skill.AwaitableDamageEffect.Kind.TAKE_SOURCE_CARD),answer);
  }
  boolean continuationExecuteDamageTake(AttackContinuation.Snapshot damage,dev.sgs.core.engine.skill.AwaitableDamageEffect.Request effect,Optional<SkillChoice> answer){
    var f=damage.damageFrames().getFirst();var owner=state.players().get(f.targetSeat());var source=state.players().get(damage.sourceSeat());
    if(effect.owner()!=owner.seat() || effect.kind()!=dev.sgs.core.engine.skill.AwaitableDamageEffect.Kind.TAKE_SOURCE_CARD)throw new IllegalArgumentException("damage take binding mismatch");
    if(deferredChoice!=null)throw new IllegalStateException("nested deferred choice");
    var input=new DeferredChoice(owner.seat(),source.seat(),PromptType.FANKUI_TAKE,effect.skill(),answer);deferredChoice=input;
    try{boolean activated=bus.executeDeferred(Timing.DAMAGE_TAKE_AFTER,SkillContext.damage(state,Timing.DAMAGE_TAKE_AFTER,source,owner,f.amount(),damageCause(damage),f.nature(),f.chainDamage()),owner.seat(),effect.skill());if(!input.consumed)throw new IllegalStateException("damage take hook not dispatched");return activated;}
    finally{deferredChoice=null;}
  }
  void trickCompleteFankui(int owner,boolean activated){bus.recordDeferred(Timing.DAMAGE_TAKE_AFTER,owner,"fankui",state.turnNumber());if(activated)state.publishSkillActivation(owner,"fankui");}
  List<Integer> trickJudgmentModifiers(){return state.alivePlayersFromCurrent().stream().filter(p->p.hp()>0&&p.handCount()>0&&p.hero().skillIds().contains("guicai")).map(SgsPlayer::seat).toList();}
  long trickRevealJudgment(int seat,long cause){
    var target=state.players().get(seat);var revealed=revealJudgmentCard();if(revealed!=null)eventListeners().forEach(l->l.onJudgmentRevealed(target,revealed));
    bus.trigger(Timing.JUDGE_CARD_REVEALED,SkillContext.judge(state,target,cause==0?null:trickPhysical(cause),revealed));return revealed==null?0:revealed.instanceId();
  }
  long trickModifyJudgment(int owner,int actor,long cause,long effective,Optional<SkillChoice> answer){
    var context=SkillContext.judge(state,state.players().get(owner),cause==0?null:trickPhysical(cause),trickPhysical(effective)).at(Timing.JUDGE_CARD_MODIFY);
    if(deferredChoice!=null)throw new IllegalStateException("nested judgment choice");
    var input=new DeferredChoice(actor,owner,PromptType.GUI_CAI_REPLACE,"guicai",answer);deferredChoice=input;
    try{bus.dispatchDeferred(Timing.JUDGE_CARD_MODIFY,context,actor,"guicai",OrderStrategy.MULTI_ENTITY_ORDER);if(!input.consumed)throw new IllegalStateException("guicai hook not dispatched");return context.judgeCard().instanceId();}
    finally{deferredChoice=null;}
  }
  void trickFinalizeJudgment(int owner,long effective){if(effective!=0){var card=trickPhysical(effective);eventListeners().forEach(l->l.onJudgmentFinalized(state.players().get(owner),card));}}
  void trickFinishJudgment(long id,int owner){if(id!=0)state.cards().finish(trickPhysical(id),owner);}
  private CardInstance trickPhysical(long id){return state.cards().zones().stream().flatMap(z->state.cards().cards(z).stream()).filter(c->c.instanceId()==id).findFirst().orElseThrow();}
  boolean trickTianduApplies(int seat,long id){
    var armor=state.players().get(seat).equipment().armor();return trickTianduApplies(seat,armor==null?0:armor.instanceId(),id);
  }
  boolean trickTianduApplies(int seat,long cause,long id){
    if(id==0)return false;var target=state.players().get(seat);
    return target.hero().skillIds().contains("tiandu") && new dev.sgs.core.engine.skill.skills.TianDuSkill(target,this).applies(SkillContext.judge(state,target,cause==0?null:trickPhysical(cause),trickPhysical(id)).at(Timing.JUDGE_CARD_EFFECT));
  }
  boolean trickCompleteBaguaJudgment(int seat,long id,boolean accepted){
    var armor=state.players().get(seat).equipment().armor();return trickCompleteJudgment(seat,armor==null?0:armor.instanceId(),id,accepted);
  }
  boolean trickCompleteJudgment(int seat,long cause,long id,boolean accepted){
    if(id==0)return false;var target=state.players().get(seat);var card=trickPhysical(id);boolean red=card.isRed();
    var context=SkillContext.judge(state,target,cause==0?null:trickPhysical(cause),card).at(Timing.JUDGE_CARD_EFFECT);
    if(deferredInvoke!=null)throw new IllegalStateException("nested judgment invocation");
    var input=trickTianduApplies(seat,cause,id)?new DeferredInvoke(seat,seat,"tiandu",accepted):null;deferredInvoke=input;
    try{bus.trigger(Timing.JUDGE_CARD_EFFECT,context);if(input!=null&&!input.consumed)throw new IllegalStateException("tiandu hook was not dispatched");}
    finally{deferredInvoke=null;state.cards().finish(card,seat);}
    return red;
  }
  List<Integer> continuationHujiaProviders(SgsPlayer lord){
    if(!lord.alive() || lord.identity()!=Identity.LORD || lord.hero()==null || !lord.hero().skillIds().contains("hujia"))return List.of();
    return state.alivePlayersFromCurrent().stream().filter(p->p!=lord && p.hero()!=null && p.hero().kingdom()==dev.sgs.core.Kingdom.WEI).map(SgsPlayer::seat).toList();
  }
  boolean continuationJianxiongApplies(SgsPlayer target,SgsPlayer source,int amount,GameCard card,DamageNature nature,boolean chain){
    return state.status()==GameStatus.RUNNING && target.hero()!=null && target.hero().skillIds().contains("jianxiong")
        && new dev.sgs.core.engine.skill.skills.JianXiongSkill(target,this).applies(
            SkillContext.damage(state,Timing.DAMAGE_TAKE_AFTER,source,target,amount,card,nature,chain));
  }
  void continuationAfterDamageWithInvoke(SgsPlayer target,SgsPlayer source,int amount,GameCard card,DamageNature nature,boolean chain,boolean accepted){
    if(deferredInvoke!=null)throw new IllegalStateException("nested deferred invocation");
    var input=new DeferredInvoke(target.seat(),seatOf(source),"jianxiong",accepted);deferredInvoke=input;
    try{
      fireAfterDamage(target,source,amount,card,nature,chain);
      if(!input.consumed)throw new IllegalStateException("deferred hook was not dispatched");
    }finally{deferredInvoke=null;}
  }

  /** Whole unsupported combinations stay on the legacy path before any new-entry cost is paid. */
  TargetRedirectContinuation continuationTargetRedirect(int source,int target,GameCard card,List<Integer> group){
    var request=bus.targetRedirect(SkillContext.attackTarget(state,state.players().get(source),state.players().get(target),card));
    return request.map(r->new TargetRedirectContinuation(this,source,target,r.skill(),card,group)).orElse(null);
  }
  boolean continuationRedirectBound(int owner,String skill){return bus.hasTargetRedirectBinding(owner,skill);}
  void continuationRedirectCompleted(int owner,String skill,boolean activated){
    bus.recordDeferred(Timing.SHA_TARGET_BEFORE,owner,skill,state.turnNumber());if(activated)state.publishSkillActivation(owner,skill);
  }
  public boolean supportsAttackContinuation(){
    if(!bus.checkpointBindings().equals(checkpointInitialBindings) || !checkpointHeroDefinitions().equals(checkpointInitialHeroes))return false;
    // A positive audited allowlist keeps newly added skills on the whole legacy path until migrated.
    var audited=java.util.Set.of("liubei","zhangfei","zhaoyun","caocao","guanyu","huanggai","huangyueying","ganning","huatuo","sunquan","lumeng","zhouyu","zhenji","xuchu","zhugeliang","machao","lvbu","huangzhong","guojia","simayi","xiahoudun","daqiao");
    return state.players().stream().allMatch(p->{var h=p.hero();return h!=null && audited.contains(h.id())
        && dev.sgs.core.hero.StandardHeroes.CATALOG.stream().anyMatch(c->c.id().equals(h.id()) && c.kingdom()==h.kingdom() && c.maxHp()==h.maxHp() && c.male()==h.male() && c.skillIds().equals(h.skillIds()));});
  }
  public Optional<PromptRequest> pendingAttackPrompt(){return activeAttack==null || activeAttack.pending().isEmpty()?Optional.empty():Optional.of(activeAttack.request(this));}
  public long attackDecisionRevision(){return activeAttack==null?-1:activeAttack.snapshot().revision();}
  public boolean hasAttackContinuation(){return activeAttack!=null;}

  /** Nonblocking ingress: commits one attack and returns at its first decision. */
  public ActionResult beginResumableAttack(SgsPlayer source,long cardId,CardType declared,SgsPlayer target){
    return beginResumableAttackTargets(source,cardId,declared,target==null?List.of():List.of(target));
  }
  /** Additional targets belong to this one committed Sha, never to repeated card uses. */
  public int attackTargetLimit(SgsPlayer source,long cardId,CardType declared){
    var cost=findOwnedCost(source,cardId);if(cost==null)return 1;
    var type=declared==null?cost.type():declared;var weapon=source.equipment().weapon();
    int base=Set.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA).contains(type) && weapon!=null && weapon.type()==CardType.FANGTIAN
        && source.handCount()==1 && source.handView().getFirst().instanceId()==cardId?3:1;
    return base+skills.mythic().additionalTargets(source);
  }
  public ActionResult attackTargetAvailability(SgsPlayer source,long cardId,CardType declared,SgsPlayer target){
    var physical=findOwnedCost(source,cardId);if(physical==null)return ActionResult.rejected(RejectReason.CARD_NOT_IN_HAND);
    var type=declared==null?physical.type():declared;
    if(!Set.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA).contains(type))return ActionResult.rejected(RejectReason.UNKNOWN_CARD);
    var result=validateShaCost(source,target,physical);if(!result.accepted())return result;
    return cardTargetAllowed(source,target,type==physical.type()?physical:new dev.sgs.core.card.VirtualCard(type,List.of(physical)))?ActionResult.ok():ActionResult.rejected(RejectReason.TARGET_PROHIBITED);
  }
  public ActionResult beginResumableAttackTargets(SgsPlayer source,long cardId,CardType declared,List<SgsPlayer> targets){
    assertContinuationIngress();
    if(!supportsAttackContinuation())return ActionResult.rejected(RejectReason.NOT_YET_IMPLEMENTED);
    return launchAttackTargets(source,cardId,declared,targets,true);
  }
  /** Whole synchronous route for combinations not yet migrated; also an independent oracle. */
  public ActionResult playAttackTargets(SgsPlayer source,long cardId,CardType declared,List<SgsPlayer> targets){
    return launchAttackTargets(source,cardId,declared,targets,false);
  }
  private ActionResult launchAttackTargets(SgsPlayer source,long cardId,CardType declared,List<SgsPlayer> targets,boolean yielded){
    if(hasActiveContinuation())return ActionResult.rejected(RejectReason.WRONG_PHASE);
    if(state.status()!=GameStatus.RUNNING)return ActionResult.rejected(RejectReason.GAME_NOT_RUNNING);
    if(source!=state.currentPlayer())return ActionResult.rejected(RejectReason.NOT_YOUR_TURN);
    if(state.phase()!=Phase.PLAY)return ActionResult.rejected(RejectReason.WRONG_PHASE);
    var physical=findOwnedCost(source,cardId);if(physical==null)return ActionResult.rejected(RejectReason.CARD_NOT_IN_HAND);
    var type=declared==null?physical.type():declared;
    if(!Set.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA).contains(type))return ActionResult.rejected(RejectReason.UNKNOWN_CARD);
    if(targets==null || targets.isEmpty())return ActionResult.rejected(RejectReason.TARGET_REQUIRED);
    if(targets.size()>attackTargetLimit(source,cardId,type))return ActionResult.rejected(RejectReason.TARGET_COUNT);
    if(new java.util.HashSet<>(targets).size()!=targets.size())return ActionResult.rejected(RejectReason.DUPLICATE_TARGET);
    boolean equipment=CardZone.equipment(source.seat()).equals(state.cards().locate(cardId));
    if(equipment && (type!=CardType.SHA || physical.type()==type))return ActionResult.rejected(RejectReason.CARD_NOT_IN_HAND);
    GameCard proposed=type==physical.type()?physical:new dev.sgs.core.card.VirtualCard(type,List.of(physical));
    for(var target:targets){
      if(target==null || !state.players().contains(target))return ActionResult.rejected(RejectReason.TARGET_NOT_IN_GAME);
      var validation=validateShaCost(source,target,physical);if(!validation.accepted())return validation;
      if(!cardTargetAllowed(source,target,proposed))return ActionResult.rejected(RejectReason.TARGET_PROHIBITED);
    }
    var ordered=targets.stream().sorted(java.util.Comparator.comparingInt(p->Math.floorMod(p.seat()-source.seat(),state.players().size()))).toList();
    checkpointOperations++;
    try{
      GameCard card=physical;
      if(type!=physical.type()){
        var transform=SkillContext.transform(state,source,physical,type,null);bus.trigger(Timing.CARD_TRANSFORM,transform);
        if(!transform.transformConfirmed())return ActionResult.rejected(RejectReason.TRANSFORM_NOT_AVAILABLE);
        card=transform.card();
      }
      if(yielded){activeAttack=AttackContinuation.ordinaryUse(this,source.seat(),ordered.stream().map(SgsPlayer::seat).toList(),card);advanceAttack();return ActionResult.ok();}
      beginOwnedUse(source,card,USE);state.registerShaPlayed();cardUseCommitted(source,card);
      int damage=state.consumeJiuBoost()?2:1;
      if(ordered.size()>1){state.publishSkillActivation(source.seat(),"fangtian");state.log("seat {} fangtian targets {}",source.seat(),ordered.stream().map(SgsPlayer::seat).toList());}
      {
        var previous=attackGroupTargets;attackGroupTargets=Set.copyOf(ordered.stream().map(SgsPlayer::seat).toList());
        try{for(var target:ordered)if(state.status()==GameStatus.RUNNING && target.alive())resolveAttack(source,target,damage,card);}
        finally{attackGroupTargets=previous;state.cards().finish(card,source.seat());}
      }
      return ActionResult.ok();
    }catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }
  private void assertContinuationOwner(){
    if(Thread.currentThread()!=checkpointOwner || checkpointFaulted)throw new IllegalStateException("continuation requires healthy owner");
  }
  void continuationCardUseCommitted(SgsPlayer actor,GameCard card){cardUseCommitted(actor,card);}
  private void assertContinuationIngress(){
    assertContinuationOwner();
    if(executingAttack!=null || checkpointOperations!=0 || !stack.checkpointIdle() || !bus.checkpointIdle())throw new IllegalStateException("continuation ingress is reentrant");
  }
  /** A transport control can observe a yielded parent or a completed action, never an inner call. */
  public boolean checkpointControlBoundary(){
    assertContinuationOwner();
    return executingAttack==null&&checkpointOperations==0&&stack.checkpointIdle()&&bus.checkpointIdle();
  }
  private void advanceAttack(){
    if(activeAttack!=null){activeAttack.advance(this);if(activeAttack.done())activeAttack=null;}
  }
  public void pollResumableAttack(){
    assertContinuationIngress();checkpointOperations++;
    try{advanceAttack();}catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}finally{checkpointOperations--;}
  }
  /** Invalid replies preserve the same pending window and deadline. No caller-supplied actor seat. */
  public ActionResult resumeAttack(java.util.UUID actor,long promptId,long revision,boolean accept,long cardId,CardType declared){
    assertContinuationIngress();
    if(activeAttack==null || activeAttack.pending().isEmpty())return ActionResult.rejected(RejectReason.WRONG_PHASE);
    var prompt=activeAttack.request(this);
    if(!DecisionBoundary.of(prompt,activeAttack.snapshot().revision()).matches(actor,promptId,revision))return ActionResult.rejected(RejectReason.RESPONSE_ONLY);
    if(DecisionBoundary.of(prompt,activeAttack.snapshot().revision()).expired(clock.nowMillis())){pollResumableAttack();return ActionResult.rejected(RejectReason.RESPONSE_ONLY);}
    if((java.util.Set.of(PromptType.CARD_ZONE_SELECT,PromptType.WEAPON_DISCARD,PromptType.WEAPON_COST_FIRST,PromptType.WEAPON_COST_SECOND,PromptType.GUI_CAI_REPLACE,PromptType.YIJI_GIVE,PromptType.FANKUI_TAKE,PromptType.GANGLIE_DISCARD,PromptType.LIULI_TARGET).contains(prompt.type())))return ActionResult.rejected(RejectReason.RESPONSE_ONLY);
    if(!accept && (cardId!=-1 || declared!=null))return ActionResult.rejected(RejectReason.UNKNOWN_CARD);
    checkpointOperations++;
    try{
      Optional<PlayDecision> decision=Optional.empty();
      if(prompt.type()==PromptType.SKILL_INVOKE){if(cardId!=-1 || declared!=null)return ActionResult.rejected(RejectReason.UNKNOWN_CARD);}
      else if(accept){
        var physical=findOwnedCost(prompt.asker(),cardId);if(physical==null)return ActionResult.rejected(RejectReason.CARD_NOT_IN_HAND);
        decision=validateContinuationDecision(prompt,new PlayDecision(physical,declared==null?physical.type():declared));
        if(decision.isEmpty())return ActionResult.rejected(RejectReason.RESPONSE_ONLY);
      }
      activeAttack.answer(this,decision,accept && prompt.type()==PromptType.SKILL_INVOKE);advanceAttack();return ActionResult.ok();
    }catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}finally{checkpointOperations--;}
  }

  /** Two real Zhangba hand costs while supplying a Qinglong follow-up; never collapse to the first ID. */
  public ActionResult resumeAttackPair(java.util.UUID actor,long promptId,long revision,List<Long> ids){
    assertContinuationIngress();
    if(activeAttack==null || activeAttack.pending().isEmpty())return ActionResult.rejected(RejectReason.WRONG_PHASE);
    var p=activeAttack.request(this);
    if(p.type()!=PromptType.JIJIANG_SHA || activeAttack.snapshot().step()!=AttackContinuation.AttackStep.QINGLONG_PROVIDER_SHA || !DecisionBoundary.of(p,activeAttack.snapshot().revision()).matches(actor,promptId,revision))return ActionResult.rejected(RejectReason.RESPONSE_ONLY);
    if(DecisionBoundary.of(p,activeAttack.snapshot().revision()).expired(clock.nowMillis())){pollResumableAttack();return ActionResult.rejected(RejectReason.RESPONSE_ONLY);}
    if(ids==null || ids.size()!=2 || ids.stream().anyMatch(Objects::isNull) || ids.get(0).equals(ids.get(1)))return ActionResult.rejected(RejectReason.UNKNOWN_CARD);
    var costs=ids.stream().map(id->findInHand(p.asker(),id)).toList();if(costs.stream().anyMatch(Objects::isNull))return ActionResult.rejected(RejectReason.CARD_NOT_IN_HAND);
    checkpointOperations++;try{var decision=validateContinuationDecision(p,PlayDecision.asIs(new VirtualCard(CardType.SHA,costs)));if(decision.isEmpty())return ActionResult.rejected(RejectReason.RESPONSE_ONLY);activeAttack.answer(this,decision,false);advanceAttack();return ActionResult.ok();}
    catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}finally{checkpointOperations--;}
  }

  /** Public equipment choice still belongs to the attacking actor, never the equipment owner. */
  public ActionResult resumeAttackChoice(java.util.UUID actor,long promptId,long revision,Optional<SkillChoice> choice){
    assertContinuationIngress();
    if(activeAttack==null || activeAttack.pending().isEmpty())return ActionResult.rejected(RejectReason.WRONG_PHASE);
    var p=activeAttack.request(this);
    if(!java.util.Set.of(PromptType.CARD_ZONE_SELECT,PromptType.WEAPON_DISCARD,PromptType.WEAPON_COST_FIRST,PromptType.WEAPON_COST_SECOND,PromptType.GUI_CAI_REPLACE,PromptType.YIJI_GIVE,PromptType.FANKUI_TAKE,PromptType.GANGLIE_DISCARD,PromptType.LIULI_TARGET).contains(p.type()) || !DecisionBoundary.of(p,activeAttack.snapshot().revision()).matches(actor,promptId,revision))
      return ActionResult.rejected(RejectReason.RESPONSE_ONLY);
    if(DecisionBoundary.of(p,activeAttack.snapshot().revision()).expired(clock.nowMillis())){pollResumableAttack();return ActionResult.rejected(RejectReason.RESPONSE_ONLY);}
    if(choice.isPresent() && !activeAttack.childChoice() && (!validWeaponChoice(p,choice.get())))
      return ActionResult.rejected(RejectReason.UNKNOWN_CARD);
    checkpointOperations++;
    try{if(activeAttack.childChoice()){if(!activeAttack.chooseChild(this,choice))return ActionResult.rejected(RejectReason.UNKNOWN_CARD);}else activeAttack.chooseWeapon(this,choice);advanceAttack();return ActionResult.ok();}
    catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}finally{checkpointOperations--;}
  }
  boolean continuationQinglongAvailable(SgsPlayer source,SgsPlayer target){
    return state.status()==GameStatus.RUNNING && source.alive() && source.hp()>0 && target.alive() && target.hp()>0
        && source.equipment().weapon()!=null && source.equipment().weapon().type()==CardType.QINGLONG
        && cardTargetAllowed(source,target,new VirtualCard(CardType.SHA,List.of()));
  }
  void continuationCommitQinglong(SgsPlayer source,SgsPlayer target,GameCard sha){
    validateQinglongPayment(source,target,source,sha,false);beginOwnedUse(source,sha,USE);continuationCompleteQinglong(source,target,source,sha,false);
  }
  void validateQinglongPayment(SgsPlayer source,SgsPlayer target,SgsPlayer supplier,GameCard sha,boolean provided){
    if(provided){
      if(!continuationQinglongAvailable(source,target) || !trickLordProviders(source,true).contains(supplier.seat()) || !cardTargetAllowed(source,target,sha) || supplier==target && emptiesKongcheng(supplier,sha))throw new IllegalStateException("invalid Qinglong supplied Sha");
      return;
    }
    if(!continuationQinglongAvailable(source,target) || sha.sourceCards().size()!=1)throw new IllegalStateException("invalid qinglong use");
    if(supplier!=source||!cardTargetAllowed(source,target,sha))throw new IllegalStateException("prohibited Qinglong target");
  }
  void continuationFinishQinglongParent(int source,List<Long> ids){
    var costs=ids.stream().map(id->state.cards().cards(CardZone.PROCESSING).stream().filter(c->c.instanceId()==id).findFirst().orElseThrow()).toList();state.cards().finish(new VirtualCard(CardType.SHA,costs),source);
  }
  void continuationCommitProvidedQinglong(SgsPlayer lord,SgsPlayer target,SgsPlayer helper,GameCard sha){
    validateQinglongPayment(lord,target,helper,sha,true);beginOwnedUse(helper,sha,RESPONSE);continuationCompleteQinglong(lord,target,helper,sha,true);
  }
  void continuationCompleteQinglong(SgsPlayer source,SgsPlayer target,SgsPlayer supplier,GameCard sha,boolean provided){
    if(provided){state.publishSkillActivation(source.seat(),"jijiang");state.log("seat {} used jijiang: sha supplied by seat {}",source.seat(),supplier.seat());}
    continuationUseQinglong(source,target,sha);
  }
  private void continuationUseQinglong(SgsPlayer source,SgsPlayer target,GameCard sha){
    if(state.currentPlayer()==source && state.phase()==Phase.PLAY)state.registerShaPlayed();cardUseCommitted(source,sha);state.publishSkillActivation(source.seat(),"qinglong");state.log("seat {} used qinglong: {} against seat {}",source.seat(),sha.type().id(),target.seat());
  }
  private void resolveQinglong(SgsPlayer source,SgsPlayer target){
    if(!continuationQinglongAvailable(source,target))return;
    var provided=provideJijiangAgainst(source,true,target);if(provided.isPresent()){var sha=provided.get();try{continuationUseQinglong(source,target,sha);resolveAttack(source,target,1,sha);}finally{state.cards().finish(sha,source.seat());}return;}
    var prompt=new PromptRequest(nextPromptId(),PromptType.QINGLONG_SHA,source,target,List.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA),List.of(),responseDeadlineMillis(),"qinglong",List.of(),false);
    var answer=ask(prompt);if(answer.isEmpty())return;var sha=answer.get().card();
    continuationCommitQinglong(source,target,sha);try{resolveAttack(source,target,1,sha);}finally{state.cards().finish(sha,source.seat());}
  }
  List<CardInstance> continuationGuanshiCosts(SgsPlayer source){
    return guanshiProposal(source,List.of()).options(state,guanshiExcluded(source));
  }
  private java.util.Set<Long> guanshiExcluded(SgsPlayer source){var weapon=source.equipment().weapon();return weapon!=null&&weapon.type()==CardType.GUANSHI?java.util.Set.of(weapon.instanceId()):java.util.Set.of();}
  private CardCostSelection guanshiProposal(SgsPlayer source,List<Long> selected){return new CardCostSelection(source.seat(),2,CardCostSelection.Scope.OWNED,selected);}
  Optional<CardCostSelection> continuationGuanshiSelect(SgsPlayer source,long first,long next){return guanshiProposal(source,first==0?List.of():List.of(first)).select(state,next,guanshiExcluded(source));}
  boolean continuationGuanshiAvailable(SgsPlayer source){return source.alive() && source.hp()>0 && source.equipment().weapon()!=null && source.equipment().weapon().type()==CardType.GUANSHI && continuationGuanshiCosts(source).size()>=2;}
  boolean continuationGuanshiPay(SgsPlayer source,long first,long second){
    var costs=continuationGuanshiPayment(source,first,second);if(costs.isEmpty())return false;
    discardCards(source,costs.get());continuationGuanshiFinish(source);return true;
  }
  Optional<List<CardInstance>> continuationGuanshiPayment(SgsPlayer source,long first,long second){
    if(!continuationGuanshiAvailable(source))return Optional.empty();
    var firstProposal=continuationGuanshiSelect(source,0,first);if(firstProposal.isEmpty())return Optional.empty();
    var complete=firstProposal.get().select(state,second,guanshiExcluded(source));if(complete.isEmpty())return Optional.empty();
    var resolved=complete.get().resolve(state,guanshiExcluded(source));if(resolved.isEmpty())return Optional.empty();
    // Keep the historical hand/equipment ordering of the committed move and its public events.
    var ids=complete.get().selected();var costs=continuationGuanshiCosts(source).stream().filter(c->ids.contains(c.instanceId())).toList();
    return Optional.of(costs);
  }
  void continuationGuanshiFinish(SgsPlayer source){state.publishSkillActivation(source.seat(),"guanshi");state.log("seat {} guanshi discarded two cards and forced the original sha",source.seat());}
  private boolean resolveGuanshi(SgsPlayer source,SgsPlayer target){
    if(!continuationGuanshiAvailable(source) || !requestSkillInvoke(source,"guanshi",target))return false;
    var first=requestSkillChoice(source,target,PromptType.WEAPON_COST_FIRST,"guanshi",continuationGuanshiCosts(source),List.of(),false);if(first.isEmpty())return false;
    var second=requestSkillChoice(source,target,PromptType.WEAPON_COST_SECOND,"guanshi",continuationGuanshiCosts(source).stream().filter(c->c.instanceId()!=first.get().cardId()).toList(),List.of(),false);return second.isPresent()&&continuationGuanshiPay(source,first.get().cardId(),second.get().cardId());
  }
  private boolean validWeaponChoice(PromptRequest p,SkillChoice choice){
    if(choice.targetSeat()!=-1)return false;
    if(p.choices().stream().anyMatch(c->c.instanceId()==choice.cardId()))return true;
    return p.skillId().equals("hanbing") && p.type()==PromptType.CARD_ZONE_SELECT && p.allowsHiddenChoice(choice.cardId());
  }
  List<CardInstance> continuationHanbingCards(SgsPlayer target){
    var cards=new ArrayList<CardInstance>(target.handView());cards.addAll(target.equipment().all());return List.copyOf(cards);
  }
  boolean continuationHanbingAvailable(SgsPlayer source,SgsPlayer target){return source.alive() && source.hp()>0 && target.alive()
      && source.equipment().weapon()!=null && source.equipment().weapon().type()==CardType.HANBING && !continuationHanbingCards(target).isEmpty();}
  void continuationHanbingDiscard(SgsPlayer source,SgsPlayer target,Optional<SkillChoice> answer){
    var chosen=continuationHanbingSelection(target,answer);if(chosen==null)return;
    state.cards().move(List.of(chosen),state.cards().locate(chosen.instanceId()),CardZone.DISCARD,DISCARD,source.seat());
    continuationHanbingDiscarded(source,target,chosen);
  }
  CardInstance continuationHanbingSelection(SgsPlayer target,Optional<SkillChoice> answer){
    var available=continuationHanbingCards(target);if(available.isEmpty()||!target.alive())return null;
    long id=answer.map(SkillChoice::cardId).orElse(Long.MIN_VALUE);CardInstance chosen;
    if(id==Long.MIN_VALUE)chosen=available.get(state.random().nextInt(available.size()));
    else if(id==0)chosen=target.handView().isEmpty()?null:target.handView().get(state.random().nextInt(target.handCount()));
    else if(id<=-2){int index=HandSelection.positionIndex(id,target.handCount());chosen=index>=0?target.handView().get(index):null;}
    else chosen=target.equipment().all().stream().filter(c->c.instanceId()==id).findFirst().orElse(null);
    if(chosen==null)throw new IllegalArgumentException("invalid hanbing choice");
    return chosen;
  }
  void continuationHanbingDiscarded(SgsPlayer source,SgsPlayer target,CardInstance chosen){
    state.log("seat {} hanbing discarded {} from seat {}",source.seat(),chosen.cardId(),target.seat());
  }
  void continuationHanbingFinish(SgsPlayer source,SgsPlayer target){state.publishSkillActivation(source.seat(),"hanbing");state.log("seat {} hanbing prevented attack damage to seat {}",source.seat(),target.seat());}
  private boolean resolveHanbing(SgsPlayer source,SgsPlayer target){
    if(!continuationHanbingAvailable(source,target)||!requestSkillInvoke(source,"hanbing",target))return false;
    for(int i=0;i<2 && target.alive() && !continuationHanbingCards(target).isEmpty();i++){
      var answer=requestChoice(source,target,PromptType.CARD_ZONE_SELECT,"hanbing",target.equipment().all(),List.of(),target.handCount()>0).choice();
      continuationHanbingDiscard(source,target,answer);
    }
    continuationHanbingFinish(source,target);return true;
  }
  boolean continuationCixiongAvailable(SgsPlayer source,SgsPlayer target){
    return source.alive() && source.hp()>0 && target.alive() && target.hp()>0 && source.hero()!=null && target.hero()!=null
        && state.male(source)!=state.male(target) && source.equipment().weapon()!=null && source.equipment().weapon().type()==CardType.CIXIONG;
  }
  void continuationCixiongFinish(SgsPlayer source,SgsPlayer target,Optional<SkillChoice> choice){
    var card=choice.flatMap(c->target.handView().stream().filter(h->h.instanceId()==c.cardId()).findFirst()).orElse(null);
    if(card!=null){discardCards(target,List.of(card));continuationCixiongDiscarded(source,target);return;}
    else{drawCards(source,1);state.log("seat {} cixiong drew one card instead of target seat {} discarding",source.seat(),target.seat());}
    state.publishSkillActivation(source.seat(),"cixiong");
  }
  void continuationCixiongDiscarded(SgsPlayer source,SgsPlayer target){
    state.log("seat {} cixiong target seat {} discarded one hand card",source.seat(),target.seat());state.publishSkillActivation(source.seat(),"cixiong");
  }
  private void resolveCixiong(SgsPlayer source,SgsPlayer target){
    if(!continuationCixiongAvailable(source,target) || !requestSkillInvoke(source,"cixiong",target))return;
    var choice=target.handCount()==0?Optional.<SkillChoice>empty():requestSkillChoice(target,source,PromptType.WEAPON_DISCARD,"cixiong",target.handView(),List.of(),false);
    continuationCixiongFinish(source,target,choice);
  }
  List<CardInstance> continuationQilinHorses(SgsPlayer source,SgsPlayer target){
    if(!source.alive() || source.hp()<1 || !target.alive() || source.equipment().weapon()==null || source.equipment().weapon().type()!=CardType.QILIN_GONG)return List.of();
    return target.equipment().all().stream().filter(c->c.type().category()==dev.sgs.core.card.CardCategory.HORSE_PLUS || c.type().category()==dev.sgs.core.card.CardCategory.HORSE_MINUS).toList();
  }
  void continuationQilinDiscard(SgsPlayer source,SgsPlayer target,long selected){
    var card=continuationQilinHorses(source,target).stream().filter(c->c.instanceId()==selected).findFirst().orElse(null);
    if(card==null)return;
    state.cards().move(List.of(card),state.cards().locate(selected),CardZone.DISCARD,DISCARD,source.seat());
    continuationQilinDiscarded(source,target,card);
  }
  void continuationQilinDiscarded(SgsPlayer source,SgsPlayer target,CardInstance card){
    state.publishSkillActivation(source.seat(),"qilin_gong");
    state.log("seat {} qilin_gong discarded {} from seat {}",source.seat(),card.cardId(),target.seat());
  }
  private void resolveQilin(SgsPlayer source,SgsPlayer target){
    var horses=continuationQilinHorses(source,target);
    if(horses.isEmpty() || !requestSkillInvoke(source,"qilin_gong",target))return;
    long chosen=horses.getFirst().instanceId();
    if(horses.size()>1){var result=requestChoice(source,target,PromptType.CARD_ZONE_SELECT,"qilin_gong",horses,List.of(),false);
      chosen=result.choice().map(SkillChoice::cardId).orElse(chosen);
    }
    continuationQilinDiscard(source,target,chosen);
  }

  /** Captures an already yielded program; the synchronous adapter cannot save its Java caller. */
  public dev.sgs.core.persistence.ContinuationCheckpoint checkpointAttack(String content){
    assertContinuationOwner();
    if(checkpointOperations!=0 || !stack.checkpointIdle() || !bus.checkpointIdle() || activeAttack==null
        || activeAttack.pending().isEmpty())throw new IllegalStateException("attack is not yielded");
    if(!listeners.isEmpty() || state.cards().observerCount()!=0 || state.logObserverCount()!=0
        || state.cards().ruleListenerRevision()!=checkpointMovementRevision)throw new IllegalStateException("unversioned observers cannot be restored");
    validateAttackProgram();
    var saved=new dev.sgs.core.persistence.ContinuationCheckpoint(activeAttack.snapshot().version(),
        dev.sgs.core.persistence.StateCheckpoint.capture(state,content),promptSequence,responseTimeoutMillis/1000,
        skills.saveMemory(),bus.checkpointBindings(),state.publicSkillSequence(),state.publicSkillActivations(),activeAttack.snapshot(),clock.nowMillis());
    saved.validate();return saved;
  }
  public static SgsGameEngine restoreAttack(dev.sgs.core.persistence.ContinuationCheckpoint saved,String expectedContent,PromptResponder responder,Clock clock){
    saved.validate();if(!saved.state().contentVersion().equals(expectedContent))throw new IllegalArgumentException("content version mismatch");
    var engine=new SgsGameEngine(saved.state().restore(),responder,clock,saved.responseTimeoutSeconds());
    if(!engine.bus.checkpointBindings().equals(saved.bindings()))throw new IllegalArgumentException("binding order mismatch");
    engine.skills.restoreMemory(saved.skillMemory(),engine.state.turnNumber());engine.promptSequence=saved.promptSequence();
    engine.state.restoreSkillActivations(saved.skillSequence(),saved.activations());
    engine.activeAttack=new AttackContinuation(saved.continuation());engine.validateAttackProgram();
    var pending=saved.continuation().pending();
    if(pending.deadlineMillis()-saved.capturedAtMillis()>engine.responseTimeoutMillis)throw new IllegalArgumentException("pending remaining time exceeds configured timeout");
    engine.activeAttack.restoreDeadline(saved.capturedAtMillis(),clock.nowMillis());
    if(pending.deadlineMillis()<=saved.capturedAtMillis()){
      engine.activeAttack.answer(engine,Optional.empty(),false);engine.pollResumableAttack();
    }
    return engine;
  }
  private void validateAttackRuleQuery(AttackContinuation.Snapshot frame){
    var query=frame.ruleQuery();IntRuleContinuation.validate(query,frame.pending(),frame.targetSeat(),state.players().size());
    var context=SkillContext.cardTarget(state,state.players().get(frame.sourceSeat()),state.players().get(frame.targetSeat()),currentAttackProgram().card(this));
    if(frame.step()!=AttackContinuation.AttackStep.TARGET_RULES || frame.dodgeProhibited() || !frame.damageFrames().isEmpty() || !frame.providers().isEmpty() || frame.providerCursor()!=0
        || query.effect().owner()!=frame.sourceSeat() || !bus.matchesAwaitedInt(query.rule(),context,query.cursor(),query.value(),query.effect(),query.step()==IntRuleContinuation.Step.INVOKE))throw new IllegalArgumentException("rule child binding or parent mismatch");
    var j=query.judgment();if(j==null)return;
    var attackCosts=new HashSet<Long>(frame.parents());attackCosts.add(frame.cardId());attackCosts.addAll(frame.extraCosts());
    if(attackCosts.contains(j.revealed()) || attackCosts.contains(j.effective()) || !CardZone.PROCESSING.equals(state.cards().locate(j.effective()))
        || j.revealed()!=j.effective()&&!CardZone.DISCARD.equals(state.cards().locate(j.revealed())))throw new IllegalArgumentException("rule judgment entity mismatch");
    var ring=state.alivePlayersFromCurrent().stream().map(SgsPlayer::seat).toList();int previous=-1;
    for(int modifier:j.modifiers()){int index=ring.indexOf(modifier);if(index<=previous || !state.players().get(modifier).hero().skillIds().contains("guicai"))throw new IllegalArgumentException("rule judgment modifier order");previous=index;}
    var processed=new HashSet<>(j.modifiers().subList(0,j.cursor()));
    if(!trickJudgmentModifiers().stream().filter(seat->!processed.contains(seat)).toList().equals(j.modifiers().subList(j.cursor(),j.modifiers().size()))
        || j.step()==JudgmentContinuation.Step.TIANDU&&!trickTianduApplies(j.owner(),0,j.effective()))throw new IllegalArgumentException("rule judgment modifier cursor");
    if(state.publicSkillActivations().stream().noneMatch(a->a.seat()==query.effect().owner() && a.skillId().equals(query.effect().skill())))throw new IllegalArgumentException("rule judgment lost invocation receipt");
  }
  /** Validate the current damage call and every suspended caller in the actual attack scope. */
  private void validateAttackDamageProgram(AttackContinuation.Snapshot root){
    validateSharedDamageProgram(root.damageProgram(),root.sourceSeat(),root.targetSeat(),root.baseDamage(),root.cardId(),root.effectiveType(),
        DamageNature.of(currentAttackProgram().card(this)),root.pending(),false);
  }
  private void validateSharedDamageProgram(DamageProgramContinuation.Snapshot program,int source,int target,int base,long cardId,
      CardType type,DamageNature nature,AttackContinuation.PendingSnapshot p,boolean movementPaused){
    validateSharedDamageProgram(program,source,target,base,cardId,type,nature,p,movementPaused,false);
  }
  void validateRevealedGiftDamage(DamageProgramContinuation.Snapshot program,int source,int target,AttackContinuation.PendingSnapshot p,boolean movementPaused){
    validateSharedDamageProgram(program,source,target,1,0,null,DamageNature.NORMAL,p,movementPaused,true);
  }
  private void validateSharedDamageProgram(DamageProgramContinuation.Snapshot program,int source,int target,int base,long cardId,
      CardType type,DamageNature nature,AttackContinuation.PendingSnapshot p,boolean movementPaused,boolean initialCardless){
    DamageProgramContinuation.validate(program);boolean cardless=initialCardless;
    for(var parent:program.parents().reversed()){
      validateSharedDamageLeaf(cardId,type,parent.damage(),source,target,base,cardless,nature);
      var reaction=parent.reaction();var frame=parent.damage().damageFrames().getFirst();
      if(reaction.owner()!=frame.targetSeat()||reaction.source()!=source||!parent.effect().equals(continuationDamageEffect(parent.damage(),false).orElse(null)))throw new IllegalArgumentException("invalid damage caller attribution");
      continuationValidateListener(parent.listeners(),parent.damage());
      source=reaction.owner();target=reaction.source();base=1;cardless=true;nature=DamageNature.NORMAL;
    }
    var d=program.damage();
    if(movementPaused&&d.step()==AttackContinuation.AttackStep.DONE&&d.damageFrames().isEmpty()){
      if(d.sourceSeat()!=source||d.targetSeat()!=target||d.cardId()!=cardId||d.effectiveType()!=type||d.baseDamage()!=base||d.cardless()!=cardless)
        throw new IllegalArgumentException("completed damage origin differs");
      return;
    }
    validateSharedDamageLeaf(cardId,type,d,source,target,base,cardless,nature);
    if(program.rescueCost()!=null){new DamageProgramContinuation(program,0,0).validateRescueCost(this);return;}
    if(program.listeners()!=null)continuationValidateListener(program.listeners(),d);
    var f=d.damageFrames().getFirst();var owner=state.players().get(f.targetSeat());var from=state.players().get(d.sourceSeat());
    if(movementPaused)return;
    if(program.reaction()!=null){
      var reaction=program.reaction();if(reaction.owner()!=owner.seat()||reaction.source()!=from.seat())throw new IllegalArgumentException("damage reaction actor mismatch");
      validateDamageReaction(reaction,d);return;
    }
    if(program.distribution()!=null){
      var y=program.distribution();
      if(y.owner()!=owner.seat()||y.amount()!=f.amount()||y.point()<0||y.point()>=y.amount()||!trickYiJiApplies(owner,from,f.amount(),damageCause(d),f.nature(),f.chainDamage())
          ||y.retained().size()>2||new HashSet<>(y.retained()).size()!=y.retained().size()||y.retained().stream().anyMatch(id->!CardZone.hand(y.owner()).equals(state.cards().locate(id)))
          ||p.askerSeat()!=owner.seat()||p.subjectSeat()!=owner.seat()||!p.skillId().equals(program.effect().skill())||!p.allowedTypes().isEmpty())throw new IllegalArgumentException("invalid attack distribution context");
      var own=program.pending();
      if(own==null||own.hidden()||!own.choices().equals(y.retained())||y.step()==YiJiContinuation.Step.INVOKE&&(p.type()!=PromptType.SKILL_INVOKE||!y.retained().isEmpty())
          ||y.step()==YiJiContinuation.Step.GIVE&&(p.type()!=PromptType.YIJI_GIVE||y.retained().isEmpty())||!Set.of(YiJiContinuation.Step.INVOKE,YiJiContinuation.Step.GIVE).contains(y.step()))throw new IllegalArgumentException("invalid attack distribution request");
      return;
    }
    if(program.step()==DamageProgramContinuation.Step.TAKE){
      var own=program.pending();
      if(own==null||p.type()!=PromptType.FANKUI_TAKE||p.askerSeat()!=owner.seat()||p.subjectSeat()!=from.seat()||!p.skillId().equals(program.effect().skill())||!p.allowedTypes().isEmpty()
          ||!trickFankuiApplies(owner,from,f.amount(),damageCause(d),f.nature(),f.chainDamage())||!own.choices().equals(from.equipment().all().stream().map(CardInstance::instanceId).toList())||own.hidden()!=(owner!=from&&from.handCount()>0))throw new IllegalArgumentException("invalid attack feedback choice");
      return;
    }
    if(program.step()!=DamageProgramContinuation.Step.DAMAGE||d.pending()==null)throw new IllegalArgumentException("attack damage program has no input");
    validateDamagePrompt(d);
  }
  private void validateSharedDamageLeaf(long rootCardId,CardType rootType,AttackContinuation.Snapshot d,int source,int target,int base,boolean cardless,DamageNature nature){
    if(d.sourceSeat()!=source||d.targetSeat()!=target||d.cardId()!=rootCardId||d.effectiveType()!=rootType||d.baseDamage()!=base||d.cardless()!=cardless||d.damageFrames().isEmpty())throw new IllegalArgumentException("attack damage call linkage differs");
    int previous=-1;boolean chain=d.damageFrames().getFirst().chainDamage();var seen=new HashSet<Integer>();
    if(!chain&&(d.damageFrames().size()!=1||d.damageFrames().getFirst().targetSeat()!=target)||chain&&(!nature.elemental()||cardless))throw new IllegalArgumentException("invalid attack chain origin");
    for(int i=0;i<d.damageFrames().size();i++){
      var f=d.damageFrames().get(i);int seat=f.targetSeat();
      if(seat<0||seat>=state.players().size()||!seen.add(seat)||f.amount()<1||f.nature()!=nature||f.chainDamage()!=chain||i>0&&f.step()!=AttackContinuation.DamageStep.BEFORE)throw new IllegalArgumentException("invalid attack chain instruction");
      int position=Math.floorMod(seat-state.currentSeat(),state.players().size());if(chain&&position<=previous)throw new IllegalArgumentException("invalid attack chain order");previous=position;
    }
  }
  private void validateAttackProgram(){validateAttackProgram(activeAttack,Set.of(),true);}
  private void validateAttackProgram(AttackContinuation attack,Set<Long> retained,boolean ordinary){
    if(!supportsAttackContinuation() || state.status()!=GameStatus.RUNNING || state.phase()!=Phase.PLAY)throw new IllegalArgumentException("unsupported attack context");
    var frame=attack.snapshot();var p=frame.pending();
    int maximum=state.players().get(frame.sourceSeat()).hero().skillIds().contains("wushuang")?2:1;
    if(frame.dodgesRemaining()<0 || frame.dodgesRemaining()>maximum)throw new IllegalArgumentException("invalid remaining Shan count");
    if(frame.dodgesRemaining()==0 && java.util.Set.of(AttackContinuation.AttackStep.DODGE,AttackContinuation.AttackStep.OWN_BAGUA,AttackContinuation.AttackStep.HUJIA_INVOKE,AttackContinuation.AttackStep.HUJIA_PROVIDER,AttackContinuation.AttackStep.HUJIA_BAGUA,AttackContinuation.AttackStep.HUJIA_SHAN,AttackContinuation.AttackStep.RESPONSE_CHILD).contains(frame.step()))throw new IllegalArgumentException("completed dodge sequence cannot ask for Shan");
    if(frame.dodgeProhibited() && !bus.hasAwaitableInt(RuleId.SHA_DODGE_COUNT,state.players().get(frame.sourceSeat())))throw new IllegalArgumentException("dodge prohibition without awaited rule owner");
    if(frame.dodgeProhibited() && java.util.Set.of(AttackContinuation.AttackStep.DODGE,AttackContinuation.AttackStep.OWN_BAGUA,AttackContinuation.AttackStep.HUJIA_INVOKE,AttackContinuation.AttackStep.HUJIA_PROVIDER,AttackContinuation.AttackStep.HUJIA_BAGUA,AttackContinuation.AttackStep.HUJIA_SHAN,AttackContinuation.AttackStep.RESPONSE_CHILD).contains(frame.step()))throw new IllegalArgumentException("Tieji-prohibited attack cannot ask for Shan");
    if(p==null || frame.sourceSeat()==frame.targetSeat() || ordinary&&frame.sourceSeat()!=state.currentSeat()
        || frame.revision()!=p.stateRevision() || p.id()!=promptSequence)throw new IllegalArgumentException("invalid attack ownership");
    if(frame.useCost()!=null){
      if(!ordinary&&frame.step()!=AttackContinuation.AttackStep.QINGLONG_COST)throw new IllegalArgumentException("ordinary cost inside a committed child");attack.validateUseCost(this);
      var costs=new HashSet<>(frame.useCost().program().cards());
      if(frame.step()==AttackContinuation.AttackStep.QINGLONG_COST){costs.add(frame.cardId());costs.addAll(frame.extraCosts());costs.addAll(frame.parents());}
      if(state.cards().cards(CardZone.PROCESSING).stream().anyMatch(c->!costs.contains(c.instanceId())&&!retained.contains(c.instanceId())))throw new IllegalArgumentException("unowned processing entity during payment");
      return;
    }
    var currentCosts=new ArrayList<Long>();currentCosts.add(frame.cardId());currentCosts.addAll(frame.extraCosts());
    if(frame.ruleQuery()!=null && frame.ruleQuery().judgment()!=null)currentCosts.add(frame.ruleQuery().judgment().effective());
    if(frame.responseProgram()!=null&&frame.responseProgram().judgmentCard()>0)currentCosts.add(frame.responseProgram().judgmentCard());
    if(frame.responseProgram()!=null&&frame.responseProgram().cost()!=null)currentCosts.addAll(frame.responseProgram().cost().cards());
    currentCosts.addAll(DamageProgramContinuation.processingCosts(frame.damageProgram()));
    var processing=state.cards().cards(CardZone.PROCESSING);
    var physical=state.cards().zones().stream().flatMap(z->state.cards().cards(z).stream()).filter(c->c.instanceId()==frame.cardId()).findFirst().orElseThrow();
    var location=state.cards().locate(frame.cardId());
    boolean acquired=location.kind()==CardZone.Kind.HAND && state.players().get(location.seat()).hero().skillIds().contains("jianxiong");
    if(ordinary&&state.shaPlayedThisTurn()<1 || processing.stream().anyMatch(c->!currentCosts.contains(c.instanceId()) && !frame.parents().contains(c.instanceId()) && !retained.contains(c.instanceId()))
        || frame.extraCosts().stream().anyMatch(id->!location.equals(state.cards().locate(id)))
        || frame.parents().stream().anyMatch(id->!CardZone.PROCESSING.equals(state.cards().locate(id)))
        || (!CardZone.PROCESSING.equals(location) && !(acquired && (frame.step()==AttackContinuation.AttackStep.DAMAGE || frame.targetCursor()>0))))
      throw new IllegalArgumentException("attack source is not a committed canonical cost");
    var providers=frame.providers();
    boolean qinglongSupply=Set.of(AttackContinuation.AttackStep.QINGLONG_LORD,AttackContinuation.AttackStep.QINGLONG_PROVIDER,AttackContinuation.AttackStep.QINGLONG_PROVIDER_SHA).contains(frame.step());
    int lordSeat=qinglongSupply?frame.sourceSeat():frame.targetSeat();
    if(!providers.isEmpty() && (state.players().get(lordSeat).identity()!=Identity.LORD
        || !state.players().get(lordSeat).hero().skillIds().contains(qinglongSupply?"jijiang":"hujia")))throw new IllegalArgumentException("providers require a valid lord skill");
    if(qinglongSupply && (!providers.equals(trickLordProviders(state.players().get(frame.sourceSeat()),true)) || providers.isEmpty() || !continuationQinglongAvailable(state.players().get(frame.sourceSeat()),state.players().get(frame.targetSeat()))))throw new IllegalArgumentException("Qinglong supply ring mismatch");
    if(providers.size()>state.players().size() || frame.providerCursor()<0 || frame.providerCursor()>providers.size()
        || new java.util.HashSet<>(providers).size()!=providers.size())throw new IllegalArgumentException("invalid provider program");
    for(int helper:providers)if(helper==lordSeat || helper<0 || helper>=state.players().size()
        || state.players().get(helper).hero().kingdom()!=(qinglongSupply?dev.sgs.core.Kingdom.SHU:dev.sgs.core.Kingdom.WEI))throw new IllegalArgumentException("invalid provider seat");
    if(frame.baseDamage()<1 || frame.baseDamage()>2)throw new IllegalArgumentException("invalid committed wine damage");
    if(frame.redirect()!=null){attack.validateRedirect(this);if(frame.step()==AttackContinuation.AttackStep.TARGET_REDIRECT)return;}
    if(frame.discardCost()!=null){attack.validateDiscardCost(this);return;}
    if(frame.ruleQuery()!=null){validateAttackRuleQuery(frame);return;}
    if(frame.responseProgram()!=null){validateSharedResponse(frame.responseProgram(),true);return;}
    if(frame.damageProgram()!=null){validateAttackDamageProgram(frame);return;}
    var damagedTargets=new java.util.HashSet<Integer>();
    for(int i=0;i<frame.damageFrames().size();i++){
      var d=frame.damageFrames().get(i);
      if(d.amount()<1 || !damagedTargets.add(d.targetSeat()) || (d.chainDamage() && !d.nature().elemental())
          || (!d.chainDamage() && (frame.damageFrames().size()!=1 || d.targetSeat()!=frame.targetSeat()))
          || (i>0 && (d.step()!=AttackContinuation.DamageStep.BEFORE || !d.chainDamage() || !d.rescueRing().isEmpty()
              || d.rescueCursor()!=0 || d.playedThisRound() || d.wasChained())))throw new IllegalArgumentException("invalid damage continuation stack");
    }
    if(p.type()==PromptType.RESCUE_TAO){
      if(frame.step()!=AttackContinuation.AttackStep.DAMAGE || frame.damageFrames().isEmpty())throw new IllegalArgumentException("rescue missing damage parent");
      var damage=frame.damageFrames().getFirst();
      var expectedRing=state.alivePlayersFromCurrent().stream().map(SgsPlayer::seat).toList();
      var victim=state.players().get(damage.targetSeat());
      var expected=p.askerSeat()==damage.targetSeat()?List.of(CardType.TAO,CardType.JIU):List.of(CardType.TAO);
      if(damage.step()!=AttackContinuation.DamageStep.RESCUE || !victim.alive() || victim.hp()>=1
          || !damage.rescueRing().equals(expectedRing) || damage.rescueCursor()>=damage.rescueRing().size()
          || damage.rescueRing().get(damage.rescueCursor())!=p.askerSeat() || p.subjectSeat()!=damage.targetSeat()
          || !p.allowedTypes().equals(expected) || !p.skillId().isEmpty())throw new IllegalArgumentException("rescue cursor/request mismatch");
    }else if(p.type()==PromptType.SKILL_INVOKE && p.skillId().equals("jianxiong")){
      if(frame.step()!=AttackContinuation.AttackStep.DAMAGE || frame.damageFrames().isEmpty())throw new IllegalArgumentException("after-damage hook missing parent");
      var damage=frame.damageFrames().getFirst();var target=state.players().get(damage.targetSeat());
      if(damage.step()!=AttackContinuation.DamageStep.AFTER_CHOICE || p.askerSeat()!=target.seat() || p.subjectSeat()!=frame.sourceSeat()
          || !p.allowedTypes().isEmpty() || !continuationJianxiongApplies(target,state.players().get(frame.sourceSeat()),damage.amount(),damageCause(frame),damage.nature(),damage.chainDamage()))
        throw new IllegalArgumentException("invalid deferred after-damage hook");
    }else{
      if(!frame.damageFrames().isEmpty())throw new IllegalArgumentException("non-damage request has damage children");
      if(p.type()==PromptType.DODGE_SHA){
        if(frame.step()!=AttackContinuation.AttackStep.DODGE || p.askerSeat()!=frame.targetSeat() || p.subjectSeat()!=frame.sourceSeat()
            || !state.players().get(p.askerSeat()).alive() || state.players().get(p.askerSeat()).hp()<1
            || !p.allowedTypes().equals(List.of(CardType.SHAN)) || !p.skillId().isEmpty())throw new IllegalArgumentException("dodge request mismatch");
      }else if(p.type()==PromptType.JIJIANG_SHA){
        if(frame.step()!=AttackContinuation.AttackStep.QINGLONG_PROVIDER_SHA || frame.providerCursor()>=providers.size() || p.askerSeat()!=providers.get(frame.providerCursor()) || p.subjectSeat()!=frame.sourceSeat() || !p.skillId().equals("jijiang") || !p.allowedTypes().equals(List.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA)))throw new IllegalArgumentException("Qinglong supplied request mismatch");
      }else if(p.type()==PromptType.QINGLONG_SHA){
        if(frame.step()!=AttackContinuation.AttackStep.QINGLONG || p.askerSeat()!=frame.sourceSeat() || p.subjectSeat()!=frame.targetSeat()
            || !p.skillId().equals("qinglong") || !p.allowedTypes().equals(List.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA))
            || !continuationQinglongAvailable(state.players().get(frame.sourceSeat()),state.players().get(frame.targetSeat())))throw new IllegalArgumentException("qinglong request mismatch");
      }else if(p.type()==PromptType.WEAPON_COST_FIRST || p.type()==PromptType.WEAPON_COST_SECOND){
        boolean first=p.type()==PromptType.WEAPON_COST_FIRST;
        if(frame.step()!=(first?AttackContinuation.AttackStep.GUANSHI_FIRST:AttackContinuation.AttackStep.GUANSHI_SECOND)
            || p.askerSeat()!=frame.sourceSeat() || p.subjectSeat()!=frame.targetSeat() || !p.skillId().equals("guanshi") || !p.allowedTypes().isEmpty()
            || !continuationGuanshiAvailable(state.players().get(frame.sourceSeat()))
            || (!first && continuationGuanshiCosts(state.players().get(frame.sourceSeat())).stream().noneMatch(c->c.instanceId()==frame.weaponCost())))throw new IllegalArgumentException("guanshi cost selection mismatch");
      }else if(p.type()==PromptType.WEAPON_DISCARD){
        if(frame.step()!=AttackContinuation.AttackStep.CIXIONG_CHOOSE || p.askerSeat()!=frame.targetSeat() || p.subjectSeat()!=frame.sourceSeat()
            || !p.skillId().equals("cixiong") || !p.allowedTypes().isEmpty() || state.players().get(frame.targetSeat()).handCount()<1
            || !continuationCixiongAvailable(state.players().get(frame.sourceSeat()),state.players().get(frame.targetSeat())))throw new IllegalArgumentException("cixiong target choice mismatch");
      }else if(p.type()==PromptType.CARD_ZONE_SELECT && p.skillId().equals("hanbing")){
        if(!java.util.Set.of(AttackContinuation.AttackStep.HANBING_FIRST,AttackContinuation.AttackStep.HANBING_SECOND).contains(frame.step())
            || p.askerSeat()!=frame.sourceSeat() || p.subjectSeat()!=frame.targetSeat() || !p.allowedTypes().isEmpty()
            || !continuationHanbingAvailable(state.players().get(frame.sourceSeat()),state.players().get(frame.targetSeat())))throw new IllegalArgumentException("hanbing selection mismatch");
      }else if(p.type()==PromptType.CARD_ZONE_SELECT){
        if(frame.step()!=AttackContinuation.AttackStep.QILIN_CHOOSE || p.askerSeat()!=frame.sourceSeat() || p.subjectSeat()!=frame.targetSeat()
            || !p.skillId().equals("qilin_gong") || !p.allowedTypes().isEmpty()
            || continuationQilinHorses(state.players().get(frame.sourceSeat()),state.players().get(frame.targetSeat())).size()!=2)
          throw new IllegalArgumentException("qilin selection mismatch");
      }else if(p.type()==PromptType.HUJIA_SHAN){
        if(frame.step()!=AttackContinuation.AttackStep.HUJIA_SHAN || frame.providerCursor()>=providers.size()
            || p.askerSeat()!=providers.get(frame.providerCursor()) || p.subjectSeat()!=frame.targetSeat()
            || !p.skillId().equals("hujia") || !p.allowedTypes().equals(List.of(CardType.SHAN)))throw new IllegalArgumentException("provider response mismatch");
      }else if(p.type()==PromptType.SKILL_INVOKE){
        var source=state.players().get(frame.sourceSeat());
        switch(p.skillId()){
          case "tieji"->{
            if(frame.step()!=AttackContinuation.AttackStep.TARGET_RULES || p.askerSeat()!=frame.sourceSeat() || p.subjectSeat()!=frame.targetSeat() || !p.allowedTypes().isEmpty() || frame.dodgeProhibited() || !state.players().get(frame.targetSeat()).alive() || !continuationTiejiAvailable(source))throw new IllegalArgumentException("Tieji request mismatch");
          }
          case "zhuque"->{
            if(frame.step()!=AttackContinuation.AttackStep.WEAPON || p.askerSeat()!=frame.sourceSeat() || p.subjectSeat()!=frame.targetSeat()
                || !p.allowedTypes().isEmpty() || frame.effectiveType()!=CardType.SHA || source.equipment().weapon()==null
                || source.equipment().weapon().type()!=CardType.ZHUQUE || physical.type()!=CardType.SHA)throw new IllegalArgumentException("weapon request mismatch");
          }
          case "jijiang"->{
            if(frame.step()!=AttackContinuation.AttackStep.QINGLONG_LORD || p.askerSeat()!=frame.sourceSeat() || p.subjectSeat()!=frame.sourceSeat() || frame.providerCursor()!=0 || !p.allowedTypes().isEmpty())throw new IllegalArgumentException("Qinglong lord invocation mismatch");
          }
          case "guanshi"->{
            if(frame.step()!=AttackContinuation.AttackStep.GUANSHI_INVOKE || p.askerSeat()!=frame.sourceSeat() || p.subjectSeat()!=frame.targetSeat() || !p.allowedTypes().isEmpty()
                || !continuationGuanshiAvailable(source))throw new IllegalArgumentException("guanshi invocation mismatch");
          }
          case "hanbing"->{
            if(frame.step()!=AttackContinuation.AttackStep.HANBING_INVOKE || p.askerSeat()!=frame.sourceSeat() || p.subjectSeat()!=frame.targetSeat() || !p.allowedTypes().isEmpty()
                || !continuationHanbingAvailable(source,state.players().get(frame.targetSeat())))throw new IllegalArgumentException("hanbing invocation mismatch");
          }
          case "cixiong"->{
            if(frame.step()!=AttackContinuation.AttackStep.CIXIONG_INVOKE || p.askerSeat()!=frame.sourceSeat() || p.subjectSeat()!=frame.targetSeat()
                || !p.allowedTypes().isEmpty() || !continuationCixiongAvailable(source,state.players().get(frame.targetSeat())))throw new IllegalArgumentException("cixiong invocation mismatch");
          }
          case "qilin_gong"->{
            if(frame.step()!=AttackContinuation.AttackStep.QILIN_INVOKE || p.askerSeat()!=frame.sourceSeat() || p.subjectSeat()!=frame.targetSeat()
                || !p.allowedTypes().isEmpty() || continuationQilinHorses(source,state.players().get(frame.targetSeat())).isEmpty())
              throw new IllegalArgumentException("qilin invocation mismatch");
          }
          case "hujia"->{
            if(frame.step()!=AttackContinuation.AttackStep.HUJIA_INVOKE || p.askerSeat()!=frame.targetSeat() || p.subjectSeat()!=frame.targetSeat()
                || providers.isEmpty() || frame.providerCursor()!=0 || !providers.equals(continuationHujiaProviders(state.players().get(frame.targetSeat())))
                || !p.allowedTypes().isEmpty())throw new IllegalArgumentException("lord request mismatch");
          }
          case "bagua"->{
            if(frame.step()==AttackContinuation.AttackStep.OWN_BAGUA){
              if(p.askerSeat()!=frame.targetSeat() || p.subjectSeat()!=frame.sourceSeat() || !p.allowedTypes().isEmpty()
                  || !continuationOwnBaguaAvailable(source,state.players().get(frame.targetSeat())))throw new IllegalArgumentException("own armor request mismatch");
              break;
            }
            if(frame.step()!=AttackContinuation.AttackStep.HUJIA_BAGUA || frame.providerCursor()>=providers.size()
                || p.askerSeat()!=providers.get(frame.providerCursor()) || p.subjectSeat()!=frame.targetSeat() || !p.allowedTypes().isEmpty()
                || state.players().get(p.askerSeat()).equipment().armor()==null
                || state.players().get(p.askerSeat()).equipment().armor().type()!=CardType.BAGUA)throw new IllegalArgumentException("provider armor request mismatch");
          }
          default->throw new IllegalArgumentException("unsupported invocation");
        }
      }else throw new IllegalArgumentException("unsupported request type");
    }
  }

  /** Whether the attacker's Blue Steel Sword ignores the target's armor. */
  private static boolean ignoresArmor(SgsPlayer source) {
    CardInstance weapon = source.equipment().weapon();
    return weapon != null && weapon.type() == CardType.QINGGANG;
  }

  /**
   * Settles a basic attack (杀 / 火杀 / 雷杀). All three share the dodge window and the per-turn
   * usage limit. An element alone never negates armor; armor-negating effects are checked separately.
   */
  void resolveAttack(SgsPlayer source, SgsPlayer target, int damage, GameCard shaCard) {
    checkpointOperations++;
    try {
    var redirect=SkillContext.attackTarget(state,source,target,shaCard);
    bus.trigger(Timing.SHA_TARGET_BEFORE,redirect);target=redirect.target();
    bus.trigger(Timing.CARD_TARGET_CONFIRMED,SkillContext.cardTarget(state,source,target,shaCard).at(Timing.CARD_TARGET_CONFIRMED));
    if(!skills.mythic().xiangle(source,target))return;
    int dodgeCount=Math.max(0,bus.queryInt(RuleId.SHA_DODGE_COUNT,SkillContext.cardTarget(state,source,target,shaCard),1));
    resolveCixiong(source,target);
    var fan=source.equipment().weapon();
    if(shaCard.type()==CardType.SHA && !shaCard.isVirtual() && fan!=null && fan.type()==CardType.ZHUQUE
        && requestSkillInvoke(source,"zhuque",target)){
      shaCard=new dev.sgs.core.card.VirtualCard(CardType.HUOSHA,shaCard.sourceCards());
      state.publishSkillActivation(source.seat(),"zhuque");
      state.log("seat {} zhuque converts normal sha to fire",source.seat());
    }
    CardType type = shaCard.type();
    boolean isFire = type == CardType.HUOSHA;
    boolean isLightning = type == CardType.LEISHA;
    if(bus.queryInt(RuleId.SHA_EFFECT_ALLOWED,SkillContext.cardTarget(state,source,target,shaCard),1)==0)return;
    boolean armorOn = !ignoresArmor(source);
    if(!armorOn && target.equipment().armor()!=null)state.publishSkillActivation(source.seat(),"qinggang");
    if(armorOn && type==CardType.SHA && target.equipment().armor()!=null
        && target.equipment().armor().type()==CardType.TENGJIA){
      state.log("seat {} tengjia nullifies normal sha",target.seat());return;
    }

    // Renwang checks color, including elemental and transformed Sha.
    if (armorOn
        && target.equipment().armor() != null
        && target.equipment().armor().type() == CardType.RENWANG
        && shaCard.isBlack()) {
      state.log(
          "seat {}'s renwang shield blocked the black sha from seat {}",
          target.seat(),
          source.seat());
      return;
    }

    boolean dodged=dodgeCount>0;
    for(int remaining=dodgeCount;remaining>0;remaining--){
      if(provideDodge(target,source,armorOn,type))continue;
      dodged=false;break;
    }
    if(dodged && !resolveGuanshi(source,target)){bus.trigger(Timing.SHA_DODGED,SkillContext.cardTarget(state,source,target,shaCard).at(Timing.SHA_DODGED));resolveQinglong(source,target);return;}
    if(resolveHanbing(source,target))return;
    resolveQilin(source,target);
    String element = isFire ? "火" : isLightning ? "雷" : "";
    state.log(
        "seat {} was hit by {}sha from seat {} for {}", target.seat(), element, source.seat(), damage);
    applyDamage(target, damage, source, shaCard);

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  /** One requested Shan: optional providers/armor, then a real hand response. */
  private boolean provideDodge(SgsPlayer target,SgsPlayer source,boolean armorOn,CardType attackType){
    if(skills.provideHujia(target))return true;
    boolean bazhen=target.equipment().armor()==null&&skills.mythic().has(target,"bazhen");
    if(armorOn && (bazhen || target.equipment().armor()!=null && target.equipment().armor().type()==CardType.BAGUA)
        && requestSkillInvoke(target,bazhen?"bazhen":"bagua",source)){
      var judged=judgeFor(target,target.equipment().armor());if(judged!=null && (skills.mythic().suit(target,judged)==dev.sgs.core.card.Suit.HEART||skills.mythic().suit(target,judged)==dev.sgs.core.card.Suit.DIAMOND)){
        bus.trigger(Timing.CARD_RESPONSE_COMMITTED,SkillContext.cardUse(state,target,new VirtualCard(CardType.SHAN,List.of())).at(Timing.CARD_RESPONSE_COMMITTED));
        if(attackType!=null)state.log("seat {}'s bagua armor judged red and dodged the sha",target.seat());return true;
      }
    }
    var prompt=new PromptRequest(nextPromptId(),PromptType.DODGE_SHA,target,source,List.of(CardType.SHAN),clock.nowMillis()+responseTimeoutMillis);
    var response=ask(prompt);if(response.isEmpty())return false;
    spendDecision(target,response.get());
    if(attackType!=null)state.log("seat {} dodged the {} from seat {}",target.seat(),attackType.id(),source.seat());return true;
  }

  /** Removes the physical sources of a response decision from the hand and discards them. */
  private void spendDecision(SgsPlayer player, PlayDecision decision) {
    beginOwnedUse(player,decision.card(),RESPONSE);
    state.cards().finish(decision.card(),player.seat());
  }

  /** Log label for a settlement card: the physical id, or "type(virtual)" for pure virtuals. */
  private static String describe(GameCard card) {
    return card.isPhysical() ? card.sourceCards().get(0).cardId() : card.type().id() + "(virtual)";
  }

  /** Reveals the top card into processing; judgment settlement owns its cleanup. */
  private CardInstance revealJudgmentCard() {
    if (state.deck().drawPileCount() == 0 && state.deck().discardCount() == 0) {
      return null;
    }
    CardInstance judged=state.cards().drawTo(CardZone.PROCESSING,1,JUDGMENT_REVEAL,state.currentSeat(),true).getFirst();
    state.log("judgment card: {}", judged.cardId());
    return judged;
  }

  /** All judgment sources use the same target-aware modification pipeline and public observer. */
  private CardInstance judgeFor(SgsPlayer target,CardInstance cause){return judgeFor(target,cause,false);}
  @Override public CardInstance judgeForSkill(SgsPlayer target,String skillId,boolean obtainBlack){
    if(!state.players().contains(target) || !target.alive() || skillId==null || skillId.isBlank())throw new IllegalArgumentException("invalid skill judgment");
    return judgeFor(target,null,obtainBlack);
  }
  private CardInstance judgeFor(SgsPlayer target,CardInstance cause,boolean obtainBlack){
    return judgeFor(target,cause,obtainBlack,null,null);
  }
  @Override public CardInstance judgeForSkill(SgsPlayer target,String id,java.util.function.Predicate<GameCard> keep,CardZone destination){
    if(!state.players().contains(target)||!target.alive()||keep==null||destination==null||destination.seat()!=target.seat())throw new IllegalArgumentException("invalid skill judgment destination");
    return judgeFor(target,null,false,keep,destination);
  }
  private CardInstance judgeFor(SgsPlayer target,CardInstance cause,boolean obtainBlack,java.util.function.Predicate<GameCard> keep,CardZone destination){
    var revealed=revealJudgmentCard();
    if(revealed!=null)eventListeners().forEach(l->l.onJudgmentRevealed(target,revealed));
    var context=SkillContext.judge(state,target,cause,revealed);
    try{
      bus.trigger(Timing.JUDGE_CARD_REVEALED,context);
      context=context.at(Timing.JUDGE_CARD_MODIFY);
      bus.trigger(Timing.JUDGE_CARD_MODIFY,context,OrderStrategy.MULTI_ENTITY_ORDER);
      var effective=context.judgeCard();
      if(effective!=null)eventListeners().forEach(l->l.onJudgmentFinalized(target,effective));
      context=context.at(Timing.JUDGE_CARD_EFFECT);bus.trigger(Timing.JUDGE_CARD_EFFECT,context);
      if(obtainBlack && context.judgeCard()!=null && context.judgeCard().isBlack() && target.alive()
          && CardZone.PROCESSING.equals(state.cards().locate(context.judgeCard().instanceId())))
        state.cards().obtainProcessing(target,context.judgeCard());
      if(keep!=null && context.judgeCard()!=null && target.alive() && keep.test(context.judgeCard())
          && CardZone.PROCESSING.equals(state.cards().locate(context.judgeCard().instanceId())))
        state.cards().move(List.of(context.judgeCard()),CardZone.PROCESSING,destination,CardMove.Reason.EFFECT_GAIN,target.seat());
      return context.judgeCard();
    }finally{
      if(revealed!=null)state.cards().finish(revealed,target.seat());
      if(context.judgeCard()!=null)state.cards().finish(context.judgeCard(),target.seat());
    }
  }

  private ActionResult playTao(SgsPlayer player, GameCard tao) {
    var verdict=validateTao(player);if(!verdict.accepted())return verdict;
    beginOwnedUse(player,tao,USE);
    try{heal(player,1);}finally{state.cards().finish(tao,player.seat());}
    state.log("seat {} recovered 1 hp from tao", player.seat());
    return ActionResult.ok();
  }

  private ActionResult playJiu(SgsPlayer player, GameCard jiu) {
    var verdict=validateJiu();if(!verdict.accepted())return verdict;
    beginOwnedUse(player,jiu,USE);
    try{state.registerJiuPlayed();}finally{state.cards().finish(jiu,player.seat());}
    state.log("seat {} readied jiu for the next sha", player.seat());
    return ActionResult.ok();
  }

  private ActionResult validateTao(SgsPlayer player){return player.hp()>=player.maxHp()?ActionResult.rejected(RejectReason.FULL_HP):ActionResult.ok();}
  private ActionResult validateJiu(){return state.jiuPlayedThisTurn()?ActionResult.rejected(RejectReason.JIU_LIMIT):ActionResult.ok();}

  /**
   * Damage settlement on the explicit resolution stack: cause-before &rarr; take-before &rarr; hp
   * reduction &rarr; (inserted dying chain first if lethal) &rarr; cause-after &rarr; take-after.
   * The continuation is pushed before the dying task so LIFO order inserts the dying chain in the
   * middle of damage settlement.
   *
   * <p>{@code causeCard} is the settlement card that deals the damage (Sha, Duel, ...) and may be
   * {@code null} for card-less skill damage. Lightning carries its delayed card and a null player
   * source; direct HP loss uses {@link #loseHp} and never enters damage timings.
   * Damage listeners see it via {@code SkillContext.card()}; an empty {@code sourceCards()} means
   * nothing physical can be obtained (Jianxiong).
   */
  void applyDamage(SgsPlayer target, int amount, SgsPlayer source, GameCard causeCard) {
    checkpointOperations++;
    try {
    applyDamage(target,amount,source,causeCard,DamageNature.of(causeCard),false);

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  void applyDamage(SgsPlayer target,int amount,SgsPlayer source,GameCard causeCard,DamageNature nature,boolean chainDamage) {
    applyDamage(target,amount,source,causeCard,nature,chainDamage,false);
  }
  private void applyDamage(SgsPlayer target,int amount,SgsPlayer source,GameCard causeCard,DamageNature nature,boolean chainDamage,boolean transfer) {
    checkpointOperations++;
    try {
    int damageDistance=source!=null && source.alive() && target.alive()?state.distance(source,target):Integer.MAX_VALUE;
    var applied=continuationApplyDamage(target,amount,source,causeCard,nature,chainDamage,transfer);
    int finalAmount=applied.amount();if(finalAmount==0)return;
    var finalNature=applied.nature();boolean wasChained=applied.wasChained();

    stack.push(new AfterDamageTask(seatOf(target),seatOf(source),finalAmount,causeCard,finalNature,chainDamage,wasChained,damageDistance,transfer));
    if (target.hp() < 1) {
      stack.push(new DyingTask(seatOf(target), seatOf(source)));
    }
    stack.drain(this);

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  record AppliedDamage(int amount,DamageNature nature,boolean wasChained){}
  AppliedDamage continuationApplyDamage(SgsPlayer target,int amount,SgsPlayer source,GameCard causeCard,DamageNature nature,boolean chainDamage){
    return continuationApplyDamage(target,amount,source,causeCard,nature,chainDamage,false);
  }
  private AppliedDamage continuationApplyDamage(SgsPlayer target,int amount,SgsPlayer source,GameCard causeCard,DamageNature nature,boolean chainDamage,boolean transfer){
    if(amount<=0 || !target.alive() || state.status()!=GameStatus.RUNNING)return new AppliedDamage(0,nature,false);
    SkillContext causeBefore =
        SkillContext.damage(state,Timing.DAMAGE_CAUSE_BEFORE,source,target,amount,causeCard,nature,chainDamage);
    if (source != null && !transfer) {
      bus.trigger(Timing.DAMAGE_CAUSE_BEFORE, causeBefore);
    }
    SkillContext takeBefore = causeBefore.at(Timing.DAMAGE_TAKE_BEFORE);
    bus.trigger(Timing.DAMAGE_TAKE_BEFORE, takeBefore);
    int finalAmount = Math.max(takeBefore.damage(), 0);
    if(finalAmount==0)return new AppliedDamage(0,nature,false);
    DamageNature finalNature=takeBefore.nature();
    boolean wasChained=state.chained(target.seat());

    target.setHp(target.hp() - finalAmount);
    eventListeners().forEach(l -> l.onDamageApplied(target,source,finalAmount,finalNature,chainDamage));
    state.log("seat {} took {} damage (hp {}) nature={} chain={} source={}",target.seat(),finalAmount,target.hp(),finalNature,chainDamage,seatOf(source));

    return new AppliedDamage(finalAmount,finalNature,wasChained);
  }

  /** Damage without a card source; see the card-carrying overload for the full contract. */
  void applyDamage(SgsPlayer target, int amount, SgsPlayer source) {
    checkpointOperations++;
    try {
    applyDamage(target, amount, source, null);

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  private void fireAfterDamage(SgsPlayer target,SgsPlayer source,int amount,GameCard causeCard,DamageNature nature,boolean chainDamage) {
    fireAfterDamage(target,source,amount,causeCard,nature,chainDamage,-1);
  }
  private void fireAfterDamage(SgsPlayer target,SgsPlayer source,int amount,GameCard causeCard,DamageNature nature,boolean chainDamage,int damageDistance) {
    fireAfterDamage(target,source,amount,causeCard,nature,chainDamage,damageDistance,false);
  }
  private void fireAfterDamage(SgsPlayer target,SgsPlayer source,int amount,GameCard causeCard,DamageNature nature,boolean chainDamage,int damageDistance,boolean transfer) {
    if (state.status() != GameStatus.RUNNING) {
      return;
    }
    if (source != null && source.alive()) {
      bus.trigger(
          Timing.DAMAGE_CAUSE_AFTER,
          SkillContext.damage(state,Timing.DAMAGE_CAUSE_AFTER,source,target,amount,causeCard,nature,chainDamage).withDamageDistance(damageDistance).withTransferredDamage(transfer));
    }
    bus.trigger(
        Timing.DAMAGE_TAKE_AFTER,
        SkillContext.damage(state,Timing.DAMAGE_TAKE_AFTER,source,target,amount,causeCard,nature,chainDamage).withTransferredDamage(transfer));
  }

  @Override public void loseHp(SgsPlayer target,int amount) {
    checkpointOperations++;
    try {
    if(!continuationApplyHpLoss(target,amount))return;
    stack.push(new HpLossAfterTask(target.seat(),amount));
    if(target.hp()<1)stack.push(new DyingTask(target.seat(),-1));
    stack.drain(this);

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  /** Asks the alive ring (current turn player first) until saved or confirmed dead. */
  private void runDying(SgsPlayer victim, SgsPlayer source) {
    continuationStartDying(victim,source);

    while (victim.hp() < 1 && victim.alive() && !skills.mythic().survivesBuqu(victim)) {
      var ring=state.alivePlayersFromCurrent();
      var round=RescueRound.start(ring.stream().map(SgsPlayer::seat).toList());
      for (SgsPlayer asker : ring) {
        if(!skills.mythic().rescueAllowed(asker,victim)){round=round.completed(false);continue;}
        if (victim.hp() >= 1) {
          break;
        }
        List<CardType> allowed = new ArrayList<>(List.of(CardType.TAO));
        if (asker == victim) {
          allowed.add(CardType.JIU);
        }
        PromptRequest prompt =
            new PromptRequest(
                nextPromptId(),
                PromptType.RESCUE_TAO,
                asker,
                victim,
                allowed,
                clock.nowMillis() + responseTimeoutMillis);
        Optional<PlayDecision> response = ask(prompt);
        if (response.isEmpty()) {
          round=round.completed(false);
          continue;
        }
        PlayDecision decision = response.get();
        continuationRescue(asker,victim,decision);
        round=round.completed(true);
      }
      if (!round.played()) {
        break;
      }
    }

    continuationCompleteDying(victim,source);
  }
  boolean continuationApplyHpLoss(SgsPlayer target,int amount){
    if(!state.players().contains(target))throw new IllegalArgumentException("unknown target");
    if(amount<=0||!target.alive()||state.status()!=GameStatus.RUNNING)return false;
    target.setHp(target.hp()-amount);
    state.log("seat {} lost {} hp (hp {})",target.seat(),amount,target.hp());return true;
  }
  void continuationHpLossAfter(SgsPlayer target,int amount){
    eventListeners().forEach(l->l.onHpLost(target,amount));
    if(state.status()==GameStatus.RUNNING)bus.trigger(Timing.HP_LOST_AFTER,SkillContext.hpLoss(state,target,amount));
  }
  void continuationStartDying(SgsPlayer victim,SgsPlayer source){
    SkillContext dying = SkillContext.dying(state, victim, source);
    bus.trigger(Timing.DYING, dying);
    bus.trigger(Timing.ASK_RESCUE, dying.at(Timing.ASK_RESCUE), OrderStrategy.MULTI_ENTITY_ORDER);
  }
  void continuationRescue(SgsPlayer asker,SgsPlayer victim,PlayDecision decision){
        beginOwnedUse(asker, decision.card(), USE);
        continuationFinishRescue(asker,victim,decision);
  }
  void continuationFinishRescue(SgsPlayer asker,SgsPlayer victim,PlayDecision decision){
        try {
          int recovery=Math.max(0,bus.queryInt(RuleId.RESCUE_RECOVERY,
              SkillContext.rescueRecovery(state,asker,victim,decision.card()),1));
          victim.setHp(Math.min(victim.maxHp(),victim.hp()+recovery));
        } finally {
          state.cards().finish(decision.card(), asker.seat());
        }
    state.log("seat {} saved seat {} with {}",asker.seat(),victim.seat(),decision.effectiveType().id());
  }
  void continuationCompleteDying(SgsPlayer victim,SgsPlayer source){
    boolean rescued = victim.hp() >= 1 || skills.mythic().survivesBuqu(victim);
    eventListeners().forEach(l -> l.onDyingResolved(victim, rescued));
    if (rescued) {
      state.log("seat {} survived near-death with hp {}", victim.seat(), victim.hp());
      return;
    }
    kill(victim, source);
  }

  private void kill(SgsPlayer victim, SgsPlayer source) {
    bus.trigger(Timing.DEATH_BEFORE_CLEANUP,SkillContext.dying(state,victim,source).at(Timing.DEATH_BEFORE_CLEANUP));
    victim.setAlive(false);
    var changes=new ArrayList<CardMovement.Transfer>();
    for(var zone:List.of(CardZone.hand(victim.seat()),CardZone.equipment(victim.seat()),CardZone.judgment(victim.seat())))
      changes.add(new CardMovement.Transfer(state.cards().cards(zone),zone,CardZone.DISCARD,DEATH,victim.seat(),true));
    state.cards().moveBatch(changes);
    var special=state.cards().cards(CardZone.special(victim.seat()));if(!special.isEmpty())state.cards().move(special,CardZone.special(victim.seat()),CardZone.DISCARD,DEATH,victim.seat());
    victim.setIdentityPublic(true);
    state.setChained(victim.seat(),false);
    if(state.teamMode())state.log("seat {} died on team {}",victim.seat(),state.teamOf(victim.seat()));
    else state.log("seat {} died as {}", victim.seat(), victim.identity().id());
    bus.trigger(Timing.DEATH, SkillContext.dying(state, victim, source).at(Timing.DEATH));
    eventListeners().forEach(l -> l.onPlayerDied(victim, source));
    applyDeathRewards(victim, source);

    if(activeSkill!=null){activeSkill.deferOutcome();return;}
    continuationFinishOutcome();
  }

  void continuationFinishOutcome(){
    GameOutcome decided = VictoryEvaluator.evaluate(state);
    if (decided != GameOutcome.ONGOING) {
      outcome = decided;
      state.finish();
      report = BattleReport.of(state, decided);
      state.log("game finished: {}", decided);
    }
  }

  private static int seatOf(SgsPlayer player) {
    return player == null ? -1 : player.seat();
  }

  private SgsPlayer playerBySeat(int seat) {
    return seat < 0 ? null : state.players().get(seat);
  }

  /** Continuation task: fires the two after-damage timings once the inserted chain finishes. */
  private static final class AfterDamageTask extends ResolutionTask<SgsGameEngine> {
    private final int targetSeat;
    private final int sourceSeat;
    private final int amount;
    private final GameCard causeCard;
    private final DamageNature nature;
    private final boolean chainDamage;
    private final boolean wasChained;
    private final int damageDistance;
    private final boolean transfer;

    AfterDamageTask(int targetSeat,int sourceSeat,int amount,GameCard causeCard,DamageNature nature,boolean chainDamage,boolean wasChained,int damageDistance) {
      this(targetSeat,sourceSeat,amount,causeCard,nature,chainDamage,wasChained,damageDistance,false);
    }
    AfterDamageTask(int targetSeat,int sourceSeat,int amount,GameCard causeCard,DamageNature nature,boolean chainDamage,boolean wasChained,int damageDistance,boolean transfer) {
      super(Timing.DAMAGE_TAKE_AFTER);
      this.targetSeat = targetSeat;
      this.sourceSeat = sourceSeat;
      this.amount = amount;
      this.causeCard = causeCard;
      this.nature=nature;this.chainDamage=chainDamage;this.wasChained=wasChained;this.damageDistance=damageDistance;this.transfer=transfer;
    }

    @Override
    public void run(SgsGameEngine engine) {
      engine.fireAfterDamage(
          engine.playerBySeat(targetSeat),engine.playerBySeat(sourceSeat),amount,causeCard,nature,chainDamage,damageDistance,transfer);
      if(!nature.elemental() || !wasChained || engine.state.status()!=GameStatus.RUNNING)return;
      engine.state.setChained(targetSeat,false);
      if(!chainDamage) {
        var ring=engine.state.alivePlayersFromCurrent();
        for(int i=ring.size()-1;i>=0;i--)engine.stack.push(new ChainDamageTask(ring.get(i).seat(),sourceSeat,amount,causeCard,nature));
      }
    }

    @Override
    public java.util.Map<String, Object> snapshot() {
      return java.util.Map.of("target",targetSeat,"source",sourceSeat,"amount",amount,"nature",nature.name(),"chain",chainDamage,"wasChained",wasChained);
    }
  }

  /** Re-check the next recipient after all earlier rescue/after-damage effects have completed. */
  private static final class ChainDamageTask extends ResolutionTask<SgsGameEngine> {
    private final int targetSeat,sourceSeat,amount;
    private final GameCard card;
    private final DamageNature nature;
    ChainDamageTask(int targetSeat,int sourceSeat,int amount,GameCard card,DamageNature nature) {
      super(Timing.DAMAGE_TAKE_BEFORE);this.targetSeat=targetSeat;this.sourceSeat=sourceSeat;
      this.amount=amount;this.card=card;this.nature=nature;
    }
    @Override public void run(SgsGameEngine engine) {
      var target=engine.playerBySeat(targetSeat);
      if(engine.state.status()==GameStatus.RUNNING && target.alive() && engine.state.chained(targetSeat))
        engine.applyDamage(target,amount,engine.playerBySeat(sourceSeat),card,nature,true);
    }
    @Override public java.util.Map<String,Object> snapshot() {
      return java.util.Map.of("kind","chain","target",targetSeat,"source",sourceSeat,"amount",amount,"nature",nature.name());
    }
  }

  private static final class HpLossAfterTask extends ResolutionTask<SgsGameEngine> {
    private final int targetSeat,amount;
    HpLossAfterTask(int targetSeat,int amount){super(Timing.HP_LOST_AFTER);this.targetSeat=targetSeat;this.amount=amount;}
    @Override public void run(SgsGameEngine engine) {
      var target=engine.playerBySeat(targetSeat);
      engine.continuationHpLossAfter(target,amount);
    }
    @Override public java.util.Map<String,Object> snapshot(){return java.util.Map.of("kind","hp_loss_after","target",targetSeat,"amount",amount);}
  }

  /** Inserted task: runs the rescue ring and kills the victim when nobody saves them. */
  private static final class DyingTask extends ResolutionTask<SgsGameEngine> {
    private final int victimSeat;
    private final int sourceSeat;

    DyingTask(int victimSeat, int sourceSeat) {
      super(Timing.DYING);
      this.victimSeat = victimSeat;
      this.sourceSeat = sourceSeat;
    }

    @Override
    public void run(SgsGameEngine engine) {
      engine.runDying(engine.playerBySeat(victimSeat), engine.playerBySeat(sourceSeat));
    }

    @Override
    public java.util.Map<String, Object> snapshot() {
      return java.util.Map.of("victim", victimSeat, "source", sourceSeat);
    }
  }

  private void applyDeathRewards(SgsPlayer victim, SgsPlayer source) {
    if(state.teamMode()){
      if(VictoryEvaluator.evaluate(state)==GameOutcome.ONGOING)
        for(var ally:state.alivePlayers())if(state.areTeammates(ally,victim)){
          drawCards(ally,1);state.log("2v2 teammate compensation seat {}",ally.seat());
        }
      return;
    }
    if (victim.identity() == Identity.REBEL && source != null && source.alive()) {
      List<CardInstance> reward=drawCards(source,3);
      state.log("seat {} drew {} cards for killing a rebel", source.seat(), reward.size());
    }
    if (victim.identity() == Identity.LOYALIST && source == state.lord()) {
      var changes=new ArrayList<CardMovement.Transfer>();
      for(var zone:List.of(CardZone.hand(source.seat()),CardZone.equipment(source.seat())))
        changes.add(new CardMovement.Transfer(state.cards().cards(zone),zone,CardZone.DISCARD,DISCARD,source.seat(),true));
      state.cards().moveBatch(changes);
      if(activeSkill!=null)activeSkill.deferLordPenaltyLog();else if(activeTrick!=null)activeTrick.deferLordPenaltyLog();else state.log("lord accidentally killed a loyalist and discarded the whole hand");
    }
  }

  /** Prefix timings/judgments with interactive bodies stay on the legacy path until migrated. */
  public boolean supportsTurnStartContinuation(){
    return !skills.mythic().active()&&unsupportedCheckpointHero()==null&&bus.supportsRuleProgram(RuleId.DRAW_COUNT)&&bus.supportsMovementProgram()
        &&state.currentPlayer().delayZone().judgmentOrder().isEmpty()&&!state.skipDrawPhase()&&!state.skipPlayPhase()
        &&bus.supportsPreparationProgram()
        &&List.of(Timing.TURN_START,Timing.PHASE_JUDGMENT_BEFORE,Timing.PHASE_JUDGMENT_START,
            Timing.PHASE_DRAW_BEFORE,Timing.PHASE_DRAW_START,Timing.PHASE_DRAW_END,Timing.PHASE_PLAY_BEFORE,Timing.PHASE_PLAY_START)
            .stream().noneMatch(bus::hasPhaseListeners);
  }
  public boolean hasTurnStartContinuation(){return activeTurnStart!=null;}
  public Optional<PromptRequest> pendingTurnStartPrompt(){return activeTurnStart==null||activeTurnStart.pending()==null?Optional.empty():Optional.of(activeTurnStart.request(this));}
  public long turnStartDecisionRevision(){return activeTurnStart==null||activeTurnStart.pending()==null?0:activeTurnStart.pending().revision();}
  public ActionResult beginResumableOpenTurn(SgsPlayer player){
    assertContinuationIngress();
    if(hasActiveContinuation())return ActionResult.rejected(RejectReason.WRONG_PHASE);
    if(state.status()!=GameStatus.RUNNING)return ActionResult.rejected(RejectReason.GAME_NOT_RUNNING);
    if(player!=state.currentPlayer())return ActionResult.rejected(RejectReason.NOT_YOUR_TURN);
    if(state.phase()!=Phase.PREPARE)return ActionResult.rejected(RejectReason.WRONG_PHASE);
    if(!supportsTurnStartContinuation())return ActionResult.rejected(RejectReason.NOT_YET_IMPLEMENTED);
    checkpointOperations++;
    try{
      skills.mythic().initializeGame();fireTiming(Timing.TURN_START,player);
      activeTurnStart=new TurnStartContinuation(this);advanceTurnStart();return ActionResult.ok();
    }catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}finally{checkpointOperations--;}
  }
  private void advanceTurnStart(){if(activeTurnStart!=null){activeTurnStart.advance(this);if(activeTurnStart.done())activeTurnStart=null;}}
  public void pollResumableTurnStart(){assertContinuationIngress();checkpointOperations++;
    try{advanceTurnStart();}catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}finally{checkpointOperations--;}}
  <T>T captureRuleMovements(CostMovementQueue queue,java.util.function.Supplier<T> action){
    if(capturingCostMovements!=null)throw new IllegalStateException("nested rule movement capture");capturingCostMovements=queue;
    try{return action.get();}finally{capturingCostMovements=null;}
  }
  void completeRuleInput(RuleFoldProgram fold,SkillContext context,boolean accepted,Optional<SkillChoice> choice){
    var p=fold.snapshot();if(p.awaiting()==null)throw new IllegalStateException("missing rule input");
    if(p.awaiting().type()==PromptType.SKILL_INVOKE){
      var binding=p.registry().get(p.cursor());if(deferredInvoke!=null)throw new IllegalStateException("nested rule invocation");
      var input=new DeferredInvoke(binding.owner(),context.actor().seat(),binding.id(),accepted);deferredInvoke=input;
      try{fold.complete(bus,context,Optional.empty());if(!input.consumed)throw new IllegalStateException("rule invocation not consumed");}finally{deferredInvoke=null;}
    }else fold.complete(bus,context,choice);
  }
  void enterResumableDrawPrefix(){
    var player=state.currentPlayer();state.advancePhase();firePhaseEntered();resolveJudgments(player);
    var before=SkillContext.phase(state,Timing.PHASE_DRAW_BEFORE,player);bus.trigger(Timing.PHASE_DRAW_BEFORE,before);state.advancePhase(false);firePhaseEntered();
  }
  void finishResumableDraw(){fireTiming(Timing.PHASE_DRAW_END,state.currentPlayer());state.advancePhase();firePhaseEntered();}
  public ActionResult resumeTurnStartInvoke(java.util.UUID actor,long id,long revision,boolean accepted){return resumeTurnStart(actor,id,revision,accepted,Optional.empty(),PromptType.SKILL_INVOKE);}
  public ActionResult resumeTurnStartChoice(java.util.UUID actor,long id,long revision,Optional<SkillChoice> choice){return resumeTurnStart(actor,id,revision,false,choice==null?Optional.empty():choice,activeTurnStart!=null&&activeTurnStart.pending()!=null&&activeTurnStart.pending().type()==PromptType.GUANXING_ORDER?PromptType.GUANXING_ORDER:PromptType.TUXI_TARGET);}
  private ActionResult resumeTurnStart(java.util.UUID actor,long id,long revision,boolean accepted,Optional<SkillChoice> choice,PromptType type){
    assertContinuationIngress();var p=activeTurnStart==null?null:activeTurnStart.pending();
    if(p==null||p.id()!=id||p.revision()!=revision||p.type()!=type||!state.players().get(p.asker()).id().equals(actor))return ActionResult.rejected(RejectReason.WRONG_PHASE);
    if(choice.isPresent()){
      var request=activeTurnStart.request(this);var answer=choice.get();
      if(!(request.allowsHiddenChoice(answer.cardId())||request.choices().stream().anyMatch(c->c.instanceId()==answer.cardId()))
          ||(request.allowedTargetSeats().isEmpty()?answer.targetSeat()!=-1:!request.allowedTargetSeats().contains(answer.targetSeat())))return ActionResult.rejected(RejectReason.TARGET_NOT_IN_GAME);
    }
    checkpointOperations++;
    try{if(!activeTurnStart.answer(this,accepted,choice))throw new IllegalStateException("rule input not consumed");advanceTurnStart();return ActionResult.ok();}
    catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}finally{checkpointOperations--;}
  }
  public dev.sgs.core.persistence.TurnStartCheckpoint checkpointTurnStart(String content){
    assertContinuationIngress();
    if(activeTurnStart==null||!supportsTurnStartContinuation()||!counterspellScopes.isEmpty()||!listeners.isEmpty()
        ||state.cards().observerCount()!=0||state.logObserverCount()!=0||state.cards().ruleListenerRevision()!=checkpointMovementRevision
        ||!bus.checkpointBindings().equals(checkpointInitialBindings)||!checkpointHeroDefinitions().equals(checkpointInitialHeroes))throw new IllegalStateException("draw not at safe point");
    activeTurnStart.validate(this);
    var saved=new dev.sgs.core.persistence.TurnStartCheckpoint(activeTurnStart.snapshot().prepare()==null?1:2,dev.sgs.core.persistence.StateCheckpoint.capture(state,content),promptSequence,responseTimeoutMillis/1000,
        skills.saveMemory(),bus.checkpointBindings(),state.publicSkillSequence(),state.publicSkillActivations(),activeTurnStart.snapshot(),clock.nowMillis());saved.validate();return saved;
  }
  public static SgsGameEngine restoreTurnStart(dev.sgs.core.persistence.TurnStartCheckpoint saved,String content,PromptResponder responder,Clock clock){
    saved.validate();if(!saved.state().contentVersion().equals(content))throw new IllegalArgumentException("draw content differs");
    var state=saved.state().restore();var e=new SgsGameEngine(state,responder,clock,saved.responseTimeoutSeconds());e.skills.restoreMemory(saved.skillMemory(),state.turnNumber());
    if(!e.bus.checkpointBindings().equals(saved.bindings()))throw new IllegalArgumentException("draw skill bindings differ");
    state.restoreSkillActivations(saved.skillSequence(),saved.activations());e.promptSequence=saved.promptSequence();
    e.activeTurnStart=new TurnStartContinuation(saved.continuation(),saved.capturedAtMillis(),clock.nowMillis());e.checkpointTurnStart(content);return e;
  }

  /** Player voluntarily ends the play phase and moves into the discard phase. */
  public boolean supportsTurnEndContinuation(){
    return !skills.mythic().active()&&unsupportedCheckpointHero()==null&&bus.supportsMovementProgram()&&List.of(Timing.PHASE_DISCARD_BEFORE,Timing.PHASE_DISCARD_START,
        Timing.PHASE_DISCARD_END,Timing.PHASE_TURN_END).stream().allMatch(bus::supportsPhase);
  }
  public boolean hasTurnEndContinuation(){return activeTurnEnd!=null;}
  public Optional<PromptRequest> pendingTurnEndPrompt(){return activeTurnEnd==null||activeTurnEnd.pending()==null?Optional.empty():Optional.of(activeTurnEnd.request(this));}
  public long turnEndDecisionRevision(){return activeTurnEnd==null||activeTurnEnd.pending()==null?0:activeTurnEnd.pending().revision();}
  public ActionResult beginResumableEndPlay(SgsPlayer player){return beginTurnEnd(player,true,List.of(),false);}
  public ActionResult beginResumableDiscard(SgsPlayer player,List<Long> ids){return beginTurnEnd(player,false,ids,false);}
  public ActionResult beginResumableTimeoutDiscard(SgsPlayer player,List<Long> ids){return beginTurnEnd(player,false,ids,true);}
  private ActionResult beginTurnEnd(SgsPlayer player,boolean endPlay,List<Long> supplied,boolean automatic){
    assertContinuationIngress();
    if(hasActiveContinuation())return ActionResult.rejected(RejectReason.WRONG_PHASE);
    if(state.status()!=GameStatus.RUNNING)return ActionResult.rejected(RejectReason.GAME_NOT_RUNNING);
    if(player!=state.currentPlayer())return ActionResult.rejected(RejectReason.NOT_YOUR_TURN);
    if(state.phase()!=(endPlay?Phase.PLAY:Phase.DISCARD))return ActionResult.rejected(RejectReason.WRONG_PHASE);
    if(!supportsTurnEndContinuation())return ActionResult.rejected(RejectReason.NOT_YET_IMPLEMENTED);
    var ids=supplied==null?List.<Long>of():supplied;var cards=new ArrayList<CardInstance>();
    if(!endPlay){
      int excess=Math.max(player.handCount()-state.handLimit(player),0);
      if(ids.size()!=excess||new HashSet<>(ids).size()!=ids.size())return ActionResult.rejected(RejectReason.DISCARD_COUNT);
      for(Long id:ids){if(id==null)return ActionResult.rejected(RejectReason.CARD_NOT_IN_HAND);var card=findInHand(player,id);
        if(card==null)return ActionResult.rejected(RejectReason.CARD_NOT_IN_HAND);cards.add(card);}
    }
    checkpointOperations++;
    try{activeTurnEnd=new TurnEndContinuation(this,endPlay,cards,automatic);advanceTurnEnd();return ActionResult.ok();}
    catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}finally{checkpointOperations--;}
  }
  private void advanceTurnEnd(){if(activeTurnEnd!=null){activeTurnEnd.advance(this);if(activeTurnEnd.done())activeTurnEnd=null;}}
  public void pollResumableTurnEnd(){
    assertContinuationIngress();checkpointOperations++;
    try{advanceTurnEnd();}catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}finally{checkpointOperations--;}
  }
  public ActionResult resumeTurnEndInvoke(java.util.UUID actor,long promptId,long revision,boolean accepted){
    assertContinuationIngress();var p=activeTurnEnd==null?null:activeTurnEnd.pending();
    if(p==null||p.id()!=promptId||p.revision()!=revision||!state.players().get(p.asker()).id().equals(actor))return ActionResult.rejected(RejectReason.WRONG_PHASE);
    checkpointOperations++;
    try{if(!activeTurnEnd.invoke(this,accepted))throw new IllegalStateException("phase invocation not consumed");advanceTurnEnd();return ActionResult.ok();}
    catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}finally{checkpointOperations--;}
  }
  public dev.sgs.core.persistence.TurnEndCheckpoint checkpointTurnEnd(String content){
    assertContinuationIngress();
    if(activeTurnEnd==null||!supportsTurnEndContinuation()||!counterspellScopes.isEmpty()||!listeners.isEmpty()
        ||state.cards().observerCount()!=0||state.logObserverCount()!=0||state.cards().ruleListenerRevision()!=checkpointMovementRevision
        ||!bus.checkpointBindings().equals(checkpointInitialBindings)||!checkpointHeroDefinitions().equals(checkpointInitialHeroes))
      throw new IllegalStateException("phase is not at a safe checkpoint");
    activeTurnEnd.validate(this);
    var saved=new dev.sgs.core.persistence.TurnEndCheckpoint(1,dev.sgs.core.persistence.StateCheckpoint.capture(state,content),promptSequence,
        responseTimeoutMillis/1000,skills.saveMemory(),bus.checkpointBindings(),state.publicSkillSequence(),state.publicSkillActivations(),activeTurnEnd.snapshot(),clock.nowMillis());
    saved.validate();return saved;
  }
  public static SgsGameEngine restoreTurnEnd(dev.sgs.core.persistence.TurnEndCheckpoint saved,String content,PromptResponder responder,Clock clock){
    saved.validate();if(!saved.state().contentVersion().equals(content))throw new IllegalArgumentException("phase content differs");
    var state=saved.state().restore();var e=new SgsGameEngine(state,responder,clock,saved.responseTimeoutSeconds());
    e.skills.restoreMemory(saved.skillMemory(),state.turnNumber());
    if(!e.bus.checkpointBindings().equals(saved.bindings()))throw new IllegalArgumentException("phase skill bindings differ");
    state.restoreSkillActivations(saved.skillSequence(),saved.activations());e.promptSequence=saved.promptSequence();
    e.activeTurnEnd=new TurnEndContinuation(saved.continuation(),saved.capturedAtMillis(),clock.nowMillis());e.checkpointTurnEnd(content);return e;
  }
  public ActionResult endPlayPhase(SgsPlayer player) {
    checkpointOperations++;
    try {
    if(hasActiveContinuation())return ActionResult.rejected(RejectReason.WRONG_PHASE);
    if (state.status() != GameStatus.RUNNING) {
      return ActionResult.rejected(RejectReason.GAME_NOT_RUNNING);
    }
    if (player != state.currentPlayer()) {
      return ActionResult.rejected(RejectReason.NOT_YOUR_TURN);
    }
    if (state.phase() != Phase.PLAY) {
      return ActionResult.rejected(RejectReason.WRONG_PHASE);
    }
    enterDiscardWindow(player);
    return ActionResult.ok();

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  /** The skip decision precedes the phase; a skipped discard emits no DISCARD_START. */
  private void enterDiscardWindow(SgsPlayer player){
    var before=SkillContext.phase(state,Timing.PHASE_DISCARD_BEFORE,player);
    bus.trigger(Timing.PHASE_DISCARD_BEFORE,before);
    state.advancePhase(); // PLAY -> DISCARD
    if(before.phaseSkipped()){
      state.advancePhase();firePhaseEntered();if(state.status()==GameStatus.RUNNING)state.advancePhase();
    }else firePhaseEntered();
  }

  /**
   * Launches an active skill (e.g. Rende). Structural checks run first with zero side effects; the
   * skill performs its own validation and returns a rejection without mutating state.
   */
  public boolean supportsActiveSkillContinuation(SgsPlayer actor,String skillId){
    return actor!=null&&state.players().contains(actor)&&supportsTrickContinuation()
        &&skills.activeSkill(actor,skillId).orElse(null) instanceof dev.sgs.core.engine.skill.PlannedActiveSkill planned
        &&(!planned.requiresAttackContinuation()||supportsAttackContinuation());
  }
  /** Read-only public presentation. Existing parent frames recover held cards without changing save bytes. */
  public List<dev.sgs.core.game.PublicCardReveal> publicCardReveals(){
    var values=new java.util.LinkedHashMap<String,dev.sgs.core.game.PublicCardReveal>();
    for(var value:state.publicCardReveals())values.put(value.id(),value);
    if(activeSkill!=null){var g=activeSkill.snapshot().revealedGift();
      if(g!=null&&g.selected()>0){var value=new dev.sgs.core.game.PublicCardReveal(
          dev.sgs.core.game.PublicCardReveal.key(state.turnNumber(),g.skill(),g.actor(),g.target(),g.selected()),
          g.actor(),g.target(),g.skill(),List.of(RevealedGiftRules.physical(state,g.selected())),true);projectPublicReveal(values,value);}
    }
    if(activeTrick!=null&&!activeTrick.done()){
      var r=activeTrick.snapshot().family().revealCost();
      if(r!=null&&r.shown()>0){var value=new dev.sgs.core.game.PublicCardReveal(
          dev.sgs.core.game.PublicCardReveal.key(state.turnNumber(),"huogong",r.target(),r.source(),r.shown()),
          r.target(),r.source(),"huogong",List.of(trickPhysical(r.shown())),true);projectPublicReveal(values,value);}
    }
    return List.copyOf(values.values());
  }
  private static void projectPublicReveal(java.util.Map<String,dev.sgs.core.game.PublicCardReveal> values,dev.sgs.core.game.PublicCardReveal value){
    boolean existing=values.values().stream().anyMatch(r->r.owner()==value.owner()&&r.subject()==value.subject()&&r.reason().equals(value.reason())
        &&r.cards().stream().map(CardInstance::instanceId).toList().equals(value.cards().stream().map(CardInstance::instanceId).toList()));
    if(!existing)values.put(value.id(),value);
  }
  @Override public boolean canUseActiveAttack(SgsPlayer source,SgsPlayer target){return validateSha(source,target).accepted();}
  int continuationCommitActiveStrike(SgsPlayer source,GameCard card){
    state.registerShaPlayed();cardUseCommitted(source,card);return state.consumeJiuBoost()?2:1;
  }
  dev.sgs.core.engine.skill.PlannedActiveSkill plannedSkill(dev.sgs.core.engine.skill.ActiveSkillPlan plan){
    var skill=skills.activeSkill(state.players().get(plan.actor()),plan.skillId()).orElse(null);
    if(!(skill instanceof dev.sgs.core.engine.skill.PlannedActiveSkill planned))throw new IllegalArgumentException("planned skill binding disappeared");
    return planned;
  }
  public boolean hasActiveSkillContinuation(){return activeSkill!=null;}
  public long activeSkillDecisionRevision(){return activeSkill==null?-1:activeSkill.revision();}
  public Optional<PromptRequest> pendingActiveSkillPrompt(){return activeSkill==null?Optional.empty():activeSkill.prompt(this);}
  /** Inspection of the actual parent; this is not yet a versioned persistent checkpoint. */
  public Optional<ActiveSkillContinuation.Snapshot> activeSkillProgram(){return activeSkill==null?Optional.empty():Optional.of(activeSkill.snapshot());}
  private void validateActiveSkillProgram(){
    var c=activeSkill.snapshot();var plan=c.plan();var skill=plannedSkill(plan);
    if(!supportsActiveSkillContinuation(state.players().get(plan.actor()),plan.skillId())||!state.hasSkill(state.players().get(plan.actor()),plan.skillId()))
      throw new IllegalArgumentException("unsupported active skill context");
    if(c.step()==ActiveSkillContinuation.Step.SELECT){if(!skill.stillPrepared(this,plan))throw new IllegalArgumentException("active selection plan changed");}
    else if(!skill.committedPlan(this,plan,c.selectedSource()))throw new IllegalArgumentException("active use memory or roles differ");
    if(c.payment()!=null)new DiscardCostContinuation(c.payment(),0,0).validate(this);
    if(c.movements().pending()!=null)new CostMovementQueue(c.movements(),0,0).validate(this);
    for(var m:c.movements().movements())if(m.event().sequence()>state.cards().checkpointSequence())throw new IllegalArgumentException("future active movement");
    if(c.response()!=null){
      var r=c.response();validateSharedResponse(r,false,true);
      if(r.pending().id()>promptSequence||r.pending().id()<1||r.pending().deadlineMillis()<0)
        throw new IllegalArgumentException("invalid active response input");
    }
    if(c.damage()!=null)validateSharedDamageProgram(c.damage(),c.duel().other(),c.duel().responder(),1,0,CardType.JUEDOU,
        DamageNature.NORMAL,dev.sgs.core.persistence.ActiveSkillCheckpoint.pending(c),c.movements().pending()!=null);
    if(c.hpLoss()!=null)new HpLossContinuation(c.hpLoss(),0,0).validate(this);
    if(c.providedAttack()!=null)new ActiveAttackContinuation(c.providedAttack(),0,0).validateAgainst(this);
    if(c.revealedGift()!=null)new RevealedGiftContinuation(c.revealedGift(),0,0).validateAgainst(this,dev.sgs.core.persistence.ActiveSkillCheckpoint.pending(c),c.movements().pending()!=null);
  }
  public dev.sgs.core.persistence.ActiveSkillCheckpoint checkpointActiveSkill(String content){
    assertContinuationIngress();
    if(activeSkill==null||checkpointFaulted||!counterspellScopes.isEmpty()||!listeners.isEmpty()
        ||state.cards().observerCount()!=0||state.logObserverCount()!=0||state.cards().ruleListenerRevision()!=checkpointMovementRevision)
      throw new IllegalStateException("active skill is not at a safe checkpoint");
    var saved=new dev.sgs.core.persistence.ActiveSkillCheckpoint(activeSkill.snapshot().revealedGift()!=null?4:activeSkill.snapshot().providedAttack()!=null?3:activeSkill.snapshot().hpLoss()==null?1:2,dev.sgs.core.persistence.StateCheckpoint.capture(state,content),
        promptSequence,responseTimeoutMillis/1000,skills.saveMemory(),bus.checkpointBindings(),state.publicSkillSequence(),state.publicSkillActivations(),
        activeSkill.snapshot(),clock.nowMillis());
    saved.validate();validateActiveSkillProgram();return saved;
  }
  public static SgsGameEngine restoreActiveSkill(dev.sgs.core.persistence.ActiveSkillCheckpoint saved,String content,PromptResponder responder,Clock clock){
    saved.validate();if(!saved.state().contentVersion().equals(content))throw new IllegalArgumentException("active content differs");
    var state=saved.state().restore();var e=new SgsGameEngine(state,responder,clock,saved.responseTimeoutSeconds());
    e.skills.restoreMemory(saved.skillMemory(),state.turnNumber());
    if(!e.bus.checkpointBindings().equals(saved.bindings()))throw new IllegalArgumentException("active skill bindings differ");
    state.restoreSkillActivations(saved.skillSequence(),saved.activations());e.promptSequence=saved.promptSequence();
    e.activeSkill=new ActiveSkillContinuation(saved.continuation(),saved.capturedAtMillis(),clock.nowMillis());
    e.checkpointActiveSkill(content);return e;
  }
  public ActionResult beginResumableActiveSkill(SgsPlayer actor,String skillId,SgsPlayer target,List<Long> ids){
    assertContinuationIngress();
    var request=validateActiveSkillRequest(actor,skillId,target,ids);
    if(!request.verdict().accepted())return request.verdict();
    if(!supportsActiveSkillContinuation(actor,skillId))return ActionResult.rejected(RejectReason.NOT_YET_IMPLEMENTED);
    var preparation=((dev.sgs.core.engine.skill.PlannedActiveSkill)request.skill()).prepare(this,actor,target,request.cards());
    if(!preparation.accepted())return ActionResult.rejected(RejectReason.SKILL_REJECTED);
    activeSkill=new ActiveSkillContinuation(preparation.plan());return ActionResult.ok();
  }
  public void pollResumableActiveSkill(){
    assertContinuationIngress();checkpointOperations++;
    try{if(activeSkill!=null){activeSkill.advance(this);if(activeSkill.done())activeSkill=null;}}
    catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}finally{checkpointOperations--;}
  }
  private PromptRequest activeSkillInput(UUID actor,long id,long revision){
    if(activeSkill==null)return null;var p=activeSkill.prompt(this).orElse(null);
    return p!=null&&DecisionBoundary.of(p,activeSkill.revision()).matches(actor,id,revision)?p:null;
  }
  private boolean skillChoiceType(PromptType type){return type.isContinuationChoice();}
  public ActionResult resumeActiveSkillSuit(UUID actor,long id,long revision,Optional<Suit> answer){
    assertContinuationIngress();var p=activeSkillInput(actor,id,revision);
    if(p==null||p.type()!=PromptType.FANJIAN_SUIT)return ActionResult.rejected(RejectReason.RESPONSE_ONLY);
    checkpointOperations++;try{
      if(DecisionBoundary.of(p,revision).expired(clock.nowMillis()))answer=Optional.empty();
      if(answer.orElse(Suit.SPADE)==Suit.NONE)return ActionResult.rejected(RejectReason.RESPONSE_ONLY);
      return activeSkill.chooseSuit(this,answer)?ActionResult.ok():ActionResult.rejected(RejectReason.RESPONSE_ONLY);
    }catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}finally{checkpointOperations--;}
  }
  public ActionResult resumeActiveSkillChoice(UUID actor,long id,long revision,Optional<SkillChoice> answer){
    assertContinuationIngress();var p=activeSkillInput(actor,id,revision);
    if(p==null||!skillChoiceType(p.type()))return ActionResult.rejected(RejectReason.RESPONSE_ONLY);
    checkpointOperations++;try{
      if(DecisionBoundary.of(p,revision).expired(clock.nowMillis()))answer=Optional.empty();
      return activeSkill.choose(this,answer)?ActionResult.ok():ActionResult.rejected(RejectReason.RESPONSE_ONLY);
    }catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}finally{checkpointOperations--;}
  }
  public ActionResult resumeActiveSkillInvoke(UUID actor,long id,long revision,boolean answer){
    assertContinuationIngress();var p=activeSkillInput(actor,id,revision);
    if(p==null||p.type()!=PromptType.SKILL_INVOKE)return ActionResult.rejected(RejectReason.RESPONSE_ONLY);
    checkpointOperations++;try{
      return activeSkill.invoke(this,answer&&clock.nowMillis()<=p.deadlineMillis())?ActionResult.ok():ActionResult.rejected(RejectReason.RESPONSE_ONLY);
    }catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}finally{checkpointOperations--;}
  }
  public ActionResult resumeActiveSkillResponse(UUID actor,long id,long revision,Optional<PlayDecision> answer){
    assertContinuationIngress();var p=activeSkillInput(actor,id,revision);
    if(p==null||p.type()==PromptType.SKILL_INVOKE||p.type()==PromptType.FANJIAN_SUIT||skillChoiceType(p.type()))return ActionResult.rejected(RejectReason.RESPONSE_ONLY);
    checkpointOperations++;try{
      if(DecisionBoundary.of(p,revision).expired(clock.nowMillis()))answer=Optional.empty();
      else if(answer.isPresent()&&!activeSkill.strikeInput()){
        var canonical=validateContinuationDecision(p,answer.get());if(canonical.isEmpty())return ActionResult.rejected(RejectReason.RESPONSE_ONLY);answer=canonical;
      }
      return activeSkill.respond(this,answer)?ActionResult.ok():ActionResult.rejected(RejectReason.RESPONSE_ONLY);
    }catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}finally{checkpointOperations--;}
  }

  private record ActiveSkillRequest(ActiveSkill skill,List<CardInstance> cards,ActionResult verdict){
    static ActiveSkillRequest reject(RejectReason reason){return new ActiveSkillRequest(null,List.of(),ActionResult.rejected(reason));}
  }
  private ActiveSkillRequest validateActiveSkillRequest(SgsPlayer player,String skillId,SgsPlayer target,List<Long> cardInstanceIds){
    if(hasActiveContinuation())return ActiveSkillRequest.reject(RejectReason.WRONG_PHASE);
    if (state.status() != GameStatus.RUNNING) {
      return ActiveSkillRequest.reject(RejectReason.GAME_NOT_RUNNING);
    }
    if (player != state.currentPlayer()) {
      return ActiveSkillRequest.reject(RejectReason.NOT_YOUR_TURN);
    }
    if (state.phase() != Phase.PLAY) {
      return ActiveSkillRequest.reject(RejectReason.WRONG_PHASE);
    }
    ActiveSkill skill = skills.activeSkill(player, skillId).orElse(null);
    if (skill == null || !state.hasSkill(player,skillId)) {
      return ActiveSkillRequest.reject(RejectReason.SKILL_UNAVAILABLE);
    }
    List<Long> ids = cardInstanceIds == null ? List.of() : cardInstanceIds;
    if(ids.stream().distinct().count()!=ids.size())return ActiveSkillRequest.reject(RejectReason.SKILL_REJECTED);
    List<CardInstance> cards = new ArrayList<>(ids.size());
    for (long id : ids) {
      CardInstance card = (dev.sgs.core.addon.AddonRuntime.current().skill(skillId).map(dev.sgs.core.addon.SkillPresentation::allowEquipmentCosts).orElse(false))
          ?findOwnedCost(player,id):findInHand(player, id);
      if (card == null) {
        return ActiveSkillRequest.reject(RejectReason.CARD_NOT_IN_HAND);
      }
      cards.add(card);
    }
    return new ActiveSkillRequest(skill,List.copyOf(cards),ActionResult.ok());
  }

  public ActionResult useActiveSkill(
      SgsPlayer player, String skillId, SgsPlayer target, List<Long> cardInstanceIds) {
    checkpointOperations++;
    try {
    var request=validateActiveSkillRequest(player,skillId,target,cardInstanceIds);
    if(!request.verdict().accepted())return request.verdict();
    var skill=request.skill();var cards=request.cards();
    SkillResult result = skill.activate(this, player, target, List.copyOf(cards));
    if (!result.accepted()) {
      // Rejected input must leave deterministic game/checkpoint state unchanged.
      return ActionResult.rejected(RejectReason.SKILL_REJECTED);
    }
    if(!"jijiang".equals(skillId))state.publishSkillActivation(player.seat(),skillId);
    return ActionResult.ok();

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  /** Confirms the exact excess cards to discard, then ends the turn. */
  public ActionResult confirmDiscard(SgsPlayer player, List<Long> cardInstanceIds) {
    checkpointOperations++;
    try {
    if(hasActiveContinuation())return ActionResult.rejected(RejectReason.WRONG_PHASE);
    if (state.status() != GameStatus.RUNNING) {
      return ActionResult.rejected(RejectReason.GAME_NOT_RUNNING);
    }
    if (player != state.currentPlayer()) {
      return ActionResult.rejected(RejectReason.NOT_YOUR_TURN);
    }
    if (state.phase() != Phase.DISCARD) {
      return ActionResult.rejected(RejectReason.WRONG_PHASE);
    }
    int excess = Math.max(player.handCount() - state.handLimit(player), 0);
    List<Long> ids = cardInstanceIds == null ? List.of() : cardInstanceIds;
    if (ids.size() != excess || ids.stream().distinct().count() != ids.size()) {
      return ActionResult.rejected(RejectReason.DISCARD_COUNT);
    }
    List<CardInstance> discarded=new ArrayList<>(excess);
    for(long id:ids){var card=findInHand(player,id);if(card==null)return ActionResult.rejected(RejectReason.CARD_NOT_IN_HAND);discarded.add(card);}
    state.cards().move(discarded,CardZone.hand(player.seat()),CardZone.DISCARD,DISCARD,player.seat());
    state.log("seat {} discarded {} confirmed cards", player.seat(), discarded.size());
    state.advancePhase(); // DISCARD -> END
    firePhaseEntered(); // PHASE_TURN_END
    if(state.status()==GameStatus.RUNNING)state.advancePhase(); // END -> next seat PREPARE
    return ActionResult.ok();

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  /** Timeout path: end play if needed, auto-discard the excess, then end turn. */
  public ActionResult timeoutTurn(SgsPlayer player) {
    checkpointOperations++;
    try {
    if(hasActiveContinuation())return ActionResult.rejected(RejectReason.WRONG_PHASE);
    if (state.status() != GameStatus.RUNNING) {
      return ActionResult.rejected(RejectReason.GAME_NOT_RUNNING);
    }
    if (player != state.currentPlayer()) {
      return ActionResult.rejected(RejectReason.NOT_YOUR_TURN);
    }
    if (state.phase() == Phase.PLAY) {
      enterDiscardWindow(player);
      if(state.currentPlayer()!=player)return ActionResult.ok();
    }
    if (state.phase() != Phase.DISCARD) {
      return ActionResult.rejected(RejectReason.WRONG_PHASE);
    }
    state.enforceDiscardLimit();
    state.advancePhase(); // DISCARD -> END
    firePhaseEntered(); // PHASE_TURN_END
    if(state.status()==GameStatus.RUNNING)state.advancePhase(); // END -> next seat PREPARE
    return ActionResult.ok();

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  Optional<PlayDecision> ask(PromptRequest prompt) {
    checkpointOperations++;
    try {
    var guhuo=skills.mythic().offerGuhuo(prompt);if(guhuo.attempted())return guhuo.answer();
    Optional<PlayDecision> response;
    try {
      response = responder.respond(state, prompt);
    } catch (RuntimeException e) {
      state.log("responder error for seat {}: {}", prompt.asker().seat(), e.toString());
      return Optional.empty();
    }
    if (response == null || response.isEmpty()) {
      return Optional.empty();
    }
    return validateContinuationDecision(prompt,response.get());

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }

  private Optional<PlayDecision> validateContinuationDecision(PromptRequest prompt,PlayDecision decision){
    return validateContinuationDecision(prompt,decision,false);
  }

  private Optional<PlayDecision> validateContinuationDecision(PromptRequest prompt,PlayDecision decision,boolean preview){
    if (clock.nowMillis() > prompt.deadlineMillis()) {
      if(!preview)state.log("late response from seat {} ignored (deadline passed)", prompt.asker().seat());
      return Optional.empty();
    }
    if(decision.card()==null)return Optional.empty();
    if(prompt.type()==PromptType.QINGLONG_SHA && (!continuationQinglongAvailable(prompt.asker(),prompt.subject()) || decision.card().sourceCards().size()!=1))return Optional.empty();
    if(prompt.allowedTypes().contains(CardType.SHA) && decision.card().sourceCards().size()==2){
      var weapon=prompt.asker().equipment().weapon();
      if(weapon==null || weapon.type()!=CardType.ZHANGBA || decision.effectiveType()!=CardType.SHA)return Optional.empty();
      var ids=decision.card().sourceCards().stream().map(CardInstance::instanceId).distinct().toList();
      if(ids.size()!=2)return Optional.empty();
      var sources=ids.stream().map(id->findInHand(prompt.asker(),id)).toList();
      if(sources.stream().anyMatch(java.util.Objects::isNull))return Optional.empty();
      var spear=new dev.sgs.core.card.VirtualCard(CardType.SHA,sources);
      if(prompt.type()==PromptType.JIEDAO_RESPONSE && (!validAttackRangeAfterCost(prompt.asker(),prompt.subject(),spear)
          || !cardTargetAllowed(prompt.asker(),prompt.subject(),spear)))return Optional.empty();
      if(!validProvidedAttackTarget(prompt,spear))return Optional.empty();
      return Optional.of(PlayDecision.asIs(spear));
    }
    if(decision.card().sourceCards().size()!=1)return Optional.empty();
    long chosenId=decision.card().sourceCards().getFirst().instanceId();
    if(prompt.type()==PromptType.HUOGONG_REVEAL || prompt.type()==PromptType.HUOGONG_PAY){
      var canonical=findInHand(prompt.asker(),chosenId);
      if(canonical==null || decision.effectiveType()!=canonical.type())return Optional.empty();
      if(prompt.type()==PromptType.HUOGONG_PAY && (prompt.choices().size()!=1 || skills.mythic().suit(prompt.asker(),canonical)!=skills.mythic().suit(prompt.subject(),prompt.choices().getFirst())))return Optional.empty();
      return Optional.of(PlayDecision.asIs(canonical));
    }
    if(prompt.type()==PromptType.WUGU_PICK){
      // A public draft is chosen from the current pool, never from the asker's private hand.
      return prompt.choices().stream().filter(c->c.instanceId()==chosenId)
          .filter(c->c.type()==decision.effectiveType()).findFirst().map(PlayDecision::asIs);
    }
    CardInstance card =
        findOwnedCost(prompt.asker(), chosenId);
    if (card == null) {
      return Optional.empty();
    }
    if (!prompt.allowedTypes().contains(decision.effectiveType())) {
      return Optional.empty();
    }
    boolean equipped=CardZone.equipment(prompt.asker().seat()).equals(state.cards().locate(chosenId));
    if(equipped && (card.type()==decision.effectiveType() || (decision.effectiveType()!=CardType.SHA
        && !(prompt.type()==PromptType.RESCUE_TAO && decision.effectiveType()==CardType.TAO))))return Optional.empty();
    if(prompt.type()==PromptType.JIEDAO_RESPONSE && (!validAttackRangeAfterCost(prompt.asker(),prompt.subject(),card)
        || !cardTargetAllowed(prompt.asker(),prompt.subject(),new dev.sgs.core.card.VirtualCard(decision.effectiveType(),List.of(card)))))return Optional.empty();
    if(!validProvidedAttackTarget(prompt,card.type()==decision.effectiveType()?card:new VirtualCard(decision.effectiveType(),List.of(card))))return Optional.empty();
    if (card.type()!=decision.effectiveType()) {
      SkillContext ctx =
          SkillContext.transform(
              state, prompt.asker(), card, decision.effectiveType(), prompt.type());
      if(preview)return bus.canTransform(ctx)?Optional.of(new PlayDecision(new VirtualCard(decision.effectiveType(),List.of(card)),decision.effectiveType())):Optional.empty();
      bus.trigger(Timing.CARD_TRANSFORM, ctx);
      if (!ctx.transformConfirmed()) {
        return Optional.empty();
      }
      // The confirmed alternation settles as the virtual counterpart built on the same source.
      return Optional.of(new PlayDecision(ctx.card(), decision.effectiveType()));
    }
    return Optional.of(PlayDecision.asIs(card));
    }

  private boolean validProvidedAttackTarget(PromptRequest prompt,GameCard card){
    if(prompt.type()==PromptType.QINGLONG_SHA)return cardTargetAllowed(prompt.asker(),prompt.subject(),card);
    if(prompt.type()!=PromptType.JIJIANG_SHA)return true;
    SgsPlayer target=providedAttackTarget;
    var attack=currentAttackProgram();
    if(attack!=null && attack.snapshot().step()==AttackContinuation.AttackStep.QINGLONG_PROVIDER_SHA)target=state.players().get(attack.snapshot().targetSeat());
    return target==null || cardTargetAllowed(prompt.subject(),target,card) && !(prompt.asker()==target && emptiesKongcheng(prompt.asker(),card));
  }
  private static CardInstance findInHand(SgsPlayer player, long instanceId) {
    return player.handView().stream()
        .filter(c -> c.instanceId() == instanceId)
        .findFirst()
        .orElse(null);
  }

  private static CardInstance findOwnedCost(SgsPlayer player,long id){
    var hand=findInHand(player,id);return hand!=null?hand:player.equipment().all().stream()
        .filter(c->c.instanceId()==id).findFirst().orElse(null);
  }

  void beginOwnedUse(SgsPlayer player,GameCard card,CardMove.Reason reason){
    moveOwnedUse(player,card,reason);finishOwnedUse(player,card,reason);
  }
  private void moveOwnedUse(SgsPlayer player,GameCard card,CardMove.Reason reason){
    var transfers=new ArrayList<CardMovement.Transfer>();
    var grouped=new java.util.LinkedHashMap<CardZone,List<CardInstance>>();
    for(var cost:card.sourceCards()){
      var zone=state.cards().locate(cost.instanceId());
      if(skills.mythic().has(player,"guhuo")&&CardZone.PROCESSING.equals(zone))continue;
      if(!CardZone.hand(player.seat()).equals(zone) && !CardZone.equipment(player.seat()).equals(zone)
          && !(skills.mythic().has(player,"jixi")&&card.type()==CardType.SHUNSHOU&&CardZone.special(player.seat()).equals(zone)))
        throw new IllegalStateException("unowned card cost");
      grouped.computeIfAbsent(zone,ignored->new ArrayList<>()).add(cost);
    }
    grouped.forEach((zone,cards)->transfers.add(new CardMovement.Transfer(cards,zone,CardZone.PROCESSING,reason,player.seat(),true)));
    state.cards().moveBatch(transfers);
  }
  void finishOwnedUse(SgsPlayer player,GameCard card,CardMove.Reason reason){
    finishOwnedUse(player,card,reason,"");
  }
  void finishOwnedUse(SgsPlayer player,GameCard card,CardMove.Reason reason,String transformSkill){
    if(transformSkill.isEmpty())bus.commitTransform(state,player,card);else bus.commitTransformReceipt(state,player,transformSkill);
    if(card.type()==CardType.SHA && card.isVirtual() && card.sourceCards().size()==2
        && player.equipment().weapon()!=null && player.equipment().weapon().type()==CardType.ZHANGBA)
      state.publishSkillActivation(player.seat(),"zhangba");
    if(reason==RESPONSE)bus.trigger(Timing.CARD_RESPONSE_COMMITTED,SkillContext.cardUse(state,player,card).at(Timing.CARD_RESPONSE_COMMITTED));
  }

  @Override public boolean canRedirectAttack(SgsPlayer attacker,SgsPlayer defender,SgsPlayer replacement,GameCard attack,CardInstance cost){
    if(attacker==null || defender==null || replacement==null || attack==null || cost==null || replacement==attacker || replacement==defender
        || !state.players().contains(attacker) || !state.players().contains(defender) || !state.players().contains(replacement)
        || !attacker.alive() || !defender.alive() || !replacement.alive() || attackGroupTargets.contains(replacement.seat()))return false;
    var zone=state.cards().locate(cost.instanceId());
    if(zone==null || zone.seat()!=defender.seat() || (zone.kind()!=CardZone.Kind.HAND && zone.kind()!=CardZone.Kind.EQUIPMENT))return false;
    return validAttackRangeAfterCost(defender,replacement,cost) && cardTargetAllowed(attacker,replacement,attack);
  }

  /** Evaluate range after the consumed equipment leaves, without temporary mutable unequipping. */
  private boolean validAttackRangeAfterCost(SgsPlayer source,SgsPlayer target,GameCard cost){
    if(target==null || !target.alive() || source==target)return false;
    var ids=cost.sourceCards().stream().map(CardInstance::instanceId).toList();
    var weapon=source.equipment().weapon();var horse=source.equipment().horseMinus();
    int range=weapon!=null && !ids.contains(weapon.instanceId())?weapon.type().weaponRange():1;
    int distance=state.ringDistance(source,target)
        -(horse!=null && !ids.contains(horse.instanceId())?1:0)
        +(target.equipment().hasDefensiveHorse()?1:0)
        +bus.queryInt(RuleId.DISTANCE_FROM_CORRECTION,SkillContext.cardTarget(state,source,target,cost),0);
    if(skills.mythic().unlimitedDistance(source))return true;
    if(skills.mythic().has(source,"tuntian"))distance-=state.cards().cards(CardZone.special(source.seat())).size();
    return Math.max(distance,1)<=range;
  }

  private ActionResult validateShaCost(SgsPlayer source,SgsPlayer target,CardInstance cost){
    var normal=validateSha(source,target);if(!normal.accepted())return normal;
    if(!validAttackRangeAfterCost(source,target,cost))return ActionResult.rejected(RejectReason.OUT_OF_RANGE);
    var weapon=source.equipment().weapon();
    if(weapon!=null && weapon.instanceId()==cost.instanceId() && weapon.type()==CardType.ZHUGE_NU){
      int limit=state.hasSkill(source,"paoxiao")?Integer.MAX_VALUE:1;
      if(state.shaPlayedThisTurn()>=limit)return ActionResult.rejected(RejectReason.SHA_LIMIT);
    }
    return ActionResult.ok();
  }

  long nextPromptId() {
    return ++promptSequence;
  }
  long responseDeadlineMillis(){return clock.nowMillis()+responseTimeoutMillis;}
  void cardUseCommitted(SgsPlayer actor,GameCard card){
    checkpointOperations++;
    try {
    bus.trigger(Timing.CARD_USE_COMMITTED,SkillContext.cardUse(state,actor,card));
    if(EquipmentDamageRules.attack(card) && state.currentPlayer()==actor && state.phase()==Phase.PLAY
        && state.shaPlayedThisTurn()>1 && actor.equipment().weapon()!=null
        && !state.hasSkill(actor,"paoxiao")
        && actor.equipment().weapon().type()==CardType.ZHUGE_NU)state.publishSkillActivation(actor.seat(),"zhuge_nu");

    } catch(RuntimeException|Error failure){checkpointFaulted=true;throw failure;}
    finally{checkpointOperations--;}
  }
}
