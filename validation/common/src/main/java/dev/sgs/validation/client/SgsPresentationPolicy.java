package dev.sgs.validation.client;

import net.minecraft.client.Minecraft;

/** The SGS board and menus own their full canvas; ordinary Minecraft screens retain their HUD. */
public final class SgsPresentationPolicy {
    private SgsPresentationPolicy(){}
    public static boolean ownsCanvas(){
        var screen=Minecraft.getInstance().screen;
        return screen instanceof MatchScreen||screen instanceof LobbyScreen||screen instanceof CollectionScreen
            ||screen instanceof ReportHistoryChatScreen||screen instanceof ProbeScreen;
    }
}
