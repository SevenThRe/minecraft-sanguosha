package dev.sgs.core.player;

import java.util.ArrayList;
import java.util.List;

import dev.sgs.core.card.CardInstance;

/** The four equipment slots of one seat: weapon, armor, +1 horse and -1 horse. */
public final class Equipment {

  private CardInstance weapon;
  private CardInstance armor;
  private CardInstance horsePlus;
  private CardInstance horseMinus;
  private CardInstance treasure;
  public CardInstance treasure(){return treasure;}
  /** Server-side extension slot; built-in deck currently contains no treasure card. */
  public CardInstance equipTreasure(CardInstance card){var old=treasure;treasure=java.util.Objects.requireNonNull(card);return old;}

  public CardInstance weapon() {
    return weapon;
  }

  public CardInstance armor() {
    return armor;
  }

  public CardInstance horsePlus() {
    return horsePlus;
  }

  public CardInstance horseMinus() {
    return horseMinus;
  }

  /**
   * Puts the card into its slot and returns the replaced card (or null). The caller moves the
   * returned card to the discard pile.
   */
  public CardInstance equip(CardInstance card) {
    CardInstance old;
    switch (card.type().category()) {
      case WEAPON -> {
        old = weapon;
        weapon = card;
      }
      case ARMOR -> {
        old = armor;
        armor = card;
      }
      case HORSE_PLUS -> {
        old = horsePlus;
        horsePlus = card;
      }
      case HORSE_MINUS -> {
        old = horseMinus;
        horseMinus = card;
      }
      default -> throw new IllegalArgumentException("not an equipment card: " + card.type());
    }
    return old;
  }

  /** Weapon attack range; bare-handed characters attack at range 1. */
  public int attackRange() {
    return weapon == null ? 1 : weapon.type().weaponRange();
  }

  public boolean hasDefensiveHorse() {
    return horsePlus != null;
  }

  public boolean hasOffensiveHorse() {
    return horseMinus != null;
  }

  /** Removes and returns every equipped card (death cleanup). */
  public List<CardInstance> clear() {
    List<CardInstance> all = new ArrayList<>(4);
    if(treasure!=null){all.add(treasure);treasure=null;}
    if (weapon != null) {
      all.add(weapon);
      weapon = null;
    }
    if (armor != null) {
      all.add(armor);
      armor = null;
    }
    if (horsePlus != null) {
      all.add(horsePlus);
      horsePlus = null;
    }
    if (horseMinus != null) {
      all.add(horseMinus);
      horseMinus = null;
    }
    return all;
  }

  /** Removes the equipped card with the given instance id, or returns null if absent. */
  public CardInstance remove(long instanceId) {
    if(treasure!=null && treasure.instanceId()==instanceId){var old=treasure;treasure=null;return old;}
    if (weapon != null && weapon.instanceId() == instanceId) {
      CardInstance c = weapon;
      weapon = null;
      return c;
    }
    if (armor != null && armor.instanceId() == instanceId) {
      CardInstance c = armor;
      armor = null;
      return c;
    }
    if (horsePlus != null && horsePlus.instanceId() == instanceId) {
      CardInstance c = horsePlus;
      horsePlus = null;
      return c;
    }
    if (horseMinus != null && horseMinus.instanceId() == instanceId) {
      CardInstance c = horseMinus;
      horseMinus = null;
      return c;
    }
    return null;
  }

  /** All currently equipped cards (used by dismantle/steal style tricks). */
  public List<CardInstance> all() {
    List<CardInstance> all = new ArrayList<>(4);
    if(treasure!=null)all.add(treasure);
    if (weapon != null) {
      all.add(weapon);
    }
    if (armor != null) {
      all.add(armor);
    }
    if (horsePlus != null) {
      all.add(horsePlus);
    }
    if (horseMinus != null) {
      all.add(horseMinus);
    }
    return all;
  }
}
