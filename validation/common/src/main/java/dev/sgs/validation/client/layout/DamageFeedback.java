package dev.sgs.validation.client.layout;

import java.util.HashMap;
import java.util.Map;

/** Presentation only: consume confirmed HP decreases, never predict damage from an attack. */
public final class DamageFeedback {
    public static final long DURATION = 650;
    public record Hit(int amount, long started) {}
    private final Map<Integer, Hit> hits = new HashMap<>();

    public void clear() { hits.clear(); }

    public void observe(int seat, int previousHp, int hp, long now) {
        if (hp >= previousHp) return;
        Hit previous = hit(seat, now);
        // Rapid consecutive losses stay readable as one accumulated number.
        int amount = previousHp - hp;
        if (previous != null && now - previous.started() < 350) amount += previous.amount();
        hits.put(seat, new Hit(amount, now));
    }

    public Hit hit(int seat, long now) {
        Hit hit = hits.get(seat);
        if (hit != null && now - hit.started() >= DURATION) {
            hits.remove(seat);
            return null;
        }
        return hit;
    }

    public boolean active(long now) {
        hits.values().removeIf(hit -> now - hit.started() >= DURATION);
        return !hits.isEmpty();
    }
}
