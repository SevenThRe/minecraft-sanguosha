package dev.sgs.validation.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import dev.sgs.validation.ProbeMod;

/** Exercises the real server control receipt and the new takeover target, without inventing state. */
public final class AutoplayOverlaySmokeChecks {
    private static int stage,frames;private static long version;
    public static boolean tick(Minecraft client,MatchScreen screen){
        if(!Boolean.getBoolean("sgs.autoplayOverlaySmoke") || stage==3)return false;
        var s=screen.stateForSmoke().snapshot();var self=s.seats().stream().filter(p->p.self()).findFirst().orElse(null);
        if(s.status()!=1 || self==null || !self.alive() || s.openingHand()!=null)return false;
        if(stage==0){
            if(self.hand().isEmpty())return false;
            version=self.control().version();screen.clickForSmoke("autoplay");stage=1;return true;
        }
        if(stage==1){
            if(!self.control().automatic())return true;
            if(++frames<8)return true;
            if(!screen.autoplayVeilVisible() || !screen.autoplayBadgeForSmoke(self.seat()) || self.control().version()<=version)throw new IllegalStateException("autoplay veil missing server confirmation");
            Screenshot.grab(client.gameDirectory,"sgs-autoplay-overlay.png",client.getMainRenderTarget(),m->{});
            version=self.control().version();screen.clickForSmoke("autoplay-cancel");stage=2;frames=0;return true;
        }
        if(self.control().automatic())return true;
        if(++frames<4)return true;
        if(screen.autoplayVeilVisible() || screen.autoplayBadgeForSmoke(self.seat()) || self.control().version()<=version)throw new IllegalStateException("autoplay cancel did not restore manual control");
        Screenshot.grab(client.gameDirectory,"sgs-autoplay-overlay-cancelled.png",client.getMainRenderTarget(),m->{});
        ProbeMod.LOG.info("SGS_AUTOPLAY_OVERLAY_PASS server_confirmed=true native_click=true manual_restored=true portrait_badge=true");stage=3;return false;
    }
}
