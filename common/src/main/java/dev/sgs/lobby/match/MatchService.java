package dev.sgs.lobby.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.architectury.networking.NetworkManager;
import dev.sgs.Sgs;
import dev.sgs.config.SgsConfig;
import dev.sgs.core.match.MatchConfig;
import dev.sgs.core.match.MatchEndpoint;
import dev.sgs.core.match.MatchSession;
import dev.sgs.core.match.dto.MatchViews.MatchSnapshot;
import dev.sgs.core.room.Room;
import dev.sgs.core.util.IdSequencer;
import dev.sgs.core.util.SystemClock;
import dev.sgs.network.GamePackets;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server lifecycle owner for live matches: starts developer and room matches, routes C2S actions to
 * the owning session worker and pushes per-viewer snapshots over the network. Match state stays on
 * the session worker thread; this service only forwards.
 */
public final class MatchService {

  private static MatchService instance;

  private final MinecraftServer server;
  private final Map<Long, MatchSession> sessionsByRoom = new ConcurrentHashMap<>();
  private final Map<UUID, Long> roomByPlayer = new ConcurrentHashMap<>();
  private final Map<UUID, Long> spectatedRoomByPlayer = new ConcurrentHashMap<>();
  private final Map<UUID, MatchEndpoint> spectatorEndpoints = new ConcurrentHashMap<>();
  private final IdSequencer devRoomIds = new IdSequencer(900_000_000L);

  private MatchService(MinecraftServer server) {
    this.server = server;
  }

  public static void onServerStarting(MinecraftServer server) {
    instance = new MatchService(server);
  }

  public static MatchService get() {
    return instance;
  }

  /** Stops every running session (server shutdown). */
  public void shutdown() {
    for (MatchSession session : sessionsByRoom.values()) {
      session.shutdown();
    }
    sessionsByRoom.clear();
    roomByPlayer.clear();
  }

  public boolean isInMatch(UUID playerId) {
    return roomByPlayer.containsKey(playerId);
  }

  /** Developer entry: one human plus bots filling the table. The human gets a random seat. */
  public boolean startDevMatch(ServerPlayer player, int playerCount) {
    if (roomByPlayer.containsKey(player.getUUID())) {
      return false;
    }
    long roomId = devRoomIds.getAsLong();
    List<MatchSession.Human> humans =
        List.of(
            new MatchSession.Human(
                player.getUUID(),
                player.getGameProfile().getName(),
                endpointFor(player.getUUID())));
    startSession(roomId, humans, playerCount);
    return true;
  }

  /** Room entry: every online room member is a human seat, bots fill the rest. */
  public void startRoomMatch(Room room) {
    List<MatchSession.Human> humans = new ArrayList<>(room.memberCount());
    for (UUID memberId : room.members()) {
      ServerPlayer online = server.getPlayerList().getPlayer(memberId);
      if (online != null) {
        humans.add(
            new MatchSession.Human(
                memberId, online.getGameProfile().getName(), endpointFor(memberId)));
      }
    }
    if (humans.isEmpty()) {
      return;
    }
    startSession(room.id(), humans, room.mode().playerCount());
  }

  private void startSession(long roomId, List<MatchSession.Human> humans, int playerCount) {
    SgsConfig config = SgsConfig.get();
    MatchConfig matchConfig =
        new MatchConfig(
            config.responseTimeoutSeconds(),
            config.turnTimeoutSeconds(),
            MatchConfig.standard().selectionTimeoutSeconds(),
            MatchConfig.standard().botStepDelayMillis());
    MatchSession session =
        MatchSession.start(
            roomId,
            humans,
            playerCount,
            matchConfig,
            SystemClock.INSTANCE,
            new Random(),
            line -> Sgs.LOGGER.info("[SGS-MATCH room={}] {}", roomId, line),
            () -> finish(roomId));
    sessionsByRoom.put(roomId, session);
    for (MatchSession.Human human : humans) {
      roomByPlayer.put(human.id(), roomId);
    }
  }

  /** Attaches a read-only spectator to a running room's match. */
  public boolean spectateRoom(ServerPlayer player, long roomId) {
    UUID playerId = player.getUUID();
    if (roomByPlayer.containsKey(playerId) || spectatedRoomByPlayer.containsKey(playerId)) {
      return false;
    }
    MatchSession session = sessionsByRoom.get(roomId);
    if (session == null) {
      return false;
    }
    MatchEndpoint endpoint = endpointFor(playerId);
    session.addSpectator(endpoint);
    spectatorEndpoints.put(playerId, endpoint);
    spectatedRoomByPlayer.put(playerId, roomId);
    return true;
  }

  /** Network thread safe: forwards an action to the owning session worker. */
  public void handleAction(ServerPlayer player, dev.sgs.core.match.MatchAction action) {
    UUID playerId = player.getUUID();
    Long roomId = roomByPlayer.get(playerId);
    if (roomId != null) {
      MatchSession session = sessionsByRoom.get(roomId);
      if (session != null) {
        session.submit(playerId, action);
      }
      return;
    }
    // Spectators may only detach.
    if (action.kind() == dev.sgs.core.match.MatchAction.Kind.LEAVE) {
      Long spectated = spectatedRoomByPlayer.remove(playerId);
      if (spectated != null) {
        MatchEndpoint endpoint = spectatorEndpoints.remove(playerId);
        MatchSession session = sessionsByRoom.get(spectated);
        if (session != null && endpoint != null) {
          session.removeSpectator(endpoint);
        }
      }
    }
  }

  /** A human disconnected: participants go offline (bots continue), spectators detach. */
  public void handleQuit(UUID playerId) {
    Long roomId = roomByPlayer.get(playerId);
    if (roomId != null) {
      MatchSession session = sessionsByRoom.get(roomId);
      if (session != null) {
        session.markOffline(playerId);
      }
      return;
    }
    Long spectated = spectatedRoomByPlayer.remove(playerId);
    if (spectated != null) {
      MatchEndpoint endpoint = spectatorEndpoints.remove(playerId);
      MatchSession session = sessionsByRoom.get(spectated);
      if (session != null && endpoint != null) {
        session.removeSpectator(endpoint);
      }
    }
  }

  private void finish(long roomId) {
    MatchSession session = sessionsByRoom.remove(roomId);
    if (session != null) {
      roomByPlayer.entrySet().removeIf(entry -> entry.getValue() == roomId);
    }
    // Room bookkeeping and GUI sync touch server state; run on the main thread.
    server.execute(
        () -> {
          if (dev.sgs.lobby.SgsLobbyService.get() != null) {
            dev.sgs.lobby.SgsLobbyService.get().afterMatchFinished(roomId);
          }
        });
  }

  private MatchEndpoint endpointFor(UUID playerId) {
    return new MatchEndpoint() {
      @Override
      public void sendSnapshot(MatchSnapshot snapshot) {
        server.execute(
            () -> {
              ServerPlayer player = server.getPlayerList().getPlayer(playerId);
              if (player != null) {
                NetworkManager.sendToPlayer(
                    player, GamePackets.GAME_SNAPSHOT, GamePackets.encodeSnapshot(snapshot));
              }
            });
      }

      @Override
      public void sendError(int nonce, String reason) {
        server.execute(
            () -> {
              ServerPlayer player = server.getPlayerList().getPlayer(playerId);
              if (player != null) {
                NetworkManager.sendToPlayer(
                    player, GamePackets.GAME_ERROR, GamePackets.encodeError(nonce, reason));
              }
            });
      }
    };
  }
}
