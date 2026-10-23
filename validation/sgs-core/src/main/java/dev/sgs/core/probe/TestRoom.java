package dev.sgs.core.probe;

import java.util.Objects;
import java.util.UUID;

/** Minimal server-owned state used only to verify transport and persistence. */
public final class TestRoom {
    public static final int MAX_VALUE = 100;
    private final UUID id;
    private final UUID owner;
    private int value;

    public TestRoom(UUID id, UUID owner, int value) {
        this.id = Objects.requireNonNull(id);
        this.owner = Objects.requireNonNull(owner);
        if (value < 0 || value > MAX_VALUE) throw new IllegalArgumentException("value out of range");
        this.value = value;
    }

    public Result submit(UUID actor, int delta) {
        if (!owner.equals(actor)) return new Result(false, "owner_required", value);
        if (delta < 1 || delta > 5) return new Result(false, "delta_must_be_1_to_5", value);
        if (value > MAX_VALUE - delta) return new Result(false, "limit_reached", value);
        value += delta;
        return new Result(true, "accepted", value);
    }

    public UUID id() { return id; }
    public UUID owner() { return owner; }
    public int value() { return value; }
    public record Result(boolean success, String reason, int value) {}
}
