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

/** Opt-in native render/input acceptance; fixture data is not live multiplayer evidence. */
final class JournalRenderChecks {
    private static int scale=2,stage,frames;private static boolean done;
    static void initialize(){if(Boolean.getBoolean("sgs.journalRender"))Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,JournalRenderChecks::tick);}
    private static void tick(Minecraft client){
        if(done||client.getOverlay()!=null||client.screen==null)return;
        try{
            client.getToasts().clear();
            if(!(client.screen instanceof MatchScreen)){show(client);return;}
            var screen=(MatchScreen)client.screen;if(++frames<25)return;
            Screenshot.grab(client.gameDirectory,"journal-gui"+scale+"-"+stage+".png",client.getMainRenderTarget(),m->{});
            if(stage==0){screen.filterJournalForSmoke(1);stage=1;frames=0;return;}
            if(stage==1){screen.scrollJournalForSmoke();stage=2;frames=0;return;}
            if(scale==2){scale=3;stage=0;show(client);return;}
            done=true;ProbeMod.LOG.info("SGS_JOURNAL_RENDER_PASS gui=2,3 filter=true wheel=true drag=true live_server=false");client.stop();
        }catch(Exception error){done=true;ProbeMod.LOG.error("SGS_JOURNAL_RENDER_FAIL",error);client.stop();}
    }
    private static void show(Minecraft client){client.options.guiScale().set(scale);client.resizeDisplay();var screen=new MatchScreen(view());client.setScreen(screen);screen.openJournalForSmoke();frames=0;}
    private static MatchNetwork.View view(){
        var players=new ArrayList<SgsPlayer>();for(int i=0;i<4;i++)players.add(new MatchPlayer(new UUID(9911,i),"玩家"+(i+1),i,true));
        var heroes=List.of(StandardHeroes.ZHANGFEI,StandardHeroes.ZHOUYU,StandardHeroes.SIMAYI,StandardHeroes.ZHAOYUN);
        var state=GameState.startTwoVTwo(players,heroes,new Deck(JunzhengDeckFactory.create(new IdSequencer()),new Random(12)),new Random(13));
        while(state.phase()!=Phase.PLAY)state.advancePhase();
        for(int i=0;i<18;i++){
            state.journal().add(state.journal().seat(1)+"的回合开始");state.publishSkillActivation(1,"yingzi");
            state.cards().drawTo(CardZone.hand(1),2,CardMove.Reason.DRAW,1,false);
            state.journal().add(state.journal().seat(0)+"对"+state.journal().seat(1)+"使用了【{card:huogong}】♦Q");
            state.journal().add(state.journal().seat(1)+"展示了【{card:shan}】♥J");
            state.journal().add(state.journal().seat(2)+"装备了【{card:qilin_gong}】♠5");
        }
        var snapshot=ViewFilter.running(state,0,System.currentTimeMillis()+300000,null,GameOutcome.ONGOING,null).withHeroPresentations(HeroCatalog.builtins().present(heroes.stream().map(h->h.id()).toList()));
        return new MatchNetwork.View(9911,1,snapshot);
    }
}
