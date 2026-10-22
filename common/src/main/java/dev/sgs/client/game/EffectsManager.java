package dev.sgs.client.game;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tiny client-side animation system. Each {@link Effect} has a seat target, a wall-clock start
 * time, a fixed duration and an opaque type tag. GameScreen renders them as thin overlays.
 *
 * <p>All effects are local-only: kill flashes, card-fly hints, turn sweeps — nothing here sends
 * any packet.
 */
public final class EffectsManager {

  public enum Kind {
    /** A seat just died — red flash fading out over {@link KILL_MS}. */
    KILL,
    /** The current seat just changed — a gold ring sweep over {@link TURN_MS}. */
    TURN,
  }

  public static final long KILL_MS = 1500L;
  public static final long TURN_MS = 1200L;

  private static final List<Effect> effects = new ArrayList<>();

  private EffectsManager() {}

  public static void spawnKill(int seat) {
    effects.add(new Effect(Kind.KILL, seat, System.currentTimeMillis(), KILL_MS));
  }

  public static void spawnTurn(int seat) {
    effects.add(new Effect(Kind.TURN, seat, System.currentTimeMillis(), TURN_MS));
  }

  public static void tick() {
    long now = System.currentTimeMillis();
    Iterator<Effect> it = effects.iterator();
    while (it.hasNext()) {
      if (now - it.next().startMs >= it.next().durationMs) {
        it.remove();
      }
    }
  }

  public static List<Effect> active() {
    return effects;
  }

  public static void clear() {
    effects.clear();
  }

  public record Effect(Kind kind, int seat, long startMs, long durationMs) {
    /** Returns 0..1 progress (0 = just started, 1 = fully elapsed). */
    public float progress() {
      long now = System.currentTimeMillis();
      return Math.min(1f, Math.max(0f, (float) (now - startMs) / durationMs));
    }
  }
}
