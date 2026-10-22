package dev.sgs.core.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.sgs.core.card.CardInstance;

/**
 * The delay-trick zone for one seat. Cards are stored in placement order (FIFO), but the engine
 * settles the zone in reverse placement order (LIFO: the later a delayed trick was attached, the
 * earlier it judges).
 */
public final class DelayZone {

  private final List<CardInstance> cards = new ArrayList<>();
  private final java.util.Map<Long,dev.sgs.core.card.CardType> effectiveTypes=new java.util.HashMap<>();
  public dev.sgs.core.card.CardType effectiveType(CardInstance card){return effectiveTypes.getOrDefault(card.instanceId(),card.type());}
  public void add(CardInstance card,dev.sgs.core.card.CardType effectiveType){
    if(effectiveType==null || effectiveType.category()!=dev.sgs.core.card.CardCategory.DELAYED_TRICK)throw new IllegalArgumentException("invalid delayed type");
    if(cards.stream().anyMatch(c->c.instanceId()==card.instanceId() || effectiveType(c)==effectiveType))throw new IllegalArgumentException("duplicate delayed slot");
    cards.add(card);effectiveTypes.put(card.instanceId(),effectiveType);
  }

  public List<CardInstance> cards() {
    return Collections.unmodifiableList(cards);
  }

  public boolean isEmpty() {
    return cards.isEmpty();
  }

  public int size() {
    return cards.size();
  }

  public void add(CardInstance card) { add(card,card.type()); }

  public CardInstance top() {
    return cards.isEmpty() ? null : cards.getLast();
  }

  public CardInstance removeTop() {
    return cards.isEmpty() ? null : remove(cards.getLast().instanceId());
  }

  /** Snapshot in actual judgment order; callers may remove entries without invalidating it. */
  public List<CardInstance> judgmentOrder() { return List.copyOf(cards.reversed()); }

  public CardInstance remove(long instanceId) {
    for (int i = 0; i < cards.size(); i++) {
      if (cards.get(i).instanceId() == instanceId) {
        effectiveTypes.remove(instanceId);return cards.remove(i);
      }
    }
    return null;
  }

  /** Removes and returns every delayed trick (death cleanup). */
  public List<CardInstance> drain() {
    List<CardInstance> all = new ArrayList<>(cards);
    cards.clear();effectiveTypes.clear();
    return all;
  }
}
