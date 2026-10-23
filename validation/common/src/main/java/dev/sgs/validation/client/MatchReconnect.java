package dev.sgs.validation.client;

import static dev.sgs.validation.client.ClientText.tr;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.TickPhase;
import net.blay09.mods.balm.api.event.TickType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import dev.sgs.validation.ProbeMod;
import dev.sgs.core.match.ReconnectSchedule;

/** Same-process recovery. Only the server's current authorized view can restore the table. */
public final class MatchReconnect {
    private static ServerData target;
    private static long match=-1,started;
    private static boolean armed,connecting,returnToLobby;
    private static Screen lastFailure;
    private static RetryScreen retry;
    private static final ReconnectSchedule schedule=new ReconnectSchedule();
    public static void initialize(){Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,MatchReconnect::tick);}
    public static void received(dev.sgs.validation.match.MatchNetwork.View view){
        var mc=Minecraft.getInstance();
        if(view.snapshot().viewerSeat()<0 || view.snapshot().status()==2){cancel();return;}
        if(mc.getCurrentServer()==null || mc.hasSingleplayerServer())return;
        if(connecting || retry!=null)ProbeMod.LOG.info("SGS_AUTO_RECONNECT_RESTORED match={} previous={} epoch={} seat={} nonce_floor={} attempts={}",view.matchId(),match,view.connectionEpoch(),view.snapshot().viewerSeat(),view.nonceFloor(),schedule.attempts());
        target=mc.getCurrentServer();match=view.matchId();armed=true;connecting=false;retry=null;lastFailure=null;schedule.reset();
    }
    public static void cancel(){armed=false;connecting=false;returnToLobby=false;target=null;match=-1;schedule.reset();}
    public static void noMatch(){boolean recovering=armed;cancel();returnToLobby=recovering;}
    public static void serverDisconnect(Component reason){
        // Shutdown is retryable. Kicks with an arbitrary literal reason must never auto-loop.
        if(armed)ProbeMod.LOG.info("SGS_AUTO_RECONNECT_SERVER_DISCONNECT reason={}",reason.getString());
        if(!translationKeys(reason).contains("multiplayer.disconnect.server_shutdown"))cancel();
    }
    public static boolean armed(){return armed;}
    public static boolean connecting(){return connecting;}
    private static void tick(Minecraft mc){
        if(returnToLobby && mc.player!=null){returnToLobby=false;LobbyClient.open();}
        if(!armed || target==null)return;
        if(mc.player!=null){
            if(mc.getCurrentServer()==null || !target.ip.equals(mc.getCurrentServer().ip))cancel();
            return;
        }
        if(mc.screen instanceof DisconnectedScreen failure && failure!=lastFailure){
            lastFailure=failure;connecting=false;
            var why=failure.getNarrationMessage();
            var kind=ReconnectSchedule.classify(translationKeys(why)+" "+why.getString());
            if(!schedule.failed(System.currentTimeMillis(),kind)){ProbeMod.LOG.info("SGS_AUTO_RECONNECT_STOP reason={}",why.getString());cancel();return;}
            retry=new RetryScreen(why);mc.setScreen(retry);
            ProbeMod.LOG.info("SGS_AUTO_RECONNECT_SCHEDULE match={} attempt={} kind={} reason={}",match,schedule.attempts()+1,kind,translationKeys(why)+why.getString());
        }else if(mc.screen instanceof ConnectScreen){
            if(connecting && System.currentTimeMillis()-started>30_000){
                // Use the native cancellation action: it marks the connector aborted and closes its channel.
                for(var child:mc.screen.children())if(child instanceof Button b){b.onPress();break;}
                connecting=false;
                if(schedule.failed(System.currentTimeMillis(),ReconnectSchedule.Failure.TRANSIENT))mc.setScreen(retry);else cancel();
            }
        }else if(mc.screen==retry && retry!=null){
            if(connecting){cancel();return;} // Native Cancel returns to our parent screen.
            if(schedule.due(System.currentTimeMillis())){
                connecting=true;started=System.currentTimeMillis();schedule.begin();
                ProbeMod.LOG.info("SGS_AUTO_RECONNECT_ATTEMPT match={} attempt={}",match,schedule.attempts());
                ConnectScreen.startConnecting(retry,mc,ServerAddress.parseString(target.ip),target,false,null);
            }
        }else if(mc.screen instanceof TitleScreen || mc.screen instanceof net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen){cancel();}
    }
    private static String translationKeys(Component c){
        var result=new StringBuilder();
        if(c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t){result.append(t.getKey()).append(' ');for(var a:t.getArgs())if(a instanceof Component nested)result.append(translationKeys(nested));}
        for(var sibling:c.getSiblings())result.append(translationKeys(sibling));return result.toString();
    }
    private static final class RetryScreen extends Screen {
        private final Component reason;
        RetryScreen(Component reason){super(Component.literal(tr("sgs.ui.reconnecting_981c7a66")));this.reason=reason;}
        @Override protected void init(){addRenderableWidget(Button.builder(Component.literal(tr("sgs.ui.cancel_4d0b4688")),b->{cancel();minecraft.setScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(new TitleScreen()));}).bounds(width/2-50,height/2+38,100,20).build());}
        @Override public void onClose(){cancel();minecraft.setScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(new TitleScreen()));}
        @Override public void render(GuiGraphics g,int x,int y,float delta){
            super.render(g,x,y,delta);g.drawCenteredString(font,title,width/2,height/2-35,0xffffff);
            g.drawCenteredString(font,tr("sgs.ui.attempt_f495347d")+(schedule.attempts()+1)+tr("sgs.ui.empty_ee9a69e6")+Math.max(0,(schedule.nextAt()-System.currentTimeMillis()+999)/1000)+tr("sgs.ui.sec_d9ad5055"),width/2,height/2-14,0xe4ca9f);
            g.drawCenteredString(font,font.plainSubstrByWidth(reason.getString(),Math.max(20,width-24)),width/2,height/2+8,0xaaaaaa);
        }
    }
}
