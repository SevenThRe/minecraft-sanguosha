package dev.sgs.core.card;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** A unique physical card in a match: definition plus suit and rank. */
public final class CardInstance implements GameCard, Serializable {

  private final long instanceId;
  private final CardDefinition definition;
  private final Suit suit;
  private final Rank rank;
  private final java.util.Map<String,CardSkillMark> skillMarks=new java.util.LinkedHashMap<>();
  public void setSkillMark(CardSkillMark mark){
    Objects.requireNonNull(mark);if(!skillMarks.containsKey(mark.id()) && skillMarks.size()>=8)throw new IllegalArgumentException("Card mark limit8");
    skillMarks.put(mark.id(),mark);
  }
  public void removeSkillMark(String id){skillMarks.remove(id);}
  public List<CardSkillMark> skillMarks(){return skillMarks==null?List.of():List.copyOf(skillMarks.values());}

  public CardInstance(long instanceId, CardDefinition definition, Suit suit, Rank rank) {
    this.instanceId = instanceId;
    this.definition = Objects.requireNonNull(definition);
    this.suit = Objects.requireNonNull(suit);
    this.rank = Objects.requireNonNull(rank);
  }

  public long instanceId() {
    return instanceId;
  }

  public CardDefinition definition() {
    return definition;
  }

  public Suit suit() {
    return suit;
  }

  public Rank rank() {
    return rank;
  }

  public CardType type() {
    return definition.type();
  }

  public String effectId() {
    return definition.effectId();
  }

  /** Stable parseable id, e.g. {@code sha_spade_7}; used by skills, judgments and data packs. */
  public String cardId() {
    return type().id() + "_" + suit.id() + "_" + rank.id();
  }

  public boolean isRed() {
    return suit.red();
  }

  @Override
  public Color color() {
    return suit.red() ? Color.RED : Color.BLACK;
  }

  @Override
  public boolean isVirtual() {
    return false;
  }

  @Override
  public List<CardInstance> sourceCards() {
    return List.of(this);
  }
}
