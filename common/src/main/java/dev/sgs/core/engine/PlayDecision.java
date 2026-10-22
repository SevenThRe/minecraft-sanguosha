package dev.sgs.core.engine;

import dev.sgs.core.card.CardType;
import dev.sgs.core.card.GameCard;

/**
 * A card committed in response to a prompt. {@code card} is the settlement-side view: a physical
 * card for plain answers, a virtual card after a confirmed alternation (e.g. Longdan), so {@code
 * playedAs} always equals {@code card.type()}.
 */
public record PlayDecision(GameCard card, CardType playedAs) {

  public static PlayDecision asIs(GameCard card) {
    return new PlayDecision(card, card.type());
  }

  public static PlayDecision alternate(GameCard card, CardType playedAs) {
    return new PlayDecision(card, playedAs);
  }

  public CardType effectiveType() {
    return playedAs;
  }

  public boolean isAlternation() {
    return playedAs != card.type();
  }
}
