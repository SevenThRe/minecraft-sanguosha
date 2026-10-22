package dev.sgs.core.room;

/** Match size presets: standard 8-player and compact 6-player identity games. */
public enum RoomMode {
  SIX(6),
  EIGHT(8),
  TWO_V_TWO(4);

  private final int playerCount;

  RoomMode(int playerCount) {
    this.playerCount = playerCount;
  }

  public int playerCount() {
    return playerCount;
  }

  public static RoomMode byPlayerCount(int count) {
    for (RoomMode mode : values()) {
      if (mode.playerCount == count) {
        return mode;
      }
    }
    throw new IllegalArgumentException("no mode for player count " + count);
  }
}
