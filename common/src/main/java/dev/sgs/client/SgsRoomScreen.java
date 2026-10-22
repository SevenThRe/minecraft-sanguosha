package dev.sgs.client;

import dev.sgs.network.SgsNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * The dedicated pre-match room screen. Shows every sparse seat slot in a 2-row grid; owner gets the
 * start / fill-AI / kick controls; empty/human/AI slots render with distinct colors.
 */
public final class SgsRoomScreen extends SgsScreen {

  private static final int SEAT_W = 160;
  private static final int SEAT_H = 80;
  private static final int GAP_X = 14;
  private static final int GAP_Y = 24;
  private static final int MENU_W = 120;
  private static final int MENU_H = 92;

  private int hoveredSlot = -1;
  private int menuSlot = -1; // which slot's popup menu is open (-1 = none)

  public SgsRoomScreen() {
    super(Component.translatable("screen.sgs.lobby.title"));
  }

  @Override
  protected void init() {
    clearWidgets();
    super.init();

    ClientLobbyState.Snapshot snap = ClientLobbyState.snapshot();
    ClientLobbyState.RoomSnapshot room = snap == null ? null : snap.room();
    if (room == null) {
      // should not happen, but safety fallback
      addRenderableWidget(
          Button.builder(
                  Component.translatable("screen.sgs.room.back"),
                  b -> minecraft.setScreen(new LobbyScreen()))
              .bounds(width / 2 - 60, height / 2, 120, 20)
              .build());
      return;
    }

    int cols = Math.min(4, room.capacity());
    int rows = (room.capacity() + cols - 1) / cols;
    int totalW = cols * SEAT_W + (cols - 1) * GAP_X;
    int startX = (width - totalW) / 2;
    int startY = 90;

    int selfSlot = findSelfSlot(room);

    for (ClientLobbyState.SlotView slot : room.slots()) {
      if (slot.slot() >= room.capacity()) {
        continue;
      }
      int row = slot.slot() / cols;
      int col = slot.slot() % cols;
      int x = startX + col * (SEAT_W + GAP_X);
      int y = startY + row * (SEAT_H + GAP_Y);

      // Kick button (owner only, on non-owner human seats)
      if (room.isOwner()
          && slot.occupantName() != null
          && !slot.occupantName().equals(room.ownerName())
          && !slot.isAi()
          && !room.playing()) {
        Button kick =
            Button.builder(
                    Component.translatable("screen.sgs.room.kick"),
                    b -> SgsNetwork.sendKick(slot.occupantName()))
                .bounds(x + SEAT_W - 50, y + 2, 46, 14)
                .build();
        kick.active = true;
        addRenderableWidget(kick);
      }
    }

    // Bottom controls
    int bottom = height - 46;
    int bx = width / 2;
    int i = 0;
    if (room.isOwner() && !room.playing()) {
      addRenderableWidget(
          Button.builder(
                  Component.translatable("screen.sgs.room.fill_ai"), b -> SgsNetwork.sendFillAi())
              .bounds(bx - 210 + i * 105, bottom, 100, 20)
              .build());
      i++;
      addRenderableWidget(
          Button.builder(
                  Component.translatable("screen.sgs.room.start_bots"),
                  b -> SgsNetwork.sendStartWithBots())
              .bounds(bx - 210 + i * 105, bottom, 100, 20)
              .build());
      i++;
    }
    if (selfSlot >= 0 && !room.playing()) {
      addRenderableWidget(
          Button.builder(
                  Component.translatable("screen.sgs.room.sit"),
                  b -> SgsNetwork.sendSimple(SgsNetwork.ACTION_SIT))
              .bounds(bx - 210 + i * 105, bottom, 100, 20)
              .build());
      i++;
    }
    addRenderableWidget(
        Button.builder(
                Component.translatable("screen.sgs.lobby.room.leave"),
                b -> SgsNetwork.sendSimple(SgsNetwork.ACTION_LEAVE))
            .bounds(bx - 210 + i * 105, bottom, 100, 20)
            .build());
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    renderSgsBackground(graphics);
    renderTitle(graphics, width, this.title);
    super.render(graphics, mouseX, mouseY, partialTick);

    ClientLobbyState.Snapshot snap = ClientLobbyState.snapshot();
    ClientLobbyState.RoomSnapshot room = snap == null ? null : snap.room();
    if (room == null) {
      graphics.drawCenteredString(
          this.font,
          Component.translatable("screen.sgs.room.none"),
          width / 2,
          height / 2 - 12,
          SgsTheme.TEXT_DIM);
      return;
    }

    renderHeader(graphics, room);
    renderSeats(graphics, room, mouseX, mouseY);
    renderPopupMenu(graphics, room, mouseX, mouseY);

    if (room.playing()) {
      graphics.drawCenteredString(
          this.font,
          Component.translatable("screen.sgs.lobby.room.playing"),
          width / 2,
          height / 2,
          SgsTheme.GOLD);
    }
  }

  private void renderHeader(GuiGraphics graphics, ClientLobbyState.RoomSnapshot room) {
    graphics.drawCenteredString(
        this.font,
        Component.translatable(
            "screen.sgs.lobby.room.header",
            room.name(),
            Component.translatable("screen.sgs.lobby.mode." + room.mode().playerCount()),
            countHumans(room) + (countAi(room) > 0 ? " (+" + countAi(room) + " AI)" : ""),
            room.capacity()),
        width / 2,
        56,
        SgsTheme.TEXT);
    String ownerLine =
        Component.translatable(
                "screen.sgs.lobby.room.owner", room.ownerName() + (room.isOwner() ? "  [you]" : ""))
            .getString();
    graphics.drawCenteredString(this.font, ownerLine, width / 2, 72, SgsTheme.GOLD);
  }

  private void renderSeats(
      GuiGraphics graphics, ClientLobbyState.RoomSnapshot room, int mouseX, int mouseY) {
    int cols = Math.min(4, room.capacity());
    int rows = (room.capacity() + cols - 1) / cols;
    int totalW = cols * SEAT_W + (cols - 1) * GAP_X;
    int startX = (width - totalW) / 2;
    int startY = 90;

    hoveredSlot = -1;

    for (int slotIdx = 0; slotIdx < room.capacity(); slotIdx++) {
      int row = slotIdx / cols;
      int col = slotIdx % cols;
      int x = startX + col * (SEAT_W + GAP_X);
      int y = startY + row * (SEAT_H + GAP_Y);

      ClientLobbyState.SlotView slot = findSlot(room, slotIdx);
      boolean hovered = mouseX >= x && mouseX <= x + SEAT_W && mouseY >= y && mouseY <= y + SEAT_H;
      if (hovered) {
        hoveredSlot = slotIdx;
      }
      renderSingleSeat(graphics, slot, x, y, hovered, slotIdx == 0 /*owner seat idx marker*/, room);
    }
  }

  private void renderSingleSeat(
      GuiGraphics graphics,
      ClientLobbyState.SlotView slot,
      int x,
      int y,
      boolean hovered,
      boolean isOwnerSlot,
      ClientLobbyState.RoomSnapshot room) {
    boolean occupied = slot.occupantName() != null;
    boolean isAiSlot = slot.isAi();

    // Frame + background
    int frameColor = isOwnerSlot ? SgsTheme.GOLD : SgsTheme.FRAME;
    graphics.fill(x, y, x + SEAT_W, y + SEAT_H, frameColor);

    if (occupied) {
      // Human seat: warm parchment bg with kingdom-ish tint
      graphics.fill(x + 2, y + 2, x + SEAT_W - 2, y + SEAT_H - 2, 0xFF2B1F14);
      // Top strip with name & seat number
      graphics.fill(x + 2, y + 2, x + SEAT_W - 2, y + 16, 0xFF352518);
      graphics.drawString(this.font, "#" + (slot.slot() + 1), x + 6, y + 5, SgsTheme.TEXT_DIM);
      graphics.drawString(this.font, slot.occupantName(), x + 22, y + 5, SgsTheme.TEXT);
      if (slot.occupantName().equals(room.ownerName())) {
        graphics.drawString(this.font, "主", x + SEAT_W - 22, y + 5, SgsTheme.GOLD);
      }
      if (hovered && !room.playing()) {
        graphics.renderOutline(x, y, SEAT_W, SEAT_H, SgsTheme.GOLD);
      }
    } else if (isAiSlot) {
      // AI reserved slot: gold-ish placeholder
      graphics.fill(x + 2, y + 2, x + SEAT_W - 2, y + SEAT_H - 2, 0xFF2D2210);
      graphics.drawCenteredString(
          this.font,
          Component.translatable("screen.sgs.room.ai_slot", slot.slot() + 1),
          x + SEAT_W / 2,
          y + SEAT_H / 2 - 4,
          SgsTheme.GOLD_DIM);
      if (hovered && room.isOwner() && !room.playing()) {
        graphics.renderOutline(x, y, SEAT_W, SEAT_H, SgsTheme.GOLD);
      }
    } else {
      // Empty slot: dim placeholder
      graphics.fill(x + 2, y + 2, x + SEAT_W - 2, y + SEAT_H - 2, 0xFF1C140F);
      graphics.drawCenteredString(
          this.font,
          Component.translatable("screen.sgs.room.empty", slot.slot() + 1),
          x + SEAT_W / 2,
          y + SEAT_H / 2 - 4,
          SgsTheme.TEXT_DIM);
      if (hovered && !room.playing()) {
        graphics.renderOutline(x, y, SEAT_W, SEAT_H, SgsTheme.TEXT_DIM);
      }
    }
  }

  private ClientLobbyState.SlotView findSlot(ClientLobbyState.RoomSnapshot room, int idx) {
    for (ClientLobbyState.SlotView s : room.slots()) {
      if (s.slot() == idx) {
        return s;
      }
    }
    return new ClientLobbyState.SlotView(idx, null, false);
  }

  private int findSelfSlot(ClientLobbyState.RoomSnapshot room) {
    String me = net.minecraft.client.Minecraft.getInstance().getUser().getName();
    for (ClientLobbyState.SlotView s : room.slots()) {
      if (me.equals(s.occupantName())) {
        return s.slot();
      }
    }
    return -1;
  }

  private int countHumans(ClientLobbyState.RoomSnapshot room) {
    int n = 0;
    for (ClientLobbyState.SlotView s : room.slots()) {
      if (s.occupantName() != null && !s.isAi()) {
        n++;
      }
    }
    return n;
  }

  private int countAi(ClientLobbyState.RoomSnapshot room) {
    int n = 0;
    for (ClientLobbyState.SlotView s : room.slots()) {
      if (s.isAi()) {
        n++;
      }
    }
    return n;
  }

  // ---------------------------------------------------------------- popup menu on empty seats

  @Override
  public boolean mouseClicked(double mouseX, double mouseY, int button) {
    int mx = (int) mouseX;
    int my = (int) mouseY;
    ClientLobbyState.Snapshot snap = ClientLobbyState.snapshot();
    ClientLobbyState.RoomSnapshot room = snap == null ? null : snap.room();

    // Popup menu buttons consume the click first.
    if (menuSlot >= 0 && room != null && !room.playing()) {
      MenuAction action = hitMenuButton(mx, my, menuSlot);
      if (action != null) {
        applyMenuAction(action, menuSlot);
        menuSlot = -1;
        return true;
      }
      // Click outside popup → close it unless the click targets an empty seat below.
      if (!isInsideMenu(mx, my, menuSlot)) {
        menuSlot = -1;
      } else {
        return true;
      }
    }

    if (room != null && !room.playing()) {
      int cols = Math.min(4, room.capacity());
      int rows = (room.capacity() + cols - 1) / cols;
      int totalW = cols * SEAT_W + (cols - 1) * GAP_X;
      int startX = (width - totalW) / 2;
      int startY = 90;

      for (int slotIdx = 0; slotIdx < room.capacity(); slotIdx++) {
        int row = slotIdx / cols;
        int col = slotIdx % cols;
        int sx = startX + col * (SEAT_W + GAP_X);
        int sy = startY + row * (SEAT_H + GAP_Y);
        if (mx >= sx && mx <= sx + SEAT_W && my >= sy && my <= sy + SEAT_H) {
          ClientLobbyState.SlotView slot = findSlot(room, slotIdx);
          // Empty or AI slot → open popup; human seat does nothing here (kick button is the only
          // action).
          if (slot.occupantName() == null) {
            menuSlot = slotIdx;
            return true;
          }
        }
      }
    }
    return super.mouseClicked(mouseX, mouseY, button);
  }

  private enum MenuAction {
    SIT,
    ADD_AI,
    INVITE
  }

  private boolean isInsideMenu(int mx, int my, int slot) {
    int[] rect = menuRect(slot);
    return mx >= rect[0] && mx <= rect[0] + MENU_W && my >= rect[1] && my <= rect[1] + MENU_H;
  }

  private int[] menuRect(int slot) {
    int cols = Math.min(4, 8);
    int totalW = cols * SEAT_W + (cols - 1) * GAP_X;
    int startX = (width - totalW) / 2;
    int startY = 90;
    int row = slot / cols;
    int col = slot % cols;
    int sx = startX + col * (SEAT_W + GAP_X);
    int sy = startY + row * (SEAT_H + GAP_Y);
    // Place popup below the seat card. If it would fall off screen, flip above.
    int menuX = sx + SEAT_W / 2 - MENU_W / 2;
    int menuY = sy + SEAT_H + 4;
    if (menuY + MENU_H > height - 50) {
      menuY = sy - MENU_H - 4;
    }
    menuX = Math.max(4, Math.min(width - MENU_W - 4, menuX));
    return new int[] {menuX, menuY};
  }

  private MenuAction hitMenuButton(int mx, int my, int slot) {
    int[] r = menuRect(slot);
    int bx = r[0] + 8;
    int bw = MENU_W - 16;
    int bh = 22;
    int by = r[1] + 28; // 2 buttons: Sit + Add AI
    String[] labels = {"sit", "ai"};
    for (int i = 0; i < labels.length; i++) {
      int yy = by + i * (bh + 3);
      if (mx >= bx && mx <= bx + bw && my >= yy && my <= yy + bh) {
        return switch (labels[i]) {
          case "sit" -> MenuAction.SIT;
          case "ai" -> MenuAction.ADD_AI;
          default -> null;
        };
      }
    }
    return null;
  }

  private void applyMenuAction(MenuAction action, int slot) {
    switch (action) {
      case SIT -> SgsNetwork.sendSit(slot);
      case ADD_AI -> SgsNetwork.sendAiSlot(slot, true);
      case INVITE -> {
        /* unused — invite flow reuses LobbyScreen's invite box */
      }
    }
  }

  private void renderPopupMenu(
      GuiGraphics graphics, ClientLobbyState.RoomSnapshot room, int mouseX, int mouseY) {
    if (menuSlot < 0 || room == null || room.playing()) {
      return;
    }
    int[] r = menuRect(menuSlot);
    int bx = r[0];
    int by = r[1];
    graphics.fill(bx, by, bx + MENU_W, by + MENU_H, 0xCC1C140F);
    graphics.fill(bx, by, bx + MENU_W, by + 1, SgsTheme.GOLD_DIM);
    graphics.fill(bx, by + MENU_H - 1, bx + MENU_W, by + MENU_H, SgsTheme.GOLD_DIM);
    graphics.fill(bx, by, bx + 1, by + MENU_H, SgsTheme.GOLD_DIM);
    graphics.fill(bx + MENU_W - 1, by, bx + MENU_W, by + MENU_H, SgsTheme.GOLD_DIM);

    graphics.drawString(
        this.font,
        Component.translatable("screen.sgs.room.popup.title"),
        bx + 6,
        by + 4,
        SgsTheme.GOLD);

    int bw = MENU_W - 16;
    int bh = 22;
    int bx2 = bx + 8;
    int by2 = by + 28;
    String[] labels = {
      Component.translatable("screen.sgs.room.popup.sit").getString(),
      Component.translatable("screen.sgs.room.popup.ai").getString(),
    };
    for (int i = 0; i < labels.length; i++) {
      int yy = by2 + i * (bh + 3);
      boolean hovered = mouseX >= bx2 && mouseX <= bx2 + bw && mouseY >= yy && mouseY <= yy + bh;
      graphics.fill(bx2, yy, bx2 + bw, yy + bh, hovered ? 0xFF42341F : 0xFF261C14);
      graphics.renderOutline(bx2, yy, bw, bh, SgsTheme.GOLD_DIM);
      graphics.drawString(this.font, labels[i], bx2 + 6, yy + 6, SgsTheme.TEXT);
    }
  }

  @Override
  public boolean shouldCloseOnEsc() {
    return true;
  }

  @Override
  public void onClose() {
    ClientLobbyState.Snapshot snap = ClientLobbyState.snapshot();
    ClientLobbyState.RoomSnapshot room = snap == null ? null : snap.room();
    if (room != null && room.playing()) {
      super.onClose(); // let ESC close the screen during play
      return;
    }
    // If still in lobby, leave first before closing.
    SgsNetwork.sendSimple(SgsNetwork.ACTION_LEAVE);
    super.onClose();
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
