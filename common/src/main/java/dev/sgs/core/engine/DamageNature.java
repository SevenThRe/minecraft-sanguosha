package dev.sgs.core.engine;

import dev.sgs.core.card.GameCard;

/** Damage properties belong to the damage event, including card-less lightning/skill damage. */
public enum DamageNature {
  NORMAL, FIRE, THUNDER;

  public boolean elemental() { return this != NORMAL; }

  public static DamageNature of(GameCard card) {
    if (card == null) return NORMAL;
    return switch (card.type()) {
      case HUOSHA,HUOGONG -> FIRE;
      case LEISHA, SHANDIAN -> THUNDER;
      default -> NORMAL;
    };
  }
}
