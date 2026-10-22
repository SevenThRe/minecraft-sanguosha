package dev.sgs.core.game;

import dev.sgs.core.player.SgsPlayer;

/** A seat in the ring: fixed index plus the player occupying it. */
public record Seat(int index, SgsPlayer player) {}
