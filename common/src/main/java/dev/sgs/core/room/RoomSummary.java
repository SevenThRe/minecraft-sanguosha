package dev.sgs.core.room;

/** Immutable snapshot of a room for list display and network sync. */
public record RoomSummary(
    long id, String name, RoomMode mode, Room.State state, int memberCount, int capacity) {

  public static RoomSummary of(Room room) {
    return new RoomSummary(
        room.id(), room.name(), room.mode(), room.state(), room.memberCount(), room.capacity());
  }
}
