package dev.sgs.core.card;

import dev.sgs.core.player.SgsPlayer;
import java.util.*;
import java.util.function.Consumer;
import static dev.sgs.core.card.CardMove.Reason.*;

/** Actual card locations plus committed move events. Only this service mutates live rule zones. */
public final class CardMovement {
  public record Transfer(List<CardInstance> cards,CardZone from,CardZone to,CardMove.Reason reason,int actor,boolean reveal,CardType judgmentType){
    public Transfer {cards=List.copyOf(cards);}
    public Transfer(List<CardInstance> cards,CardZone from,CardZone to,CardMove.Reason reason,int actor,boolean reveal){this(cards,from,to,reason,actor,reveal,null);}
  }
  private record Processing(CardInstance card,boolean faceUp){}
  private final List<SgsPlayer> players;
  private final Deck deck;
  private final Map<Long,Processing> processing=new LinkedHashMap<>();
  private final Map<Integer,List<CardInstance>> special=new TreeMap<>();
  public void restoreSpecial(int seat,List<CardInstance> values){
    if(ruleListenerRevision!=0 || !recent.isEmpty() || seat<0 || seat>=players.size() || special.containsKey(seat))throw new IllegalStateException("invalid special-zone bootstrap");
    var ids=new HashSet<Long>();for(var c:values)if(!ids.add(c.instanceId()) || locate(c.instanceId())!=null)throw new IllegalArgumentException("duplicate special card");
    special.put(seat,new ArrayList<>(values));
  }
  private final Deque<CardMove> recent=new ArrayDeque<>();
  private final List<Consumer<CardMove>> listeners=new ArrayList<>();
  private long sequence;
  private Consumer<CardMove> ruleListener=ignored->{};
  private long ruleListenerRevision;
  public long ruleListenerRevision(){return ruleListenerRevision;}
  public int observerCount(){return listeners.size();}

  private final Consumer<CardMove> presentation;
  public CardMovement(List<SgsPlayer> players,Deck deck){this(players,deck,ignored->{});}
  public CardMovement(List<SgsPlayer> players,Deck deck,Consumer<CardMove> presentation){this.players=players;this.deck=deck;this.presentation=Objects.requireNonNull(presentation);}
  public void addListener(Consumer<CardMove> listener){listeners.add(Objects.requireNonNull(listener));}
  public void setRuleListener(Consumer<CardMove> listener){ruleListener=Objects.requireNonNull(listener);ruleListenerRevision++;}
  public List<CardMove> recentMoves(){return List.copyOf(recent);}
  public List<CardMove> recentPublicMoves(){return recent.stream().filter(CardMove::publicCards).toList();}
  public List<CardInstance> processingCards(){return processing.values().stream().map(Processing::card).toList();}
  public List<CardInstance> publicProcessingCards(){return processing.values().stream().filter(Processing::faceUp).map(Processing::card).toList();}
  public long checkpointSequence(){return sequence;}
  /** Fresh-state bootstrap only; no move events, rules, draws or effect callbacks are executed. */
  public void restoreCheckpointProcessing(List<CardInstance> ordered,Set<Long> faceUp,long restoredSequence){
    if(sequence!=0 || !processing.isEmpty() || !recent.isEmpty() || !listeners.isEmpty())
      throw new IllegalStateException("movement already active");
    if(restoredSequence<0)throw new IllegalArgumentException("negative movement sequence");
    var ids=new HashSet<Long>();for(var card:ordered)if(!ids.add(card.instanceId()) || locate(card.instanceId())!=null)
      throw new IllegalArgumentException("duplicate physical card");
    if(!ids.containsAll(faceUp))throw new IllegalArgumentException("public card outside processing");
    for(var card:ordered)processing.put(card.instanceId(),new Processing(card,faceUp.contains(card.instanceId())));
    sequence=restoredSequence;
  }

  public List<CardInstance> cards(CardZone zone){
    if(zone.seat()>=players.size())throw new IllegalArgumentException("unknown seat");
    return switch(zone.kind()){
      case DRAW->deck.drawPileView();case DISCARD->List.copyOf(deck.discardPileView());case PROCESSING->processingCards();
      case HAND->List.copyOf(players.get(zone.seat()).handView());
      case EQUIPMENT->List.copyOf(players.get(zone.seat()).equipment().all());
      case JUDGMENT->List.copyOf(players.get(zone.seat()).delayZone().cards());
      case SPECIAL->List.copyOf(special.getOrDefault(zone.seat(),List.of()));
    };
  }
  public CardZone locate(long id){
    CardZone found=null;
    for(var zone:zones())for(var card:cards(zone))if(card.instanceId()==id){
      if(found!=null)throw new IllegalStateException("duplicate card instance "+id);
      found=zone;
    }
    return found;
  }
  public List<CardZone> zones(){
    var zones=new ArrayList<>(List.of(CardZone.DRAW,CardZone.DISCARD,CardZone.PROCESSING));
    for(int i=0;i<players.size();i++){zones.add(CardZone.hand(i));zones.add(CardZone.equipment(i));zones.add(CardZone.judgment(i));}
    special.forEach((seat,cards)->{if(!cards.isEmpty())zones.add(CardZone.special(seat));});
    return List.copyOf(zones);
  }
  public void assertUnique(){
    var seen=new HashSet<Long>();for(var zone:zones())for(var card:cards(zone))
      if(!seen.add(card.instanceId()))throw new IllegalStateException("duplicate card instance "+card.instanceId());
  }
  /** Match-level conservation check, using the physical IDs created by that match's deck. */
  public void assertConserved(Set<Long> expected){
    var actual=new HashSet<Long>();
    for(var zone:zones())for(var card:cards(zone))
      if(!actual.add(card.instanceId()))throw new IllegalStateException("duplicate card instance "+card.instanceId());
    if(!actual.equals(expected))throw new IllegalStateException("physical card set changed: expected="+expected.size()+" actual="+actual.size());
  }
  public void move(List<CardInstance> cards,CardZone from,CardZone to,CardMove.Reason reason,int actor){
    moveBatch(List.of(new Transfer(cards,from,to,reason,actor,true)));
  }
  /** Validate the entire batch, commit every zone, then notify. Listeners never see half a swap. */
  public void moveBatch(List<Transfer> requests){
    var ids=new HashSet<Long>();var actual=new ArrayList<Transfer>();var publicFlags=new ArrayList<Boolean>();
    for(var request:requests){
      if(request.actor()<-1 || request.actor()>=players.size())throw new IllegalArgumentException("unknown actor");
      if(request.from().equals(request.to()))throw new IllegalArgumentException("same source and destination");
      var canonical=new ArrayList<CardInstance>();
      for(var supplied:request.cards()){
        long id=supplied.instanceId();
        if(!ids.add(id))throw new IllegalArgumentException("duplicate source in move batch");
        if(!request.from().equals(locate(id)))throw new IllegalStateException("card not at expected source: "+id);
        canonical.add(cards(request.from()).stream().filter(c->c.instanceId()==id).findFirst().orElseThrow());
      }
      cards(request.to()); // Validate destination before any mutation.
      CardType effective=request.judgmentType();
      if(effective!=null && (request.to().kind()!=CardZone.Kind.JUDGMENT || canonical.size()!=1 || effective.category()!=CardCategory.DELAYED_TRICK))throw new IllegalArgumentException("invalid delayed type override");
      if(effective==null && request.from().kind()==CardZone.Kind.JUDGMENT && request.to().kind()==CardZone.Kind.JUDGMENT && canonical.size()==1)
        effective=players.get(request.from().seat()).delayZone().effectiveType(canonical.getFirst());
      actual.add(new Transfer(canonical,request.from(),request.to(),request.reason(),request.actor(),request.reveal(),effective));
      boolean faceUpSource=request.from().kind()==CardZone.Kind.EQUIPMENT || request.from().kind()==CardZone.Kind.JUDGMENT
          || request.from().equals(CardZone.DISCARD) || (request.from().equals(CardZone.PROCESSING)
          && canonical.stream().allMatch(c->processing.get(c.instanceId()).faceUp()));
      boolean faceUpDestination=request.to().equals(CardZone.DISCARD) || request.to().kind()==CardZone.Kind.EQUIPMENT
          || request.to().kind()==CardZone.Kind.JUDGMENT || (request.to().equals(CardZone.PROCESSING)&&request.reveal());
      publicFlags.add((faceUpSource || faceUpDestination) && !request.to().equals(CardZone.DRAW));
    }
    var occupied=new HashSet<String>();
    for(var zone:zones())if(zone.kind()==CardZone.Kind.EQUIPMENT || zone.kind()==CardZone.Kind.JUDGMENT)
      for(var card:cards(zone))if(!ids.contains(card.instanceId()))occupied.add(slot(zone,card));
    for(var request:actual)for(var card:request.cards()){
      var to=request.to();
      if(to.kind()==CardZone.Kind.EQUIPMENT && !card.type().isEquipment())throw new IllegalArgumentException("not equipment");
      if(to.kind()==CardZone.Kind.JUDGMENT && (request.judgmentType()==null?card.type():request.judgmentType()).category()!=CardCategory.DELAYED_TRICK)throw new IllegalArgumentException("not a delayed trick");
      if((to.kind()==CardZone.Kind.EQUIPMENT || to.kind()==CardZone.Kind.JUDGMENT) && !occupied.add(to.kind()==CardZone.Kind.JUDGMENT?to+":"+(request.judgmentType()==null?card.type():request.judgmentType()):slot(to,card)))
        throw new IllegalStateException("destination slot occupied");
    }
    for(var request:actual)for(var card:request.cards())remove(request.from(),card.instanceId());
    for(var request:actual)for(var card:request.cards()){
      if(request.to().kind()==CardZone.Kind.JUDGMENT)players.get(request.to().seat()).delayZone().add(card,request.judgmentType()==null?card.type():request.judgmentType());
      else add(request.to(),card,request.reveal());
    }
    if(actual.stream().anyMatch(t->t.reason()==SHUFFLE))deck.shuffleDrawPile();
    var committed=new ArrayList<CardMove>();
    for(int i=0;i<actual.size();i++){
      var r=actual.get(i);if(r.cards().isEmpty())continue;
      var event=new CardMove(++sequence,r.from(),r.to(),r.cards(),r.reason(),r.actor(),publicFlags.get(i));
      recent.addLast(event);while(recent.size()>256)recent.removeFirst();committed.add(event);
    }
    // Copy the listener list so a listener may safely register another listener.
    committed.forEach(presentation);
    for(var event:committed){ruleListener.accept(event);for(var listener:List.copyOf(listeners))listener.accept(event);}
  }
  private String slot(CardZone zone,CardInstance card){
    return zone+":"+(zone.kind()==CardZone.Kind.EQUIPMENT?card.type().category():players.get(zone.seat()).delayZone().effectiveType(card));
  }
  private void remove(CardZone from,long id){
    switch(from.kind()){
      case DRAW->deck.removeDraw(id);case DISCARD->deck.removeDiscard(id);case PROCESSING->processing.remove(id);
      case HAND->players.get(from.seat()).removeCard(id);case EQUIPMENT->players.get(from.seat()).equipment().remove(id);
      case JUDGMENT->players.get(from.seat()).delayZone().remove(id);
      case SPECIAL->special.getOrDefault(from.seat(),List.of()).removeIf(c->c.instanceId()==id);
    }
  }
  private void add(CardZone to,CardInstance card,boolean reveal){
    switch(to.kind()){
      case DRAW->deck.placeOnBottom(card);case DISCARD->deck.discard(card);case PROCESSING->processing.put(card.instanceId(),new Processing(card,reveal));
      case HAND->players.get(to.seat()).addCard(card);case EQUIPMENT->players.get(to.seat()).equipment().equip(card);
      case JUDGMENT->players.get(to.seat()).delayZone().add(card);
      case SPECIAL->special.computeIfAbsent(to.seat(),ignored->new ArrayList<>()).add(card);
    }
  }
  /** Opening setup only: select replacements before old cards enter the shuffled draw pile. */
  public List<CardInstance> replaceOpeningHand(SgsPlayer player){
    int seat=player.seat();
    if(seat<0 || seat>=players.size() || players.get(seat)!=player)throw new IllegalArgumentException("unknown player");
    if(ruleListenerRevision!=0)throw new IllegalStateException("opening replacement requires unbound engine");
    var old=List.copyOf(player.handView());
    if(old.isEmpty() || deck.drawPileCount()<old.size())throw new IllegalStateException("insufficient opening replacements");
    var replacement=deck.topCards(old.size());
    moveBatch(List.of(new Transfer(old,CardZone.hand(seat),CardZone.DRAW,SHUFFLE,seat,false),
        new Transfer(replacement,CardZone.DRAW,CardZone.hand(seat),DRAW,seat,false)));
    return replacement;
  }

  public List<CardInstance> drawTo(CardZone to,int count,CardMove.Reason reason,int actor,boolean reveal){
    if(count<0)throw new IllegalArgumentException("negative draw");
    cards(to);
    if(actor<-1 || actor>=players.size())throw new IllegalArgumentException("unknown actor");
    var drawn=new ArrayList<CardInstance>();
    for(int i=0;i<count;i++){
      recycleIfEmpty();
      if(deck.drawPileCount()==0)break;
      var card=deck.topCards(1).getFirst();
      moveBatch(List.of(new Transfer(List.of(card),CardZone.DRAW,to,reason,actor,reveal)));drawn.add(card);
      // Form the next pile as soon as the last card leaves, not on a later draw request.
      recycleIfEmpty();
    }
    return List.copyOf(drawn);
  }
  private void recycleIfEmpty(){
    if(deck.drawPileCount()==0 && deck.discardCount()>0)
      move(new ArrayList<>(deck.discardPileView()),CardZone.DISCARD,CardZone.DRAW,SHUFFLE,-1);
  }
  public void beginUse(SgsPlayer player,GameCard card,CardMove.Reason reason){move(card.sourceCards(),CardZone.hand(player.seat()),CardZone.PROCESSING,reason,player.seat());}
  public void finish(GameCard card,int actor){
    var remaining=card.sourceCards().stream().filter(c->processing.containsKey(c.instanceId())).toList();
    if(!remaining.isEmpty())move(remaining,CardZone.PROCESSING,CardZone.DISCARD,FINISH,actor);
  }
  /** Guanxing: restore a private processing pool atomically to ordered top/bottom, never reveal it. */
  public void returnPrivateDeckCards(List<CardInstance> top,List<CardInstance> bottom,int actor){
    if(actor<0 || actor>=players.size())throw new IllegalArgumentException("unknown actor");
    var all=new ArrayList<CardInstance>();all.addAll(top);all.addAll(bottom);var ids=new HashSet<Long>();
    for(var card:all){var found=processing.get(card.instanceId());
      if(!ids.add(card.instanceId()) || found==null || found.faceUp())throw new IllegalArgumentException("not a distinct private processing card");}
    var canonicalTop=top.stream().map(c->processing.get(c.instanceId()).card()).toList();
    var canonicalBottom=bottom.stream().map(c->processing.get(c.instanceId()).card()).toList();
    all.forEach(c->processing.remove(c.instanceId()));deck.placeOnTop(canonicalTop);deck.placeOnBottom(canonicalBottom);
    // All placement is committed before observers see either private movement receipt.
    var events=new ArrayList<CardMove>();
    for(var group:List.of(canonicalTop,canonicalBottom))if(!group.isEmpty()){
      var move=new CardMove(++sequence,CardZone.PROCESSING,CardZone.DRAW,group,SKILL_GIVE,actor,false);
      recent.addLast(move);while(recent.size()>256)recent.removeFirst();events.add(move);
    }
    events.forEach(presentation);
    for(var event:events){ruleListener.accept(event);for(var listener:List.copyOf(listeners))listener.accept(event);}
  }
  /** Damage-card acquisition is all-or-nothing; no sources or an already moved source yields 0. */
  public int obtainProcessing(SgsPlayer recipient,GameCard card){
    if(card==null || card.sourceCards().isEmpty() || card.sourceCards().stream().anyMatch(c->!processing.containsKey(c.instanceId())))return 0;
    move(card.sourceCards(),CardZone.PROCESSING,CardZone.hand(recipient.seat()),EFFECT_GAIN,recipient.seat());return card.sourceCards().size();
  }
  public CardInstance replaceJudgment(SgsPlayer actor,CardInstance previous,CardInstance replacement){
    var source=locate(replacement.instanceId());
    if(source==null || source.seat()!=actor.seat() || (source.kind()!=CardZone.Kind.HAND && source.kind()!=CardZone.Kind.EQUIPMENT))
      throw new IllegalStateException("judgment replacement must be owned by actor");
    var canonical=cards(source).stream().filter(c->c.instanceId()==replacement.instanceId()).findFirst().orElseThrow();
    var changes=new ArrayList<Transfer>();
    if(previous!=null)changes.add(new Transfer(List.of(previous),CardZone.PROCESSING,CardZone.DISCARD,JUDGMENT_REPLACE,actor.seat(),true));
    changes.add(new Transfer(List.of(canonical),source,CardZone.PROCESSING,JUDGMENT_REPLACE,actor.seat(),true));moveBatch(changes);return canonical;
  }
  /** Guidao exchanges the old judgment and the replacement in one committed movement. */
  public CardInstance exchangeJudgment(SgsPlayer actor,CardInstance previous,CardInstance replacement){
    var source=locate(replacement.instanceId());
    if(source==null||source.seat()!=actor.seat()||(source.kind()!=CardZone.Kind.HAND&&source.kind()!=CardZone.Kind.EQUIPMENT))throw new IllegalArgumentException("unowned judgment replacement");
    var changes=new ArrayList<Transfer>();
    if(previous!=null)changes.add(new Transfer(List.of(previous),CardZone.PROCESSING,CardZone.hand(actor.seat()),JUDGMENT_REPLACE,actor.seat(),true));
    changes.add(new Transfer(List.of(replacement),source,CardZone.PROCESSING,JUDGMENT_REPLACE,actor.seat(),true));moveBatch(changes);return replacement;
  }
}
