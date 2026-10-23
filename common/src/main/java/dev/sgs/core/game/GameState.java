package dev.sgs.core.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import dev.sgs.core.GameStatus;
import dev.sgs.core.Identity;
import dev.sgs.core.Phase;
import dev.sgs.core.card.CardInstance;
import dev.sgs.core.card.Deck;
import dev.sgs.core.card.CardMovement;
import dev.sgs.core.card.CardMove;
import dev.sgs.core.card.CardZone;
import dev.sgs.core.hero.HeroDefinition;
import dev.sgs.core.player.SgsPlayer;

/**
 * Central match state and the turn phase skeleton. All state transitions are driven explicitly by
 * method calls so the engine stays synchronous and unit testable without a Minecraft runtime.
 */
public final class GameState {
  // Presentation receipts only. Live continuations reconstruct held disclosures after restoration;
  // completed animations intentionally are not part of authoritative rules checkpoints.
  private final Deque<PublicCardReveal> publicCardReveals=new ArrayDeque<>();
  private long publicRevealSequence;
  public List<PublicCardReveal> publicCardReveals(){return List.copyOf(publicCardReveals);}
  public void publishCardReveal(String reason,int owner,int subject,CardInstance card){
    if(owner<0||owner>=players.size()||subject<0||subject>=players.size()||!CardZone.hand(owner).equals(cards().locate(card.instanceId())))
      throw new IllegalArgumentException("reveal is not an owned hand entity");
    var canonical=cards().cards(CardZone.hand(owner)).stream().filter(c->c.instanceId()==card.instanceId()).findFirst().orElseThrow();
    var value=new PublicCardReveal(PublicCardReveal.key(turnNumber,reason,owner,subject,canonical.instanceId())+":"+(++publicRevealSequence),owner,subject,reason,List.of(canonical),true);
    publicCardReveals.addLast(value);
    journal.add(journal.seat(owner)+"展示了"+MatchJournal.card(canonical));
    while(publicCardReveals.size()>16)publicCardReveals.removeFirst();
  }
  public void completeCardReveal(String reason,int owner,int subject,long card){
    var latest=publicCardReveals.reversed().stream().filter(r->r.held()&&r.reason().equals(reason)&&r.owner()==owner&&r.subject()==subject&&r.cards().stream().anyMatch(c->c.instanceId()==card)).findFirst();
    if(latest.isEmpty())return;
    var key=latest.get().id();var copy=publicCardReveals.stream().map(r->r.id().equals(key)?r.completed():r).toList();
    publicCardReveals.clear();publicCardReveals.addAll(copy);
  }

  private dev.sgs.core.match.dto.MatchViews.DraftView draftView;
  private final Deque<dev.sgs.core.match.dto.MatchViews.TableEffect> tableEffects=new ArrayDeque<>();
  private final java.util.Map<Long,List<dev.sgs.core.match.dto.MatchViews.TableEffect>> publicUses=new java.util.HashMap<>();
  private long tableSequence;
  private java.util.function.Consumer<dev.sgs.core.match.dto.MatchViews.TableEffect> tableEffectObserver=effect->{};
  /** Runtime presentation barrier; never serialized into checkpoints. */
  public void setTableEffectObserver(java.util.function.Consumer<dev.sgs.core.match.dto.MatchViews.TableEffect> observer){tableEffectObserver=java.util.Objects.requireNonNull(observer);}

  private int presentationCheckpointVersion=3;
  public int presentationCheckpointVersion(){return presentationCheckpointVersion;}
  public void presentationCheckpointVersion(int version){presentationCheckpointVersion=version;}
  private List<Long> damageTotals=new ArrayList<>(Collections.nCopies(8,0L));private boolean completeStatistics=true;
  public List<Long> damageTotals(){return List.copyOf(damageTotals);}public boolean completeStatistics(){return completeStatistics;}
  public void restoreStatistics(List<Long> values,boolean complete){if(values.size()!=8||values.stream().anyMatch(v->v==null||v<0))throw new IllegalArgumentException("invalid damage statistics");damageTotals=new ArrayList<>(values);completeStatistics=complete;}
  public void recordDamage(int seat,int amount){if(seat>=0&&seat<players.size()&&amount>0)damageTotals.set(seat,Math.addExact(damageTotals.get(seat),amount));}

  public dev.sgs.core.match.dto.MatchViews.TablePresentation tablePresentation(){
    publicUses.keySet().removeIf(id->{var zone=cards().locate(id);return zone==null || zone.kind()!=dev.sgs.core.card.CardZone.Kind.PROCESSING && zone.kind()!=dev.sgs.core.card.CardZone.Kind.JUDGMENT;});
    var effects=new java.util.TreeMap<Long,dev.sgs.core.match.dto.MatchViews.TableEffect>();
    for(var event:tableEffects)effects.put(event.id(),event);
    for(var events:publicUses.values())for(var event:events)effects.put(event.id(),event);
    return new dev.sgs.core.match.dto.MatchViews.TablePresentation(draftView,List.copyOf(effects.values()),damageTotals(),completeStatistics);
  }
  public void restoreDraft(dev.sgs.core.match.dto.MatchViews.DraftView draft){draftView=draft;}
  public void beginDraft(long id,int source,String title,List<CardInstance> cards){draftView=new dev.sgs.core.match.dto.MatchViews.DraftView(id,source,source,title,cards.stream().map(c->new dev.sgs.core.match.dto.MatchViews.DraftSlot(dev.sgs.core.match.dto.MatchViews.CardView.of(c),-1)).toList(),true);}
  public void draftPicker(int seat){if(draftView!=null&&draftView.active())draftView=new dev.sgs.core.match.dto.MatchViews.DraftView(draftView.id(),draftView.source(),seat,draftView.title(),draftView.slots(),true);}
  public void claimDraft(long card,int seat){if(draftView!=null&&draftView.active())draftView=new dev.sgs.core.match.dto.MatchViews.DraftView(draftView.id(),draftView.source(),seat,draftView.title(),draftView.slots().stream().map(slot->slot.card().instanceId()==card?new dev.sgs.core.match.dto.MatchViews.DraftSlot(slot.card(),seat):slot).toList(),true);}
  public void finishDraft(){draftView=null;}
  public void beginPublicCardUse(List<CardInstance> cards){
    var ids=cards.stream().map(CardInstance::instanceId).collect(java.util.stream.Collectors.toSet());
    ids.forEach(publicUses::remove);
    tableEffects.removeIf(e->e.kind().equals("CARD_USE") && (ids.isEmpty()?e.cards().isEmpty():e.cards().stream().anyMatch(c->ids.contains(c.instanceId()))));
  }
  /** Call only after all displayed cards have become public under the actual rule. */
  public void tableEffect(String kind,int source,int target,String label,List<CardInstance> cards,boolean success){
    tableEffects.addLast(new dev.sgs.core.match.dto.MatchViews.TableEffect(++tableSequence,kind,source,target,label,cards.stream().map(dev.sgs.core.match.dto.MatchViews.CardView::of).toList(),success));
    if(kind.equals("CARD_USE"))for(var card:cards){
      var receipts=publicUses.computeIfAbsent(card.instanceId(),id->new java.util.ArrayList<>());
      if(!receipts.isEmpty() && receipts.getLast().source()!=source)receipts.clear();
      receipts.removeIf(e->e.target()==target);receipts.add(tableEffects.getLast());
    }
    while(tableEffects.size()>16)tableEffects.removeFirst();
    tableEffectObserver.accept(tableEffects.getLast());
  }

  public static final int MIN_PLAYERS = 2;
  public static final int MAX_PLAYERS = 8;
  public static final int INITIAL_HAND_SIZE = 4;
  public static final int DRAW_PER_TURN = 2;
  public static final int LORD_EXTRA_HP = 1;

  private static final int LOG_LIMIT = 100;

  private final List<SgsPlayer> players;
  private final Deck deck;
  private final CardMovement cards;
  private final Random random;
  private final Deque<String> logs = new ArrayDeque<>();
  private final MatchJournal journal=new MatchJournal(this);
  public MatchJournal journal(){return journal;}
  /** Transient presentation receipts; neither hidden identities nor rule/save state. */
  public record PublicSkillActivation(long sequence,int seat,String skillId) {}
  private final Deque<PublicSkillActivation> publicSkillActivations=new ArrayDeque<>();
  private long publicSkillSequence;
  public long publicSkillSequence(){return publicSkillSequence;}
  /** Restore receipts without replaying activations, logging, or invoking rules. */
  public void restoreSkillActivations(long sequence,List<PublicSkillActivation> values){
    if(sequence<0 || values.size()>32)throw new IllegalArgumentException("invalid skill receipts");
    long previous=0;
    for(var value:values){
      if(value.sequence()<=previous || value.sequence()>sequence || value.seat()<0 || value.seat()>=players.size()
          || value.skillId()==null || value.skillId().isBlank() || value.skillId().length()>128)
        throw new IllegalArgumentException("invalid skill activation");
      previous=value.sequence();
    }
    publicSkillActivations.clear();publicSkillActivations.addAll(values);publicSkillSequence=sequence;
  }
  public List<PublicSkillActivation> publicSkillActivations(){return List.copyOf(publicSkillActivations);}
  public void publishSkillActivation(int seat,String skillId){
    if(status==GameStatus.HERO_SELECTION || seat<0 || seat>=players.size() || players.get(seat).hero()==null)return;
    if(skillId==null || skillId.isBlank())throw new IllegalArgumentException("missing skill id");
    publicSkillActivations.addLast(new PublicSkillActivation(++publicSkillSequence,seat,skillId));
    journal.skill(seat,skillId);
    while(publicSkillActivations.size()>32)publicSkillActivations.removeFirst();
  }
  // Optional external observers (headless simulator, bridge logging); never required for play.
  private final List<Consumer<String>> logSinks = new CopyOnWriteArrayList<>();
  private final List<Consumer<String>> diagnosticLogSinks = new CopyOnWriteArrayList<>();
  /** Transient diagnostics, not rule observers. Reattach after restoration; never mutate rules. */
  public void addDiagnosticLogSink(Consumer<String> sink){diagnosticLogSinks.add(java.util.Objects.requireNonNull(sink));}
  public int logObserverCount(){return logSinks.size();}

  private dev.sgs.core.room.RoomMode mode;
  public dev.sgs.core.room.RoomMode mode(){return mode;}
  public boolean teamMode(){return mode==dev.sgs.core.room.RoomMode.TWO_V_TWO;}
  public int teamOf(int seat){return teamMode() && seat>=0 && seat<4 ? (seat==0 || seat==3?0:1):-1;}
  public boolean areTeammates(SgsPlayer a,SgsPlayer b){return a!=null && b!=null && teamMode() && teamOf(a.seat())==teamOf(b.seat());}
  public int normalDrawCount(){return teamMode() && turnNumber==1 && currentSeat==0?1:DRAW_PER_TURN;}
  private java.util.function.ToIntFunction<SgsPlayer> handLimit=p->Math.max(0,p.hp());
  private java.util.function.ToIntFunction<SgsPlayer> skillDistance=p->0;
  private java.util.function.BiPredicate<SgsPlayer,String> skillEnabled=(p,id)->p.hero()!=null&&p.hero().skillIds().contains(id);
  private java.util.function.Function<SgsPlayer,dev.sgs.core.Kingdom> kingdom=p->p.hero().kingdom();
  private java.util.function.Predicate<SgsPlayer> male=p->p.hero().male();
  public boolean hasSkill(SgsPlayer p,String id){return skillEnabled.test(p,id);}
  public dev.sgs.core.Kingdom kingdom(SgsPlayer p){return kingdom.apply(p);}
  public boolean male(SgsPlayer p){return male.test(p);}
  public void ruleHeroTraits(java.util.function.BiPredicate<SgsPlayer,String> enabled,java.util.function.Function<SgsPlayer,dev.sgs.core.Kingdom> faction,java.util.function.Predicate<SgsPlayer> sex){skillEnabled=enabled;kingdom=faction;male=sex;}
  private java.util.function.Function<SgsPlayer,List<dev.sgs.core.match.dto.MatchViews.StatusView>> ruleStatuses=p->List.of();
  public void rulePublicStatuses(java.util.function.Function<SgsPlayer,List<dev.sgs.core.match.dto.MatchViews.StatusView>> provider){ruleStatuses=provider;}
  private java.util.function.IntUnaryOperator nextTurn=seat->seat;
  public void ruleNextTurn(java.util.function.IntUnaryOperator rule){nextTurn=rule;}
  public void ruleHandLimit(java.util.function.ToIntFunction<SgsPlayer> rule){handLimit=rule;}
  public int handLimit(SgsPlayer p){return Math.max(0,handLimit.applyAsInt(p));}
  public void ruleSkillDistance(java.util.function.ToIntFunction<SgsPlayer> rule){skillDistance=rule;}
  public void skipTurn(){if(phase!=Phase.PREPARE)throw new IllegalStateException("turn already started");phase=Phase.END;advancePhase();}

  private GameStatus status = GameStatus.HERO_SELECTION;
  private int currentSeat;
  private Phase phase = Phase.NONE;
  private int turnNumber;
  private int shaPlayedThisTurn;
  private boolean jiuPlayedThisTurn;
  private boolean jiuBoostPending;
  private boolean skipPlayPhase;
  private boolean skipDrawPhase;
  private record PublicStatus(dev.sgs.core.match.dto.MatchViews.StatusView view,java.util.function.BooleanSupplier active) {}
  private final java.util.Map<Integer,java.util.Map<String,PublicStatus>> publicStatuses=new java.util.HashMap<>();
  /** Presentation providers read the authoritative rule object, including restored skill memory. */
  public void registerPublicStatus(int seat,String id,String label,String description,java.util.function.BooleanSupplier active){
    publicStatuses.computeIfAbsent(seat,key->new java.util.LinkedHashMap<>()).put(id,
        new PublicStatus(new dev.sgs.core.match.dto.MatchViews.StatusView(id,label,description),active));
  }
  public void clearPublicStatuses(int seat){publicStatuses.remove(seat);}
  public List<dev.sgs.core.match.dto.MatchViews.StatusView> publicStatuses(int seat){
    if(status!=GameStatus.RUNNING || !players.get(seat).alive())return List.of();
    var result=new ArrayList<dev.sgs.core.match.dto.MatchViews.StatusView>();
    if(seat==currentSeat && jiuBoostPending)result.add(new dev.sgs.core.match.dto.MatchViews.StatusView("jiu","酒","本回合下一张杀伤害+1"));
    for(var entry:publicStatuses.getOrDefault(seat,java.util.Map.of()).values())if(entry.active().getAsBoolean())result.add(entry.view());
    result.addAll(ruleStatuses.apply(players.get(seat)));
    return List.copyOf(result);
  }
  private final java.util.Set<Integer> chainedSeats = new java.util.HashSet<>();

  /** Rule data only; active engine frames/skill objects are deliberately not part of this record. */
  public record TurnState(GameStatus status,int currentSeat,Phase phase,int turnNumber,int shaPlayed,
      boolean jiuPlayed,boolean jiuBoost,boolean skipPlay,boolean skipDraw,
      dev.sgs.core.room.RoomMode mode,java.util.Set<Integer> chained,List<String> logs){
    public TurnState {chained=java.util.Set.copyOf(chained);logs=List.copyOf(logs);}
  }
  public TurnState checkpointTurnState(){return new TurnState(status,currentSeat,phase,turnNumber,shaPlayedThisTurn,
      jiuPlayedThisTurn,jiuBoostPending,skipPlayPhase,skipDrawPhase,mode,chainedSeats,recentLogs());}
  /** Does not deal, assign identities, heal, draw, log startup or notify observers. */
  public static GameState restoreCheckpoint(List<SgsPlayer> players,Deck deck,Random random,TurnState saved){
    int count=players.size();
    if(count<MIN_PLAYERS || count>MAX_PLAYERS || saved.currentSeat()<0 || saved.currentSeat()>=count
        || saved.turnNumber()<1 || saved.shaPlayed()<0 || saved.logs().size()>LOG_LIMIT
        || saved.status()==null || saved.phase()==null || (saved.mode()!=null && saved.mode().playerCount()!=count)
        || (saved.status()==GameStatus.RUNNING && saved.phase()==Phase.NONE)
        || (saved.status()==GameStatus.FINISHED && saved.phase()!=Phase.NONE))
      throw new IllegalArgumentException("invalid rule turn state");
    for(int seat=0;seat<count;seat++)if(players.get(seat).seat()!=seat)throw new IllegalArgumentException("invalid seat order");
    for(int seat:saved.chained())if(seat<0 || seat>=count || !players.get(seat).alive())throw new IllegalArgumentException("invalid chained seat");
    var state=new GameState(List.copyOf(players),java.util.Objects.requireNonNull(deck),java.util.Objects.requireNonNull(random));
    state.status=saved.status();state.currentSeat=saved.currentSeat();state.phase=saved.phase();state.turnNumber=saved.turnNumber();
    state.shaPlayedThisTurn=saved.shaPlayed();state.jiuPlayedThisTurn=saved.jiuPlayed();state.jiuBoostPending=saved.jiuBoost();
    state.skipPlayPhase=saved.skipPlay();state.skipDrawPhase=saved.skipDraw();state.mode=saved.mode();
    state.chainedSeats.addAll(saved.chained());state.logs.addAll(saved.logs());return state;
  }

  public boolean chained(int seat) { return chainedSeats.contains(seat); }

  public void setChained(int seat, boolean chained) {
    if (seat < 0 || seat >= players.size()) throw new IllegalArgumentException("unknown seat");
    boolean previous=chainedSeats.contains(seat);
    if (chained && players.get(seat).alive()) chainedSeats.add(seat);
    else chainedSeats.remove(seat);
    if(previous!=chainedSeats.contains(seat))journal.add(journal.seat(seat)+(chainedSeats.contains(seat)?"进入连环状态":"解除连环状态"));
  }

  private GameState(List<SgsPlayer> players, Deck deck, Random random) {
    this.players = players;
    this.mode=players.size()==6?dev.sgs.core.room.RoomMode.SIX:players.size()==8?dev.sgs.core.room.RoomMode.EIGHT:null;
    this.deck = deck;
    this.cards = new CardMovement(players,deck,journal::movement);
    this.random = random;
  }

  /**
   * Deals identities and heroes, sets life totals (lord +1), deals opening hands and positions the
   * turn marker on the lord.
   */
  public static GameState start(
      List<SgsPlayer> seatedPlayers, List<HeroDefinition> heroesBySeat, Deck deck, Random random) {
    GameState state = create(seatedPlayers, heroesBySeat, deck, random);
    IdentityTable.assign(state.players, random);
    return begin(state, heroesBySeat);
  }

  /**
   * Same startup as {@link #start}, but identities were already dealt to the seats by the caller
   * (used by the network match flow that randomizes physical seating, deals identities, then
   * rotates the table so the lord anchors seat number one).
   */
  public static GameState startAssigned(
      List<SgsPlayer> seatedPlayers, List<HeroDefinition> heroesBySeat, Deck deck, Random random) {
    GameState state = create(seatedPlayers, heroesBySeat, deck, random);
    for (SgsPlayer player : state.players) {
      if (player.identity() == null) {
        throw new IllegalArgumentException("startAssigned requires an identity on every seat");
      }
    }
    return begin(state, heroesBySeat);
  }

  /** Project 2v2 initial: public seats 1/4 versus 2/3; identities are not gameplay roles. */
  public static GameState startTwoVTwo(List<SgsPlayer> players,List<HeroDefinition> heroes,Deck deck,Random random){
    if(players.size()!=4)throw new IllegalArgumentException("2v2 needs four seats");
    GameState state=create(players,heroes,deck,random);
    state.mode=dev.sgs.core.room.RoomMode.TWO_V_TWO;
    for(var player:players)player.assignIdentity(Identity.LOYALIST);
    return begin(state,heroes);
  }

  private static GameState create(
      List<SgsPlayer> seatedPlayers, List<HeroDefinition> heroesBySeat, Deck deck, Random random) {
    if (seatedPlayers.size() < MIN_PLAYERS || seatedPlayers.size() > MAX_PLAYERS) {
      throw new IllegalArgumentException(
          "player count must be between " + MIN_PLAYERS + " and " + MAX_PLAYERS);
    }
    if (seatedPlayers.size() != heroesBySeat.size()) {
      throw new IllegalArgumentException("heroes must match player count");
    }
    return new GameState(List.copyOf(seatedPlayers), deck, random);
  }

  private static GameState begin(GameState state, List<HeroDefinition> heroesBySeat) {
    for (int i = 0; i < state.players.size(); i++) {
      SgsPlayer player = state.players.get(i);
      HeroDefinition hero = heroesBySeat.get(i);
      player.assignHero(hero);
      // Standard identity rules: the lord bonus only applies with five or more players.
      int maxHp = hero.maxHp() + (!state.teamMode() && state.players.size() >= 5
          && player.identity() == Identity.LORD ? LORD_EXTRA_HP : 0);
      player.setMaxHp(maxHp);
      player.setHp(maxHp);
      player.setAlive(true);
      player.setIdentityPublic(!state.teamMode() && player.identity() == Identity.LORD);
      state.cards.drawTo(CardZone.hand(player.seat()),INITIAL_HAND_SIZE+(state.teamMode() && i==3?1:0),CardMove.Reason.DRAW,player.seat(),false);
    }

    state.currentSeat = state.teamMode()?0:state.lordSeat();
    state.phase = Phase.PREPARE;
    state.turnNumber = 1;
    state.status = GameStatus.RUNNING;
    if(state.teamMode())state.log("2v2 game started: public teams assigned; first seat 1");
    else state.log("game started with {} players; lord is seat {}",state.players.size(),state.currentSeat);
    return state;
  }

  public GameStatus status() {
    return status;
  }

  public Phase phase() {
    return phase;
  }

  public int turnNumber() {
    return turnNumber;
  }

  public int shaPlayedThisTurn() {
    return shaPlayedThisTurn;
  }

  public void registerShaPlayed() {
    shaPlayedThisTurn++;
  }

  public boolean jiuPlayedThisTurn() {
    return jiuPlayedThisTurn;
  }

  /** Offensive wine usage is a turn allowance, distinct from its unconsumed Sha bonus. */
  public void registerJiuPlayed() { jiuPlayedThisTurn = true; jiuBoostPending = true; }
  public boolean jiuBoostPending() { return jiuBoostPending; }
  /** Called only after a Sha use has been validated and committed, including a later dodge. */
  public boolean consumeJiuBoost() { boolean pending=jiuBoostPending;jiuBoostPending=false;return pending; }

  /** Acedia judged non-heart: this turn's play phase is skipped. */
  public boolean skipPlayPhase() {
    return skipPlayPhase;
  }
  public boolean skipDrawPhase(){return skipDrawPhase;}
  public void setSkipDrawPhase(boolean value){skipDrawPhase=value;}

  public void setSkipPlayPhase(boolean value) {
    skipPlayPhase = value;
  }

  public int currentSeat() {
    return currentSeat;
  }

  public SgsPlayer currentPlayer() {
    return players.get(currentSeat);
  }

  public Deck deck() {
    return deck;
  }
  public CardMovement cards(){return cards;}

  public Random random() {
    return random;
  }

  public List<SgsPlayer> players() {
    return players;
  }

  public List<Seat> seats() {
    List<Seat> seats = new ArrayList<>(players.size());
    for (int i = 0; i < players.size(); i++) {
      seats.add(new Seat(i, players.get(i)));
    }
    return Collections.unmodifiableList(seats);
  }

  public SgsPlayer lord() {
    return teamMode()?null:players.get(lordSeat());
  }

  public int lordSeat() {
    if(teamMode())return -1;
    for (int i = 0; i < players.size(); i++) {
      if (players.get(i).identity() == Identity.LORD) {
        return i;
      }
    }
    throw new IllegalStateException("no lord in game");
  }

  public List<SgsPlayer> alivePlayers() {
    List<SgsPlayer> alive = new ArrayList<>();
    for (SgsPlayer player : players) {
      if (player.alive()) {
        alive.add(player);
      }
    }
    return alive;
  }

  /** Alive players in turn order starting after the given seat (exclusive). */
  public List<SgsPlayer> alivePlayersAfter(int seat) {
    List<SgsPlayer> ordered = new ArrayList<>();
    int n = players.size();
    for (int step = 1; step < n; step++) {
      SgsPlayer player = players.get((seat + step) % n);
      if (player.alive()) {
        ordered.add(player);
      }
    }
    return ordered;
  }

  /** Alive players in ask order: the current turn player first, then the ring. */
  public List<SgsPlayer> alivePlayersFromCurrent() {
    List<SgsPlayer> ordered = new ArrayList<>();
    SgsPlayer current = currentPlayer();
    if (current.alive()) {
      ordered.add(current);
    }
    ordered.addAll(alivePlayersAfter(currentSeat));
    return ordered;
  }

  /** Alive ring order starting at the given seat (inclusive). */
  public List<SgsPlayer> alivePlayersFromSeat(int seat) {
    List<SgsPlayer> ordered = new ArrayList<>();
    SgsPlayer start = players.get(seat);
    if (start.alive()) {
      ordered.add(start);
    }
    ordered.addAll(alivePlayersAfter(seat));
    return ordered;
  }

  /**
   * Seat distance across alive players with horse modifiers: the attacker's -1 horse shortens the
   * distance by one, the defender's +1 horse lengthens it by one; the floor is 1 for distinct
   * players.
   */
  public int distance(SgsPlayer from, SgsPlayer to) {return distanceExcluding(from,to,null);}

  /** Exclude a physical equipment cost before applying the common minimum distance. */
  public int distanceExcluding(SgsPlayer from,SgsPlayer to,dev.sgs.core.card.CardInstance excluded) {
    if (from == to) {
      return 0;
    }
    int raw = ringDistance(from, to)+skillDistance.applyAsInt(from);
    if(hasSkill(from,"mashu"))raw--;
    if(hasSkill(from,"yicong") && from.hp()>2)raw--;
    if(hasSkill(to,"yicong") && to.hp()<=2)raw++;
    if (from.equipment().hasOffensiveHorse() && from.equipment().horseMinus()!=excluded) {
      raw -= 1;
    }
    if (to.equipment().hasDefensiveHorse() && to.equipment().horsePlus()!=excluded) {
      raw += 1;
    }
    return Math.max(raw, 1);
  }

  /** Ring distance across alive players; dead seats do not count as positions. */
  public int ringDistance(SgsPlayer from, SgsPlayer to) {
    List<SgsPlayer> ring = alivePlayers();
    int fromIndex = -1;
    int toIndex = -1;
    for (int i = 0; i < ring.size(); i++) {
      if (ring.get(i) == from) {
        fromIndex = i;
      }
      if (ring.get(i) == to) {
        toIndex = i;
      }
    }
    if (fromIndex < 0 || toIndex < 0) {
      throw new IllegalArgumentException("distance requires two alive players");
    }
    int diff = Math.abs(fromIndex - toIndex);
    return Math.min(diff, ring.size() - diff);
  }

  /** Attack range of a seat: the equipped weapon's range, or 1 bare-handed. */
  public int attackRange(SgsPlayer player) {
    return player.equipment().attackRange();
  }

  /** Whether {@code from} can target {@code to} with a Sha. */
  public boolean inBasicAttackRange(SgsPlayer from, SgsPlayer to) {
    return from != to && distance(from, to) <= attackRange(from);
  }

  public int nextAliveSeat(int seat) {
    int n = players.size();
    for (int step = 1; step <= n; step++) {
      int candidate = (seat + step) % n;
      if (players.get(candidate).alive()) {
        return candidate;
      }
    }
    throw new IllegalStateException("no alive seat found");
  }

  /** Advances one phase, applying draw/discard side effects on entry. */
  public void advancePhase() {
    advancePhase(true);
  }

  /** Engine uses false so draw-start skills execute BEFORE the normal draw effect. */
  public void advancePhase(boolean drawOnEntry) {
    if (status != GameStatus.RUNNING) {
      throw new IllegalStateException("game is not running");
    }
    switch (phase) {
      case PREPARE -> phase = Phase.JUDGMENT;
      case JUDGMENT -> {
        phase = Phase.DRAW;
        if(drawOnEntry && !skipDrawPhase) {
          cards.drawTo(CardZone.hand(currentSeat),normalDrawCount(),CardMove.Reason.DRAW,currentSeat,false);
          log("seat {} drew {} cards", currentSeat, DRAW_PER_TURN);
        }
      }
      case DRAW -> {
        phase = Phase.PLAY;
        shaPlayedThisTurn = 0;
      }
        // Entering discard does not auto-discard: players confirm their own
        // cards; timeout fallback calls enforceDiscardLimit() explicitly.
      case PLAY -> phase = Phase.DISCARD;
      case DISCARD -> phase = Phase.END;
      case END -> {
        jiuPlayedThisTurn = false;
        jiuBoostPending = false;
        currentSeat = nextTurn.applyAsInt(nextAliveSeat(currentSeat));
        turnNumber++;
        phase = Phase.PREPARE;
        skipPlayPhase = false;
        skipDrawPhase = false;
        log("turn {} starts at seat {}", turnNumber, currentSeat);
      }
      default -> throw new IllegalStateException("cannot advance from phase " + phase);
    }
  }

  /**
   * Discards cards in excess of current hp. Automatic discard used when the play phase ends; card
   * choice is deterministic (earliest hand positions) because no player input is available.
   */
  public List<CardInstance> enforceDiscardLimit() {
    SgsPlayer player = currentPlayer();
    int excess = player.handCount() - handLimit(player);
    if (excess <= 0) {
      return List.of();
    }
    List<CardInstance> discarded = new ArrayList<>(excess);
    List<CardInstance> hand = new ArrayList<>(player.handView());
    discarded.addAll(hand.subList(0,excess));
    cards.move(discarded,CardZone.hand(currentSeat),CardZone.DISCARD,CardMove.Reason.DISCARD,currentSeat);
    log("seat {} auto-discarded {} cards", player.seat(), discarded.size());
    return Collections.unmodifiableList(discarded);
  }

  public void finish() {
    status = GameStatus.FINISHED;
    phase = Phase.NONE;
  }

  public void log(String message) {
    logs.addLast(message);
    while (logs.size() > LOG_LIMIT) {
      logs.removeFirst();
    }
    for (Consumer<String> sink : logSinks) { sink.accept(message); }
    for (Consumer<String> sink : diagnosticLogSinks) { sink.accept(message); }
  }

  /** Subscribes an external observer to every formatted game log line. */
  public void addLogSink(Consumer<String> sink) {
    if (sink != null) {
      logSinks.add(sink);
    }
  }

  public void log(String pattern, Object... args) {
    // Only known public facts enter the player journal. Diagnostics never cross this boundary.
    String entry=switch(pattern){
      case "turn {} starts at seat {}" -> journal.seat((int)args[1])+"的回合开始";
      case "game started with {} players; lord is seat {}", "2v2 game started: public teams assigned; first seat 1" -> "对局开始";
      case "seat {} took {} damage (hp {}) nature={} chain={} source={}" -> journal.seat((int)args[0])+"受到"+((int)args[5]>=0?"来自"+journal.seat((int)args[5])+"的":"")+args[1]+"点伤害，剩余体力"+args[2];
      case "seat {} lost {} hp (hp {})" -> journal.seat((int)args[0])+"失去"+args[1]+"点体力，剩余体力"+args[2];
      case "seat {} died as {}", "seat {} died on team {}" -> journal.seat((int)args[0])+"阵亡";
      case "seat {} survived near-death with hp {}" -> journal.seat((int)args[0])+"脱离濒死，剩余体力"+args[1];
      case "seat {} turns over: {}" -> journal.seat((int)args[0])+"翻面";
      case "seat {} dodged the {} from seat {}" -> journal.seat((int)args[0])+"闪避了"+journal.seat((int)args[2])+"的杀";
      case "seat {} skips draw phase due to bingliang" -> journal.seat((int)args[0])+"因兵粮寸断跳过摸牌阶段";
      case "seat {} is acedia-bound and skips the play phase" -> journal.seat((int)args[0])+"因乐不思蜀跳过出牌阶段";
      case "game finished: {}" -> "对局结束";
      default -> null;
    };
    if(entry!=null)journal.add(entry);
    log(simpleFormat(pattern, args));
  }

  public List<String> recentLogs() {
    return List.copyOf(logs);
  }

  private static String simpleFormat(String pattern, Object... args) {
    if (args.length == 0) {
      return pattern;
    }
    StringBuilder builder = new StringBuilder();
    int argIndex = 0;
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      if (c == '{'
          && i + 1 < pattern.length()
          && pattern.charAt(i + 1) == '}'
          && argIndex < args.length) {
        builder.append(args[argIndex++]);
        i++;
      } else {
        builder.append(c);
      }
    }
    return builder.toString();
  }
}
