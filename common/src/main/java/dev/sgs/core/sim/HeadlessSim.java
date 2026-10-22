package dev.sgs.core.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import dev.sgs.core.GameStatus;
import dev.sgs.core.card.Deck;
import dev.sgs.core.card.DevelopmentDeckFactory;
import dev.sgs.core.engine.GameOutcome;
import dev.sgs.core.engine.SgsGameEngine;
import dev.sgs.core.game.GameState;
import dev.sgs.core.hero.HeroDefinition;
import dev.sgs.core.hero.StandardHeroes;
import dev.sgs.core.player.SgsPlayer;
import dev.sgs.core.util.IdSequencer;

/**
 * Runs a complete identity match with bot seats through the public engine surface the same way the
 * networked integration will: advance the phase skeleton into the play window, play cards, end the
 * play phase, then confirm discards. Prompt answers (dodge, rescue, duel) go through the real
 * {@link dev.sgs.core.engine.PromptResponder} path, so damage, the resolution stack and victory
 * evaluation all settle for real. Pure JVM, no Minecraft runtime; used by the {@code /sgs dev sim}
 * developer command as a zero-asset smoke test.
 */
public final class HeadlessSim {

  /** Outcome of one simulated match. A failed match carries the error message for the report. */
  public record Result(
      boolean finished,
      GameOutcome outcome,
      int turns,
      int ticks,
      int players,
      long seed,
      String error) {}

  private static final int DEFAULT_MAX_TICKS = 20_000;

  private HeadlessSim() {}

  public static Result run(int playerCount, long seed) {
    return run(playerCount, seed, DEFAULT_MAX_TICKS, line -> {});
  }

  /**
   * @param logSink receives every formatted game log line (full settlement trace).
   */
  public static Result run(int playerCount, long seed, int maxTicks, Consumer<String> logSink) {
    return run(playerCount,seed,maxTicks,logSink,false);
  }

  public static Result runTwoVTwo(long seed,int maxTicks,Consumer<String> logSink){
    return run(4,seed,maxTicks,logSink,true);
  }

  private static Result run(int playerCount,long seed,int maxTicks,Consumer<String> logSink,boolean teams){
    try {
      List<SgsPlayer> seats = new ArrayList<>(playerCount);
      List<HeroDefinition> heroes = new ArrayList<>(playerCount);
      for (int i = 0; i < playerCount; i++) {
        seats.add(new SimPlayer(i));
        heroes.add(StandardHeroes.ALL.get(i % StandardHeroes.ALL.size()));
      }
      Random random = new Random(seed);
      Deck deck = new Deck(DevelopmentDeckFactory.create(new IdSequencer()), new Random(seed));
      GameState state = teams?GameState.startTwoVTwo(seats,heroes,deck,random):GameState.start(seats, heroes, deck, random);
      var physicalIds=state.cards().zones().stream().flatMap(zone->state.cards().cards(zone).stream())
          .map(dev.sgs.core.card.CardInstance::instanceId).collect(java.util.stream.Collectors.toUnmodifiableSet());
      state.cards().addListener(move->state.cards().assertConserved(physicalIds));
      state.addLogSink(logSink);
      SgsGameEngine engine = new SgsGameEngine(state, BotPolicy.INSTANCE, () -> 0L, 20);

      int ticks = 0;
      while (state.status() == GameStatus.RUNNING) {
        if (++ticks > maxTicks) {
          return new Result(
              false,
              GameOutcome.ONGOING,
              state.turnNumber(),
              ticks,
              playerCount,
              seed,
              "match did not finish within " + maxTicks + " ticks");
        }
        tick(engine, state);
        state.cards().assertConserved(physicalIds);
      }
      return new Result(
          true,
          engine.outcome() == null ? GameOutcome.ONGOING : engine.outcome(),
          state.turnNumber(),
          ticks,
          playerCount,
          seed,
          null);
    } catch (RuntimeException e) {
      return new Result(false, GameOutcome.ONGOING, -1, -1, playerCount, seed, e.toString());
    }
  }

  private static void tick(SgsGameEngine engine, GameState state) {
    SgsPlayer player = state.currentPlayer();
    switch (state.phase()) {
        // Prepare/judgment/draw are advanced explicitly, same driver shape as the real client:
        // the engine settles prompts and damage while the bot acts inside the play window.
      case PREPARE -> engine.openTurn(player);
      case PLAY -> BotTurnDriver.runPlay(engine, state, player);
      case DISCARD -> BotTurnDriver.runDiscard(engine, player);
      default -> throw new IllegalStateException("unexpected phase " + state.phase());
    }
  }
}
