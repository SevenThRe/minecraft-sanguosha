package dev.sgs.validation.client;

import dev.sgs.validation.ProbeNetwork;
import java.util.UUID;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import dev.sgs.validation.client.widget.SgsButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ProbeScreen extends Screen {
    private ProbeNetwork.Snapshot state;
    private boolean pending;
    private Button submitButton;

    public ProbeScreen(ProbeNetwork.Snapshot state) {
        super(Component.translatable("screen.sgs.test"));
        this.state = state;
    }

    public UUID room() { return state.room(); }
    public void update(ProbeNetwork.Snapshot state) { this.state = state; pending = false; }

    @Override protected void init() {
        addRenderableWidget(SgsButton.make(Component.literal("三国杀大厅"), button -> LobbyClient.open())
                .bounds(8, 8, 96, 20).build());
        addRenderableWidget(SgsButton.make(Component.literal("素材验收"), button -> minecraft.setScreen(new dev.sgs.validation.client.art.ArtGalleryScreen(this)))
                .bounds(width - 88, 8, 80, 20).build());
        submitButton = addRenderableWidget(SgsButton.make(Component.translatable("screen.sgs.submit"), button -> {
            if (pending) return;
            pending = true;
            Balm.getNetworking().sendToServer(new ProbeNetwork.Intent(state.room(), 1));
        }).bounds(width / 2 - 70, height / 2 + 25, 140, 20).build());
    }

    void pressSubmitForSmoke() { submitButton.onPress(); submitButton.active = false; }
    ProbeNetwork.Snapshot snapshotForSmoke() { return state; }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 65, 0xffffff);
        graphics.drawCenteredString(font, "Room: " + state.room(), width / 2, height / 2 - 40, 0xffffff);
        graphics.drawCenteredString(font, "Server value: " + state.value(), width / 2, height / 2 - 20, 0xffffff);
        graphics.drawCenteredString(font, pending ? "Waiting for server..." : state.reason(), width / 2, height / 2, state.success() ? 0x77ff77 : 0xff7777);
    }

    @Override public boolean isPauseScreen() { return false; }
}
