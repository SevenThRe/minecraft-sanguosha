package dev.sgs.core.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.sgs.core.Identity;
import dev.sgs.core.game.GameState;
import dev.sgs.core.player.SgsPlayer;

/** Final per-seat result lines shown after a match ends (FR-5.6). */
public record BattleReport(GameOutcome outcome, int turnNumber, List<Line> lines) {

  /** One seat row: identity is public because every identity is revealed at game end. */
  public record Line(
      int seat, String name, Identity identity, String heroId, int hp, boolean alive) {}

  public static BattleReport of(GameState state, GameOutcome outcome) {
    List<Line> lines = new ArrayList<>(state.players().size());
    for (SgsPlayer player : state.players()) {
      lines.add(
          new Line(
              player.seat(),
              player.name(),
              player.identity(),
              player.hero() == null ? null : player.hero().id(),
              Math.max(player.hp(), 0),
              player.alive()));
    }
    return new BattleReport(outcome, state.turnNumber(), Collections.unmodifiableList(lines));
  }
}
