package dev.sgs.client.game;

import dev.sgs.core.match.dto.MatchViews.MatchSnapshot;
import dev.sgs.core.match.dto.MatchViews.SeatView;

/** Latest per-viewer match snapshot received from the server; consumed by {@link GameScreen}. */
public final class ClientGameState {

  private static volatile MatchSnapshot snapshot;
  private static int nonceCounter;

  private ClientGameState() {}

  /** Monotonic per-client action nonce; the server rejects replays. */
  public static synchronized int nextNonce() {
    return ++nonceCounter;
  }

  public static MatchSnapshot snapshot() {
    return snapshot;
  }

  /** Applies the next snapshot and spawns local-only effects for visible transitions. */
  public static void apply(MatchSnapshot next) {
    MatchSnapshot old = snapshot;
    snapshot = next;
    if (old != null && next != null) {
      detectTransitions(old, next);
    }
  }

  private static void detectTransitions(MatchSnapshot old, MatchSnapshot next) {
    // Kill effect: alive→dead.
    for (SeatView newSeat : next.seats()) {
      if (!newSeat.alive()) {
        SeatView oldSeat = findSeat(old, newSeat.seat());
        if (oldSeat != null && oldSeat.alive()) {
          EffectsManager.spawnKill(newSeat.seat());
        }
      }
    }
    // Turn effect: currentSeat changed (only during PLAY/DISCARD — avoid spamming in other phases).
    if (old.currentSeat() != next.currentSeat()
        && next.seats().stream().anyMatch(s -> s.seat() == next.currentSeat())) {
      EffectsManager.spawnTurn(next.currentSeat());
    }
  }

  private static SeatView findSeat(MatchSnapshot snap, int seat) {
    for (SeatView s : snap.seats()) {
      if (s.seat() == seat) {
        return s;
      }
    }
    return null;
  }

  static void clear() {
    snapshot = null;
    EffectsManager.clear();
  }
}
