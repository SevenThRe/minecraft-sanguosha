package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.TickPhase;
import net.blay09.mods.balm.api.event.TickType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Opt-in native client receipt checks. No local fake room state or simulated users. */
public final class LobbySmokeChecks {
    private static boolean running;
    private static int ticks,stage,settled;
    private static long firstRoom,staleRequest;
    public static void initialize() {
        if (Boolean.getBoolean("sgs.artSmoke")) Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,LobbySmokeChecks::tick);
    }
    public static void begin() {running=true;LobbyClient.open();}
    private static void tick(Minecraft client) {
        if (!running) return;
        try {
            if (++ticks>600) throw new IllegalStateException("timeout stage="+stage);
            if (!(client.screen instanceof LobbyScreen screen) || screen.smokeView()==null || screen.smokePending()) return;
            var view=screen.smokeView();
            switch(stage) {
                case 0 -> {screen.smokePress("create");stage=1;}
                case 1 -> {
                    if (view.ownRoom()<0 || view.rooms().size()!=1 || view.rooms().getFirst().members()!=1) throw new IllegalStateException("create receipt");
                    firstRoom=view.ownRoom();Balm.getNetworking().sendToServer(LobbyClient.lastIntent);stage=2;settled=0;
                }
                case 2 -> {
                    if (++settled<12) return;
                    if (view.ownRoom()!=firstRoom || view.rooms().size()!=1) throw new IllegalStateException("duplicate mutated room");
                    staleRequest=LobbyClient.request(view.revision()-1,"leave","six",0);stage=3;
                }
                case 3 -> {
                    if (view.request()!=staleRequest) return;
                    if (view.success() || view.ownRoom()!=firstRoom) throw new IllegalStateException("stale accepted");
                    screen.smokePress("leave");stage=4;
                }
                case 4 -> {
                    if (view.ownRoom()>=0 || !view.rooms().isEmpty()) throw new IllegalStateException("leave receipt");
                    screen.smokePress("queue");stage=5;
                }
                case 5 -> {
                    if (!view.queued().equals("six") || view.sixQueued()!=1 || view.eightQueued()!=0) throw new IllegalStateException("queue isolation");
                    screen.smokePress("cancel");stage=6;
                }
                case 6 -> {
                    if (!view.queued().isEmpty() || view.sixQueued()!=0) throw new IllegalStateException("cancel receipt");
                    screen.smokePress("create");stage=7;settled=0;
                }
                case 7 -> {
                    if (++settled<12) return;
                    if (view.ownRoom()<=firstRoom || view.rooms().size()!=1) throw new IllegalStateException("final room receipt");
                    screen.smokePress("ai_fill");stage=8;return;
                }
                case 8 -> {
                    var room=view.rooms().getFirst();
                    if(room.ai()!=5 || room.members()!=1 || room.seats().stream().filter(s->s.kind()==2).count()!=5)
                        throw new IllegalStateException("AI fill did not preserve human seat");
                    screen.smokePress("ai_remove:1");stage=9;return;
                }
                case 9 -> {
                    if(view.rooms().getFirst().ai()!=4)throw new IllegalStateException("AI remove receipt");
                    screen.smokePress("ai_add:1");stage=10;return;
                }
                case 10 -> {
                    if(view.rooms().getFirst().ai()!=5 || view.rooms().getFirst().members()!=1)throw new IllegalStateException("AI add receipt");
                    screen.smokePress("leave");stage=11;return;
                }
                case 11 -> {
                    if(view.ownRoom()>=0)return;
                    screen.smokePress("mode:eight");screen.smokePress("create");stage=12;return;
                }
                case 12 -> {screen.smokePress("ai_fill");stage=13;return;}
                case 13 -> {
                    if(view.rooms().getFirst().ai()!=7 || view.rooms().getFirst().members()!=1)throw new IllegalStateException("8-seat AI fill receipt");
                    screen.smokePress("leave");stage=14;return;
                }
                case 14 -> {
                    if(view.ownRoom()>=0)return;
                    screen.smokePress("mode:two_v_two");screen.smokePress("create");stage=15;return;
                }
                case 15 -> {screen.smokePress("ai_fill");stage=16;return;}
                case 16 -> {
                    if(view.rooms().getFirst().ai()!=3 || view.rooms().getFirst().members()!=1)throw new IllegalStateException("4-seat AI fill receipt");
                    stage=17;
                    Screenshot.grab(client.gameDirectory,"sgs-lobby-v3.png",client.getMainRenderTarget(), message -> {
                        ProbeMod.LOG.info("SGS_LOBBY_SMOKE_PASS create=true duplicate=true stale=true queue_cancel=true ai_fill_modes=6,8,4 ai_remove_add=true native_ui=true");
                        client.execute(client::stop);
                    });
                    running=false;
                }
                default -> {}
            }
        } catch (RuntimeException error) {
            running=false;ProbeMod.LOG.error("SGS_LOBBY_SMOKE_FAIL",error);client.stop();
        }
    }
}
