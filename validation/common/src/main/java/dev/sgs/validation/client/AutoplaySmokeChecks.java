package dev.sgs.validation.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import dev.sgs.validation.ProbeMod;

/** Opt-in native acceptance driver. It never supplies rules state or bypasses server controls. */
public final class AutoplaySmokeChecks {
    private static final boolean ENABLED=Boolean.getBoolean("sgs.autoplaySmoke");
    private static final boolean HOST="host".equals(System.getProperty("sgs.matchRole",""));
    private static int stage,frames,retryFrames,peerAutoFrames;private static long match,epoch;
    private static int seat;private static long backgroundAt;private static net.minecraft.client.multiplayer.ServerData originalServer;private static boolean peerOffline,peerAuto,peerManual;
    public static void initialize(){if(ENABLED)net.blay09.mods.balm.api.Balm.getEvents().onTickEvent(net.blay09.mods.balm.api.event.TickType.Client,net.blay09.mods.balm.api.event.TickPhase.End,AutoplaySmokeChecks::background);}
    private static void background(Minecraft mc){
        mc.getToasts().clear();if(!HOST)return;
        if(stage==8 && mc.screen!=null && mc.screen.getTitle().getString().equals("正在重连")){
            if(++retryFrames<4)return;
            capture(mc,"sgs-autoplay-retry.png");
            for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals("取消")){b.onPress();break;}
            require(!MatchReconnect.armed(),"cancel left reconnect armed");stage=9;backgroundAt=System.currentTimeMillis();
        }else if(stage==9){
            require(!MatchReconnect.armed() && !(mc.screen instanceof net.minecraft.client.gui.screens.ConnectScreen),"cancel restarted a connection");
            if(System.currentTimeMillis()-backgroundAt>14_000){ProbeMod.LOG.info("SGS_AUTOPLAY_CANCEL_NATIVE_PASS");stage=10;manualConnect(mc);}
        }else if(stage==11 && mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen){
            require(!MatchReconnect.armed(),"server kick retained automatic reconnect");
            if(backgroundAt==0)backgroundAt=System.currentTimeMillis();
            if(System.currentTimeMillis()-backgroundAt>5_000){capture(mc,"sgs-autoplay-kicked.png");ProbeMod.LOG.info("SGS_AUTOPLAY_KICK_NATIVE_PASS");stage=12;manualConnect(mc);}
        }
    }
    private static void manualConnect(Minecraft mc){net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new net.minecraft.client.gui.screens.TitleScreen(),mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(originalServer.ip),originalServer,false,null);}
    public static boolean tick(Minecraft mc,MatchScreen screen){
        if(!ENABLED)return false;
        var view=screen.stateForSmoke();var s=view.snapshot();if(s.status()!=1)return false;
        if(screen.overlayForSmoke()){screen.pressForSmoke("overlay-close");ProbeMod.LOG.info("SGS_AUTOPLAY_DRIVER_CLOSE_OVERLAY");return true;}
        var own=s.seats().stream().filter(p->p.self()).findFirst().orElseThrow();
        if(!HOST){
            var host=s.seats().stream().filter(p->p.name().equals("SgsLobbyHost")).findFirst().orElse(null);
            if(host!=null && !host.online() && !peerOffline){peerOffline=true;ProbeMod.LOG.info("SGS_AUTOPLAY_PEER_OFFLINE seat={}",host.seat());}
            if(host!=null && !host.online() && host.control().source().equals("DISCONNECT") && !peerAuto){if(++peerAutoFrames<4)return true;peerAuto=true;capture(mc,"sgs-autoplay-peer-offline.png");ProbeMod.LOG.info("SGS_AUTOPLAY_PEER_AUTO seat={}",host.seat());}
            if(peerAuto && host!=null && host.online() && !host.control().automatic() && !peerManual){peerManual=true;ProbeMod.LOG.info("SGS_AUTOPLAY_PEER_RESTORED seat={}",host.seat());}
            if(host!=null && !host.online() || peerOffline && !peerManual)return true;return false;
        }
        if(stage==0){
            if(s.prompt()==null || s.prompt().askerSeat()!=s.viewerSeat())return false;
            originalServer=mc.getCurrentServer();match=view.matchId();seat=s.viewerSeat();epoch=view.connectionEpoch();screen.clickForSmoke("autoplay");stage=1;frames=0;return true;
        }
        if(stage==1){
            if(!own.control().source().equals("USER"))return true;
            if(++frames<4)return true;capture(mc,"sgs-autoplay-user.png");
            ProbeMod.LOG.info("SGS_AUTOPLAY_USER_PROMPT_PASS match={} seat={} version={}",match,seat,own.control().version());
            screen.clickForSmoke("autoplay");stage=2;return true;
        }
        if(stage==2){if(own.control().automatic())return true;screen.clickForSmoke("autoplay");stage=3;return true;}
        if(stage==3){if(!own.control().source().equals("USER"))return true;ProbeMod.LOG.info("SGS_AUTOPLAY_CUT_REQUEST stage=1");stage=4;return true;}
        if(stage==4){
            if(view.connectionEpoch()<=epoch)return true;require(view.matchId()==match && s.viewerSeat()==seat,"ownership changed");
            require(own.control().source().equals("USER"),"manual autoplay lost on reconnect");
            ProbeMod.LOG.info("SGS_AUTOPLAY_USER_RECONNECT_PASS epoch={}",view.connectionEpoch());epoch=view.connectionEpoch();
            screen.clickForSmoke("autoplay");stage=5;return true;
        }
        if(stage==5){if(own.control().automatic())return true;ProbeMod.LOG.info("SGS_AUTOPLAY_CUT_REQUEST stage=2");stage=6;return true;}
        if(stage==6){
            if(view.connectionEpoch()<=epoch)return true;require(view.matchId()==match && s.viewerSeat()==seat,"second ownership changed");
            require(!own.control().automatic(),"disconnect autoplay did not return manual");
            if(++frames<8)return true;capture(mc,"sgs-autoplay-reconnected.png");
            ProbeMod.LOG.info("SGS_AUTOPLAY_NATIVE_PASS match={} seat={} epoch={} original_epoch={} nonce_floor={}",match,seat,view.connectionEpoch(),epoch,view.nonceFloor());epoch=view.connectionEpoch();stage=7;
        }
        if(stage==7){ProbeMod.LOG.info("SGS_AUTOPLAY_CUT_REQUEST stage=3");stage=8;return true;}
        if(stage==10 && view.connectionEpoch()>epoch){epoch=view.connectionEpoch();backgroundAt=0;stage=11;ProbeMod.LOG.info("SGS_AUTOPLAY_KICK_REQUEST");return true;}
        if(stage==12 && view.connectionEpoch()>epoch){require(view.matchId()==match && s.viewerSeat()==seat,"post-kick ownership changed");ProbeMod.LOG.info("SGS_AUTOPLAY_FINAL_PASS match={} seat={} epoch={}",match,seat,view.connectionEpoch());stage=13;}
        return stage<13;
    }
    private static void require(boolean valid,String reason){if(!valid)throw new IllegalStateException("SGS_AUTOPLAY_FAIL "+reason);}
    private static void capture(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),ignored->{});}
}
