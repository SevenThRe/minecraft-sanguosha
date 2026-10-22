package dev.sgs.lobby;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.architectury.networking.NetworkManager;
import dev.sgs.core.room.Room;
import dev.sgs.core.room.RoomError;
import dev.sgs.core.room.RoomManager;
import dev.sgs.core.room.RoomMode;
import dev.sgs.core.room.RoomResult;
import dev.sgs.core.room.RoomSummary;
import dev.sgs.core.util.IdSequencer;
import dev.sgs.network.SgsNetwork;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side lobby service bridging the pure-Java {@link RoomManager} with online players. Every
 * mutation ends with a lobby sync broadcast so open GUIs refresh immediately.
 */
public final class SgsLobbyService {

  private static SgsLobbyService instance;

  private final MinecraftServer server;
  private final RoomManager manager;

  private SgsLobbyService(MinecraftServer server) {
    this.server = server;
    IdSequencer roomIds = new IdSequencer();
    this.manager = new RoomManager(roomIds::getAsLong);
    this.manager.setListener(
        new RoomManager.Listener() {
          @Override
          public void onInvite(UUID inviter, UUID target, Room room) {
            ServerPlayer targetPlayer = server.getPlayerList().getPlayer(target);
            if (targetPlayer != null) {
              sendSync(targetPlayer);
            }
          }

          @Override
          public void onMatchmade(Room room) {
            broadcastSync();
          }

          @Override
          public void onRoomDisbanded(Room room) {
            broadcastSync();
          }
        });
  }

  public static void onServerStarting(MinecraftServer server) {
    instance = new SgsLobbyService(server);
  }

  public static SgsLobbyService get() {
    return instance;
  }

  public void openLobby(ServerPlayer player) {
    SgsNetwork.sendOpenLobby(player);
    sendSync(player);
  }

  public void handleDisconnect(ServerPlayer player) {
    manager.handleDisconnect(player.getUUID());
    broadcastSync();
  }

  public void handleAction(ServerPlayer player, SgsNetwork.LobbyRequest request) {
    UUID id = player.getUUID();
    switch (request.action()) {
      case SgsNetwork.ACTION_CREATE -> {
        RoomMode mode = modeOrNull(request.modeOrdinal());
        if (mode == null) {
          return;
        }
        RoomResult<Room> result = manager.createRoom(id, request.name(), mode);
        if (!result.isOk()) {
          SgsNetwork.sendLobbyError(player, result.error());
        }
      }
      case SgsNetwork.ACTION_JOIN -> report(player, manager.joinRoom(id, request.roomId()));
      case SgsNetwork.ACTION_LEAVE -> report(player, manager.leaveRoom(id));
      case SgsNetwork.ACTION_INVITE -> {
        ServerPlayer target = server.getPlayerList().getPlayerByName(request.name());
        if (target == null) {
          SgsNetwork.sendLobbyError(player, RoomError.ROOM_NOT_FOUND);
        } else {
          report(player, manager.invite(id, target.getUUID()));
        }
      }
      case SgsNetwork.ACTION_ACCEPT -> report(player, manager.acceptInvite(id));
      case SgsNetwork.ACTION_DECLINE -> report(player, manager.declineInvite(id));
      case SgsNetwork.ACTION_QUEUE_JOIN -> {
        RoomMode mode = modeOrNull(request.modeOrdinal());
        if (mode == null) {
          return;
        }
        report(player, manager.joinQueue(id, mode));
      }
      case SgsNetwork.ACTION_QUEUE_LEAVE -> report(player, manager.leaveQueue(id));
      case SgsNetwork.ACTION_REFRESH -> {}
      case SgsNetwork.ACTION_SWAP_SEAT -> swapSeat(player, request.name());
      case SgsNetwork.ACTION_START_BOTS -> startMatchWithBots(player);
      case SgsNetwork.ACTION_SIT -> report(player, manager.sit(id, request.slot()));
      case SgsNetwork.ACTION_AI_SLOT -> {
        Room room = manager.roomOf(id).orElse(null);
        long roomId = room == null ? -1L : room.id();
        report(player, manager.reserveAiSlot(id, roomId, request.slot(), request.flag()));
      }
      case SgsNetwork.ACTION_AI_FILL -> {
        Room room = manager.roomOf(id).orElse(null);
        if (room != null) {
          report(player, manager.fillAllAiSlots(id, room.id()));
        }
      }
      case SgsNetwork.ACTION_KICK -> kick(player, request.name());
      case SgsNetwork.ACTION_SPECTATE -> spectate(player, request.roomId());
      default -> {}
    }
    broadcastSync();
  }

  private void kick(ServerPlayer player, String targetName) {
    ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
    if (target == null) {
      SgsNetwork.sendLobbyError(player, RoomError.NOT_IN_ROOM);
      return;
    }
    report(player, manager.kick(player.getUUID(), target.getUUID()));
  }

  private void spectate(ServerPlayer player, long roomId) {
    boolean ok = dev.sgs.lobby.match.MatchService.get().spectateRoom(player, roomId);
    if (!ok) {
      SgsNetwork.sendLobbyError(player, RoomError.MATCH_NOT_FOUND);
    }
  }

  private void swapSeat(ServerPlayer player, String targetName) {
    ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
    if (target == null) {
      SgsNetwork.sendLobbyError(player, RoomError.ROOM_NOT_FOUND);
      return;
    }
    report(player, manager.swapSeat(player.getUUID(), target.getUUID()));
  }

  private void startMatchWithBots(ServerPlayer player) {
    RoomResult<Room> result = commandStart(player);
    if (!result.isOk()) {
      SgsNetwork.sendLobbyError(player, result.error());
    }
  }

  // ---------------------------------------------------------------- chat commands

  public RoomResult<Room> commandCreate(ServerPlayer player, String name, RoomMode mode) {
    return manager.createRoom(player.getUUID(), name, mode);
  }

  public RoomResult<Room> commandJoin(ServerPlayer player, long roomId) {
    return manager.joinRoom(player.getUUID(), roomId);
  }

  public RoomResult<Room> commandLeave(ServerPlayer player) {
    return manager.leaveRoom(player.getUUID());
  }

  public RoomResult<Room> commandKick(ServerPlayer player, String targetName) {
    ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
    if (target == null) {
      return RoomResult.fail(RoomError.NOT_IN_ROOM);
    }
    return manager.kick(player.getUUID(), target.getUUID());
  }

  public RoomResult<Room> commandStart(ServerPlayer player) {
    Room room = manager.roomOf(player.getUUID()).orElse(null);
    if (room == null) {
      return RoomResult.fail(RoomError.NOT_IN_ROOM);
    }
    RoomResult<Room> result = manager.beginMatch(player.getUUID(), room.id());
    if (!result.isOk()) {
      return result;
    }
    dev.sgs.lobby.match.MatchService.get().startRoomMatch(room);
    return result;
  }

  public List<RoomSummary> commandList() {
    return manager.allRooms();
  }

  public boolean commandSpectate(ServerPlayer player, long roomId) {
    return dev.sgs.lobby.match.MatchService.get().spectateRoom(player, roomId);
  }

  /** Disbands the room after its match worker exits. Safe to call from the worker callback. */
  public void afterMatchFinished(long roomId) {
    Room room = manager.room(roomId).orElse(null);
    if (room != null) {
      for (UUID member : new ArrayList<>(room.members())) {
        if (!member.equals(room.owner())) {
          manager.leaveRoom(member);
        }
      }
      manager.leaveRoom(room.owner());
    }
    broadcastSync();
  }

  private void report(ServerPlayer player, RoomResult<?> result) {
    if (!result.isOk()) {
      SgsNetwork.sendLobbyError(player, result.error());
    }
  }

  private static RoomMode modeOrNull(int ordinal) {
    RoomMode[] modes = RoomMode.values();
    return ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : null;
  }

  public void broadcastSync() {
    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
      sendSync(player);
    }
  }

  /** Serializes the lobby snapshot for one player. */
  public void sendSync(ServerPlayer player) {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    UUID id = player.getUUID();

    Room room = manager.roomOf(id).orElse(null);
    buf.writeBoolean(room != null);
    if (room != null) {
      buf.writeLong(room.id());
      buf.writeUtf(room.name());
      buf.writeByte(room.mode().ordinal());
      buf.writeUtf(nameOf(room.owner()));
      buf.writeBoolean(room.owner().equals(id));
      buf.writeVarInt(room.capacity());
      for (int slot = 0; slot < room.capacity(); slot++) {
        UUID occupant = room.occupant(slot);
        if (occupant != null) {
          buf.writeByte(1); // HUMAN
          buf.writeUtf(nameOf(occupant));
        } else if (room.aiSlots().contains(slot)) {
          buf.writeByte(2); // AI
        } else {
          buf.writeByte(0); // EMPTY
        }
      }
      buf.writeByte(room.state().ordinal());
    }

    RoomMode queued = manager.queuedMode(id).orElse(null);
    buf.writeByte(queued == null ? -1 : queued.ordinal());
    for (RoomMode mode : RoomMode.values()) {
      buf.writeInt(manager.queueSize(mode));
    }

    Long inviteRoomId = manager.pendingInvite(id).orElse(null);
    Room inviteRoom = inviteRoomId == null ? null : manager.room(inviteRoomId).orElse(null);
    buf.writeBoolean(inviteRoom != null);
    if (inviteRoom != null) {
      buf.writeUtf(nameOf(inviteRoom.owner()));
      buf.writeUtf(inviteRoom.name());
    }

    List<RoomSummary> joinable = manager.joinableRooms();
    buf.writeVarInt(joinable.size());
    for (RoomSummary summary : joinable) {
      buf.writeLong(summary.id());
      buf.writeUtf(summary.name());
      buf.writeByte(summary.mode().ordinal());
      buf.writeInt(summary.memberCount());
      buf.writeInt(summary.capacity());
    }

    NetworkManager.sendToPlayer(player, SgsNetwork.LOBBY_SYNC, buf);
  }

  private String nameOf(UUID uuid) {
    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
    return player == null ? uuid.toString().substring(0, 8) : player.getGameProfile().getName();
  }
}
