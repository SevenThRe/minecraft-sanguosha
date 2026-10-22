package dev.sgs.core.card;

/** Broad card classes. Delayed tricks sit in the judgment zone before resolving. */
public enum CardCategory {
  BASIC,
  TRICK,
  DELAYED_TRICK,
  WEAPON,
  ARMOR,
  HORSE_PLUS,
  HORSE_MINUS;

  public boolean isEquipment() {
    return this == WEAPON || this == ARMOR || this == HORSE_PLUS || this == HORSE_MINUS;
  }
}
