package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.TickPhase;
import net.blay09.mods.balm.api.event.TickType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Two independent real graphical clients exercise shared server membership through UI buttons. */
public final class LobbyMultiplayerChecks {
    private static final String ROLE=System.getProperty("sgs.lobbyRole","");
    private static int ticks,stage,waitTicks;
    private static boolean seenTwo,finished;
    public static void initialize() {
        if (!ROLE.isEmpty()) Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,LobbyMultiplayerChecks::tick);
    }
    private static void tick(Minecraft client) {
        if (finished) return;
        try {
            if (++ticks>3600) throw new IllegalStateException("timeout role="+ROLE+" stage="+stage);
            if (client.player==null || client.level==null) return;
            if (stage==0) {if(ticks<80)return;if(NativeTableEntry.open(client))stage=1;return;}
            if (!(client.screen instanceof LobbyScreen screen) || screen.smokeView()==null || screen.smokePending()) return;
            var view=screen.smokeView();
            if (ROLE.equals("host")) {
                if (stage==1) {screen.smokePress("create");stage=2;return;}
                if (view.ownRoom()<0) {
                    if (!view.success()) {stage=1;return;}
                    throw new IllegalStateException("host room missing");
                }
                var own=view.rooms().stream().filter(r -> r.id()==view.ownRoom()).findFirst().orElseThrow();
                if (!view.ownReady()) {screen.smokePress("ready");return;}
                if (own.members()==2 && own.ready()==2) seenTwo=true;
                if (seenTwo && own.members()==1 && own.ready()==1) complete(client);
            } else if (ROLE.equals("guest")) {
                if (stage==1) {
                    if (view.rooms().isEmpty()) return;
                    screen.smokePress("join:"+view.rooms().getFirst().id());stage=2;return;
                }
                if (stage==2) {
                    if (view.ownRoom()<0) {stage=1;return;}
                    var own=view.rooms().stream().filter(r -> r.id()==view.ownRoom()).findFirst().orElseThrow();
                    if (own.members()!=2) throw new IllegalStateException("guest did not see both participants");
                    if (!view.ownReady()) {screen.smokePress("ready");return;}
                    if (own.ready()!=2) return;
                    if (++waitTicks<40) return;
                    seenTwo=true;screen.smokePress("leave");stage=3;return;
                }
                if (stage==3 && view.ownRoom()<0 && seenTwo) complete(client);
            } else throw new IllegalArgumentException("unknown role");
        } catch (RuntimeException error) {
            finished=true;ProbeMod.LOG.error("SGS_LOBBY_MULTIPLAYER_FAIL role="+ROLE,error);client.stop();
        }
    }
    private static void complete(Minecraft client) {
        finished=true;
        Screenshot.grab(client.gameDirectory,"sgs-lobby-two-"+ROLE+".png",client.getMainRenderTarget(),message -> {
            ProbeMod.LOG.info("SGS_LOBBY_MULTIPLAYER_PASS role={} observed_two=true ready_sync=true",ROLE);
            client.execute(client::stop);
        });
    }
}
