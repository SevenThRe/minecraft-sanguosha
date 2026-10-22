package dev.sgs.client;

import dev.sgs.core.room.RoomMode;
import dev.sgs.network.SgsNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Fullscreen lobby GUI: server matchmaking tab and room tab (list / create / invite). */
public final class LobbyScreen extends SgsScreen {

  private enum Tab {
    MATCH,
    ROOM
  }

  private static final int BUTTON_H = 20;

  private Tab tab = Tab.MATCH;
  private RoomMode createMode = RoomMode.EIGHT;
  private EditBox roomNameBox;
  private EditBox inviteNameBox;
  private String roomNameText = "";
  private String inviteNameText = "";

  public LobbyScreen() {
    super(Component.translatable("screen.sgs.lobby.title"));
  }

  /** Rebuilds widgets from the latest server snapshot, preserving input focus text. */
  public void refresh() {
    if (roomNameBox != null) {
      roomNameText = roomNameBox.getValue();
    }
    if (inviteNameBox != null) {
      inviteNameText = inviteNameBox.getValue();
    }
    roomNameBox = null;
    inviteNameBox = null;
    clearWidgets();
    init();
  }

  @Override
  public void init() {
    int cx = this.width / 2;
    ClientLobbyState.Snapshot snap = ClientLobbyState.snapshot();
    // If we are inside a room already, jump straight to the dedicated room screen.
    if (snap != null && snap.room() != null) {
      minecraft.setScreen(new SgsRoomScreen());
      return;
    }
    addTabButton(cx - 82, 28, Tab.MATCH);
    addTabButton(cx + 2, 28, Tab.ROOM);
    if (tab == Tab.MATCH) {
      initMatchTab(cx);
    } else {
      initRoomTab(cx);
    }
  }

  private void addTabButton(int x, int y, Tab target) {
    Component label =
        Component.translatable(
            target == Tab.MATCH ? "screen.sgs.lobby.tab.match" : "screen.sgs.lobby.tab.room");
    Button button =
        addRenderableWidget(
            Button.builder(label, b -> switchTab(target)).bounds(x, y, 80, BUTTON_H).build());
    button.active = tab != target;
  }

  private void switchTab(Tab target) {
    tab = target;
    refresh();
  }

  // ---- Matchmaking tab ----

  private void initMatchTab(int cx) {
    ClientLobbyState.Snapshot snap = ClientLobbyState.snapshot();
    int y = 66;
    for (RoomMode mode : RoomMode.values()) {
      boolean queued = snap.queuedMode() == mode;
      Component label =
          Component.translatable(
              queued ? "screen.sgs.lobby.queue.leave" : "screen.sgs.lobby.queue.join");
      addRenderableWidget(
          Button.builder(
                  label,
                  b -> {
                    if (queued) {
                      SgsNetwork.sendSimple(SgsNetwork.ACTION_QUEUE_LEAVE);
                    } else {
                      SgsNetwork.sendQueueJoin(mode);
                    }
                  })
              .bounds(cx + 30, y, 110, BUTTON_H)
              .build());
      y += 30;
    }
  }

  // ---- Room tab ----

  private void initRoomTab(int cx) {
    ClientLobbyState.Snapshot snap = ClientLobbyState.snapshot();
    int y = 60;

    if (snap.invite() != null) {
      addRenderableWidget(
          Button.builder(
                  Component.translatable("screen.sgs.lobby.invite.accept"),
                  b -> SgsNetwork.sendSimple(SgsNetwork.ACTION_ACCEPT))
              .bounds(cx - 110, y, 80, BUTTON_H)
              .build());
      addRenderableWidget(
          Button.builder(
                  Component.translatable("screen.sgs.lobby.invite.decline"),
                  b -> SgsNetwork.sendSimple(SgsNetwork.ACTION_DECLINE))
              .bounds(cx - 25, y, 80, BUTTON_H)
              .build());
      y += 26;
    }

    if (snap.room() != null) {
      initInRoom(cx, y, snap.room());
    } else {
      initRoomBrowser(cx, y, snap);
    }
  }

  private void initInRoom(int cx, int y, ClientLobbyState.RoomSnapshot room) {
    int bottom = this.height - 36;
    addRenderableWidget(
        Button.builder(
                Component.translatable("screen.sgs.lobby.room.leave"),
                b -> SgsNetwork.sendSimple(SgsNetwork.ACTION_LEAVE))
            .bounds(cx + 40, bottom, 100, BUTTON_H)
            .build());

    if (room.isOwner() && !room.playing()) {
      addRenderableWidget(
          Button.builder(
                  Component.translatable("screen.sgs.lobby.room.start_bots"),
                  b -> SgsNetwork.sendStartWithBots())
              .bounds(cx - 150, bottom, 140, BUTTON_H)
              .build());
      addRenderableWidget(
          Button.builder(
                  Component.translatable("screen.sgs.lobby.room.fill_ai"),
                  b -> SgsNetwork.sendFillAi())
              .bounds(cx - 300, bottom, 140, BUTTON_H)
              .build());
      inviteNameBox = new EditBox(this.font, cx - 5, bottom, 110, BUTTON_H, Component.empty());
      inviteNameBox.setValue(inviteNameText);
      inviteNameBox.setHint(Component.translatable("screen.sgs.lobby.room.invite_hint"));
      inviteNameBox.setMaxLength(16);
      addRenderableWidget(inviteNameBox);
      addRenderableWidget(
          Button.builder(
                  Component.translatable("screen.sgs.lobby.room.invite"),
                  b -> {
                    if (inviteNameBox != null && !inviteNameBox.getValue().isBlank()) {
                      SgsNetwork.sendInvite(inviteNameBox.getValue().trim());
                    }
                  })
              .bounds(cx + 110, bottom, 40, BUTTON_H)
              .build());
    }

    int rowY = y + 34;
    for (ClientLobbyState.SlotView slot : room.slots()) {
      if (slot.occupantName() != null && !room.playing()) {
        if (room.isOwner()
            && !slot.occupantName().equals(room.ownerName())
            && !slot.occupantName().equals(
                net.minecraft.client.Minecraft.getInstance().getUser().getName())) {
          final String target = slot.occupantName();
          addRenderableWidget(
              Button.builder(
                      Component.translatable("screen.sgs.lobby.room.kick"),
                      b -> SgsNetwork.sendKick(target))
                  .bounds(cx + 60, rowY - 2, 60, 12)
                  .build());
        }
      }
      rowY += 12;
    }
  }

  private void initRoomBrowser(int cx, int y, ClientLobbyState.Snapshot snap) {
    roomNameBox = new EditBox(this.font, cx - 150, y, 120, BUTTON_H, Component.empty());
    roomNameBox.setValue(roomNameText);
    roomNameBox.setHint(Component.translatable("screen.sgs.lobby.room.name_hint"));
    roomNameBox.setMaxLength(24);
    addRenderableWidget(roomNameBox);

    addRenderableWidget(
        Button.builder(
                Component.translatable("screen.sgs.lobby.mode." + createMode.playerCount()),
                b -> {
                  createMode = createMode == RoomMode.EIGHT ? RoomMode.SIX : RoomMode.EIGHT;
                  refresh();
                })
            .bounds(cx - 25, y, 60, BUTTON_H)
            .build());

    addRenderableWidget(
        Button.builder(
                Component.translatable("screen.sgs.lobby.room.create"),
                b -> {
                  String name = roomNameBox == null ? "" : roomNameBox.getValue().trim();
                  SgsNetwork.sendCreateRoom(name, createMode);
                })
            .bounds(cx + 40, y, 100, BUTTON_H)
            .build());

    int rowY = y + 34;
    int maxRows = Math.max(0, (this.height - rowY - 20) / 24);
    int shown = 0;
    for (ClientLobbyState.RoomEntry entry : snap.joinable()) {
      if (shown >= maxRows) {
        break;
      }
      long roomId = entry.id();
      addRenderableWidget(
          Button.builder(
                  Component.translatable("screen.sgs.lobby.room.join"),
                  b -> SgsNetwork.sendJoinRoom(roomId))
              .bounds(cx + 70, rowY, 70, BUTTON_H)
              .build());
      rowY += 24;
      shown++;
    }
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    renderSgsBackground(graphics);
    renderTitle(graphics, width, this.title);
    super.render(graphics, mouseX, mouseY, partialTick);
    int cx = this.width / 2;

    ClientLobbyState.Snapshot snap = ClientLobbyState.snapshot();
    if (tab == Tab.MATCH) {
      renderMatchTab(graphics, cx, snap);
    } else {
      renderRoomTab(graphics, cx, snap);
    }
  }

  private void renderMatchTab(GuiGraphics graphics, int cx, ClientLobbyState.Snapshot snap) {
    int y = 72;
    for (RoomMode mode : RoomMode.values()) {
      int queued = snap.queueSizes()[mode.ordinal()];
      Component line =
          Component.translatable(
              "screen.sgs.lobby.queue.status",
              Component.translatable("screen.sgs.lobby.mode." + mode.playerCount()),
              queued,
              mode.playerCount());
      graphics.drawString(this.font, line, cx - 150, y + 5, 0xE0E0E0);
      y += 30;
    }
    if (snap.queuedMode() != null) {
      graphics.drawCenteredString(
          this.font,
          Component.translatable(
              "screen.sgs.lobby.queue.waiting",
              Component.translatable("screen.sgs.lobby.mode." + snap.queuedMode().playerCount())),
          cx,
          y + 10,
          0xFFD75F);
    }
  }

  private void renderRoomTab(GuiGraphics graphics, int cx, ClientLobbyState.Snapshot snap) {
    int y = 60;
    if (snap.invite() != null) {
      graphics.drawCenteredString(
          this.font,
          Component.translatable(
              "screen.sgs.lobby.invite.from",
              snap.invite().inviterName(),
              snap.invite().roomName()),
          cx,
          y + 6,
          0x7FE07F);
      y += 26;
    }

    ClientLobbyState.RoomSnapshot room = snap.room();
    if (room != null) {
      int humans = 0;
      for (ClientLobbyState.SlotView slot : room.slots()) {
        if (slot.occupantName() != null) {
          humans++;
        }
      }
      graphics.drawCenteredString(
          this.font,
          Component.translatable(
              "screen.sgs.lobby.room.header",
              room.name(),
              Component.translatable("screen.sgs.lobby.mode." + room.mode().playerCount()),
              humans,
              room.capacity()),
          cx,
          y + 4,
          0xFFFFFF);
      y += 18;
      graphics.drawCenteredString(
          this.font,
          Component.translatable("screen.sgs.lobby.room.owner", room.ownerName()),
          cx,
          y,
          0xA0A0A0);
      y += 16;
      for (ClientLobbyState.SlotView slot : room.slots()) {
        if (slot.occupantName() != null) {
          Component line =
              Component.translatable(
                  "screen.sgs.lobby.room.seat", slot.slot() + 1, slot.occupantName());
          graphics.drawString(this.font, line, cx - 40, y, 0xE0E0E0);
        } else if (slot.isAi()) {
          graphics.drawString(
              this.font,
              Component.translatable("screen.sgs.lobby.room.seat_ai", slot.slot() + 1),
              cx - 40,
              y,
              0xFFD75F);
        } else {
          graphics.drawString(
              this.font,
              Component.translatable("screen.sgs.lobby.room.seat_empty", slot.slot() + 1),
              cx - 40,
              y,
              0x707070);
        }
        y += 12;
      }
      if (room.playing()) {
        graphics.drawCenteredString(
            this.font,
            Component.translatable("screen.sgs.lobby.room.playing"),
            cx,
            this.height - 64,
            0xFFD75F);
      }
      return;
    }

    y += 34;
    graphics.drawString(
        this.font, Component.translatable("screen.sgs.lobby.room.list"), cx - 150, y, 0xA0A0A0);
    y += 10;
    if (snap.joinable().isEmpty()) {
      graphics.drawCenteredString(
          this.font, Component.translatable("screen.sgs.lobby.room.empty"), cx, y + 20, 0x707070);
      return;
    }
    int maxRows = Math.max(0, (this.height - y - 20) / 24);
    int shown = 0;
    for (ClientLobbyState.RoomEntry entry : snap.joinable()) {
      if (shown >= maxRows) {
        break;
      }
      Component line =
          Component.translatable(
              "screen.sgs.lobby.room.row",
              entry.name(),
              Component.translatable("screen.sgs.lobby.mode." + entry.mode().playerCount()),
              entry.memberCount(),
              entry.capacity());
      graphics.drawString(this.font, line, cx - 150, y + 12, 0xE0E0E0);
      y += 24;
      shown++;
    }
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  /** Renders the SGS dark-gold background before widgets. */
  protected void renderSgsBg(GuiGraphics graphics) {
    renderSgsBackground(graphics);
  }
}
