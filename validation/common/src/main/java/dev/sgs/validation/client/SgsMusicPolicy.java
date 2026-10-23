package dev.sgs.validation.client;

import net.minecraft.client.Minecraft;

/** Suppress vanilla BGM while using SGS, without changing saved sound settings. */
public final class SgsMusicPolicy {
    private SgsMusicPolicy() {}

    public static boolean active() {
        var client=Minecraft.getInstance();
        if(client.getConnection()==null)return false;
        return MatchClient.hasSession() || client.screen instanceof MatchScreen
            || client.screen instanceof LobbyScreen || client.screen instanceof CollectionScreen
            || client.screen instanceof ReportHistoryChatScreen || client.screen instanceof ProbeScreen;
    }
}
