package dev.sgs.core.card;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Draw pile plus discard pile. When the draw pile is empty the discard pile is shuffled back in, so
 * no card instance is ever lost while it resides in the deck. Cards currently held by players are
 * not part of either pile.
 */
public final class Deck {

  private final Deque<CardInstance> drawPile = new ArrayDeque<>();
  private final List<CardInstance> discardPile = new ArrayList<>();
  private final Random random;

  public Deck(Collection<CardInstance> cards, Random random) {
    this.random = random;
    List<CardInstance> shuffled = new ArrayList<>(cards);
    Collections.shuffle(shuffled, random);
    drawPile.addAll(shuffled);
  }

  /** Checkpoint bootstrap: supplied draw order is top first; must never consume RNG/shuffle. */
  public static Deck restoreOrdered(List<CardInstance> draw,List<CardInstance> discard,Random random){
    Deck deck=new Deck(List.of(),java.util.Objects.requireNonNull(random));
    var seen=new java.util.HashSet<Long>();
    for(var card:draw)if(!seen.add(card.instanceId()))throw new IllegalArgumentException("duplicate physical card");
    for(var card:discard)if(!seen.add(card.instanceId()))throw new IllegalArgumentException("duplicate physical card");
    deck.drawPile.addAll(List.copyOf(draw));deck.discardPile.addAll(List.copyOf(discard));return deck;
  }
  /** Server persistence only, never a client-facing random seed API. */
  public Random checkpointRandom(){return random;}

  public int drawPileCount() {
    return drawPile.size();
  }

  public int discardCount() {
    return discardPile.size();
  }

  /** Draws one card, recycling the discard pile first if necessary. */
  public CardInstance draw() {
    if (drawPile.isEmpty()) {
      recycle();
    }
    if (drawPile.isEmpty()) {
      throw new IllegalStateException("deck exhausted: no cards left to draw");
    }
    return drawPile.pop();
  }

  public List<CardInstance> draw(int count) {
    validateAvailable(count);
    List<CardInstance> drawn = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      drawn.add(draw());
    }
    return drawn;
  }

  public void discard(CardInstance card) {
    discardPile.add(card);
  }

  /** Puts a card on top of the draw pile; used by tests and future scrying-style skills. */
  public void placeOnTop(CardInstance card) {
    drawPile.push(card);
  }

  /** First element will be drawn first; unlike repeated push(), preserves supplied order. */
  public void placeOnTop(List<CardInstance> cards) {
    List<CardInstance> checked=List.copyOf(cards);
    for(int index=checked.size()-1;index>=0;index--) drawPile.addFirst(checked.get(index));
  }

  public void placeOnBottom(CardInstance card) { drawPile.addLast(card); }

  /** Supplied order is top-to-bottom; the last element becomes the absolute bottom. */
  public void placeOnBottom(List<CardInstance> cards) { drawPile.addAll(List.copyOf(cards)); }

  public List<CardInstance> topCards(int count) {
    if(count<0) throw new IllegalArgumentException("negative card count");
    return drawPile.stream().limit(count).toList();
  }

  /** Non-mutating, bottom-first view; never shuffles/recycles to satisfy a peek. */
  public List<CardInstance> bottomCards(int count) {
    if(count<0) throw new IllegalArgumentException("negative card count");
    List<CardInstance> cards=new ArrayList<>();
    var iterator=drawPile.descendingIterator();
    while(iterator.hasNext() && cards.size()<count) cards.add(iterator.next());
    return List.copyOf(cards);
  }

  public CardInstance drawBottom() {
    if(drawPile.isEmpty()) recycle();
    if(drawPile.isEmpty()) throw new IllegalStateException("deck exhausted: no cards left to draw");
    return drawPile.removeLast();
  }

  public List<CardInstance> drawBottom(int count) {
    validateAvailable(count);
    List<CardInstance> cards=new ArrayList<>(count);
    for(int index=0;index<count;index++) cards.add(drawBottom());
    return List.copyOf(cards);
  }

  private void validateAvailable(int count) {
    if(count<0) throw new IllegalArgumentException("negative card count");
    if(count>drawPile.size()+discardPile.size()) throw new IllegalStateException("insufficient cards; piles unchanged");
  }

  public void discardAll(Collection<CardInstance> cards) {
    discardPile.addAll(cards);
  }

  public List<CardInstance> discardPileView() {
    return Collections.unmodifiableList(discardPile);
  }

  /** Server-core only; never include this ordered view in a client snapshot. */
  public List<CardInstance> drawPileView(){return List.copyOf(drawPile);}
  CardInstance removeDraw(long id){return removeById(drawPile,id);}
  CardInstance removeDiscard(long id){return removeById(discardPile,id);}
  private CardInstance removeById(Collection<CardInstance> zone,long id){
    var iterator=zone.iterator();while(iterator.hasNext()){var card=iterator.next();if(card.instanceId()==id){iterator.remove();return card;}}
    throw new IllegalStateException("card absent from deck zone");
  }
  void shuffleDrawPile(){var shuffled=new ArrayList<>(drawPile);Collections.shuffle(shuffled,random);drawPile.clear();drawPile.addAll(shuffled);}

  private void recycle() {
    if (discardPile.isEmpty()) {
      return;
    }
    Collections.shuffle(discardPile, random);
    drawPile.addAll(discardPile);
    discardPile.clear();
  }
}
