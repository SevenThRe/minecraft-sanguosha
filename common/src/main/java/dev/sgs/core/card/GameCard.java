package dev.sgs.core.card;

import java.util.List;

/**
 * A card as seen by the settlement chain (play / respond / damage). {@link CardInstance} is the
 * physical side; {@link VirtualCard} is the view-as / skill-generated side. Skills and equipment
 * must read suit, rank and color through this interface so a transformed card reports its effective
 * values instead of the physical ones.
 *
 * <p>Suit/rank/color derivation for virtual cards:
 *
 * <pre>
 * sources | suit()        | rank() | color()
 * --------+---------------+--------+--------------------------
 * 0       | Suit.NONE     | null   | Color.NONE (colorless)
 * 1       | inherited     | inherited | inherited
 * 2+ red  | Suit.NONE     | null   | Color.RED
 * 2+ black| Suit.NONE     | null   | Color.BLACK
 * 2+ mixed| Suit.NONE     | null   | Color.NONE
 * </pre>
 *
 * Note that {@link Suit#NONE} does not imply {@link Color#NONE}: two red cards of different suits
 * merge into a colorless-suited but still red card.
 *
 * <p>Zones (hand, equipment, delay zone, discard pile) store only {@link CardInstance}. Cards
 * discarded outside the settlement chain are never wrapped into a {@code GameCard}; only use / play
 * / damage flows operate on this interface.
 */
public interface GameCard {

  /** The effective type: a Shan regarded as a Sha reports {@link CardType#SHA}. */
  CardType type();

  /** The effective suit; {@link Suit#NONE} unless exactly one physical source exists. */
  Suit suit();

  /** The effective rank; {@code null} unless exactly one physical source exists. */
  Rank rank();

  /** The effective color; {@link Color#NONE} for colorless (pure virtual or mixed-source). */
  Color color();

  default boolean isRed() {
    return color() == Color.RED;
  }

  default boolean isBlack() {
    return color() == Color.BLACK;
  }

  default boolean isColorless() {
    return color() == Color.NONE;
  }

  /** True for {@link VirtualCard}, false for {@link CardInstance}. */
  boolean isVirtual();

  default boolean isPhysical() {
    return !isVirtual();
  }

  /** Physical Iron Chain supports recast; a view-as skill must explicitly grant it. */
  default boolean canRecast(){return type()==CardType.TIESUO && !isVirtual();}

  /**
   * The physical cards behind this card: {@code [this]} for a physical card, the conversion sources
   * for a transformed card, or empty for a pure virtual (skill-generated) card. An empty list means
   * nothing physical can be obtained from this card (e.g. by Jianxiong).
   */
  List<CardInstance> sourceCards();
}
