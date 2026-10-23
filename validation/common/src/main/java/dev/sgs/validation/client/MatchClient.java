package dev.sgs.validation.client;

import static dev.sgs.validation.client.ClientText.tr;
import dev.sgs.core.match.MatchAction;
import dev.sgs.validation.match.MatchNetwork;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.client.Minecraft;

/** Holds the server view and private client deductions. Hiding the screen never leaves the match. */
public final class MatchClient {
    static final LocalIdentityMarks identityMarks=new LocalIdentityMarks();
    private static MatchNetwork.View view;
    private static dev.sgs.validation.match.SkinNetwork.Result lastSkinResult;
    public static dev.sgs.validation.match.SkinNetwork.Result skinResultForSmoke(){return lastSkinResult;}
    private static int nonce;
    private static final java.util.concurrent.atomic.AtomicLong skinRequests=new java.util.concurrent.atomic.AtomicLong();
    private static boolean hidden;
    private static boolean settingsOpen;
    private static long dismissedMatch=-1;
    public static void initialize(){
        MatchReconnect.initialize();MatchChatClient.initialize();
        MatchNetwork.targetReceiver=reply->Minecraft.getInstance().execute(()->{if(Minecraft.getInstance().screen instanceof MatchScreen screen)screen.targetReply(reply);});
        dev.sgs.validation.match.SkinNetwork.receiver=r->Minecraft.getInstance().execute(()->{
            lastSkinResult=r;
            if(view!=null && view.matchId()==r.match() && Minecraft.getInstance().screen instanceof MatchScreen screen)screen.skinResult(r);
        });
        MatchNetwork.resumeReceiver=available->Minecraft.getInstance().execute(()->{if(!available)MatchReconnect.noMatch();});
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.client.DisconnectedFromServerEvent.class,event->{
            view=null;nonce=0;hidden=false;settingsOpen=false;dismissedMatch=-1;
            identityMarks.clear();MatchChatClient.clear();
            dev.sgs.validation.client.art.PlayerSkinRenderer.clearUvFixtures();
        });
        MatchNetwork.receiver=next->Minecraft.getInstance().execute(()->{
            if(!ReconnectSmokeChecks.received(next))return;
            if(!RoomMatchSmokeChecks.received(next.snapshot()))return;
            if(next.matchId()==dismissedMatch)return;
            if(view==null || view.matchId()!=next.matchId()){nonce=0;hidden=false;settingsOpen=false;}
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
            if(view!=null && view.matchId()==next.matchId() && view.connectionEpoch()==next.connectionEpoch() && next.revision()>view.revision()){
                long last=view.snapshot().publicMoves().stream().mapToLong(m->m.sequence()).max().orElse(0);
                for(var move:next.snapshot().publicMoves())if(move.sequence()>last && (move.reason().equals("USE") || move.reason().equals("RESPONSE")))for(var card:move.cards()){
                    var type=dev.sgs.core.card.CardType.values()[card.typeOrdinal()];
                    if(type==dev.sgs.core.card.CardType.JIU || type==dev.sgs.core.card.CardType.TAO)
                        client.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(type==dev.sgs.core.card.CardType.JIU?net.minecraft.sounds.SoundEvents.GENERIC_DRINK:net.minecraft.sounds.SoundEvents.GENERIC_EAT,1.0F));
                }
            }
            view=next;MatchChatClient.bind(next);MatchReconnect.received(next);autoInvokeOptionalSkill();
            if(client.screen instanceof MatchScreen screen) screen.update(next);
            else if(!hidden && !settingsOpen && !(client.screen instanceof ReportHistoryChatScreen) && !EquipmentEffectsChecks.showingGallery() && !CreativeTabSmokeChecks.showing())client.setScreen(new MatchScreen(next));
        });
        MatchNetwork.errorReceiver=reason->Minecraft.getInstance().execute(()->{
            if(Boolean.getBoolean("sgs.reconnectSmoke"))dev.sgs.validation.ProbeMod.LOG.info("SGS_RECONNECT_ERROR_RECEIVED reason={} screen={}",reason,
                Minecraft.getInstance().screen==null?"none":Minecraft.getInstance().screen.getClass().getSimpleName());
            if(Minecraft.getInstance().screen instanceof MatchScreen screen)screen.error(reason);
        });
    }
    public static long selectSkin(String hero,String skin){
        if(view==null)return 0;
        long request=skinRequests.incrementAndGet();
        Balm.getNetworking().sendToServer(new dev.sgs.validation.match.SkinNetwork.Intent(view.matchId(),view.connectionEpoch(),request,hero,skin));
        return request;
    }
    static void autoInvokeOptionalSkill(){if(view!=null)OptionalSkillPreferences.accept(view);}
    public static int nextNonce(){return ++nonce;}
    public static boolean hasSession(){return view!=null;}
    public static int nonceForSmoke(){return nonce;}
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
        hidden=true;MatchChatClient.open=false;
        if(view!=null && view.snapshot().viewerSeat()<0){
            LobbyClient.request(0,"unwatch","",0);dismissedMatch=view.matchId();view=null;
        }
        LobbyClient.open();
    }
    public static void openSettings(){
        if(view==null)return;
        var client=Minecraft.getInstance();
        settingsOpen=true;
        // Keep all vanilla settings subpages open while match snapshots continue arriving.
        var returnToMatch=new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal(tr("sgs.ui.return_to_match_e1d60dda"))){
            @Override protected void init(){
                settingsOpen=false;
                if(!resume())LobbyClient.open();
            }
        };
        client.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(returnToMatch,client.options));
    }
    public static boolean resume(){if(view==null)return false;hidden=false;settingsOpen=false;Minecraft.getInstance().setScreen(new MatchScreen(view));return true;}
    public static void leaveFinished(){
        if(view==null || view.snapshot().status()!=2)return;
        if(view.snapshot().viewerSeat()<0){hide();return;}
        MatchReconnect.cancel();send(MatchAction.leave(nextNonce()));
        dismissedMatch=view.matchId();view=null;MatchChatClient.clear();hidden=false;LobbyClient.open();
    }
}
