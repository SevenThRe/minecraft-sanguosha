package dev.sgs.network;

import dev.architectury.networking.NetworkManager;
import dev.sgs.Sgs;
import dev.sgs.core.room.RoomError;
import dev.sgs.core.room.RoomMode;
import dev.sgs.lobby.SgsLobbyService;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Packet ids, C2S lobby actions and encode helpers. S2C receivers live in the client layer. */
public final class SgsNetwork {

  public static final ResourceLocation LOBBY_ACTION =
      new ResourceLocation(Sgs.MOD_ID, "lobby_action");
  public static final ResourceLocation OPEN_LOBBY = new ResourceLocation(Sgs.MOD_ID, "open_lobby");
  public static final ResourceLocation LOBBY_SYNC = new ResourceLocation(Sgs.MOD_ID, "lobby_sync");
  public static final ResourceLocation LOBBY_ERROR =
      new ResourceLocation(Sgs.MOD_ID, "lobby_error");

  public static final int ACTION_CREATE = 0;
  public static final int ACTION_JOIN = 1;
  public static final int ACTION_LEAVE = 2;
  public static final int ACTION_INVITE = 3;
  public static final int ACTION_ACCEPT = 4;
  public static final int ACTION_DECLINE = 5;
  public static final int ACTION_QUEUE_JOIN = 6;
  public static final int ACTION_QUEUE_LEAVE = 7;
  public static final int ACTION_REFRESH = 8;
  public static final int ACTION_SWAP_SEAT = 9;
  public static final int ACTION_START_BOTS = 10;
  public static final int ACTION_SIT = 11;
  public static final int ACTION_AI_SLOT = 12;
  public static final int ACTION_AI_FILL = 13;
  public static final int ACTION_KICK = 14;
  public static final int ACTION_SPECTATE = 15;

  private SgsNetwork() {}

  /** Called from common init; registers server-side receivers. */
  public static void init() {
    NetworkManager.registerReceiver(
        NetworkManager.Side.C2S,
        LOBBY_ACTION,
        (buf, ctx) -> {
          int action = buf.readByte();
          String name = "";
          long roomId = -1L;
          int modeOrdinal = -1;
          int slot = -1;
          boolean flag = false;
          switch (action) {
            case ACTION_CREATE -> {
              name = buf.readUtf(24);
              modeOrdinal = buf.readByte();
            }
            case ACTION_JOIN, ACTION_SPECTATE -> roomId = buf.readLong();
            case ACTION_INVITE, ACTION_SWAP_SEAT, ACTION_KICK -> name = buf.readUtf(16);
            case ACTION_QUEUE_JOIN -> modeOrdinal = buf.readByte();
            case ACTION_SIT -> slot = buf.readByte();
            case ACTION_AI_SLOT -> {
              slot = buf.readByte();
              flag = buf.readBoolean();
            }
            default -> {}
          }
          LobbyRequest request = new LobbyRequest(action, name, roomId, modeOrdinal, slot, flag);
          ctx.queue(
              () -> {
                if (ctx.getPlayer() instanceof ServerPlayer player) {
                  SgsLobbyService.get().handleAction(player, request);
                }
              });
        });

    NetworkManager.registerReceiver(
        NetworkManager.Side.C2S,
        GamePackets.GAME_ACTION,
        (buf, ctx) -> {
          dev.sgs.core.match.MatchAction action;
          try {
            action = GamePackets.decodeAction(buf);
          } catch (RuntimeException e) {
            Sgs.LOGGER.warn("malformed game action packet: {}", e.toString());
            return;
          }
          ctx.queue(
              () -> {
                if (ctx.getPlayer() instanceof ServerPlayer player
                    && dev.sgs.lobby.match.MatchService.get() != null) {
                  dev.sgs.lobby.match.MatchService.get().handleAction(player, action);
                }
              });
        });
  }

  public record LobbyRequest(
      int action, String name, long roomId, int modeOrdinal, int slot, boolean flag) {}

  // ---- Client send helpers (used by the lobby screen) ----

  public static void sendCreateRoom(String name, RoomMode mode) {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    buf.writeByte(ACTION_CREATE);
    buf.writeUtf(name, 24);
    buf.writeByte(mode.ordinal());
    NetworkManager.sendToServer(LOBBY_ACTION, buf);
  }

  public static void sendJoinRoom(long roomId) {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    buf.writeByte(ACTION_JOIN);
    buf.writeLong(roomId);
    NetworkManager.sendToServer(LOBBY_ACTION, buf);
  }

  public static void sendSimple(int action) {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    buf.writeByte(action);
    NetworkManager.sendToServer(LOBBY_ACTION, buf);
  }

  public static void sendInvite(String targetName) {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    buf.writeByte(ACTION_INVITE);
    buf.writeUtf(targetName, 16);
    NetworkManager.sendToServer(LOBBY_ACTION, buf);
  }

  public static void sendQueueJoin(RoomMode mode) {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    buf.writeByte(ACTION_QUEUE_JOIN);
    buf.writeByte(mode.ordinal());
    NetworkManager.sendToServer(LOBBY_ACTION, buf);
  }

  public static void sendSwapSeat(String targetName) {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    buf.writeByte(ACTION_SWAP_SEAT);
    buf.writeUtf(targetName, 16);
    NetworkManager.sendToServer(LOBBY_ACTION, buf);
  }

  public static void sendStartWithBots() {
    sendSimple(ACTION_START_BOTS);
  }

  public static void sendSit(int slot) {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    buf.writeByte(ACTION_SIT);
    buf.writeByte(slot);
    NetworkManager.sendToServer(LOBBY_ACTION, buf);
  }

  public static void sendAiSlot(int slot, boolean reserved) {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    buf.writeByte(ACTION_AI_SLOT);
    buf.writeByte(slot);
    buf.writeBoolean(reserved);
    NetworkManager.sendToServer(LOBBY_ACTION, buf);
  }

  public static void sendFillAi() {
    sendSimple(ACTION_AI_FILL);
  }

  public static void sendKick(String targetName) {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    buf.writeByte(ACTION_KICK);
    buf.writeUtf(targetName, 16);
    NetworkManager.sendToServer(LOBBY_ACTION, buf);
  }

  public static void sendSpectate(long roomId) {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    buf.writeByte(ACTION_SPECTATE);
    buf.writeLong(roomId);
    NetworkManager.sendToServer(LOBBY_ACTION, buf);
  }

  public static void sendGameAction(dev.sgs.core.match.MatchAction action) {
    NetworkManager.sendToServer(GamePackets.GAME_ACTION, GamePackets.encodeAction(action));
  }

  // ---- Server send helpers ----

  public static void sendOpenLobby(ServerPlayer player) {
    NetworkManager.sendToPlayer(player, OPEN_LOBBY, new FriendlyByteBuf(Unpooled.buffer()));
  }

  public static void sendLobbyError(ServerPlayer player, RoomError error) {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    buf.writeByte(error.ordinal());
    NetworkManager.sendToPlayer(player, LOBBY_ERROR, buf);
  }
}
