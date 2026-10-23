package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Two real clients; owner clicks the picker, peer observes server-authorized choices. */
public final class SocialSkinSyncChecks {
    private static int stage,ticks,hold,peerClassicFrames,peerActionFrames;private static boolean done,seenClassic;private static long request;
    public static boolean tick(Minecraft client,MatchScreen screen){
        if(!Boolean.getBoolean("sgs.socialSkinSmoke") || done)return false;
        var view=screen.stateForSmoke();var s=view.snapshot();
        if(s.status()!=1 || s.openingHand()!=null)return false;
        if(++ticks>1200)throw new IllegalStateException("skin sync timed out stage="+stage);
        String role=System.getProperty("sgs.matchRole","");
        if(role.equals("guest")){
            var owner=s.seats().stream().filter(seat->seat.name().equals("SgsLobbyHost")).findFirst().orElseThrow();
            String selected=view.skins().get(owner.seat());
            if("classic".equals(selected) && ++peerClassicFrames>=5){seenClassic=true;capture(client,"peer-classic");}
            if(seenClassic && "action".equals(selected) && ++peerActionFrames>=5){
                capture(client,"peer-action");done=true;ProbeMod.LOG.info("SGS_SKIN_SYNC_PEER_PASS owner={} hero={} classic_then_action=true",owner.seat(),owner.heroId());
            }
            return !done;
        }
        var me=s.seats().stream().filter(seat->seat.self()).findFirst().orElseThrow();
        switch(stage){
            case 0 -> {request=MatchClient.selectSkin("invalid:other_hero","action");stage=1;}
            case 1 -> {
                var reply=MatchClient.skinResultForSmoke();if(reply==null || reply.request()!=request)return true;
                if(reply.accepted())throw new IllegalStateException("cross-hero skin accepted");
                var p=screen.skinStripForSmoke();screen.mouseClicked(p[0],p[1],0);
                if(!screen.controlsForSmoke().contains("skin:action"))throw new IllegalStateException("fixture needs an action skin hero");
                screen.clickForSmoke("skin:classic");screen.clickForSmoke("skin-apply");stage=2;
            }
            case 2 -> {
                if(!screen.skinForSmoke().equals("classic") || screen.overlayForSmoke())return true;
                if(++hold<80)return true;capture(client,"owner-classic");
                var p=screen.skinStripForSmoke();screen.mouseClicked(p[0],p[1],0);screen.clickForSmoke("skin:action");screen.clickForSmoke("skin-apply");stage=3;hold=0;
            }
            case 3 -> {
                if(!screen.skinForSmoke().equals("action") || screen.overlayForSmoke())return true;
                if(++hold<80)return true;capture(client,"owner-action");done=true;
                ProbeMod.LOG.info("SGS_SKIN_SYNC_OWNER_PASS hero={} cross_hero_rejected=true confirmed_ui=true",me.heroId());
            }
            default -> throw new IllegalStateException("invalid skin stage");
        }
        return !done;
    }
    private static boolean peerClassicCaptured;
    private static void capture(Minecraft client,String name){
        if(name.equals("peer-classic")){if(peerClassicCaptured)return;peerClassicCaptured=true;}
        Screenshot.grab(client.gameDirectory,"sgs-skin-sync-"+name+".png",client.getMainRenderTarget(),m->{});
    }
}
