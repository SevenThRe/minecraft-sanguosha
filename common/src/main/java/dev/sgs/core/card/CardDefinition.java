package dev.sgs.core.card;

import java.io.Serializable;
import java.util.Objects;

/**
 * Static, shareable definition of a card kind. The effect id equals the type id for the standard
 * deck; it stays explicit so data packs can later map definitions to code-registered effects.
 */
public final class CardDefinition implements Serializable {

  private final String id;
  private final CardType type;
  private final String effectId;

  public CardDefinition(String id, CardType type, String effectId) {
    this.id = Objects.requireNonNull(id);
    this.type = Objects.requireNonNull(type);
    this.effectId = Objects.requireNonNull(effectId);
  }

  public static CardDefinition basic(CardType type) {
    return new CardDefinition(type.id(), type, type.id());
  }

  public String id() {
    return id;
  }

  public CardType type() {
    return type;
  }

  public String effectId() {
    return effectId;
  }
}
