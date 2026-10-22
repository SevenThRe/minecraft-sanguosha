package dev.sgs.core.engine;

import dev.sgs.core.Identity;
import dev.sgs.core.game.GameState;
import dev.sgs.core.player.SgsPlayer;

/**
 * Applies the identity match victory rules (FR-5.5). Evaluated after every death:
 *
 * <ul>
 *   <li>Lord alive, every rebel and the renegade dead: lord/loyalist victory.
 *   <li>Lord dead while the renegade is the sole survivor: renegade victory.
 *   <li>Lord dead in every other case: rebel victory.
 * </ul>
 *
 * <p>Matches with no renegade identity in the table (2-3 player simplified rules) never produce a
 * renegade victory.
 */
public final class VictoryEvaluator {

  private VictoryEvaluator() {}

  public static GameOutcome evaluate(GameState state) {
    if(state.teamMode()){
      boolean a=state.alivePlayers().stream().anyMatch(p->state.teamOf(p.seat())==0);
      boolean b=state.alivePlayers().stream().anyMatch(p->state.teamOf(p.seat())==1);
      return a&&b?GameOutcome.ONGOING:a?GameOutcome.TEAM_A_WIN:b?GameOutcome.TEAM_B_WIN:GameOutcome.DRAW;
    }
    boolean lordAlive = false;
    int rebelsAlive = 0;
    int loyalistsAlive = 0;
    int renegadesAlive = 0;
    boolean renegadeExists = false;

    for (SgsPlayer player : state.players()) {
      Identity identity = player.identity();
      if (identity == Identity.RENEGADE) {
        renegadeExists = true;
      }
      if (!player.alive()) {
        continue;
      }
      switch (identity) {
        case LORD -> lordAlive = true;
        case REBEL -> rebelsAlive++;
        case LOYALIST -> loyalistsAlive++;
        case RENEGADE -> renegadesAlive++;
      }
    }

    if (lordAlive) {
      return rebelsAlive == 0 && renegadesAlive == 0 ? GameOutcome.LORD_WIN : GameOutcome.ONGOING;
    }
    if (renegadeExists && renegadesAlive == 1 && rebelsAlive == 0 && loyalistsAlive == 0) {
      return GameOutcome.RENEGADE_WIN;
    }
    return GameOutcome.REBELS_WIN;
  }
}
