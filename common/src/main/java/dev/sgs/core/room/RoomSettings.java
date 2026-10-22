package dev.sgs.core.room;

import java.util.Objects;
import java.util.Set;

/** Immutable server-authoritative room rules. Passwords must never enter public snapshots. */
public record RoomSettings(
    String password,
    int turnSeconds,
    int responseSeconds,
    int selectionSeconds,
    boolean allowChat,
    boolean allowSpectators,
    boolean doubleRenegade,
    boolean fixedSeats,
    Set<String> bannedHeroes) {
  public RoomSettings {
    password = Objects.requireNonNullElse(password, "");
    if (password.length() > 32) throw new IllegalArgumentException("password exceeds 32 characters");
    if (turnSeconds < 1 || turnSeconds > 300 || responseSeconds < 1 || responseSeconds > 300
        || selectionSeconds < 1 || selectionSeconds > 300)
      throw new IllegalArgumentException("timeouts must be between 1 and 300 seconds");
    bannedHeroes = Set.copyOf(Objects.requireNonNull(bannedHeroes));
    if (bannedHeroes.size() > 6 || bannedHeroes.stream().anyMatch(String::isBlank))
      throw new IllegalArgumentException("at most six nonblank hero IDs may be banned");
  }

  public static RoomSettings defaults() {
    return new RoomSettings("", 15, 10, 30, true, true, false, false, Set.of());
  }

  public boolean passwordProtected() { return !password.isEmpty(); }
}
