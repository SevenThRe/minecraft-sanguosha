package dev.sgs;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.LongArgumentType.getLong;
import static com.mojang.brigadier.arguments.LongArgumentType.longArg;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.sgs.config.SgsConfig;
import dev.sgs.core.room.RoomError;
import dev.sgs.core.room.RoomMode;
import dev.sgs.core.room.RoomResult;
import dev.sgs.core.room.RoomSummary;
import dev.sgs.core.sim.HeadlessSim;
import dev.sgs.lobby.SgsLobbyService;
import dev.sgs.lobby.match.MatchService;
import dev.sgs.network.SgsNetwork;
import dev.sgs.platform.SgsPlatform;
import dev.sgs.registry.SgsBlocks;
import dev.sgs.registry.SgsItems;
import dev.sgs.registry.SgsTabs;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Sgs {

  public static final String MOD_ID = "sgs";
  public static final Logger LOGGER = LoggerFactory.getLogger("SGS");

  private Sgs() {}

  public static void init() {
    // Registration order matters: blocks first, then their block items, then tabs.
    SgsBlocks.BLOCKS.register();
    SgsItems.ITEMS.register();
    SgsTabs.TABS.register();
    SgsConfig.load(SgsPlatform.getConfigDirectory());

    SgsNetwork.init();
    LifecycleEvent.SERVER_STARTING.register(
        server -> {
          SgsLobbyService.onServerStarting(server);
          MatchService.onServerStarting(server);
        });
    LifecycleEvent.SERVER_STOPPING.register(
        server -> {
          if (MatchService.get() != null) {
            MatchService.get().shutdown();
          }
        });
    PlayerEvent.PLAYER_QUIT.register(
        player -> {
          if (MatchService.get() != null) {
            MatchService.get().handleQuit(player.getUUID());
          }
          if (SgsLobbyService.get() != null) {
            SgsLobbyService.get().handleDisconnect(player);
          }
        });

    CommandRegistrationEvent.EVENT.register(
        (dispatcher, registryAccess, environment) ->
            dispatcher.register(buildSgsCommand()));

    LOGGER.info(
        "[SGS-INIT] MC-SGS common layer initialized; config directory: {}",
        SgsPlatform.getConfigDirectory());
  }

  private static long randomSeed() {
    return ThreadLocalRandom.current().nextLong();
  }

  /** Builds the full brigadier command tree for {@code /sgs}. */
  private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>
      buildSgsCommand() {
    // dev sim branch
    var simBranch =
        Commands.literal("sim")
            .executes(ctx -> runSimulation(ctx.getSource(), 6, randomSeed(), 1))
            .then(
                Commands.argument("players", integer(2, 8))
                    .executes(
                        ctx ->
                            runSimulation(
                                ctx.getSource(),
                                getInteger(ctx, "players"),
                                randomSeed(),
                                1))
                    .then(
                        Commands.argument("seed", longArg())
                            .executes(
                                ctx ->
                                    runSimulation(
                                        ctx.getSource(),
                                        getInteger(ctx, "players"),
                                        getLong(ctx, "seed"),
                                        1))
                            .then(
                                Commands.argument("games", integer(1, 20))
                                    .executes(
                                        ctx ->
                                            runSimulation(
                                                ctx.getSource(),
                                                getInteger(ctx, "players"),
                                                getLong(ctx, "seed"),
                                                getInteger(ctx, "games"))))));
    // dev play branch
    var playBranch =
        Commands.literal("play")
            .executes(ctx -> startDevPlay(ctx.getSource(), 6))
            .then(
                Commands.argument("players", integer(6, 8))
                    .executes(
                        ctx -> startDevPlay(ctx.getSource(), getInteger(ctx, "players"))));
    // dev parent
    var devBranch = Commands.literal("dev").then(playBranch).then(simBranch);

    // create branch
    var createBranch =
        Commands.literal("create")
            .executes(ctx -> createRoom(ctx, "", RoomMode.EIGHT))
            .then(
                Commands.argument("name", string())
                    .executes(ctx -> createRoom(ctx, getString(ctx, "name"), RoomMode.EIGHT))
                    .then(
                        Commands.argument("players", integer(6, 8))
                            .executes(
                                ctx ->
                                    createRoom(
                                        ctx,
                                        getString(ctx, "name"),
                                        getInteger(ctx, "players") == 6
                                            ? RoomMode.SIX
                                            : RoomMode.EIGHT))));

    return Commands.literal("sgs")
        .executes(ctx -> openLobby(ctx))
        .then(Commands.literal("lobby").executes(ctx -> openLobby(ctx)))
        .then(createBranch)
        .then(
            Commands.literal("join")
                .then(
                    Commands.argument("roomId", longArg(1L))
                        .executes(ctx -> joinRoom(ctx, getLong(ctx, "roomId")))))
        .then(Commands.literal("leave").executes(ctx -> leaveRoom(ctx)))
        .then(Commands.literal("start").executes(ctx -> startRoom(ctx)))
        .then(
            Commands.literal("kick")
                .then(
                    Commands.argument("player", string())
                        .executes(ctx -> kickPlayer(ctx, getString(ctx, "player")))))
        .then(Commands.literal("list").executes(ctx -> listRooms(ctx)))
        .then(
            Commands.literal("spectate")
                .then(
                    Commands.argument("roomId", longArg(1L))
                        .executes(ctx -> spectateRoom(ctx, getLong(ctx, "roomId")))))
        .then(devBranch);
  }

  // ---------------------------------------------------------------- chat commands

  private static int openLobby(CommandContext<CommandSourceStack> ctx) {
    if (!SgsConfig.get().enableCommand()) {
      ctx.getSource().sendFailure(Component.translatable("command.sgs.disabled"));
      return 0;
    }
    SgsLobbyService.get().openLobby(expectPlayer(ctx));
    return 1;
  }

  private static int createRoom(CommandContext<CommandSourceStack> ctx, String name, RoomMode mode) {
    RoomResult<dev.sgs.core.room.Room> result = SgsLobbyService.get().commandCreate(expectPlayer(ctx), name, mode);
    if (!result.isOk()) {
      ctx.getSource().sendFailure(Component.literal("[SGS] " + errorMessage(result.error())));
      return 0;
    }
    dev.sgs.core.room.Room room = result.value();
    ctx.getSource().sendSystemMessage(
        Component.translatable("command.sgs.create.ok", room.id(), room.name()));
    SgsLobbyService.get().openLobby(expectPlayer(ctx));
    return 1;
  }

  private static int joinRoom(CommandContext<CommandSourceStack> ctx, long roomId) {
    RoomResult<?> result = SgsLobbyService.get().commandJoin(expectPlayer(ctx), roomId);
    if (!result.isOk()) {
      ctx.getSource().sendFailure(Component.literal("[SGS] " + errorMessage(result.error())));
      return 0;
    }
    ctx.getSource().sendSystemMessage(Component.translatable("command.sgs.join.ok", roomId));
    return 1;
  }

  private static int leaveRoom(CommandContext<CommandSourceStack> ctx) {
    RoomResult<?> result = SgsLobbyService.get().commandLeave(expectPlayer(ctx));
    if (!result.isOk()) {
      ctx.getSource().sendFailure(Component.literal("[SGS] " + errorMessage(result.error())));
      return 0;
    }
    ctx.getSource().sendSystemMessage(Component.translatable("command.sgs.leave.ok"));
    return 1;
  }

  private static int startRoom(CommandContext<CommandSourceStack> ctx) {
    RoomResult<?> result = SgsLobbyService.get().commandStart(expectPlayer(ctx));
    if (!result.isOk()) {
      ctx.getSource().sendFailure(Component.literal("[SGS] " + errorMessage(result.error())));
      return 0;
    }
    ctx.getSource().sendSystemMessage(Component.translatable("command.sgs.start.ok"));
    return 1;
  }

  private static int kickPlayer(CommandContext<CommandSourceStack> ctx, String targetName) {
    RoomResult<?> result = SgsLobbyService.get().commandKick(expectPlayer(ctx), targetName);
    if (!result.isOk()) {
      ctx.getSource().sendFailure(Component.literal("[SGS] " + errorMessage(result.error())));
      return 0;
    }
    ctx.getSource().sendSystemMessage(Component.translatable("command.sgs.kick.ok", targetName));
    return 1;
  }

  private static int listRooms(CommandContext<CommandSourceStack> ctx) {
    List<RoomSummary> rooms = SgsLobbyService.get().commandList();
    if (rooms.isEmpty()) {
      ctx.getSource().sendSystemMessage(Component.translatable("command.sgs.list.empty"));
      return 1;
    }
    ctx.getSource().sendSystemMessage(Component.translatable("command.sgs.list.header"));
    for (RoomSummary summary : rooms) {
      ctx.getSource()
          .sendSystemMessage(
              Component.literal(
                  String.format(
                      "  #%d %s [%d/%d %s]",
                      summary.id(),
                      summary.name(),
                      summary.memberCount(),
                      summary.capacity(),
                      summary.mode())));
    }
    return 1;
  }

  private static int spectateRoom(CommandContext<CommandSourceStack> ctx, long roomId) {
    boolean ok = SgsLobbyService.get().commandSpectate(expectPlayer(ctx), roomId);
    if (!ok) {
      ctx.getSource().sendFailure(Component.literal("[SGS] match not found for room #" + roomId));
      return 0;
    }
    ctx.getSource().sendSystemMessage(Component.translatable("command.sgs.spectate.ok", roomId));
    return 1;
  }

  private static ServerPlayer expectPlayer(CommandContext<CommandSourceStack> ctx)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    return ctx.getSource().getPlayerOrException();
  }

  private static String errorMessage(RoomError error) {
    return switch (error) {
      case ROOM_NOT_FOUND -> "room not found";
      case ROOM_FULL -> "room is full";
      case ROOM_NOT_WAITING -> "room is no longer waiting";
      case ALREADY_IN_ROOM -> "already in a room";
      case NOT_IN_ROOM -> "you are not in a room";
      case NOT_OWNER -> "not the owner";
      case NOT_INVITED -> "you have not been invited";
      case CANNOT_INVITE_SELF -> "cannot invite yourself";
      case TARGET_IN_ROOM -> "target already in a room";
      case ALREADY_QUEUED -> "already in queue";
      case NOT_QUEUED -> "not in queue";
      case INVALID_SEAT -> "invalid seat";
      case SEAT_TAKEN -> "seat taken";
      case CANNOT_KICK_OWNER -> "cannot kick the owner";
      case MATCH_NOT_FOUND -> "match not found";
    };
  }

  // ---------------------------------------------------------------- dev helpers

  /** Starts a real server-authoritative match with the caller as the only human seat. */
  private static int startDevPlay(CommandSourceStack source, int players)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    ServerPlayer player = source.getPlayerOrException();
    boolean started = MatchService.get().startDevMatch(player, players);
    if (!started) {
      source.sendFailure(Component.literal("[SGS] you are already in a match"));
      return 0;
    }
    source.sendSystemMessage(
        Component.literal(
            "[SGS] starting a " + players + "-seat match; your seat is randomized, good luck"));
    return 1;
  }

  /**
   * Runs {@code games} headless bot matches. The full settlement trace of every match goes to the
   * server log (latest.log, prefixed SGS-SIM); chat gets one summary line per match.
   */
  private static int runSimulation(CommandSourceStack source, int players, long seed, int games) {
    int finished = 0;
    for (int i = 0; i < games; i++) {
      long matchSeed = games == 1 ? seed : seed + i;
      List<String> tail = new ArrayList<>();
      HeadlessSim.Result result =
          HeadlessSim.run(
              players,
              matchSeed,
              20_000,
              line -> {
                LOGGER.info("[SGS-SIM seed={}] {}", matchSeed, line);
                tail.add(line);
              });
      if (result.finished()) {
        finished++;
        source.sendSystemMessage(
            Component.literal(
                String.format(
                    "[SGS-SIM] seed=%d %dp -> %s, turns=%d, ticks=%d",
                    matchSeed, players, result.outcome(), result.turns(), result.ticks())));
      } else {
        source.sendFailure(
            Component.literal(
                String.format(
                    "[SGS-SIM] seed=%d %dp -> FAILED: %s", matchSeed, players, result.error())));
      }
      for (String line : tail.subList(Math.max(0, tail.size() - 4), tail.size())) {
        source.sendSystemMessage(Component.literal("  " + line));
      }
    }
    if (games > 1) {
      source.sendSystemMessage(
          Component.literal(String.format("[SGS-SIM] %d/%d matches finished", finished, games)));
    }
    return 1;
  }
}
