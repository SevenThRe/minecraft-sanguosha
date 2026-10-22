package dev.sgs.core.engine;

/** A server-validated selection. Zero chooses a random hand card; -2-index chooses an opaque hand position. */
public record SkillChoice(long cardId,int targetSeat) {}
