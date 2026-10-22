package dev.sgs.core.card;

/** Playing card suits. Red suits matter for future equipment interactions. */
public enum Suit {
  SPADE("spade", false),
  HEART("heart", true),
  CLUB("club", false),
  DIAMOND("diamond", true),
  /** Suit-less value used by virtual cards without exactly one physical source. */
  NONE("none", false);

  private final String id;
  private final boolean red;

  Suit(String id, boolean red) {
    this.id = id;
    this.red = red;
  }

  public String id() {
    return id;
  }

  public boolean red() {
    return red;
  }
}
