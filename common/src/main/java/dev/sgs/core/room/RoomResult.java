package dev.sgs.core.room;

/** Outcome of a room operation: either a typed error or an optional payload. */
public record RoomResult<T>(RoomError error, T value) {

  public static <T> RoomResult<T> ok(T value) {
    return new RoomResult<>(null, value);
  }

  public static <T> RoomResult<T> ok() {
    return new RoomResult<>(null, null);
  }

  public static <T> RoomResult<T> fail(RoomError error) {
    return new RoomResult<>(error, null);
  }

  public boolean isOk() {
    return error == null;
  }
}
