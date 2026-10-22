package dev.sgs.client;

import java.util.ArrayList;
import java.util.List;

import dev.architectury.networking.NetworkManager;
import dev.sgs.client.game.ClientGameState;
import dev.sgs.client.game.GameScreen;
import dev.sgs.core.match.dto.MatchViews.MatchSnapshot;
import dev.sgs.core.room.RoomError;
import dev.sgs.core.room.RoomMode;
import dev.sgs.network.GamePackets;
import dev.sgs.network.SgsNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/** Client entry: S2C receivers for the lobby GUI. Invoked only on the client distribution. */
public final class SgsClient {

  private SgsClient() {}

  public static void init() {
    NetworkManager.registerReceiver(
        NetworkManager.Side.S2C,
        SgsNetwork.OPEN_LOBBY,
        (buf, ctx) -> ctx.queue(() -> Minecraft.getInstance().setScreen(new LobbyScreen())));

    NetworkManager.registerReceiver(
        NetworkManager.Side.S2C,
        SgsNetwork.LOBBY_SYNC,
        (buf, ctx) -> {
          ClientLobbyState.Snapshot snapshot = decodeSync(buf);
          ctx.queue(
              () -> {
                ClientLobbyState.apply(snapshot);
                if (Minecraft.getInstance().screen instanceof LobbyScreen lobby) {
                  lobby.refresh();
                }
              });
        });

    NetworkManager.registerReceiver(
        NetworkManager.Side.S2C,
        SgsNetwork.LOBBY_ERROR,
        (buf, ctx) -> {
          int ordinal = buf.readByte();
          ctx.queue(
              () -> {
                RoomError[] errors = RoomError.values();
                if (ordinal < 0 || ordinal >= errors.length) {
                  return;
                }
                Component message =
                    Component.translatable("lobby.sgs.error." + errors[ordinal].name());
                if (Minecraft.getInstance().player != null) {
                  Minecraft.getInstance().player.displayClientMessage(message, false);
                }
              });
        });

    NetworkManager.registerReceiver(
        NetworkManager.Side.S2C,
        GamePackets.GAME_SNAPSHOT,
        (buf, ctx) -> {
          MatchSnapshot snapshot = GamePackets.decodeSnapshot(buf);
          ctx.queue(
              () -> {
                ClientGameState.apply(snapshot);
                if (!(Minecraft.getInstance().screen instanceof GameScreen)) {
                  Minecraft.getInstance().setScreen(new GameScreen());
                }
              });
        });

    NetworkManager.registerReceiver(
        NetworkManager.Side.S2C,
        GamePackets.GAME_ERROR,
        (buf, ctx) -> {
          int nonce = GamePackets.decodeErrorNonce(buf);
          String reason = GamePackets.decodeErrorReason(buf);
          ctx.queue(
              () -> {
                if (Minecraft.getInstance().player != null) {
                  Minecraft.getInstance()
                      .player
                      .displayClientMessage(
                          Component.translatable("game.sgs.error." + reason, nonce), false);
                }
              });
        });
  }

  private static ClientLobbyState.Snapshot decodeSync(FriendlyByteBuf buf) {
    ClientLobbyState.RoomSnapshot room = null;
    if (buf.readBoolean()) {
      long id = buf.readLong();
      String name = buf.readUtf();
      RoomMode mode = RoomMode.values()[buf.readByte()];
      String ownerName = buf.readUtf();
      boolean isOwner = buf.readBoolean();
      int capacity = buf.readVarInt();
      List<ClientLobbyState.SlotView> slots = new ArrayList<>(capacity);
      for (int i = 0; i < capacity; i++) {
        int kind = buf.readByte();
        String occupantName = kind == ClientLobbyState.SlotView.OCCUPANT_HUMAN ? buf.readUtf() : null;
        slots.add(
            new ClientLobbyState.SlotView(
                i, occupantName, kind == ClientLobbyState.SlotView.OCCUPANT_AI));
      }
      boolean playing = buf.readByte() != 0;
      room =
          new ClientLobbyState.RoomSnapshot(
              id, name, mode, ownerName, isOwner, List.copyOf(slots), capacity, playing);
    }

    int queuedOrdinal = buf.readByte();
    RoomMode queuedMode = queuedOrdinal < 0 ? null : RoomMode.values()[queuedOrdinal];
    int[] queueSizes = new int[RoomMode.values().length];
    for (int i = 0; i < queueSizes.length; i++) {
      queueSizes[i] = buf.readInt();
    }

    ClientLobbyState.Invite invite = null;
    if (buf.readBoolean()) {
      invite = new ClientLobbyState.Invite(buf.readUtf(), buf.readUtf());
    }

    int joinableCount = buf.readVarInt();
    List<ClientLobbyState.RoomEntry> joinable = new ArrayList<>(joinableCount);
    for (int i = 0; i < joinableCount; i++) {
      joinable.add(
          new ClientLobbyState.RoomEntry(
              buf.readLong(),
              buf.readUtf(),
              RoomMode.values()[buf.readByte()],
              buf.readInt(),
              buf.readInt()));
    }
    return new ClientLobbyState.Snapshot(
        room, queuedMode, queueSizes, invite, List.copyOf(joinable));
  }
}
