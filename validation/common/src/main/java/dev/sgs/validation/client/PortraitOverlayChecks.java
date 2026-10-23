package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Explicitly labelled presentation fixture; never sends actions or modifies the server. */
public final class PortraitOverlayChecks {
    private static int scale=1,frames,original;private static boolean started,done;
    private PortraitOverlayChecks(){}
    public static boolean tick(Minecraft client,MatchScreen screen){
        if(!Boolean.getBoolean("sgs.portraitOverlaySmoke") || done || screen.stateForSmoke().snapshot().status()!=1)return false;
        client.getToasts().clear();
        if(!started){original=client.options.guiScale().get();started=true;screen.portraitFixtureForSmoke(true);client.options.guiScale().set(scale);client.resizeDisplay();return true;}
        if(++frames<5)return true;
        Screenshot.grab(client.gameDirectory,"sgs-portrait-overlays-gui"+scale+".png",client.getMainRenderTarget(),message->{});
        ProbeMod.LOG.info("SGS_PORTRAIT_OVERLAY_CAPTURED gui={} fixture=true",scale);
        if(++scale<=3){frames=0;client.options.guiScale().set(scale);client.resizeDisplay();return true;}
        done=true;screen.portraitFixtureForSmoke(false);client.options.guiScale().set(original);client.resizeDisplay();
        ProbeMod.LOG.info("SGS_PORTRAIT_OVERLAY_PASS native_gui_scales=3 fixture_cleared=true");return true;
    }
}
