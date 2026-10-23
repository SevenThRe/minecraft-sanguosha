package dev.sgs.core.persistence;

import dev.sgs.core.*;
import dev.sgs.core.card.*;
import dev.sgs.core.game.GameState;
import dev.sgs.core.hero.HeroDefinition;
import dev.sgs.core.match.MatchPlayer;
import dev.sgs.core.player.SgsPlayer;
import java.util.*;

/** Authoritative rules data, never a client view. No engine frame/skill/transport state implied. */
public record StateCheckpoint(int version,String contentVersion,GameState.TurnState turn,
    List<Player> players,List<Zone> zones,Set<Long> faceUpProcessing,long movementSequence,
    byte[] gameRandom,byte[] deckRandom,boolean sharedRandom,List<Long> damageTotals,boolean completeStatistics,dev.sgs.core.match.dto.MatchViews.DraftView draft){
  public StateCheckpoint(int version,String contentVersion,GameState.TurnState turn,List<Player> players,List<Zone> zones,Set<Long> faceUpProcessing,long movementSequence,byte[] gameRandom,byte[] deckRandom,boolean sharedRandom){this(version,contentVersion,turn,players,zones,faceUpProcessing,movementSequence,gameRandom,deckRandom,sharedRandom,Collections.nCopies(8,0L),false,null);}

  public static final int VERSION=3,MAX_CARDS=4096;
  public record Card(long id,String definitionId,CardType type,String effectId,Suit suit,Rank rank,CardType judgmentType){
    public Card(long id,String definitionId,CardType type,String effectId,Suit suit,Rank rank){this(id,definitionId,type,effectId,suit,rank,null);}
    static Card of(CardInstance c){return new Card(c.instanceId(),c.definition().id(),c.type(),c.definition().effectId(),c.suit(),c.rank());}
    static Card ofJudgment(CardInstance c,CardType effective){return new Card(c.instanceId(),c.definition().id(),c.type(),c.definition().effectId(),c.suit(),c.rank(),effective);}
    CardInstance create(){return new CardInstance(id,new CardDefinition(definitionId,type,effectId),suit,rank);}
  }
  public record Player(UUID id,String name,int seat,boolean human,boolean online,Identity identity,
      HeroDefinition hero,int hp,int maxHp,boolean alive,boolean identityPublic){}
  public record Zone(CardZone location,List<Card> cards){public Zone{cards=List.copyOf(cards);}}
  public StateCheckpoint{
    damageTotals=List.copyOf(damageTotals);if(damageTotals.size()!=8||damageTotals.stream().anyMatch(v->v<0))throw new IllegalArgumentException("invalid statistics");
    players=List.copyOf(players);zones=List.copyOf(zones);faceUpProcessing=Set.copyOf(faceUpProcessing);
    gameRandom=gameRandom.clone();deckRandom=deckRandom.clone();
  }
  @Override public byte[] gameRandom(){return gameRandom.clone();}
  @Override public byte[] deckRandom(){return deckRandom.clone();}

  /** Call on the owning worker at an actual safe point; there is deliberately no concurrent traversal. */
  public static StateCheckpoint capture(GameState game,String contentVersion){
    var players=game.players().stream().map(p->new Player(p.id(),p.name(),p.seat(),
      p instanceof MatchPlayer m && m.human(),p.online(),p.identity(),p.hero(),p.hp(),p.maxHp(),p.alive(),p.identityPublic())).toList();
    var zones=game.cards().zones().stream().map(z->new Zone(z,game.cards().cards(z).stream()
        .map(c->z.kind()==CardZone.Kind.JUDGMENT?Card.ofJudgment(c,game.players().get(z.seat()).delayZone().effectiveType(c)):Card.of(c)).toList())).toList();
    var publicIds=game.cards().publicProcessingCards().stream().map(CardInstance::instanceId).collect(java.util.stream.Collectors.toSet());
    boolean shared=game.random()==game.deck().checkpointRandom();
    var checkpoint=new StateCheckpoint(game.presentationCheckpointVersion(),contentVersion,game.checkpointTurnState(),players,zones,publicIds,
      game.cards().checkpointSequence(),RandomCheckpoint.capture(game.random()),
      shared?new byte[0]:RandomCheckpoint.capture(game.deck().checkpointRandom()),shared,game.damageTotals(),game.completeStatistics(),game.tablePresentation().draft());
    checkpoint.validate();return checkpoint;
  }

  public void validate(){
    if(version!=2&&version!=VERSION)throw new IllegalArgumentException("unsupported checkpoint version "+version);
    text(contentVersion,128);Objects.requireNonNull(turn);
    if(draft!=null){
      if(!draft.active()||draft.slots().isEmpty()||draft.slots().size()>8||draft.source()<0||draft.source()>=players.size()||draft.picker()<0||draft.picker()>=players.size()||!draft.title().equals("五谷丰登"))throw new IllegalArgumentException("invalid draft");
      var ids=new HashSet<Long>();for(var slot:draft.slots()){if(!ids.add(slot.card().instanceId())||slot.recipient()< -1||slot.recipient()>=players.size())throw new IllegalArgumentException("invalid draft slot");
        var physical=zones.stream().flatMap(z->z.cards().stream()).filter(c->c.id()==slot.card().instanceId()).findFirst().orElseThrow(()->new IllegalArgumentException("unknown draft card"));
        if(physical.type().ordinal()!=slot.card().typeOrdinal()||physical.suit().ordinal()!=slot.card().suitOrdinal()||physical.rank().ordinal()!=slot.card().rankOrdinal())throw new IllegalArgumentException("draft face mismatch");
      }
    }

    if(players.size()<2 || players.size()>8 || zones.size()<3+players.size()*3 || zones.size()>3+players.size()*4 || movementSequence<0)
      throw new IllegalArgumentException("invalid checkpoint dimensions");
    var expectedZones=new HashSet<>(List.of(CardZone.DRAW,CardZone.DISCARD,CardZone.PROCESSING));
    var playerIds=new HashSet<UUID>();
    for(int seat=0;seat<players.size();seat++){
      var p=players.get(seat);text(p.name(),256);Objects.requireNonNull(p.hero());Objects.requireNonNull(p.identity());
      text(p.hero().id(),128);Objects.requireNonNull(p.hero().kingdom());
      if(p.id()==null || !playerIds.add(p.id()) || p.seat()!=seat || p.maxHp()<1 || p.maxHp()>1000
          || p.hp()>p.maxHp() || p.hp() < -1_000_000 || p.hero().maxHp()>1000 || p.hero().skillIds().size()>64
          || (!p.alive() && p.hp()>0))throw new IllegalArgumentException("invalid player state");
      p.hero().skillIds().forEach(id->text(id,128));
      expectedZones.add(CardZone.hand(seat));expectedZones.add(CardZone.equipment(seat));expectedZones.add(CardZone.judgment(seat));
    }
    var seenZones=new HashSet<CardZone>();var ids=new HashSet<Long>();var processing=new HashSet<Long>();
    for(var z:zones)if(z.location().kind()==CardZone.Kind.SPECIAL){
      if(z.location().seat()>=players.size() || z.cards().isEmpty())throw new IllegalArgumentException("invalid special zone");
      expectedZones.add(z.location());
    }
    for(var z:zones){
      if(!expectedZones.contains(z.location()) || !seenZones.add(z.location()))throw new IllegalArgumentException("invalid/duplicate zone");
      var occupied=new HashSet<String>();
      for(var card:z.cards()){
        text(card.definitionId(),256);text(card.effectId(),256);Objects.requireNonNull(card.type());Objects.requireNonNull(card.rank());Objects.requireNonNull(card.suit());
        if(card.id()<=0 || card.id()==Long.MAX_VALUE || !ids.add(card.id()) || card.suit()==Suit.NONE)
          throw new IllegalArgumentException("invalid/duplicate physical card");
        if(z.location().kind()!=CardZone.Kind.JUDGMENT && card.judgmentType()!=null)
          throw new IllegalArgumentException("delayed effect outside judgment zone");
        if(ids.size()>MAX_CARDS)throw new IllegalArgumentException("too many cards");
        if(z.location().kind()==CardZone.Kind.EQUIPMENT){
          if(!card.type().isEquipment() || !occupied.add(card.type().category().name()))throw new IllegalArgumentException("invalid equipment slot");
        }else if(z.location().kind()==CardZone.Kind.JUDGMENT){
          var effective=card.judgmentType()==null?card.type():card.judgmentType();
          if(effective.category()!=CardCategory.DELAYED_TRICK || !occupied.add(effective.name()))throw new IllegalArgumentException("invalid judgment slot");
        }else if(z.location().equals(CardZone.PROCESSING))processing.add(card.id());
      }
    }
    if(ids.isEmpty() || !seenZones.equals(expectedZones) || !processing.containsAll(faceUpProcessing))throw new IllegalArgumentException("invalid physical partition");
    if(turn.jiuBoost() && !turn.jiuPlayed())throw new IllegalArgumentException("wine flags inconsistent");
    for(var line:turn.logs())text(line,4096);
    // Validate scalars/seat/chains without mutating the caller's current state.
    var dummy=players.stream().map(StateCheckpoint::restorePlayer).map(p->(SgsPlayer)p).toList();
    GameState.restoreCheckpoint(dummy,Deck.restoreOrdered(List.of(),List.of(),new Random(0)),new Random(0),turn);
    RandomCheckpoint.restore(gameRandom);
    if(sharedRandom){if(deckRandom.length!=0)throw new IllegalArgumentException("shared RNG has duplicate payload");}
    else RandomCheckpoint.restore(deckRandom);
  }
  static void text(String text,int max){
    if(text==null || text.isEmpty() || text.length()>max)throw new IllegalArgumentException("invalid text field");
    for(int i=0;i<text.length();i++){
      char c=text.charAt(i);
      if(Character.isHighSurrogate(c)){
        if(i+1>=text.length() || !Character.isLowSurrogate(text.charAt(++i)))throw new IllegalArgumentException("invalid unicode text");
      }else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("invalid unicode text");
    }
  }
  private static MatchPlayer restorePlayer(Player p){
    var player=new MatchPlayer(p.id(),p.name(),p.seat(),p.human());player.assignHero(p.hero());player.assignIdentity(p.identity());
    player.setMaxHp(p.maxHp());player.setHp(p.hp());player.setAlive(p.alive());player.setOnline(p.online());player.setIdentityPublic(p.identityPublic());return player;
  }
  public long nextPhysicalId(){return zones.stream().flatMap(z->z.cards().stream()).mapToLong(Card::id).max().orElse(0)+1;}
  public GameState restore(){
    validate();var gameRng=RandomCheckpoint.restore(gameRandom);var deckRng=sharedRandom?gameRng:RandomCheckpoint.restore(deckRandom);
    var restoredPlayers=players.stream().map(StateCheckpoint::restorePlayer).map(p->(SgsPlayer)p).toList();
    var data=new HashMap<CardZone,List<CardInstance>>();
    for(var zone:zones)data.put(zone.location(),zone.cards().stream().map(Card::create).toList());
    var deck=Deck.restoreOrdered(data.get(CardZone.DRAW),data.get(CardZone.DISCARD),deckRng);
    for(var player:restoredPlayers){
      player.addCards(data.get(CardZone.hand(player.seat())));
      data.get(CardZone.equipment(player.seat())).forEach(player.equipment()::equip);
      var savedZone=zones.stream().filter(z->z.location().equals(CardZone.judgment(player.seat()))).findFirst().orElseThrow();
      for(int i=0;i<savedZone.cards().size();i++){
        var saved=savedZone.cards().get(i);var physical=data.get(savedZone.location()).get(i);
        player.delayZone().add(physical,saved.judgmentType()==null?saved.type():saved.judgmentType());
      }
    }
    var game=GameState.restoreCheckpoint(restoredPlayers,deck,gameRng,turn);
    for(var zone:zones)if(zone.location().kind()==CardZone.Kind.SPECIAL)game.cards().restoreSpecial(zone.location().seat(),data.get(zone.location()));
    game.cards().restoreCheckpointProcessing(data.get(CardZone.PROCESSING),faceUpProcessing,movementSequence);
    game.presentationCheckpointVersion(version);game.restoreDraft(draft);game.restoreStatistics(damageTotals,completeStatistics);game.cards().assertUnique();return game;
  }
}
