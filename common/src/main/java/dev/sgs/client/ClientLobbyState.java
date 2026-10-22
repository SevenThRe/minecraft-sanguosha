package dev.sgs.client;

import java.util.List;

import dev.sgs.core.room.RoomMode;

/** Latest lobby snapshot received from the server; consumed by the lobby screen. */
public final class ClientLobbyState {

  public record SlotView(int slot, String occupantName, boolean isAi) {
    public static final int OCCUPANT_EMPTY = 0;
    public static final int OCCUPANT_HUMAN = 1;
    public static final int OCCUPANT_AI = 2;
  }

  public record RoomSnapshot(
      long id,
      String name,
      RoomMode mode,
      String ownerName,
      boolean isOwner,
      List<SlotView> slots,
      int capacity,
      boolean playing) {}

  public record RoomEntry(long id, String name, RoomMode mode, int memberCount, int capacity) {}

  public record Invite(String inviterName, String roomName) {}

  public record Snapshot(
      RoomSnapshot room,
      RoomMode queuedMode,
      int[] queueSizes,
      Invite invite,
      List<RoomEntry> joinable) {}

  private static volatile Snapshot snapshot =
      new Snapshot(null, null, new int[RoomMode.values().length], null, List.of());

  private ClientLobbyState() {}

  public static Snapshot snapshot() {
    return snapshot;
  }

  static void apply(Snapshot next) {
    snapshot = next;
  }
}
