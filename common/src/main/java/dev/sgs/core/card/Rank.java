package dev.sgs.core.card;

/** Playing card ranks A through K. */
public enum Rank {
  ACE("A", 1),
  TWO("2", 2),
  THREE("3", 3),
  FOUR("4", 4),
  FIVE("5", 5),
  SIX("6", 6),
  SEVEN("7", 7),
  EIGHT("8", 8),
  NINE("9", 9),
  TEN("10", 10),
  JACK("J", 11),
  QUEEN("Q", 12),
  KING("K", 13);

  private final String label;
  private final int value;

  Rank(String label, int value) {
    this.label = label;
    this.value = value;
  }

  public String label() {
    return label;
  }

  /** Lowercase label used in stable card ids, e.g. {@code a}, {@code 7}, {@code k}. */
  public String id() {
    return label.toLowerCase(java.util.Locale.ROOT);
  }

  public int value() {
    return value;
  }

  public static Rank of(int value) {
    for (Rank rank : values()) {
      if (rank.value == value) {
        return rank;
      }
    }
    throw new IllegalArgumentException("invalid rank value: " + value);
  }
}
