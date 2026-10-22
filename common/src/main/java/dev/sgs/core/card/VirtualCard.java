package dev.sgs.core.card;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * A settlement card produced by a view-as skill or generated out of thin air. It never exists in
 * any zone; it only lives while its effect settles.
 *
 * <p>Chained transformation is impossible at the type level: the sources are {@link CardInstance}s,
 * and {@code CardInstance} is a final class on the physical side of {@link GameCard}, so a {@code
 * VirtualCard} can never wrap another virtual card. A card is transformed at most once per
 * settlement.
 *
 * <p>Suit, rank and color derive from the sources (see the matrix on {@link GameCard}): zero
 * sources make a colorless card without suit or rank; one source inherits everything; multiple
 * sources keep only the color when it is unanimous.
 */
public final class VirtualCard implements GameCard, Serializable {

  private final CardType type;
  private final List<CardInstance> sources;
  private final boolean recastAllowed;

  public VirtualCard(CardType type, List<CardInstance> sources) {
    this(type,sources,false);
  }

  public VirtualCard(CardType type,List<CardInstance> sources,boolean recastAllowed){
    this.type = Objects.requireNonNull(type, "type");
    this.sources = List.copyOf(sources);
    if(recastAllowed && (type!=CardType.TIESUO || sources.size()!=1))throw new IllegalArgumentException("unsupported recast conversion");
    this.recastAllowed=recastAllowed;
  }
  @Override public boolean canRecast(){return recastAllowed;}

  @Override
  public CardType type() {
    return type;
  }

  @Override
  public Suit suit() {
    return sources.size() == 1 ? sources.get(0).suit() : Suit.NONE;
  }

  @Override
  public Rank rank() {
    return sources.size() == 1 ? sources.get(0).rank() : null;
  }

  @Override
  public Color color() {
    if (sources.isEmpty()) {
      return Color.NONE;
    }
    boolean red = sources.get(0).isRed();
    for (CardInstance source : sources) {
      if (source.isRed() != red) {
        return Color.NONE;
      }
    }
    return red ? Color.RED : Color.BLACK;
  }

  @Override
  public boolean isVirtual() {
    return true;
  }

  @Override
  public List<CardInstance> sourceCards() {
    return sources;
  }
}
