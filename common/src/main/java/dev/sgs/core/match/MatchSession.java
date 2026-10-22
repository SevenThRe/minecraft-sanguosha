package dev.sgs.core.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import dev.sgs.core.GameStatus;
import dev.sgs.core.Identity;
import dev.sgs.core.Phase;
import dev.sgs.core.card.CardInstance;
import dev.sgs.core.card.CardType;
import dev.sgs.core.card.Deck;
import dev.sgs.core.engine.ActionResult;
import dev.sgs.core.engine.GameEventListener;
import dev.sgs.core.engine.GameOutcome;
import dev.sgs.core.engine.PlayDecision;
import dev.sgs.core.engine.PromptRequest;
import dev.sgs.core.engine.PromptResponder;
import dev.sgs.core.engine.RejectReason;
import dev.sgs.core.engine.SgsGameEngine;
import dev.sgs.core.game.GameState;
import dev.sgs.core.game.HeroSelection;
import dev.sgs.core.game.IdentityTable;
import dev.sgs.core.hero.HeroDefinition;
import dev.sgs.core.hero.StandardHeroes;
import dev.sgs.core.match.MatchAction.Kind;
import dev.sgs.core.match.MatchMailbox.Envelope;
import dev.sgs.core.match.dto.MatchViews.CardView;
import dev.sgs.core.match.dto.MatchViews.JudgmentView;
import dev.sgs.core.match.dto.MatchViews.MatchSnapshot;
import dev.sgs.core.sim.BotPolicy;
import dev.sgs.core.sim.BotTurnDriver;
import dev.sgs.core.util.Clock;
import dev.sgs.core.util.IdSequencer;

/**
 * Server-authoritative live match. Each session owns one dedicated worker thread that is the only
 * reader and writer of the {@link GameState}/{@link SgsGameEngine}; network threads only enqueue
 * {@link MatchAction}s into the mailbox. The flow is:
 *
 * <ol>
 *   <li>participants are shuffled; identity-intro rooms draw roles on reveal or timeout, then
 *       rotate so the lord anchors seat one clockwise before hero offers,
 *   <li>hero selection by participant UUID (bots pick instantly, humans get a deadline); the final
 *       action order is already visible and remains unchanged at game start,
 *   <li>the worker opens turns, services play/discard windows with timeouts, answers prompts
 *       through the same mailbox, and pushes per-viewer snapshots.
 * </ol>
 */
public final class MatchSession implements PromptResponder {

  /** One human seat descriptor from the lobby/developer command. */
  public record Human(UUID id, String name, MatchEndpoint endpoint) {}

  private static final long FINISH_LINGER_MILLIS = 120_000L;

  private final long roomId;
  private final MatchConfig config;
  private final Clock clock;
  private final Random random;
  private final Consumer<String> logSink;
  private final Runnable onClosed;
  private final MatchMailbox mailbox = new MatchMailbox();
  private final java.util.Deque<MatchMailbox.SaveCheckpoint> deferredCheckpoints=new java.util.ArrayDeque<>();
  private final List<Participant> participants = new ArrayList<>();
  private Map<UUID,java.util.Set<String>> ownedHeroPools=Map.of();
  private final Map<UUID, Participant> byId = new HashMap<>();
  private final Map<UUID, Long> lastNonceByPlayer = new HashMap<>();
  private final Map<UUID,java.util.concurrent.atomic.AtomicInteger> ingressNonces=new HashMap<>();
  private record PendingInput(long promptId,UUID actor,boolean allowWuxieScopePass){
    PendingInput(long promptId,UUID actor){this(promptId,actor,false);}
  }
  private volatile PendingInput pendingInput;
  private final List<MatchEndpoint> spectators = new ArrayList<>(); // Worker-owned.
  private volatile MatchSnapshot publicSnapshot;
  private long selectionDeadline;
  private boolean identityIntroActive;
  private HeroBanRound heroBanRound;
  private boolean heroBanActive;
  private int heroBanStage;
  private long heroBanDeadline;
  private boolean openingHandActive;
  private long openingHandDeadline;
  private long identityIntroDeadline;
  private final List<Identity> undrawnIdentities = new ArrayList<>();
  private volatile boolean teammateSharing;
  public void setTeammateSharing(boolean enabled){mailbox.submitControl(new MatchMailbox.TeamSharing(enabled));}
  private HandVisibility visibility(){return state!=null && state.teamMode()?new HandVisibility(teammateSharing,java.util.Map.of(0,0,1,1,2,1,3,0)):HandVisibility.PRIVATE;}

  private GameState state;
  private SgsGameEngine engine;
  private volatile boolean stopped;
  private Thread worker;
  private long phaseDeadline;
  private PromptRequest activePrompt;
  private dev.sgs.core.engine.CounterspellWindow activeCounterspellWindow;
  private volatile boolean counterspellCollecting;
  private volatile Map<UUID,PromptRequest> counterspellInputs=Map.of();
  private long retiredCounterspellThrough;
  private long counterspellAiRound;

  private JudgmentView lastJudgment;
  private Long restoredWindowDeadline;
  private final java.util.concurrent.CompletableFuture<Void> restoredReady=new java.util.concurrent.CompletableFuture<>();

  /** Capture on the owning worker; an unsupported synchronous stack fails explicitly. */
  public java.util.concurrent.CompletableFuture<dev.sgs.core.persistence.MatchCheckpoint> checkpoint(){
    return requestCheckpoint(false);
  }
  /** Server shutdown capture: stop publishing or advancing this session after its safe point. */
  public java.util.concurrent.CompletableFuture<dev.sgs.core.persistence.MatchCheckpoint> checkpointAndPause(){
    return requestCheckpoint(true);
  }
  private java.util.concurrent.CompletableFuture<dev.sgs.core.persistence.MatchCheckpoint> requestCheckpoint(boolean pause){
    var result=new java.util.concurrent.CompletableFuture<dev.sgs.core.persistence.MatchCheckpoint>();
    if(stopped)result.completeExceptionally(new IllegalStateException("session stopped"));
    else mailbox.submitControl(new MatchMailbox.SaveCheckpoint(result,pause));
    return result;
  }

  private dev.sgs.core.persistence.MatchCheckpoint captureCheckpoint(){
    if(activeCounterspellWindow!=null && (engine==null || engine.pendingTrickWindow().orElse(null)!=activeCounterspellWindow))throw new IllegalStateException("counterspell parent is not checkpointable");
    if(openingHandActive)throw new IllegalStateException("opening hand selection is not checkpointable");
    if(engine==null)throw new IllegalStateException(identityIntroActive?"identity introduction is not checkpointable":"hero selection is not checkpointable");
    boolean attack=engine.hasAttackContinuation();
    byte[] data=attack?dev.sgs.core.persistence.ContinuationCheckpointCodec.encode(engine.checkpointAttack(config.contentPool().id()))
        :engine.hasTrickContinuation()?dev.sgs.core.persistence.TrickCheckpointCodec.encode(engine.checkpointTrick(config.contentPool().id()))
        :engine.hasActiveSkillContinuation()?dev.sgs.core.persistence.ActiveSkillCheckpointCodec.encode(engine.checkpointActiveSkill(config.contentPool().id()))
        :engine.hasTurnStartContinuation()?dev.sgs.core.persistence.TurnStartCheckpointCodec.encode(engine.checkpointTurnStart(config.contentPool().id()))
        :engine.hasTurnEndContinuation()?dev.sgs.core.persistence.TurnEndCheckpointCodec.encode(engine.checkpointTurnEnd(config.contentPool().id()))
        :dev.sgs.core.persistence.IdleEngineCheckpointCodec.encode(engine.checkpoint(config.contentPool().id()));
    var floors=new HashMap<UUID,Integer>();
    ingressNonces.forEach((id,counter)->floors.put(id,counter.get()));
    return new dev.sgs.core.persistence.MatchCheckpoint(dev.sgs.core.persistence.MatchCheckpoint.VERSION,roomId,config.contentPool().id(),attack,data,
        Math.max(0,phaseDeadline-clock.nowMillis()),teammateSharing,floors,
        new dev.sgs.core.room.RoomSettings(config.roomSettings().password(),config.turnTimeoutSeconds(),config.responseTimeoutSeconds(),
            config.selectionTimeoutSeconds(),config.roomSettings().allowChat(),config.roomSettings().allowSpectators(),
            config.roomSettings().doubleRenegade(),config.roomSettings().fixedSeats(),config.roomSettings().bannedHeroes()),config.botStepDelayMillis(),participants.stream().filter(p->p.human && p.control.source()==SeatControl.Source.USER).map(p->p.player.id()).collect(Collectors.toSet()),
        participants.stream().filter(p->p.human).collect(Collectors.toMap(p->p.player.id(),p->p.control.snapshot(clock.nowMillis(),disconnectGraceMillis))));
  }

  /** Rebind to fresh endpoints and worker; selected heroes/cards are restored without a new deal. */
  public static MatchSession restore(dev.sgs.core.persistence.MatchCheckpoint saved,MatchConfig config,
      Map<UUID,MatchEndpoint> endpoints,Clock clock,Consumer<String> logs,Runnable closed){
    return restore(saved,config,endpoints,clock,logs,closed,false);
  }
  /** A fresh native world has not observed client connections yet; absence is not a disconnect. */
  public static MatchSession restoreAwaitingReconnect(dev.sgs.core.persistence.MatchCheckpoint saved,MatchConfig config,
      Map<UUID,MatchEndpoint> endpoints,Clock clock,Consumer<String> logs,Runnable closed){
    return restore(saved,config,endpoints,clock,logs,closed,true);
  }
  private static MatchSession restore(dev.sgs.core.persistence.MatchCheckpoint saved,MatchConfig config,
      Map<UUID,MatchEndpoint> endpoints,Clock clock,Consumer<String> logs,Runnable closed,boolean awaitingReconnect){
    if(!saved.contentId().equals(config.contentPool().id()))throw new IllegalArgumentException("session content version mismatch");
    if(config.turnTimeoutSeconds()!=saved.roomSettings().turnSeconds() || config.responseTimeoutSeconds()!=saved.roomSettings().responseSeconds()
        || config.selectionTimeoutSeconds()!=saved.roomSettings().selectionSeconds() || !config.roomSettings().equals(saved.roomSettings())
        || config.botStepDelayMillis()!=saved.botStepDelayMillis())throw new IllegalArgumentException("session rules mismatch");
    var session=new MatchSession(saved.roomId(),config,clock,new Random(0),logs,closed);
    session.teammateSharing=saved.teammateSharing();
    // Engine construction and its checkpoint owner must be the new worker itself.
    session.worker=new Thread(()->session.runRestored(saved,Map.copyOf(endpoints),awaitingReconnect),"sgs-match-restored-"+saved.roomId());
    session.worker.setDaemon(true);session.worker.start();
    try{session.restoredReady.get(5,java.util.concurrent.TimeUnit.SECONDS);return session;}
    catch(InterruptedException error){Thread.currentThread().interrupt();session.shutdown();throw new IllegalStateException("restore interrupted",error);}
    catch(java.util.concurrent.ExecutionException|java.util.concurrent.TimeoutException error){session.shutdown();throw new IllegalStateException("restore failed",error);}
  }

  private void runRestored(dev.sgs.core.persistence.MatchCheckpoint saved,Map<UUID,MatchEndpoint> endpoints,boolean awaitingReconnect){
    try{
      engine=saved.attack()?SgsGameEngine.restoreAttack(dev.sgs.core.persistence.ContinuationCheckpointCodec.decode(saved.engine()),saved.contentId(),this,clock)
          :saved.trick()?SgsGameEngine.restoreTrick(dev.sgs.core.persistence.TrickCheckpointCodec.decode(saved.engine()),saved.contentId(),this,clock)
          :saved.activeSkill()?SgsGameEngine.restoreActiveSkill(dev.sgs.core.persistence.ActiveSkillCheckpointCodec.decode(saved.engine()),saved.contentId(),this,clock)
          :saved.turnStart()?SgsGameEngine.restoreTurnStart(dev.sgs.core.persistence.TurnStartCheckpointCodec.decode(saved.engine()),saved.contentId(),this,clock)
          :saved.turnEnd()?SgsGameEngine.restoreTurnEnd(dev.sgs.core.persistence.TurnEndCheckpointCodec.decode(saved.engine()),saved.contentId(),this,clock)
          :SgsGameEngine.restoreIdle(dev.sgs.core.persistence.IdleEngineCheckpointCodec.decode(saved.engine()),saved.contentId(),this,clock);
      state=engine.state();
      for(var seat:state.players()){
        var player=(MatchPlayer)seat;boolean previouslyOnline=player.online();player.setOnline(!player.human() || !awaitingReconnect && endpoints.containsKey(player.id()));
        var participant=new Participant(player,endpoints.getOrDefault(player.id(),MatchEndpoint.NOOP),player.human());
        participant.heroId=player.hero().id();
        var control=saved.seatControls().get(player.id());
        if(control!=null){
          participant.control.restore(control,clock.nowMillis());
          if(player.online())participant.control.connection(true,clock.nowMillis());
          else if(awaitingReconnect){if(previouslyOnline)participant.control.awaitConnection();}
          else participant.control.connection(false,clock.nowMillis());
        }else{
          participant.control.restoreUser(saved.userAutoplay().contains(player.id()));
          if(!player.online()){
            if(awaitingReconnect && previouslyOnline)participant.control.awaitConnection();
            else participant.control.connection(false,clock.nowMillis());
          }
        }
        add(participant);
        int floor=saved.nonceFloors().get(player.id());ingressNonces.get(player.id()).set(floor);
        lastNonceByPlayer.put(player.id(),(long)floor);
      }
      // A phase parent already ended the previous action window. Its eventual DISCARD window
      // gets its own budget; the parent's pending invocation restores its separate response budget.
      phaseDeadline=(saved.turnEnd()||saved.turnStart())?0L:clock.nowMillis()+saved.phaseRemainingMillis();
      restoredWindowDeadline=(saved.turnEnd()||saved.turnStart())?null:phaseDeadline;
      installSessionObservers();
      restoredReady.complete(null);
      if(engine.hasAttackContinuation())driveYieldedAttack();
      if(engine.hasTrickContinuation())driveYieldedTrick();
      if(engine.hasActiveSkillContinuation())driveYieldedActiveSkill();
      if(engine.hasTurnStartContinuation())driveYieldedTurnStart();
      if(engine.hasTurnEndContinuation())driveYieldedTurnEnd();
      runMatch();
    }catch(AbortedException ignored){log("restored match {} abandoned",roomId);}
    catch(RuntimeException failure){restoredReady.completeExceptionally(failure);log("restored match {} failed: {}",roomId,failure);}
    finally{restoredReady.completeExceptionally(new IllegalStateException("restored worker stopped"));failDeferredCheckpoints();onClosed.run();}
  }

  private long windowDeadline(int seconds){
    if(restoredWindowDeadline!=null){long deadline=restoredWindowDeadline;restoredWindowDeadline=null;return deadline;}
    return clock.nowMillis()+seconds*1000L;
  }

  private static final class Participant {
    final MatchPlayer player;
    final MatchEndpoint endpoint;
    final boolean human;
    List<String> candidates = List.of();
    String heroId;
    int identityStage,chosenBackSlot=-1;
    boolean openingHandDone;
    long connectionEpoch;
    final SeatControl control=new SeatControl();

    Participant(MatchPlayer player, MatchEndpoint endpoint, boolean human) {
      this.player = player;
      this.endpoint = endpoint;
      this.human = human;
    }
  }

  /** Internal control-flow signal: a human left, the match is abandoned. */
  private static final class AbortedException extends RuntimeException {
    private AbortedException() {
      super(null, null, false, false);
    }
  }

  private MatchSession(
      long roomId,
      MatchConfig config,
      Clock clock,
      Random random,
      Consumer<String> logSink,
      Runnable onClosed) {
    this.roomId = roomId;
    this.config = config;
    this.clock = clock;
    this.random = random;
    this.logSink = logSink;
    this.onClosed = onClosed;
  }

  /**
   * Creates and starts a session. Humans fill the first seats and bots fill the rest. Use
   * startWithSeats when explicit hall slots must be retained.
   */
  public static MatchSession start(
      long roomId,
      List<Human> humans,
      int playerCount,
      MatchConfig config,
      Clock clock,
      Random random,
      Consumer<String> logSink,
      Runnable onClosed) {
    return startWithSeats(roomId, humans, playerCount, config, clock, random, logSink, onClosed, Map.of());
  }

  /** Uses explicit human hall slots and fills the remaining slots with owner-reserved bots. */
  public static MatchSession startWithSeats(long roomId, List<Human> humans, int playerCount,
      MatchConfig config, Clock clock, Random random, Consumer<String> logSink, Runnable onClosed,
      Map<UUID, Integer> hallSeats) {
    return startWithSeats(roomId,humans,playerCount,config,clock,random,logSink,onClosed,hallSeats,Map.of());
  }
  public static MatchSession startWithSeats(long roomId,List<Human> humans,int playerCount,
      MatchConfig config,Clock clock,Random random,Consumer<String> logSink,Runnable onClosed,
      Map<UUID,Integer> hallSeats,Map<UUID,java.util.Set<String>> ownership) {
    if (playerCount < GameState.MIN_PLAYERS || playerCount > GameState.MAX_PLAYERS) {
      throw new IllegalArgumentException("unsupported player count: " + playerCount);
    }
    if (humans.isEmpty() || humans.size() > playerCount) {
      throw new IllegalArgumentException("human count must be 1..playerCount");
    }
    config.validateHeroOffers(playerCount);
    MatchSession session = new MatchSession(roomId, config, clock, random, logSink, onClosed);
    session.ownedHeroPools=ownership.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,e->java.util.Set.copyOf(e.getValue())));
    Map<Integer, Human> bySlot = new HashMap<>();
    for (int index = 0; index < humans.size(); index++) {
      Human human = humans.get(index);
      int slot = hallSeats.isEmpty() ? index : hallSeats.getOrDefault(human.id(), -1);
      if (slot < 0 || slot >= playerCount || bySlot.put(slot, human) != null)
        throw new IllegalArgumentException("invalid or duplicate hall seat");
    }
    for (int slot = 0; slot < playerCount; slot++) {
      Human human = bySlot.get(slot);
      MatchPlayer player = human == null
          ? new MatchPlayer(UUID.randomUUID(), "bot" + slot, slot, false)
          : new MatchPlayer(human.id(), human.name(), slot, true);
      session.add(new Participant(player, human == null ? MatchEndpoint.NOOP : human.endpoint(), human != null));
    }
    if(!ownership.isEmpty()) {
      var pool=config.heroPool().stream().map(HeroDefinition::id).toList();
      var eligible=session.participants.stream().map(p->p.human?session.ownedHeroPools.getOrDefault(p.player.id(),java.util.Set.<String>of()):java.util.Set.copyOf(pool)).toList();
      dev.sgs.core.collection.OwnedHeroSelection.deal(pool,eligible,config.heroCandidateCount(playerCount),new Random(0));
    }
    session.worker = new Thread(session::runSafely, "sgs-match-" + roomId);
    session.worker.setDaemon(true);
    session.worker.start();
    return session;
  }

  private void add(Participant participant) {
    participants.add(participant);
    byId.put(participant.player.id(), participant);
    ingressNonces.put(participant.player.id(),new java.util.concurrent.atomic.AtomicInteger());
  }

  /** Network thread entry point: non-blocking action enqueue. Unknown senders are ignored. */
  public void submit(UUID senderId, MatchAction action) {submit(senderId,action,0);}
  public void submit(UUID senderId,MatchAction action,long epoch){submit(senderId,action,epoch,-1);}
  public void submit(UUID senderId,MatchAction action,long epoch,long controlVersion){
    if(epoch<0)throw new IllegalArgumentException("negative trusted epoch");
    Participant participant=byId.get(senderId);
    if(stopped || participant==null || !participant.human)return;
    var counter=ingressNonces.get(senderId);
    while(true){
      int previous=counter.get();
      if(action.nonce()<=previous){rejectIngress(senderId,action,"REPLAY");return;}
      if(counter.compareAndSet(previous,action.nonce()))break;
    }
    if(action.kind()!=Kind.PLAY_ATTACK_TARGETS && !action.targetSeats().isEmpty()){rejectIngress(senderId,action,"INVALID_CHOICE");return;}
    var pending=pendingInput;
    if(pending!=null && action.kind()!=Kind.LEAVE){
      if(action.kind()!=Kind.PROMPT_RESPONSE && !(action.kind()==Kind.PASS_WUXIE_SCOPE && pending.allowWuxieScopePass())){rejectIngress(senderId,action,RejectReason.WRONG_PHASE.name());return;}
      if(!pending.actor().equals(senderId) || action.promptId()!=pending.promptId()){
        rejectIngress(senderId,action,"INVALID_CHOICE");return;
      }
    }
    if(pending==null && counterspellCollecting && action.kind()!=Kind.LEAVE){
      var own=counterspellInputs.get(senderId);
      if(action.kind()!=Kind.PROMPT_RESPONSE && action.kind()!=Kind.PASS_WUXIE_SCOPE){rejectIngress(senderId,action,RejectReason.WRONG_PHASE.name());return;}
      if(own==null || action.promptId()!=own.id()){rejectIngress(senderId,action,"INVALID_CHOICE");return;}
    }else if(pending==null && !counterspellCollecting && (action.kind()==Kind.PROMPT_RESPONSE || action.kind()==Kind.PASS_WUXIE_SCOPE)){rejectIngress(senderId,action,"INVALID_CHOICE");return;}
    mailbox.submit(senderId, action,epoch,controlVersion);
  }
  private void rejectIngress(UUID sender,MatchAction action,String reason){
    mailbox.submitControl(new MatchMailbox.Rejection(sender,action.nonce(),reason));
  }
  public void setAutoplay(UUID player,boolean enabled,long epoch,long version,int nonce){
    var counter=ingressNonces.get(player);if(stopped || counter==null || !byId.get(player).human)return;
    while(true){int old=counter.get();if(nonce<=old){mailbox.submitControl(new MatchMailbox.Rejection(player,nonce,"REPLAY"));return;}
      if(counter.compareAndSet(old,nonce))break;}
    mailbox.submitControl(new MatchMailbox.AutoControl(player,enabled,epoch,version,nonce));
  }
  private volatile long disconnectGraceMillis=10_000L;
  public void setDisconnectGraceMillis(long value){if(value<0 || value>300_000)throw new IllegalArgumentException("grace");disconnectGraceMillis=value;}
  private boolean automatic(Participant p){return p==null || !p.human || p.control.automatic();}
  private void serviceControls(){mailbox.drainControls(this::handleControl);flushDeferredCheckpoints();if(stopped)throw new AbortedException();expireControls();}
  private void flushDeferredCheckpoints(){
    if(deferredCheckpoints.isEmpty())return;
    boolean boundary=engine==null||engine.checkpointControlBoundary();
    // An unrepresented manual wait still rejects promptly; do not invent a save of its caller.
    boolean manualWait=activePrompt!=null&&!automatic(byId.get(activePrompt.asker().id()));
    if(!boundary&&!manualWait)return;
    while(!deferredCheckpoints.isEmpty())handleControl(deferredCheckpoints.removeFirst());
  }
  private void failDeferredCheckpoints(){
    while(!deferredCheckpoints.isEmpty())deferredCheckpoints.removeFirst().result()
        .completeExceptionally(new IllegalStateException("session stopped before a checkpoint boundary"));
  }
  private void expireControls(){
    boolean changed=false;for(var p:participants)if(p.human)changed|=p.control.expire(clock.nowMillis(),disconnectGraceMillis);
    if(changed){log("autoplay disconnect grace expired");publishControl();}
  }
  private void publishControl(){if(state!=null)broadcast();else if(identityIntroActive)broadcastIdentityIntro();else if(heroBanActive)broadcastHeroBan();else if(selectionDeadline>0)broadcastSelection(selectionDeadline);}
  private java.util.Map<Integer,dev.sgs.core.match.dto.MatchViews.ControlView> controlViews(){
    var result=new HashMap<Integer,dev.sgs.core.match.dto.MatchViews.ControlView>();
    for(var p:participants)result.put(p.player.seat(),new dev.sgs.core.match.dto.MatchViews.ControlView(p.control.source().name(),p.control.version()));return result;
  }
  /** Highest reserved ingress nonce, including queued/rejected attempts; safe for transport readers. */
  public int nonceFloor(UUID player){var counter=ingressNonces.get(player);return counter==null?0:counter.get();}

  public long roomId() {
    return roomId;
  }

  public boolean isParticipant(UUID playerId) {
    return byId.containsKey(playerId);
  }

  /** Marks a human disconnected; bots keep playing and snapshots stop for that seat. */
  public void markOffline(UUID playerId,long epoch){if(!stopped)mailbox.submitControl(new MatchMailbox.Connectivity(playerId,false,epoch));}
  public void markOffline(UUID playerId) {
    connectivity(playerId,false);
  }
  public void markOnline(UUID playerId){markOnline(playerId,0);}
  public void markOnline(UUID playerId,long epoch){
    if(epoch<0)throw new IllegalArgumentException("negative trusted epoch");
    if(!stopped && byId.containsKey(playerId))mailbox.submitControl(new MatchMailbox.Connectivity(playerId,true,epoch));
  }
  private void connectivity(UUID playerId,boolean online){
    if(!stopped && byId.containsKey(playerId))mailbox.submitControl(new MatchMailbox.Connectivity(playerId,online));
  }

  /** An immutable, spectator-filtered publication; no mutable rules are read by the caller. */
  public MatchSnapshot publicSnapshot(){return publicSnapshot;}

  /**
   * Attaches a read-only spectator. Spectators receive the same table view as a player with no
   * seat: all hands and unrevealed identities stay hidden. The worker services this subscription;
   * selection snapshots contain no candidate offers.
   */
  public void addSpectator(MatchEndpoint endpoint) {
    if(!stopped)mailbox.submitControl(new MatchMailbox.Spectator(java.util.Objects.requireNonNull(endpoint),true));
  }

  public void removeSpectator(MatchEndpoint endpoint) {
    if(!stopped)mailbox.submitControl(new MatchMailbox.Spectator(endpoint,false));
  }

  private boolean staleEnvelope(Envelope e){
    var actor=byId.get(e.senderId());
    return actor==null || e.epoch()!=actor.connectionEpoch || e.controlVersion()>=0 && (e.controlVersion()!=actor.control.version() || automatic(actor) && e.action().kind()!=Kind.LEAVE);
  }
  private boolean retiredCounterspell(Envelope e){return (e.action().kind()==Kind.PROMPT_RESPONSE || e.action().kind()==Kind.PASS_WUXIE_SCOPE)
      && e.action().promptId()>0 && e.action().promptId()<=retiredCounterspellThrough;}
  private Envelope waitForAction(java.util.function.Predicate<Envelope> filter,long millis){
    long end=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(Math.max(0,millis));
    while(true){
      flushDeferredCheckpoints();if(stopped)throw new AbortedException();
      // Round a positive fraction up: truncation could close a prompt before its advertised deadline.
      long nanos=end-System.nanoTime();
      long remaining=nanos<=0?0:1+(nanos-1)/1_000_000L;
      expireControls();
      var next=mailbox.poll(e->staleEnvelope(e)||retiredCounterspell(e)||filter.test(e),Math.min(100,remaining),this::handleControl);
      if(next==null)return null;
      if(staleEnvelope(next) || retiredCounterspell(next)){
        var actor=byId.get(next.senderId());if(actor!=null)actor.endpoint.sendError(next.action().nonce(),staleEnvelope(next)?"STALE_CONNECTION":"WINDOW_CLOSED");
        continue;
      }
      return next;
    }
  }
  private void handleControl(MatchMailbox.Control control){
    if(stopped){if(control instanceof MatchMailbox.SaveCheckpoint save)save.result().completeExceptionally(new IllegalStateException("session stopped"));return;}
    if(control instanceof MatchMailbox.SaveCheckpoint save){
      if(engine!=null&&!engine.checkpointControlBoundary()&&activePrompt!=null
          &&automatic(byId.get(activePrompt.asker().id()))){
        deferredCheckpoints.addLast(save);log("SGS_CHECKPOINT_DEFERRED atomic_decision=true asker={}",activePrompt.asker().seat());return;
      }
      try{var saved=captureCheckpoint();if(save.pause())stopped=true;save.result().complete(saved);}
      catch(RuntimeException failure){if(save.pause())stopped=true;save.result().completeExceptionally(failure);}
      finally{if(save.pause())mailbox.wake();}
    }else if(control instanceof MatchMailbox.TeamSharing sharing){
      teammateSharing=sharing.enabled();if(state!=null)broadcast();
    }else if(control instanceof MatchMailbox.Rejection rejected){
      var participant=byId.get(rejected.playerId());
      if(participant!=null)participant.endpoint.sendError(rejected.nonce(),rejected.reason());
      log("ingress rejected seat={} nonce={} reason={}",participant==null?-1:participant.player.seat(),rejected.nonce(),rejected.reason());
    }else if(control instanceof MatchMailbox.AutoControl update){
      var p=byId.get(update.playerId());if(p==null || !p.human)return;
      if(update.epoch()!=p.connectionEpoch || !p.player.online() || state!=null && state.status()!=GameStatus.RUNNING
          || !p.control.request(update.enabled(),update.version())){p.endpoint.sendError(update.nonce(),"CONTROL_CHANGED");publishControl();return;}
      log("autoplay control seat={} source={} version={}",p.player.seat(),p.control.source(),p.control.version());
      mailbox.wake();publishControl();
    }else if(control instanceof MatchMailbox.Connectivity update){
      Participant participant=byId.get(update.playerId());
      if(participant==null || !participant.human)return;
      if(update.epoch()>=0){if(update.epoch()<participant.connectionEpoch)return;participant.connectionEpoch=update.epoch();}
      participant.player.setOnline(update.online());
      participant.control.connection(update.online(),clock.nowMillis());mailbox.wake();
      log("connection state seat={} online={}",participant.player.seat(),update.online());
      if(state==null){if(identityIntroActive)broadcastIdentityIntro();else if(heroBanActive)broadcastHeroBan();else broadcastSelection(selectionDeadline);}else broadcast();
    }else if(control instanceof MatchMailbox.Spectator observer){
      if(observer.joined()){
        if(!spectators.contains(observer.endpoint()))spectators.add(observer.endpoint());
        if(publicSnapshot!=null)safeSend(observer.endpoint(),publicSnapshot);
      }else spectators.remove(observer.endpoint());
    }
  }

  /** Stops the worker and waits briefly for it to exit. */
  public void shutdown() {
    stopped = true;
    mailbox.wake();
    Thread thread = worker;
    if (thread != null) {
      thread.interrupt();
      try {
        thread.join(2000L);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void runSafely() {
    try {
      determineSeats();
      if(config.identityIntroduction(participants.size()))runIdentityIntroduction();
      if(!stopped && config.heroBansEnabled() && config.identityIntroduction(participants.size()))runHeroBan();
      if(!stopped)runHeroSelection();
      if (!stopped) {
        seatAndBegin();
        if(!stopped)runMatch();
      }
    } catch (AbortedException e) {
      log("match {} abandoned by participant", roomId);
    } catch (RuntimeException e) {
      log("match {} worker failed: {}", roomId, e);
    } finally {
      failDeferredCheckpoints();
      onClosed.run();
    }
  }

  // ---------------------------------------------------------------- hero selection

  private void runIdentityIntroduction(){
    identityIntroActive=true;identityIntroDeadline=clock.nowMillis()+config.identityIntroductionSeconds()*1000L;
    for(var participant:participants)if(!participant.human)autoDrawIdentity(participant);
    broadcastIdentityIntro();
    while(!stopped && participants.stream().anyMatch(p->p.human&&p.identityStage<2)){
      serviceControls();for(var p:participants)if(automatic(p))autoDrawIdentity(p);
      if(participants.stream().noneMatch(p->p.human&&p.identityStage<2)){broadcastIdentityIntro();break;}
      long remaining=identityIntroDeadline-clock.nowMillis();if(remaining<=0)break;
      var envelope=waitForAction(e->true,remaining);
      if(envelope==null){if(clock.nowMillis()<identityIntroDeadline)continue;break;}
      var sender=byId.get(envelope.senderId());var action=envelope.action();
      if(action.kind()==Kind.LEAVE)abort();if(sender==null || isReplay(sender,action))continue;
      if(clock.nowMillis()>=identityIntroDeadline)break;
      String rejection=null;
      if(action.kind()==Kind.REVEAL_IDENTITY){
        if(sender.identityStage!=0)rejection="IDENTITY_ALREADY_REVEALED";
        else if(action.targetSeat()<0 || action.targetSeat()>=participants.size())rejection="INVALID_IDENTITY_SLOT";
        else{drawIdentity(sender);sender.chosenBackSlot=action.targetSeat();sender.identityStage=1;}
      }else if(action.kind()==Kind.CONFIRM_IDENTITY){
        if(sender.identityStage==0)rejection="IDENTITY_NOT_REVEALED";
        else if(sender.identityStage==2)rejection="IDENTITY_ALREADY_CONFIRMED";
        else if(action.targetSeat()!=MatchAction.NO_SEAT)rejection="INVALID_IDENTITY_SLOT";
        else sender.identityStage=2;
      }else rejection="IDENTITY_NOT_READY";
      if(rejection!=null)sender.endpoint.sendError(action.nonce(),rejection);
      broadcastIdentityIntro();
    }
    if(!stopped){
      for(var participant:participants)autoDrawIdentity(participant);
      broadcastIdentityIntro();
      // Identities only become fixed on draw; anchor the final table after everyone has drawn.
      rotateLordToFirstSeat();
    }
    identityIntroActive=false;
  }
  private void drawIdentity(Participant participant){
    if(participant.player.identity()!=null)return;
    participant.player.assignIdentity(undrawnIdentities.remove(random.nextInt(undrawnIdentities.size())));
    participant.player.setIdentityPublic(participant.player.identity()==Identity.LORD);
  }
  private void autoDrawIdentity(Participant participant){
    if(participant.identityStage>=2)return;
    drawIdentity(participant);
    participant.identityStage=2;
  }
  private void broadcastIdentityIntro(){
    var seats=participants.stream().map(p->(dev.sgs.core.player.SgsPlayer)p.player).toList();
    publicSnapshot=ViewFilter.identityIntro(seats,-1,0,identityIntroDeadline,-1);
    for(var spectator:List.copyOf(spectators))safeSend(spectator,publicSnapshot);
    for(var participant:participants)if(participant.human)safeSend(participant.endpoint,
        ViewFilter.identityIntro(seats,participant.player.seat(),participant.identityStage,identityIntroDeadline,participant.chosenBackSlot));
  }

  private void runHeroBan(){
    var pool=config.heroPool().stream().map(HeroDefinition::id).toList();
    var eligible=participants.stream().map(p->p.human && !ownedHeroPools.isEmpty()?ownedHeroPools.getOrDefault(p.player.id(),java.util.Set.<String>of()):java.util.Set.copyOf(pool)).toList();
    // Preserve existing limited-collection rooms: no ban can reduce their available selection.
    try{heroBanRound=new HeroBanRound(pool,eligible,config.heroCandidateCount(participants.size()),random);}
    catch(dev.sgs.core.collection.OwnedHeroSelection.Unavailable insufficient){log("hero ban skipped: insufficient owned reserve");return;}
    if(heroBanRound.candidates().isEmpty()){heroBanRound=null;return;}
    heroBanActive=true;heroBanStage=0;heroBanDeadline=clock.nowMillis()+Math.min(30,Math.max(1,config.selectionTimeoutSeconds()))*1000L;
    for(var p:participants)if(!p.human)heroBanRound.autoChoose(p.player.seat(),random);
    broadcastHeroBan();
    while(!stopped && !heroBanRound.complete()){
      serviceControls();for(var p:participants)if(automatic(p))heroBanRound.autoChoose(p.player.seat(),random);
      if(heroBanRound.complete())break;
      long remaining=heroBanDeadline-clock.nowMillis();if(remaining<=0)break;
      var envelope=waitForAction(e->true,remaining);if(envelope==null)continue;
      var sender=byId.get(envelope.senderId());var action=envelope.action();
      if(action.kind()==Kind.LEAVE)abort();if(sender==null || isReplay(sender,action))continue;
      if(clock.nowMillis()>=heroBanDeadline){sender.endpoint.sendError(action.nonce(),"BAN_TIMEOUT");break;}
      String error=action.kind()!=Kind.BAN_HERO?"BAN_NOT_READY":heroBanRound.choose(sender.player.seat(),action.heroId());
      if(error!=null)sender.endpoint.sendError(action.nonce(),error);broadcastHeroBan();
    }
    if(stopped)return;
    for(var p:participants)heroBanRound.autoChoose(p.player.seat(),random);
    heroBanStage=1;heroBanDeadline=clock.nowMillis()+1500;broadcastHeroBan();
    while(!stopped && clock.nowMillis()<heroBanDeadline){
      var envelope=waitForAction(e->true,heroBanDeadline-clock.nowMillis());if(envelope==null)continue;
      if(envelope.action().kind()==Kind.LEAVE)abort();
      var sender=byId.get(envelope.senderId());if(sender!=null && !isReplay(sender,envelope.action()))sender.endpoint.sendError(envelope.action().nonce(),"BAN_FINISHED");
    }
    heroBanActive=false;
  }
  private void broadcastHeroBan(){
    var seats=participants.stream().map(p->(dev.sgs.core.player.SgsPlayer)p.player).toList();
    var choices=heroBanRound.choices().entrySet().stream().sorted(Map.Entry.comparingByKey())
        .map(e->new dev.sgs.core.match.dto.MatchViews.HeroBanChoice(e.getKey(),e.getValue())).toList();
    var resultIds=choices.stream().map(dev.sgs.core.match.dto.MatchViews.HeroBanChoice::heroId).toList();
    publicSnapshot=ViewFilter.selecting(seats,false,-1,List.of(),0).withHeroBan(new dev.sgs.core.match.dto.MatchViews.HeroBanView(heroBanStage,heroBanDeadline,List.of(),choices))
        .withHeroPresentations(dev.sgs.core.addon.AddonRuntime.current().present(resultIds));
    for(var spectator:List.copyOf(spectators))safeSend(spectator,publicSnapshot);
    for(var p:participants)if(p.human)safeSend(p.endpoint,ViewFilter.selecting(seats,false,p.player.seat(),List.of(),0)
        .withHeroBan(new dev.sgs.core.match.dto.MatchViews.HeroBanView(heroBanStage,heroBanDeadline,heroBanRound.candidates(),choices))
        .withHeroPresentations(dev.sgs.core.addon.AddonRuntime.current().present(heroBanRound.candidates())));
  }

  private void runHeroSelection() {
    List<String> pool =
        config.heroPool().stream().map(HeroDefinition::id).collect(Collectors.toList());
    long deadline = clock.nowMillis() + config.selectionTimeoutSeconds() * 1000L;
    selectionDeadline=deadline;
    var offers=heroBanRound!=null?heroBanRound.offers(random):config.uniqueHeroSelection(participants.size())
        ?HeroSelection.dealCandidates(pool,participants.size(),config.heroCandidateCount(participants.size()),random):null;
    if(heroBanRound==null && !ownedHeroPools.isEmpty())offers=dev.sgs.core.collection.OwnedHeroSelection.deal(pool,
        participants.stream().map(p->p.human?ownedHeroPools.getOrDefault(p.player.id(),java.util.Set.<String>of()):java.util.Set.copyOf(pool)).toList(),
        config.heroCandidateCount(participants.size()),random);
    for(int seat=0;seat<participants.size();seat++){
      var participant=participants.get(seat);
      participant.candidates=offers==null?HeroSelection.randomCandidates(pool,config.heroCandidateCount(participants.size()),random):offers.get(seat);
    }
    for (Participant participant : participants) {
      if (!participant.human && (!lordFirstSelection() || participant.player.seat()==0)) {
        chooseHero(participant,HeroSelection.timeoutPick(participant.candidates, random));
      }
    }
    boolean waitingLord=lordChoicePending();
    broadcastSelection(deadline);

    while (!stopped && humansPending() > 0) {
      serviceControls();
      for(var p:participants)if(p.heroId==null && automatic(p) && (!waitingLord || p.player.seat()==0))chooseHero(p,HeroSelection.timeoutPick(p.candidates,random));
      if(waitingLord && !lordChoicePending()){
        waitingLord=false;deadline=clock.nowMillis()+config.selectionTimeoutSeconds()*1000L;selectionDeadline=deadline;
        for(var p:participants)if(p.heroId==null&&automatic(p))chooseHero(p,HeroSelection.timeoutPick(p.candidates,random));
        broadcastSelection(deadline);
      }
      if(humansPending()==0){broadcastSelection(deadline);break;}
      long remaining = deadline - clock.nowMillis();
      if (remaining <= 0L) {
        if(waitingLord){var lord=participants.getFirst();chooseHero(lord,HeroSelection.timeoutPick(lord.candidates,random));continue;}
        break;
      }
      Envelope envelope =
          waitForAction(
              e -> {
                if (e.action().kind() == Kind.LEAVE) {
                  return true;
                }
                Participant sender = byId.get(e.senderId());
                return sender != null
                    && sender.human
                    && (e.action().kind()==Kind.BAN_HERO || e.action().kind()==Kind.REVEAL_IDENTITY || e.action().kind()==Kind.CONFIRM_IDENTITY
                        || sender.heroId==null && e.action().kind()==Kind.SELECT_HERO);
              },
              remaining);
      if (envelope == null) {
        if(clock.nowMillis()<deadline || waitingLord)continue;break;
      }
      Participant sender = byId.get(envelope.senderId());
      MatchAction action = envelope.action();
      if (action.kind() == Kind.LEAVE) {
        abort();
      }
      if (isReplay(sender, action)) {
        continue;
      }
      if(clock.nowMillis()>=deadline)continue;
      if(action.kind()!=Kind.SELECT_HERO){sender.endpoint.sendError(action.nonce(),action.kind()==Kind.BAN_HERO?"BAN_FINISHED":"IDENTITY_INTRO_FINISHED");broadcastSelection(deadline);continue;}
      if(waitingLord && sender.player.seat()!=0){sender.endpoint.sendError(action.nonce(),"WAIT_FOR_LORD");continue;}
      if(config.uniqueHeroSelection(participants.size()) && selectedHeroIds().contains(action.heroId())){
        sender.endpoint.sendError(action.nonce(),"HERO_TAKEN");broadcastSelection(deadline);continue;
      }
      if (!sender.candidates.contains(action.heroId())) {
        sender.endpoint.sendError(action.nonce(), "INVALID_HERO");
        continue;
      }
      chooseHero(sender,action.heroId());
      if(!waitingLord)broadcastSelection(deadline);
    }
    // Timeout fallback for humans who never chose.
    for (Participant participant : participants) {
      if (participant.heroId == null) {
        chooseHero(participant,HeroSelection.timeoutPick(participant.candidates, random));
      }
    }
    broadcastSelection(deadline);
  }

  private java.util.Set<String> selectedHeroIds(){
    return participants.stream().map(p->p.heroId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
  }
  private void chooseHero(Participant participant,String id){
    if(config.uniqueHeroSelection(participants.size()) && selectedHeroIds().contains(id))throw new IllegalStateException("hero already selected");
    var definition=config.heroPool().stream().filter(hero->hero.id().equals(id)).findFirst()
        .orElseThrow(()->new IllegalArgumentException("unknown hero choice: "+id));
    participant.heroId=id;participant.player.assignHero(definition);
  }

  private int humansPending() {
    int pending = 0;
    for (Participant participant : participants) {
      if (participant.human && participant.heroId == null) {
        pending++;
      }
    }
    return pending;
  }

  private boolean lordFirstSelection(){return config.heroBansEnabled()&&config.identityIntroduction(participants.size());}
  private boolean lordChoicePending(){return lordFirstSelection()&&participants.getFirst().heroId==null;}
  private void broadcastSelection(long deadline) {
    var seats=participants.stream().map(p->(dev.sgs.core.player.SgsPlayer)p.player).toList();
    boolean teamMode=participants.size()==4;
    publicSnapshot=ViewFilter.selecting(seats,teamMode,dev.sgs.core.match.dto.MatchViews.HIDDEN,List.of(),deadline,lordFirstSelection());
    for(var spectator:List.copyOf(spectators))safeSend(spectator,publicSnapshot);
    for (Participant participant : participants) {
      if (participant.human) {
        safeSend(
            participant.endpoint,
            ViewFilter.selecting(seats,teamMode,participant.player.seat(),lordChoicePending()&&participant.player.seat()!=0?List.of():participant.candidates,deadline,lordFirstSelection()));
      }
    }
  }

  // ---------------------------------------------------------------- seating / start

  private void determineSeats() {
    // Shuffle provisional seats. Identity-intro rooms draw roles only on reveal or timeout.
    List<Participant> shuffled = new ArrayList<>(participants);
    if (!config.roomSettings().fixedSeats()) Collections.shuffle(shuffled, random);
    List<dev.sgs.core.player.SgsPlayer> dealt = new ArrayList<>(shuffled.size());
    for (Participant participant : shuffled) {
      dealt.add(participant.player);
    }
    if(config.identityIntroduction(dealt.size())){
      undrawnIdentities.addAll(IdentityTable.forPlayerCount(dealt.size()));
      if(config.roomSettings().doubleRenegade())
        undrawnIdentities.set(undrawnIdentities.indexOf(Identity.REBEL),Identity.RENEGADE);
      participants.clear();participants.addAll(shuffled);
      for(int seat=0;seat<participants.size();seat++)participants.get(seat).player.setSeat(seat);
      return;
    }
    if(dealt.size()!=4)IdentityTable.assign(dealt, random, config.roomSettings().doubleRenegade());
    else for(var player:dealt)player.assignIdentity(Identity.LOYALIST);
    int lordIndex = 0;
    for (int i = 0; i < dealt.size(); i++) {
      if (dealt.size()!=4 && dealt.get(i).identity() == Identity.LORD) {
        lordIndex = i;
        break;
      }
    }
    Collections.rotate(shuffled, -lordIndex);
    participants.clear();participants.addAll(shuffled);
    for(int seat=0;seat<participants.size();seat++){
      var player=participants.get(seat).player;player.setSeat(seat);
      player.setIdentityPublic(participants.size()!=4 && player.identity()==Identity.LORD);
    }
  }

  private void rotateLordToFirstSeat(){
    int lordIndex=0;
    for(int i=0;i<participants.size();i++)if(participants.get(i).player.identity()==Identity.LORD){lordIndex=i;break;}
    Collections.rotate(participants,-lordIndex);
    for(int seat=0;seat<participants.size();seat++)participants.get(seat).player.setSeat(seat);
  }

  private void seatAndBegin() {
    Map<String, HeroDefinition> definitions = new HashMap<>();
    for (HeroDefinition hero : config.heroPool()) {
      definitions.put(hero.id(), hero);
    }
    List<dev.sgs.core.player.SgsPlayer> players = new ArrayList<>(participants.size());
    List<HeroDefinition> heroesBySeat = new ArrayList<>(participants.size());
    for (int seat = 0; seat < participants.size(); seat++) {
      Participant participant = participants.get(seat);
      players.add(participant.player);
      HeroDefinition hero = definitions.get(participant.heroId);
      if (hero == null) {
        throw new IllegalStateException("unknown hero pick: " + participant.heroId);
      }
      heroesBySeat.add(hero);
    }
    Deck deck =
        new Deck(config.contentPool().create(new IdSequencer()), new Random(random.nextLong()));
    state = players.size()==4?GameState.startTwoVTwo(players,heroesBySeat,deck,random):GameState.startAssigned(players, heroesBySeat, deck, random);
    if(config.openingHandSelection(players.size()))runOpeningHandSelection();
    if(stopped)return;
    engine = new SgsGameEngine(state, this, clock, config.responseTimeoutSeconds());
    installSessionObservers();
  }

  private void runOpeningHandSelection(){
    openingHandActive=true;openingHandDeadline=clock.nowMillis()+config.openingHandSelectionSeconds()*1000L;
    for(var p:participants)p.openingHandDone=!p.human;
    broadcast();
    try{
      while(!stopped && participants.stream().anyMatch(p->!p.openingHandDone)){
        serviceControls();for(var p:participants)if(automatic(p))p.openingHandDone=true;
        if(participants.stream().allMatch(p->p.openingHandDone)){broadcast();break;}
        long remaining=openingHandDeadline-clock.nowMillis();if(remaining<=0)break;
        var envelope=waitForAction(e->true,remaining);
        if(envelope==null){if(clock.nowMillis()<openingHandDeadline)continue;break;}
        var sender=byId.get(envelope.senderId());var action=envelope.action();
        if(action.kind()==Kind.LEAVE)abort();if(sender==null || isReplay(sender,action))continue;
        String error=null;
        if(action.kind()!=Kind.REPLACE_OPENING_HAND && action.kind()!=Kind.KEEP_OPENING_HAND)error="OPENING_HAND_NOT_READY";
        else if(sender.openingHandDone)error="OPENING_HAND_ALREADY_DECIDED";
        else if(action.promptId()!=0 || action.accept() || action.cardId()!=MatchAction.NO_CARD || action.declaredTypeOrdinal()!=-1
            || action.targetSeat()!=MatchAction.NO_SEAT || action.secondarySeat()!=MatchAction.NO_SEAT
            || !action.cardIds().isEmpty() || action.skillId()!=null || action.heroId()!=null)error="INVALID_CHOICE";
        else if(clock.nowMillis()>=openingHandDeadline)break;
        else{
          if(action.kind()==Kind.REPLACE_OPENING_HAND){
            if(state.deck().drawPileCount()<sender.player.handCount() || sender.player.handCount()==0)error="OPENING_HAND_UNAVAILABLE";
            else state.cards().replaceOpeningHand(sender.player);
          }
          if(error==null){sender.openingHandDone=true;log("match {} opening hand accepted: seat={} action={} count={} nonce={}",roomId,sender.player.seat(),action.kind(),sender.player.handCount(),action.nonce());}
        }
        if(error!=null)sender.endpoint.sendError(action.nonce(),error);
        broadcast();
      }
      for(var p:participants)p.openingHandDone=true;
    }finally{openingHandActive=false;}
  }

  private java.util.Set<Long> physicalIds;
  private long lastConservationAudit=-1;
  private void installSessionObservers(){
    physicalIds=state.cards().zones().stream().flatMap(zone->state.cards().cards(zone).stream())
        .map(CardInstance::instanceId).collect(java.util.stream.Collectors.toUnmodifiableSet());
    if (logSink != null) {
      state.addDiagnosticLogSink(logSink);
    }
    engine.addPresentationListener(
        new GameEventListener() {
          @Override
          public void onJudgmentFinalized(dev.sgs.core.player.SgsPlayer judge, CardInstance card) {
            onJudgmentRevealed(judge,card);
          }
          public void onJudgmentRevealed(dev.sgs.core.player.SgsPlayer judge, CardInstance card) {
            lastJudgment = new JudgmentView(judge.seat(), CardView.of(card));
          }
        });
  }

  // ---------------------------------------------------------------- match loop

  private void runMatch() {
    if(restoredWindowDeadline==null)phaseDeadline = 0L;
    broadcast();
    while (!stopped && state.status() == GameStatus.RUNNING) {
      dev.sgs.core.player.SgsPlayer current = state.currentPlayer();
      if (state.phase() == Phase.PREPARE) {
        restoredWindowDeadline=null;
        phaseDeadline = 0L;
        if(engine.supportsTurnStartContinuation()){
          var result=engine.beginResumableOpenTurn(current);if(!result.accepted())throw new IllegalStateException("turn start rejected: "+result.reason());driveYieldedTurnStart();
        }else engine.openTurn(current);
        broadcast();
        continue;
      }
      Participant participant = byId.get(current.id());
      // Judgment damage (lightning) can kill the seat before its window opens.
      if (!current.alive()) {
        restoredWindowDeadline=null;
        phaseDeadline = 0L;
        dispatchTimeoutTurn(current);
        broadcast();
        continue;
      }
      if (state.phase() == Phase.PLAY) {
        if (participant.human) {
          humanPlayWindow(current);
        } else {
          botPlayWindow(current);
        }
      } else if (state.phase() == Phase.DISCARD) {
        if (participant.human) {
          humanDiscardWindow(current);
        } else {
          dispatchAutoDiscard(current,false);
          phaseDeadline = 0L;
          broadcast();
        }
      } else {
        throw new IllegalStateException("unexpected parked phase: " + state.phase());
      }
    }
    phaseDeadline = 0L;
    activePrompt = null;
    broadcast();
    if (!stopped) {
      // Keep the result screen reachable until a client leaves or the linger expires.
      long end=clock.nowMillis()+FINISH_LINGER_MILLIS;
      while(!stopped && clock.nowMillis()<end)if(waitForAction(e -> e.action().kind() == Kind.LEAVE,end-clock.nowMillis())!=null)break;
    }
  }

  private void humanPlayWindow(dev.sgs.core.player.SgsPlayer current) {
    long deadline = windowDeadline(config.turnTimeoutSeconds());
    phaseDeadline = deadline;
    broadcast();
    while (!stopped
        && state.status() == GameStatus.RUNNING
        && state.phase() == Phase.PLAY
        && state.currentPlayer() == current) {
      serviceControls();
      if(automatic(byId.get(current.id())) && clock.nowMillis()<deadline){
        runAutoPlayStep(current);waitForAction(e->false,Math.max(1,config.botStepDelayMillis()));continue;
      }
      Envelope envelope =
          waitForAction(
              e -> e.action().kind() == Kind.LEAVE || e.senderId().equals(current.id()),
              deadline - clock.nowMillis());
      if (envelope == null) {
        if (clock.nowMillis() < deadline) {
          continue;
        }
        phaseDeadline = 0L;
        dispatchTimeoutTurn(current);
        broadcast();
        return;
      }
      MatchAction action = envelope.action();
      if (action.kind() == Kind.LEAVE) {
        abort();
      }
      Participant sender = byId.get(envelope.senderId());
      if (isReplay(sender, action)) {
        continue;
      }
      if (!envelope.senderId().equals(current.id())) {
        sender.endpoint.sendError(action.nonce(), RejectReason.NOT_YOUR_TURN.name());
        continue;
      }
      ActionResult result = dispatchPlay(current, action);
      if (!result.accepted()) {
        sender.endpoint.sendError(action.nonce(), result.reason().name());
      }
      broadcast();
    }
    phaseDeadline = 0L;
  }

  private ActionResult dispatchPlay(dev.sgs.core.player.SgsPlayer current, MatchAction action) {
    // Only -1 means "no target". Malformed seats must never silently become Iron Chain recast.
    if(action.targetSeat()<MatchAction.NO_SEAT || action.targetSeat()>=state.players().size()
        || action.secondarySeat()<MatchAction.NO_SEAT || action.secondarySeat()>=state.players().size())
      return ActionResult.rejected(RejectReason.TARGET_NOT_IN_GAME);
    return switch (action.kind()) {
      case END_PLAY -> dispatchEndPlay(current);
      case PLAY_ATTACK_TARGETS -> {
        if(action.targetSeat()!=-1 || action.secondarySeat()!=-1 || action.promptId()!=0 || !action.accept()
            || !action.cardIds().isEmpty() || action.skillId()!=null || action.heroId()!=null || action.targetSeats().isEmpty()
            || action.targetSeats().size()>3 || action.targetSeats().stream().anyMatch(seat->seat<0 || seat>=state.players().size())
            || action.declaredTypeOrdinal() < -1 || action.declaredTypeOrdinal()>=CardType.values().length)
          yield ActionResult.rejected(RejectReason.TARGET_NOT_IN_GAME);
        var targets=action.targetSeats().stream().map(this::resolveSeat).toList();
        var declared=action.declaredTypeOrdinal()==-1?null:CardType.values()[action.declaredTypeOrdinal()];
        var result=engine.supportsAttackContinuation()?engine.beginResumableAttackTargets(current,action.cardId(),declared,targets):engine.playAttackTargets(current,action.cardId(),declared,targets);
        if(result.accepted() && engine.hasAttackContinuation())driveYieldedAttack();
        yield result;
      }
      case PLAY_CARD -> dispatchCard(current,action,null);
      case PLAY_CARD_AS -> {
        int ordinal = action.declaredTypeOrdinal();
        if (ordinal < 0 || ordinal >= CardType.values().length) {
          yield ActionResult.rejected(RejectReason.UNKNOWN_CARD);
        }
        yield dispatchCard(current,action,CardType.values()[ordinal]);
      }
      case ACTIVE_SKILL -> dispatchActiveSkill(current,action.skillId(),resolveSeat(action.targetSeat()),action.cardIds());
      default -> ActionResult.rejected(RejectReason.WRONG_PHASE);
    };
  }

  private ActionResult dispatchEndPlay(dev.sgs.core.player.SgsPlayer actor){
    if(!engine.supportsTurnEndContinuation())return engine.endPlayPhase(actor);
    var result=engine.beginResumableEndPlay(actor);if(result.accepted()){phaseDeadline=0;driveYieldedTurnEnd();}return result;
  }
  private ActionResult dispatchDiscard(dev.sgs.core.player.SgsPlayer actor,List<Long> ids,boolean automatic){
    if(!engine.supportsTurnEndContinuation())return automatic?engine.timeoutTurn(actor):engine.confirmDiscard(actor,ids);
    var result=automatic?engine.beginResumableTimeoutDiscard(actor,ids):engine.beginResumableDiscard(actor,ids);
    if(result.accepted()){phaseDeadline=0;driveYieldedTurnEnd();}return result;
  }
  private void dispatchAutoDiscard(dev.sgs.core.player.SgsPlayer actor,boolean timeout){
    int excess=Math.max(0,actor.handCount()-state.handLimit(actor));
    var result=dispatchDiscard(actor,actor.handView().stream().limit(excess).map(CardInstance::instanceId).toList(),timeout);
    if(!result.accepted())throw new IllegalStateException("automatic discard rejected: "+result.reason());
  }
  private void dispatchTimeoutTurn(dev.sgs.core.player.SgsPlayer actor){
    if(!engine.supportsTurnEndContinuation()){engine.timeoutTurn(actor);return;}
    if(state.phase()==Phase.PLAY)dispatchEndPlay(actor);
    if(!stopped&&state.currentPlayer()==actor&&state.phase()==Phase.DISCARD)dispatchAutoDiscard(actor,true);
  }
  private void driveYieldedTurnStart(){
    while(!stopped&&engine.hasTurnStartContinuation()){
      engine.pollResumableTurnStart();if(!engine.hasTurnStartContinuation())break;
      var prompt=engine.pendingTurnStartPrompt().orElseThrow();long revision=engine.turnStartDecisionRevision();
      log("SGS_RESUMABLE_TURN_START_PATH pending={} skill={} engine_caller_released=true",prompt.type(),prompt.skillId());
      ActionResult result;
      if(prompt.type()==dev.sgs.core.engine.PromptType.SKILL_INVOKE){boolean answer=invoke(state,prompt);if(stopped)return;result=engine.resumeTurnStartInvoke(prompt.asker().id(),prompt.id(),revision,answer);}
      else{var answer=choose(state,prompt);if(stopped)return;result=engine.resumeTurnStartChoice(prompt.asker().id(),prompt.id(),revision,answer);}
      if(!result.accepted())throw new IllegalStateException("validated draw input rejected: "+result.reason());
    }
  }
  private void driveYieldedTurnEnd(){
    while(!stopped&&engine.hasTurnEndContinuation()){
      engine.pollResumableTurnEnd();if(!engine.hasTurnEndContinuation())break;
      var prompt=engine.pendingTurnEndPrompt().orElseThrow();long revision=engine.turnEndDecisionRevision();
      log("SGS_RESUMABLE_TURN_END_PATH pending={} skill={} engine_caller_released=true",prompt.type(),prompt.skillId());
      boolean answer=invoke(state,prompt);if(stopped)return;
      var result=engine.resumeTurnEndInvoke(prompt.asker().id(),prompt.id(),revision,answer);
      if(!result.accepted())throw new IllegalStateException("validated phase input rejected: "+result.reason());
    }
  }
  private ActionResult dispatchActiveSkill(dev.sgs.core.player.SgsPlayer actor,String skillId,
      dev.sgs.core.player.SgsPlayer target,List<Long> costs){
    if(!engine.supportsActiveSkillContinuation(actor,skillId))return engine.useActiveSkill(actor,skillId,target,costs);
    var result=engine.beginResumableActiveSkill(actor,skillId,target,costs);
    if(result.accepted())driveYieldedActiveSkill();return result;
  }
  private boolean reportedYieldedActiveSkill;
  private void driveYieldedActiveSkill(){
    while(!stopped&&engine.hasActiveSkillContinuation()){
      engine.pollResumableActiveSkill();if(!engine.hasActiveSkillContinuation())break;
      var prompt=engine.pendingActiveSkillPrompt().orElseThrow();long revision=engine.activeSkillDecisionRevision();
      if(!reportedYieldedActiveSkill){reportedYieldedActiveSkill=true;log("SGS_RESUMABLE_ACTIVE_SKILL_PATH engine_caller_released=true");}
      ActionResult result;
      if(prompt.type()==dev.sgs.core.engine.PromptType.SKILL_INVOKE){
        boolean answer=invoke(state,prompt);if(stopped)return;
        result=engine.resumeActiveSkillInvoke(prompt.asker().id(),prompt.id(),revision,answer);
      }else if(prompt.type()==dev.sgs.core.engine.PromptType.FANJIAN_SUIT){
        var answer=chooseSuit(state,prompt);if(stopped)return;
        result=engine.resumeActiveSkillSuit(prompt.asker().id(),prompt.id(),revision,answer);
      }else if(prompt.type().isContinuationChoice()){
        var answer=choose(state,prompt);if(stopped)return;
        result=engine.resumeActiveSkillChoice(prompt.asker().id(),prompt.id(),revision,answer);
      }else{
        var answer=respond(state,prompt);if(stopped)return;
        result=engine.resumeActiveSkillResponse(prompt.asker().id(),prompt.id(),revision,answer);
      }
      if(!result.accepted())throw new IllegalStateException("validated active skill input was rejected: "+result.reason());
    }
  }

  /** Transport adapter around yielded rules, with no engine Java caller waiting on a response. */
  private ActionResult dispatchCard(dev.sgs.core.player.SgsPlayer current,MatchAction action,CardType declared){
    var physical=current.handView().stream().filter(c->c.instanceId()==action.cardId()).findFirst().orElse(null);
    if(physical==null)physical=current.equipment().all().stream().filter(c->c.instanceId()==action.cardId()).findFirst().orElse(null);
    var type=declared==null && physical!=null?physical.type():declared;
    if(action.secondarySeat()==-1 && type!=null && (type==CardType.SHA || type==CardType.HUOSHA || type==CardType.LEISHA)
        && engine.supportsAttackContinuation()){
      var result=engine.beginResumableAttack(current,action.cardId(),declared,resolveSeat(action.targetSeat()));
      if(result.accepted())driveYieldedAttack();
      return result;
    }
    if((action.secondarySeat()==-1 || type==CardType.TIESUO || type==CardType.JIEDAO) && (type!=CardType.TIESUO || action.targetSeat()>=0) && SgsGameEngine.resumableTrickType(type) && engine.supportsTrickContinuation(type)){
      var result=engine.beginResumableTrick(current,action.cardId(),declared,resolveSeat(action.targetSeat()),resolveSeat(action.secondarySeat()));
      if(result.accepted())driveYieldedTrick();return result;
    }
    return engine.playCardAs(current,action.cardId(),declared,resolveSeat(action.targetSeat()),resolveSeat(action.secondarySeat()));
  }
  private boolean reportedYieldedTrick;
  private void driveYieldedTrick(){
    while(!stopped && engine.hasTrickContinuation()){
      engine.pollResumableTrick();if(!engine.hasTrickContinuation())break;
      var window=engine.pendingTrickWindow().orElse(null);long revision=engine.trickDecisionRevision();
      if(!reportedYieldedTrick){reportedYieldedTrick=true;log("SGS_RESUMABLE_TRICK_PATH engine_caller_released=true");}
      if(window!=null){var result=collectCounterspell(state,window);if(stopped)return;engine.resumeTrickWindow(window.windowId(),revision,result);continue;}
      var prompt=engine.pendingTrickPrompt().orElseThrow();
      ActionResult result;
      if(prompt.type()==dev.sgs.core.engine.PromptType.SKILL_INVOKE){var accepted=invoke(state,prompt);if(stopped)return;result=engine.resumeTrickInvoke(prompt.asker().id(),prompt.id(),revision,accepted);}
      else if(java.util.Set.of(dev.sgs.core.engine.PromptType.WEAPON_DISCARD,dev.sgs.core.engine.PromptType.WEAPON_COST_FIRST,dev.sgs.core.engine.PromptType.WEAPON_COST_SECOND,dev.sgs.core.engine.PromptType.CARD_ZONE_SELECT,dev.sgs.core.engine.PromptType.YIJI_GIVE,dev.sgs.core.engine.PromptType.FANKUI_TAKE,dev.sgs.core.engine.PromptType.GUI_CAI_REPLACE,dev.sgs.core.engine.PromptType.GANGLIE_DISCARD,dev.sgs.core.engine.PromptType.LIULI_TARGET).contains(prompt.type())){var answer=choose(state,prompt);if(stopped)return;result=engine.resumeTrickChoice(prompt.asker().id(),prompt.id(),revision,answer);}
      else{var answer=respond(state,prompt);if(stopped)return;result=engine.resumeTrickResponse(prompt.asker().id(),prompt.id(),revision,answer);}
      if(!result.accepted())throw new IllegalStateException("validated trick input was rejected: "+result.reason());
    }
  }
  private boolean reportedYieldedAttack;
  private void driveYieldedAttack(){
    while(!stopped && engine.hasAttackContinuation()){
      engine.pollResumableAttack();
      var prompt=engine.pendingAttackPrompt().orElse(null);if(prompt==null)continue;
      if(!reportedYieldedAttack && logSink!=null){reportedYieldedAttack=true;logSink.accept("SGS_RESUMABLE_ATTACK_PATH pending="+prompt.type()+" engine_caller_released=true");}
      long decisionRevision=engine.attackDecisionRevision();
      // respond/invoke retain the existing connection/nonce/deadline mailbox checks. The
      // response is then independently validated against the canonical yielded rules state.
      if(prompt.type()==dev.sgs.core.engine.PromptType.SKILL_INVOKE){
        boolean accepted=invoke(state,prompt);
        engine.resumeAttack(prompt.asker().id(),prompt.id(),decisionRevision,accepted,-1,null);
      }else if((prompt.type()==dev.sgs.core.engine.PromptType.CARD_ZONE_SELECT || prompt.type()==dev.sgs.core.engine.PromptType.WEAPON_DISCARD || prompt.type()==dev.sgs.core.engine.PromptType.WEAPON_COST_FIRST || prompt.type()==dev.sgs.core.engine.PromptType.WEAPON_COST_SECOND || prompt.type()==dev.sgs.core.engine.PromptType.GUI_CAI_REPLACE || prompt.type()==dev.sgs.core.engine.PromptType.YIJI_GIVE || prompt.type()==dev.sgs.core.engine.PromptType.FANKUI_TAKE || prompt.type()==dev.sgs.core.engine.PromptType.GANGLIE_DISCARD || prompt.type()==dev.sgs.core.engine.PromptType.LIULI_TARGET)){
        var choice=choose(state,prompt);if(stopped)return;
        engine.resumeAttackChoice(prompt.asker().id(),prompt.id(),decisionRevision,choice);
      }else{
        var choice=respond(state,prompt);
        var result=choice.isPresent() && choice.get().card().sourceCards().size()==2
            ?engine.resumeAttackPair(prompt.asker().id(),prompt.id(),decisionRevision,choice.get().card().sourceCards().stream().map(CardInstance::instanceId).toList())
            :engine.resumeAttack(prompt.asker().id(),prompt.id(),decisionRevision,choice.isPresent(),
                choice.map(d->d.card().sourceCards().getFirst().instanceId()).orElse(-1L),choice.map(dev.sgs.core.engine.PlayDecision::effectiveType).orElse(null));
        if(!result.accepted() && automatic(byId.get(prompt.asker().id())) && engine.pendingAttackPrompt().map(p->p.id()==prompt.id()).orElse(false))
          engine.resumeAttack(prompt.asker().id(),prompt.id(),engine.attackDecisionRevision(),false,-1,null);
      }
    }
  }

  private void humanDiscardWindow(dev.sgs.core.player.SgsPlayer current) {
    long deadline = windowDeadline(config.responseTimeoutSeconds());
    phaseDeadline = deadline;
    broadcast();
    while (!stopped
        && state.status() == GameStatus.RUNNING
        && state.phase() == Phase.DISCARD
        && state.currentPlayer() == current) {
      serviceControls();
      if(automatic(byId.get(current.id())) && clock.nowMillis()<deadline){dispatchAutoDiscard(current,false);phaseDeadline=0;broadcast();return;}
      Envelope envelope =
          waitForAction(
              e -> e.action().kind() == Kind.LEAVE || e.senderId().equals(current.id()),
              deadline - clock.nowMillis());
      if (envelope == null) {
        if (clock.nowMillis() < deadline) {
          continue;
        }
        phaseDeadline = 0L;
        dispatchTimeoutTurn(current);
        broadcast();
        return;
      }
      MatchAction action = envelope.action();
      if (action.kind() == Kind.LEAVE) {
        abort();
      }
      Participant sender = byId.get(envelope.senderId());
      if (isReplay(sender, action)) {
        continue;
      }
      if (!envelope.senderId().equals(current.id())) {
        sender.endpoint.sendError(action.nonce(), RejectReason.NOT_YOUR_TURN.name());
        continue;
      }
      if (action.kind() != Kind.CONFIRM_DISCARD) {
        sender.endpoint.sendError(action.nonce(), RejectReason.WRONG_PHASE.name());
        continue;
      }
      ActionResult result = dispatchDiscard(current, action.cardIds(),false);
      if (!result.accepted()) {
        sender.endpoint.sendError(action.nonce(), result.reason().name());
        broadcast();
      } else {
        phaseDeadline = 0L;
        broadcast();
      }
    }
    phaseDeadline = 0L;
  }

  private void botPlayWindow(dev.sgs.core.player.SgsPlayer current) {
    while (!stopped
        && state.status() == GameStatus.RUNNING
        && state.phase() == Phase.PLAY
        && state.currentPlayer() == current) {
      runAutoPlayStep(current);
      broadcast();
      sleepStep(config.botStepDelayMillis());
    }
  }

  private void runAutoPlayStep(dev.sgs.core.player.SgsPlayer current){
      serviceControls();if(!automatic(byId.get(current.id())))return;
      BotTurnDriver.runPlay(engine,state,current,(card,declared,target,secondary)->dispatchCard(current,
          declared==null?MatchAction.playCard(1,card,target==null?-1:target.seat(),secondary==null?-1:secondary.seat())
              :MatchAction.playCardAs(1,card,declared.ordinal(),target==null?-1:target.seat(),secondary==null?-1:secondary.seat()),declared),
          (card,declared,targets)->dispatchPlay(current,MatchAction.playAttackTargets(1,card,declared==null?-1:declared.ordinal(),targets.stream().map(dev.sgs.core.player.SgsPlayer::seat).toList())),
          (skill,target,costs)->dispatchActiveSkill(current,skill,target,costs),()->dispatchEndPlay(current));
      broadcast();
  }

  private dev.sgs.core.player.SgsPlayer resolveSeat(int seat) {
    if (seat == MatchAction.NO_SEAT) {
      return null;
    }
    if (seat < 0 || seat >= state.players().size()) {
      return null;
    }
    return state.players().get(seat);
  }

  // ---------------------------------------------------------------- prompts

  @Override public Optional<dev.sgs.core.engine.SkillChoice> choose(GameState gameState,PromptRequest prompt){
    Participant participant=byId.get(prompt.asker().id());activePrompt=prompt;
    pendingInput=new PendingInput(prompt.id(),prompt.asker().id());broadcast();
    try{
      if(participant==null || !participant.human)return BotPolicy.INSTANCE.choose(gameState,prompt);
      while(!stopped){
        serviceControls();if(automatic(participant) && clock.nowMillis()<prompt.deadlineMillis())return BotPolicy.INSTANCE.choose(gameState,prompt);
        long wait=Math.max(0,prompt.deadlineMillis()-clock.nowMillis());
        Envelope envelope=waitForAction(e->e.senderId().equals(prompt.asker().id())
            && (e.action().kind()==Kind.LEAVE || e.action().kind()==Kind.PROMPT_RESPONSE && e.action().promptId()==prompt.id()),wait);
        if(envelope==null){if(clock.nowMillis()<prompt.deadlineMillis())continue;return Optional.empty();}
        MatchAction action=envelope.action();if(action.kind()==Kind.LEAVE)abort();
        if(isReplay(participant,action))continue;
        if(clock.nowMillis()>prompt.deadlineMillis())return Optional.empty();
        if(!action.accept())return Optional.empty();
        boolean cardAllowed=prompt.allowsHiddenChoice(action.cardId())
            || prompt.choices().stream().anyMatch(c->c.instanceId()==action.cardId());
        boolean targetAllowed=prompt.allowedTargetSeats().isEmpty()?action.targetSeat()==-1:prompt.allowedTargetSeats().contains(action.targetSeat());
        if(!cardAllowed || !targetAllowed || action.secondarySeat()!=-1 || action.declaredTypeOrdinal()!=-1 || !action.cardIds().isEmpty()){
          participant.endpoint.sendError(action.nonce(),"INVALID_CHOICE");continue;
        }
        return Optional.of(new dev.sgs.core.engine.SkillChoice(action.cardId(),action.targetSeat()));
      }
      return Optional.empty();
    }finally{activePrompt=null;pendingInput=null;broadcast();}
  }

  @Override public Optional<dev.sgs.core.card.Suit> chooseSuit(GameState gameState,PromptRequest prompt){
    if(prompt.type()!=dev.sgs.core.engine.PromptType.FANJIAN_SUIT)return Optional.empty();
    Participant participant=byId.get(prompt.asker().id());activePrompt=prompt;
    pendingInput=new PendingInput(prompt.id(),prompt.asker().id());broadcast();
    try{
      if(participant==null || !participant.human)return BotPolicy.INSTANCE.chooseSuit(gameState,prompt);
      while(!stopped){
        serviceControls();if(automatic(participant) && clock.nowMillis()<prompt.deadlineMillis())return BotPolicy.INSTANCE.chooseSuit(gameState,prompt);
        long wait=Math.max(0,prompt.deadlineMillis()-clock.nowMillis());
        Envelope envelope=waitForAction(e->e.senderId().equals(prompt.asker().id())
            && (e.action().kind()==Kind.LEAVE || (e.action().kind()==Kind.PROMPT_RESPONSE && e.action().promptId()==prompt.id())),wait);
        if(envelope==null){if(clock.nowMillis()<prompt.deadlineMillis())continue;return Optional.empty();}
        MatchAction action=envelope.action();if(action.kind()==Kind.LEAVE)abort();
        if(isReplay(participant,action))continue;
        if(!action.accept() || action.cardId()!=-1 || action.declaredTypeOrdinal()!=-1 || !action.cardIds().isEmpty()
            || action.secondarySeat()!=-1 || action.targetSeat()<0 || action.targetSeat()>3){
          participant.endpoint.sendError(action.nonce(),"INVALID_CHOICE");continue;
        }
        if(clock.nowMillis()>prompt.deadlineMillis())return Optional.empty();
        return Optional.of(dev.sgs.core.card.Suit.values()[action.targetSeat()]);
      }
      return Optional.empty();
    }finally{activePrompt=null;pendingInput=null;broadcast();}
  }

  @Override public boolean invoke(GameState gameState,PromptRequest prompt){
    Participant participant=byId.get(prompt.asker().id());activePrompt=prompt;pendingInput=new PendingInput(prompt.id(),prompt.asker().id());broadcast();
    try{
      if(participant==null || !participant.human)return BotPolicy.INSTANCE.invoke(gameState,prompt);
      while(!stopped){
        serviceControls();if(automatic(participant) && clock.nowMillis()<prompt.deadlineMillis())return BotPolicy.INSTANCE.invoke(gameState,prompt);
        long wait=Math.max(0,prompt.deadlineMillis()-clock.nowMillis());
        Envelope envelope=waitForAction(e->e.senderId().equals(prompt.asker().id())
            && (e.action().kind()==Kind.LEAVE || (e.action().kind()==Kind.PROMPT_RESPONSE
            && e.action().promptId()==prompt.id())),wait);
        if(envelope==null){if(clock.nowMillis()<prompt.deadlineMillis())continue;return false;}
        MatchAction action=envelope.action();if(action.kind()==Kind.LEAVE)abort();
        if(isReplay(participant,action))continue;
        if(action.cardId()!=-1 || action.declaredTypeOrdinal()!=-1 || !action.cardIds().isEmpty()){
          participant.endpoint.sendError(action.nonce(),"INVALID_CHOICE");continue;
        }
        if(clock.nowMillis()>prompt.deadlineMillis())return false;
        state.log("seat {} skill decision {} accepted={}",prompt.asker().seat(),prompt.skillId(),action.accept());
        return action.accept();
      }
      return false;
    }finally{activePrompt=null;pendingInput=null;broadcast();}
  }

  private void refreshCounterspellInputs(dev.sgs.core.engine.CounterspellWindow window){
    var copy=new HashMap<UUID,PromptRequest>();for(var p:window.pendingRequests())copy.put(p.asker().id(),p);
    counterspellInputs=Map.copyOf(copy);
  }
  private PromptResponder.WuxieResponse counterspellReply(MatchAction action,PromptRequest prompt){
    if(action.targetSeat()!=-1 || action.secondarySeat()!=-1 || action.skillId()!=null || action.heroId()!=null || !action.cardIds().isEmpty())return null;
    if(action.kind()==Kind.PASS_WUXIE_SCOPE){
      return !action.accept() && action.cardId()==-1 && action.declaredTypeOrdinal()==-1?PromptResponder.WuxieResponse.skipScope():null;
    }
    if(action.kind()!=Kind.PROMPT_RESPONSE)return null;
    if(!action.accept())return action.cardId()==-1 && action.declaredTypeOrdinal()==-1?PromptResponder.WuxieResponse.decline():null;
    if(action.declaredTypeOrdinal() < -1 || action.declaredTypeOrdinal()>=CardType.values().length)return null;
    var card=prompt.asker().handView().stream().filter(c->c.instanceId()==action.cardId()).findFirst().orElse(null);
    if(card==null)return null;
    var type=action.declaredTypeOrdinal()==-1?card.type():CardType.values()[action.declaredTypeOrdinal()];
    if(type!=CardType.WUXIE)return null;
    return PromptResponder.WuxieResponse.answer(Optional.of(type==card.type()?PlayDecision.asIs(card):PlayDecision.alternate(card,type)));
  }
  /** All invited actors share one deadline. Only this worker validates and selects a winner. */
  @Override public dev.sgs.core.engine.CounterspellResult collectCounterspell(GameState gameState,dev.sgs.core.engine.CounterspellWindow window){
    window.retainEligible(engine::hasCounterspellResponse);
    if(window.closed())return window.finish();
    if(activeCounterspellWindow!=null)throw new IllegalStateException("nested counterspell collector");
    activeCounterspellWindow=window;refreshCounterspellInputs(window);counterspellCollecting=true;
    try{
      log("match {} counterspell window opened: window={} subject={} depth={} deadline={} invited={}",roomId,window.windowId(),window.subjectSeat(),window.chainDepth(),window.deadlineMillis(),window.requests().size());
      broadcast(); // Real invitations precede every AI proposal.
      long aiDelay=Math.min(350,Math.max(1,(window.deadlineMillis()-clock.nowMillis())/4));
      long aiDue=Math.min(window.deadlineMillis(),clock.nowMillis()+aiDelay);boolean aiProcessed=false;
      while(!stopped && !window.closed() && !window.expired()){
        long now=clock.nowMillis();long wait=Math.max(0,Math.min(window.deadlineMillis()-now,aiProcessed?Long.MAX_VALUE:aiDue-now));
        var envelope=waitForAction(e->true,wait);
        // Recheck already queued human actions at the AI boundary before planning any proposal.
        if(envelope==null && !aiProcessed && clock.nowMillis()>=aiDue && !window.expired())envelope=waitForAction(e->true,0);
        if(envelope!=null){
          var actor=byId.get(envelope.senderId());var action=envelope.action();if(action.kind()==Kind.LEAVE)abort();
          if(actor==null || isReplay(actor,action))continue;
          var prompt=counterspellInputs.get(envelope.senderId());
          if(prompt==null || prompt.id()!=action.promptId()){actor.endpoint.sendError(action.nonce(),"WINDOW_CLOSED");continue;}
          var reply=counterspellReply(action,prompt);
          var receipt=reply==null?dev.sgs.core.engine.CounterspellWindow.Receipt.INVALID:window.offer(envelope.senderId(),action.promptId(),reply);
          if(receipt==dev.sgs.core.engine.CounterspellWindow.Receipt.INVALID){actor.endpoint.sendError(action.nonce(),"INVALID_CHOICE");continue;}
          if(receipt==dev.sgs.core.engine.CounterspellWindow.Receipt.LATE || receipt==dev.sgs.core.engine.CounterspellWindow.Receipt.CLOSED){actor.endpoint.sendError(action.nonce(),"WINDOW_CLOSED");continue;}
          if(receipt==dev.sgs.core.engine.CounterspellWindow.Receipt.SCOPE_PASSED)
            log("match {} wuxie scope pass accepted: seat={} prompt={} nonce={}",roomId,actor.player.seat(),action.promptId(),action.nonce());
          refreshCounterspellInputs(window);
          if(!window.closed())broadcast();
          continue;
        }
        if(clock.nowMillis()>=aiDue && !window.expired()){
          aiProcessed=true;
          var bots=new ArrayList<>(window.pendingRequests().stream().filter(p->{var who=byId.get(p.asker().id());return automatic(who);}).toList());
          if(!bots.isEmpty())Collections.rotate(bots,-(int)Math.floorMod(counterspellAiRound++,(long)bots.size()));
          for(var prompt:bots){
            serviceControls();if(!automatic(byId.get(prompt.asker().id())))continue;
            if(window.closed() || window.expired())break;
            var decision=BotPolicy.INSTANCE.respond(gameState,prompt);
            var receipt=window.offer(prompt.asker().id(),prompt.id(),PromptResponder.WuxieResponse.answer(decision));
            if(receipt==dev.sgs.core.engine.CounterspellWindow.Receipt.INVALID)window.offer(prompt.asker().id(),prompt.id(),PromptResponder.WuxieResponse.decline());
          }
          refreshCounterspellInputs(window);if(!window.closed())broadcast();
        }
      }
      if(stopped)throw new AbortedException();
      return window.finish();
    }finally{
      var closed=window.finish();counterspellCollecting=false;counterspellInputs=Map.of();activeCounterspellWindow=null;
      log("match {} counterspell window closed: window={} deadline={} invited={} winner={}",roomId,window.windowId(),window.deadlineMillis(),window.requests().size(),closed.winner().isPresent());
      for(var prompt:window.requests())retiredCounterspellThrough=Math.max(retiredCounterspellThrough,prompt.id());
      broadcast();
    }
  }

  @Override
  public Optional<PlayDecision> respond(GameState gameState, PromptRequest prompt) {
    return respondTyped(gameState,prompt).decision();
  }
  @Override public PromptResponder.WuxieResponse respondWuxie(GameState gameState,PromptRequest prompt){return respondTyped(gameState,prompt);}
  private PromptResponder.WuxieResponse respondTyped(GameState gameState,PromptRequest prompt){
    Participant participant = byId.get(prompt.asker().id());
    activePrompt = prompt;
    pendingInput=new PendingInput(prompt.id(),prompt.asker().id(),prompt.type()==dev.sgs.core.engine.PromptType.WUXIE_COUNTER);
    broadcast();
    try {
      serviceControls();
      if (automatic(participant)) {
        return PromptResponder.WuxieResponse.answer(BotPolicy.INSTANCE.respond(gameState, prompt));
      }
      while (!stopped) {
        serviceControls();if(automatic(participant) && clock.nowMillis()<prompt.deadlineMillis())return PromptResponder.WuxieResponse.answer(BotPolicy.INSTANCE.respond(gameState,prompt));
        long wait = Math.max(prompt.deadlineMillis() - clock.nowMillis(), 0L);
        Envelope envelope =
            waitForAction(
                e ->
                    e.senderId().equals(prompt.asker().id())
                        && (e.action().kind() == Kind.LEAVE
                            || ((e.action().kind() == Kind.PROMPT_RESPONSE || e.action().kind()==Kind.PASS_WUXIE_SCOPE)
                                && e.action().promptId() == prompt.id())),
                wait);
        if (envelope == null) {
          if(clock.nowMillis()<prompt.deadlineMillis())continue;
          return PromptResponder.WuxieResponse.decline();
        }
        MatchAction action = envelope.action();
        if (action.kind() == Kind.LEAVE) {
          abort();
        }
        if (isReplay(participant, action)) {
          continue;
        }
        if(action.kind()==Kind.PASS_WUXIE_SCOPE){
          if(prompt.type()!=dev.sgs.core.engine.PromptType.WUXIE_COUNTER || action.accept() || action.cardId()!=-1
              || action.declaredTypeOrdinal()!=-1 || action.targetSeat()!=-1 || action.secondarySeat()!=-1
              || !action.cardIds().isEmpty() || action.skillId()!=null || action.heroId()!=null){
            participant.endpoint.sendError(action.nonce(),"INVALID_CHOICE");continue;
          }
          if(clock.nowMillis()>prompt.deadlineMillis())return PromptResponder.WuxieResponse.decline();
          log("match {} wuxie scope pass accepted: seat={} prompt={} nonce={}",roomId,participant.player.seat(),prompt.id(),action.nonce());
          return PromptResponder.WuxieResponse.skipScope();
        }
        if (!action.accept()) {
          return PromptResponder.WuxieResponse.decline();
        }
        if(clock.nowMillis()>prompt.deadlineMillis())return PromptResponder.WuxieResponse.decline();
        if(!action.cardIds().isEmpty()){
          if(!prompt.allowedTypes().contains(CardType.SHA) || action.cardId()!=-1
              || action.declaredTypeOrdinal()!=CardType.SHA.ordinal() || action.cardIds().size()!=2
              || action.cardIds().get(0).equals(action.cardIds().get(1))){
            participant.endpoint.sendError(action.nonce(),"INVALID_CHOICE");continue;
          }
          var costs=new ArrayList<CardInstance>();
          for(long id:action.cardIds())prompt.asker().handView().stream()
              .filter(c->c.instanceId()==id).findFirst().ifPresent(costs::add);
          if(costs.size()!=2 || prompt.asker().equipment().weapon()==null
              || prompt.asker().equipment().weapon().type()!=CardType.ZHANGBA){
            participant.endpoint.sendError(action.nonce(),"INVALID_CHOICE");continue;
          }
          return PromptResponder.WuxieResponse.answer(Optional.of(PlayDecision.asIs(new dev.sgs.core.card.VirtualCard(CardType.SHA,costs))));
        }
        CardInstance card = null;
        boolean draft=prompt.type()==dev.sgs.core.engine.PromptType.WUGU_PICK;
        var available=new ArrayList<CardInstance>(draft?prompt.choices():prompt.asker().handView());
        // Engine still validates the actual view-as skill, ownership and weapon-removal range.
        if(!draft && (action.declaredTypeOrdinal()==CardType.SHA.ordinal()
            || (prompt.type()==dev.sgs.core.engine.PromptType.RESCUE_TAO && action.declaredTypeOrdinal()==CardType.TAO.ordinal())))available.addAll(prompt.asker().equipment().all());
        for (CardInstance candidate : available) {
          if (candidate.instanceId() == action.cardId()) {
            card = candidate;
            break;
          }
        }
        if (card == null) {
          participant.endpoint.sendError(action.nonce(), draft?"INVALID_CHOICE":RejectReason.CARD_NOT_IN_HAND.name());
          continue;
        }
        int ordinal = action.declaredTypeOrdinal();
        if(ordinal < -1 || ordinal>=CardType.values().length){participant.endpoint.sendError(action.nonce(),RejectReason.UNKNOWN_CARD.name());continue;}
        CardType declared = ordinal < 0 ? card.type() : CardType.values()[ordinal];
        if((draft && declared!=card.type()) || (!draft && !prompt.allowedTypes().contains(declared))){
          participant.endpoint.sendError(action.nonce(),draft?"INVALID_CHOICE":RejectReason.RESPONSE_ONLY.name());continue;
        }
        return PromptResponder.WuxieResponse.answer(Optional.of(
            declared == card.type()
                ? PlayDecision.asIs(card)
                : PlayDecision.alternate(card, declared)));
      }
      return PromptResponder.WuxieResponse.decline();
    } finally {
      activePrompt = null;
      pendingInput=null;
      broadcast();
    }
  }

  // ---------------------------------------------------------------- plumbing

  private void broadcast() {
    if(physicalIds!=null){
      state.cards().assertConserved(physicalIds);
      long revision=state.cards().checkpointSequence();
      if(revision/128!=lastConservationAudit){
        lastConservationAudit=revision/128;
        log("card conservation checked: physical={} move={} boundary=publication",physicalIds.size(),revision);
      }
    }
    GameOutcome outcome = engine == null ? GameOutcome.ONGOING : engine.outcome();
    var currentWindow=activeCounterspellWindow;
    dev.sgs.core.match.dto.MatchViews.CounterspellWindowView windowView=currentWindow==null || activePrompt!=null?null:
        new dev.sgs.core.match.dto.MatchViews.CounterspellWindowView(currentWindow.windowId(),currentWindow.subjectSeat(),currentWindow.chainDepth(),currentWindow.deadlineMillis());
    for (Participant participant : participants) {
      MatchSnapshot snapshot =
          windowView==null?ViewFilter.running(
              state, participant.player.seat(), phaseDeadline, activePrompt, outcome, lastJudgment,visibility()):
              ViewFilter.counterspell(state,participant.player.seat(),phaseDeadline,counterspellInputs.get(participant.player.id()),windowView,outcome,lastJudgment,visibility());
      if(openingHandActive && participant.human)snapshot=snapshot.withOpeningHand(
          new dev.sgs.core.match.dto.MatchViews.OpeningHandView(participant.openingHandDone?1:0,openingHandDeadline));
      if(engine!=null && participant.human)snapshot=snapshot.withSelfSkillAvailability(
          engine.activeSkillAvailability(participant.player).entrySet().stream().map(entry->
              new dev.sgs.core.match.dto.MatchViews.SkillAvailabilityView(entry.getKey(),entry.getValue().isEmpty(),entry.getValue())).toList());
      if(engine!=null)snapshot=snapshot.withHeroPresentations(snapshot.heroPresentations().stream().map(h->{
        var player=state.players().stream().filter(p->p.hero()!=null&&p.hero().id().equals(h.id())).findFirst().orElse(null);
        if(player==null)return h;
        return new dev.sgs.core.addon.HeroPresentation(h.id(),h.name(),h.description(),h.portrait(),engine.effectiveSkillIds(player).stream()
            .map(id->dev.sgs.core.addon.AddonRuntime.current().skill(id).orElseThrow()).toList());
      }).toList());
      if(engine!=null && participant.human && activePrompt==null && windowView==null && !openingHandActive){
        var hints=new java.util.ArrayList<dev.sgs.core.match.dto.MatchViews.TargetAvailabilityView>();
        engine.singleTrickTargetAvailability(participant.player).forEach((card,targets)->targets.forEach((seat,reason)->
            hints.add(new dev.sgs.core.match.dto.MatchViews.TargetAvailabilityView(card,seat,reason))));
        snapshot=snapshot.withSelfTargetAvailability(hints);
        snapshot=snapshot.withOwnCardPlayOptions(engine.cardPlayAvailability(participant.player));
      }
      if(engine!=null && participant.human && !openingHandActive){
        var responsePrompt=activePrompt!=null && activePrompt.asker()==participant.player?activePrompt:
            windowView==null?null:counterspellInputs.get(participant.player.id());
        if(responsePrompt!=null)snapshot=snapshot.withOwnCardPlayOptions(engine.cardResponseAvailability(participant.player,responsePrompt));
      }
      if(engine!=null)snapshot=snapshot.withPublicCardReveals(engine.publicCardReveals().stream().map(dev.sgs.core.match.dto.MatchViews.PublicCardRevealView::of).toList());
      safeSend(participant.endpoint, snapshot);
    }
      MatchSnapshot spectatorView =
          ViewFilter.running(
              state,
              dev.sgs.core.match.dto.MatchViews.HIDDEN,
              phaseDeadline,
              activePrompt,
              outcome,
              lastJudgment);
      if(windowView!=null)spectatorView=ViewFilter.counterspell(state,-1,phaseDeadline,null,windowView,outcome,lastJudgment,HandVisibility.PRIVATE);
      if(engine!=null)spectatorView=spectatorView.withPublicCardReveals(engine.publicCardReveals().stream().map(dev.sgs.core.match.dto.MatchViews.PublicCardRevealView::of).toList());
      publicSnapshot=spectatorView.withControls(controlViews());
      for (MatchEndpoint spectator : List.copyOf(spectators)) {
        safeSend(spectator, spectatorView);
      }
  }

  private void safeSend(MatchEndpoint endpoint, MatchSnapshot snapshot) {
    try {
      endpoint.sendSnapshot(snapshot.withControls(controlViews()));
    } catch (RuntimeException e) {
      log("endpoint push failed in match {}: {}", roomId, e.toString());
    }
  }

  private boolean isReplay(Participant participant, MatchAction action) {
    long last = lastNonceByPlayer.getOrDefault(participant.player.id(), 0L);
    if (action.nonce() <= last) {
      log("replayed intent seat={} nonce={} floor={}",participant.player.seat(),action.nonce(),last);
      participant.endpoint.sendError(action.nonce(), "REPLAY");
      return true;
    }
    lastNonceByPlayer.put(participant.player.id(), (long) action.nonce());
    return false;
  }

  private void sleepStep(long millis) {
    if (millis <= 0L) {
      return;
    }
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      if (stopped) {
        abort();
      }
    }
  }

  private void abort() {
    stopped = true;
    mailbox.wake();
    throw new AbortedException();
  }

  private void log(String message, Object... args) {
    if (logSink == null) {
      return;
    }
    StringBuilder builder = new StringBuilder(message.length() + 16);
    int argIndex = 0;
    int cursor = 0;
    int placeholder;
    while ((placeholder = message.indexOf("{}", cursor)) >= 0) {
      builder.append(message, cursor, placeholder);
      builder.append(argIndex < args.length ? String.valueOf(args[argIndex++]) : "{}");
      cursor = placeholder + 2;
    }
    builder.append(message.substring(cursor));
    logSink.accept(builder.toString());
  }
}
