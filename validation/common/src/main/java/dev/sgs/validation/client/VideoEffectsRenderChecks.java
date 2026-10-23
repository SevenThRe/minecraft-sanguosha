package dev.sgs.validation.client;

import dev.sgs.core.*;
import dev.sgs.core.addon.HeroCatalog;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.GameOutcome;
import dev.sgs.core.game.GameState;
import dev.sgs.core.hero.StandardHeroes;
import dev.sgs.core.match.*;
import dev.sgs.core.player.SgsPlayer;
import dev.sgs.core.util.IdSequencer;
import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.match.MatchNetwork;
import java.util.*;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Deterministic native rendering evidence, explicitly separate from live multiplayer. */
final class VideoEffectsRenderChecks {
    private static int scale=1,stage,frames;
    private static boolean done;
    private static final String[] NAMES={"target-open","target-clash","target-rest","hit-cut"};
    private static final long[] AGES={320,530,820,210};
    static void initialize(){
        if(Boolean.getBoolean("sgs.videoEffectsRender"))
            Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,VideoEffectsRenderChecks::tick);
    }
    private static void tick(Minecraft client){
        if(done || client.getOverlay()!=null || client.screen==null)return;
        try{
            client.getToasts().clear();
            if(!(client.screen instanceof MatchScreen)){show(client);return;}
            var screen=(MatchScreen)client.screen;
            screen.combatFrameForSmoke(stage==3,AGES[stage]);
            if(++frames<20)return;
            if(stage<3 && !screen.attackTargetMarkersForSmoke().containsKey(1))
                throw new IllegalStateException("Target effect missing");
            Screenshot.grab(client.gameDirectory,"video-effects-gui"+scale+"-"+NAMES[stage]+".png",client.getMainRenderTarget(),m->{});
            ProbeMod.LOG.info("SGS_VIDEO_EFFECTS_RENDER_CAPTURE gui={} state={} age={}",scale,NAMES[stage],AGES[stage]);
            if(++stage==NAMES.length){stage=0;scale++;}
            if(scale>3){done=true;ProbeMod.LOG.info("SGS_VIDEO_EFFECTS_RENDER_PASS scales=3 frames=4 live_server=false");client.stop();return;}
            show(client);
        }catch(Exception e){done=true;ProbeMod.LOG.error("SGS_VIDEO_EFFECTS_RENDER_FAIL",e);client.stop();}
    }
    private static void show(Minecraft c){
        c.options.guiScale().set(scale);c.resizeDisplay();c.setScreen(new MatchScreen(view()));frames=0;
    }
    private static MatchNetwork.View view(){
        var players=new ArrayList<SgsPlayer>();
        for(int i=0;i<6;i++){
            var p=new MatchPlayer(new UUID(8873,i),"玩家"+(i+1),i,true);
            p.assignIdentity(i==0?Identity.LORD:Identity.REBEL);players.add(p);
        }
        var state=GameState.startAssigned(players,Collections.nCopies(6,StandardHeroes.ZHANGFEI),
            new Deck(JunzhengDeckFactory.create(new IdSequencer()),new Random(17)),new Random(18));
        while(state.currentSeat()!=0 || state.phase()!=Phase.PLAY)state.advancePhase();
        var snapshot=ViewFilter.running(state,0,System.currentTimeMillis()+300000,null,GameOutcome.ONGOING,null)
            .withHeroPresentations(HeroCatalog.builtins().present(List.of("zhangfei")));
        return new MatchNetwork.View(8873,stage+1,snapshot);
    }
}
