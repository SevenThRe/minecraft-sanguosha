package dev.sgs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shared base for every SGS screen. Dark-gold / deep-brown background, warm title text, and
 * {@code isPauseScreen()} returning false so opening SGS GUI does not freeze the server.
 */
public abstract class SgsScreen extends Screen {

  protected SgsScreen(Component title) {
    super(title);
  }

  public void renderSgsBackground(GuiGraphics gui) {
    int width = gui.guiWidth();
    int height = gui.guiHeight();
    gui.fill(0, 0, width, height, 0xFF1C140F);
    int[] stops = {0xFF261C14, 0xFF2E2319, 0xFF34281D, 0xFF382C20, 0xFF3C2F22};
    for (int i = 0; i < stops.length; i++) {
      gui.fill(0, (height / stops.length) * i, width, (height / stops.length) * (i + 1), stops[i]);
    }
    gui.fill(0, 0, width, 2, SgsTheme.GOLD_DIM);
    gui.fill(0, height - 2, width, height, SgsTheme.GOLD_DIM);
    gui.fill(0, 0, 2, height, SgsTheme.GOLD_DIM);
    gui.fill(width - 2, 0, width, height, SgsTheme.GOLD_DIM);
  }

  public void renderTitle(GuiGraphics gui, int width, Component title) {
    gui.drawCenteredString(Minecraft.getInstance().font, title, width / 2, 10, SgsTheme.GOLD);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
