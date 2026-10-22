package dev.sgs.validation.client;

import dev.sgs.core.match.MatchAction;
import dev.sgs.validation.match.MatchNetwork;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.client.Minecraft;

/** Holds the server view and private client deductions. Hiding the screen never leaves the match. */
public final class MatchClient {
    static final LocalIdentityMarks identityMarks=new LocalIdentityMarks();
    private static MatchNetwork.View view;
    private static dev.sgs.validation.match.SkinNetwork.Result lastSkinResult;

    private static int nonce;
    private static final java.util.concurrent.atomic.AtomicLong skinRequests=new java.util.concurrent.atomic.AtomicLong();
    private static boolean hidden;
    private static long dismissedMatch=-1;
    public static void initialize(){
        MatchReconnect.initialize();
        dev.sgs.validation.match.SkinNetwork.receiver=r->Minecraft.getInstance().execute(()->{
            lastSkinResult=r;
            if(view!=null && view.matchId()==r.match() && Minecraft.getInstance().screen instanceof MatchScreen screen)screen.skinResult(r);
        });
        MatchNetwork.resumeReceiver=available->Minecraft.getInstance().execute(()->{if(!available)MatchReconnect.noMatch();});
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.client.DisconnectedFromServerEvent.class,event->{
            view=null;nonce=0;hidden=false;dismissedMatch=-1;
            identityMarks.clear();
        });
        MatchNetwork.receiver=next->Minecraft.getInstance().execute(()->{
            if(next.matchId()==dismissedMatch)return;
            if(view==null || view.matchId()!=next.matchId()){nonce=0;hidden=false;}
            else if(next.revision()<view.revision())return;
            else if(next.connectionEpoch()>view.connectionEpoch())hidden=false;
            nonce=Math.max(nonce,next.nonceFloor());
            var client=Minecraft.getInstance();
            if(view!=null && view.matchId()==next.matchId()
                && view.connectionEpoch()==next.connectionEpoch() && next.revision()>view.revision()
                && view.snapshot().status()!=0 && next.snapshot().status()!=0
                && dev.sgs.core.match.dto.HealthChanges.lostHealth(view.snapshot().seats(),next.snapshot().seats()))
                client.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.PLAYER_HURT,1.0F));
            view=next;MatchReconnect.received(next);
            if(client.screen instanceof MatchScreen screen) screen.update(next);
            else if(!hidden && !(client.screen instanceof MatchChatScreen))client.setScreen(new MatchScreen(next));
        });
        MatchNetwork.errorReceiver=reason->Minecraft.getInstance().execute(()->{

            if(Minecraft.getInstance().screen instanceof MatchScreen screen)screen.error(reason);
        });
    }
    public static long selectSkin(String hero,String skin){
        if(view==null)return 0;
        long request=skinRequests.incrementAndGet();
        Balm.getNetworking().sendToServer(new dev.sgs.validation.match.SkinNetwork.Intent(view.matchId(),view.connectionEpoch(),request,hero,skin));
        return request;
    }
    public static int nextNonce(){return ++nonce;}
    public static boolean hasSession(){return view!=null;}

    public static void setAutoplay(boolean enabled){
        if(view==null)return;var own=view.snapshot().seats().stream().filter(s->s.self()).findFirst().orElse(null);
        if(own==null)return;
        Balm.getNetworking().sendToServer(new MatchNetwork.ControlIntent(view.matchId(),view.connectionEpoch(),own.control().version(),nextNonce(),enabled));
    }
    public static void send(MatchAction action){
        if(view!=null && view.snapshot().seats().stream().anyMatch(s->s.self() && s.control().automatic()) && action.kind()!=MatchAction.Kind.LEAVE)return;
        if(view!=null)Balm.getNetworking().sendToServer(new MatchNetwork.Intent(MatchNetwork.JSON.toJson(MatchNetwork.Command.of(view,action))));
    }
    public static void prepareSpectating(){dismissedMatch=-1;hidden=false;}
    public static void hide(){
        hidden=true;
        if(view!=null && view.snapshot().viewerSeat()<0){
            LobbyClient.request(0,"unwatch","",0);dismissedMatch=view.matchId();view=null;
        }
        LobbyClient.open();
    }
    public static boolean resume(){if(view==null)return false;hidden=false;Minecraft.getInstance().setScreen(new MatchScreen(view));return true;}
    public static void leaveFinished(){
        if(view==null || view.snapshot().status()!=2)return;
        if(view.snapshot().viewerSeat()<0){hide();return;}
        MatchReconnect.cancel();send(MatchAction.leave(nextNonce()));
        dismissedMatch=view.matchId();view=null;hidden=false;LobbyClient.open();
    }
}
