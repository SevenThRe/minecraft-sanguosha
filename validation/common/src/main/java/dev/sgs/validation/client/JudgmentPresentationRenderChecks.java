package dev.sgs.validation.client;

import dev.sgs.core.*;
import dev.sgs.core.addon.HeroCatalog;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.GameOutcome;
import dev.sgs.core.game.GameState;
import dev.sgs.core.hero.StandardHeroes;
import dev.sgs.core.match.*;
import dev.sgs.core.match.dto.MatchViews.*;
import dev.sgs.core.player.SgsPlayer;
import dev.sgs.core.util.IdSequencer;
import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.match.MatchNetwork;
import java.util.*;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Synthetic public judgment scenes rendered by the real client, not live-server acceptance. */
final class JudgmentPresentationRenderChecks {
    private static int scale=1,stage,frames;
    private static boolean done;
    private static final String[] LABELS={"BINGLIANG","LEBU","SHANDIAN","BAGUA"};
    static void initialize(){if(Boolean.getBoolean("sgs.judgmentPresentationRender"))Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,JudgmentPresentationRenderChecks::tick);}
    private static void tick(Minecraft client){
        if(done || client.getOverlay()!=null || client.screen==null)return;
        try{
            client.getToasts().clear();
            if(!(client.screen instanceof MatchScreen)){show(client);return;}
            var screen=(MatchScreen)client.screen;
            // Freeze the public animation at the readable result while screenshots settle.
            installScene(screen);
            if(++frames<24)return;
            String name=LABELS[stage/2].toLowerCase(Locale.ROOT)+(stage%2==0?"-success":"-failure");
            Screenshot.grab(client.gameDirectory,"judgment-presentation-gui"+scale+"-"+name+".png",client.getMainRenderTarget(),m->{});
            ProbeMod.LOG.info("SGS_JUDGMENT_PRESENTATION_RENDER_CAPTURE gui={} state={} synthetic=true live_server=false",scale,name);
            if(++stage==8){stage=0;scale++;}
            if(scale>3){done=true;ProbeMod.LOG.info("SGS_JUDGMENT_PRESENTATION_RENDER_PASS scales=3 states=8 live_server=false");client.stop();return;}
            show(client);
        }catch(Exception e){done=true;ProbeMod.LOG.error("SGS_JUDGMENT_PRESENTATION_RENDER_FAIL",e);client.stop();}
    }
    private static void show(Minecraft c)throws Exception{c.options.guiScale().set(scale);c.resizeDisplay();var screen=new MatchScreen(view());c.setScreen(screen);installScene(screen);frames=0;}
    private static void set(MatchScreen screen,String name,Object value)throws Exception{var f=MatchScreen.class.getDeclaredField(name);f.setAccessible(true);f.set(screen,value);}
    private static void installScene(MatchScreen screen)throws Exception{
        var view=screen.stateForSmoke();String label=LABELS[stage/2];boolean success=stage%2==0;
        Suit suit=switch(label){case "BINGLIANG"->success?Suit.SPADE:Suit.CLUB;case "LEBU"->success?Suit.CLUB:Suit.HEART;case "SHANDIAN"->success?Suit.SPADE:Suit.DIAMOND;default->success?Suit.HEART:Suit.SPADE;};
        var judged=new CardView(99001,CardType.SHA.ordinal(),suit.ordinal(),Rank.SEVEN.ordinal());
        var cause=new CardView(99002,CardType.valueOf(label).ordinal(),Suit.SPADE.ordinal(),Rank.TEN.ordinal());
        set(screen,"sceneKey",view.matchId()+":"+view.connectionEpoch());
        set(screen,"scene",new TableEffect(1,"JUDGMENT",0,0,label,List.of(judged,cause),success));
        set(screen,"sceneStarted",net.minecraft.Util.getMillis()-1700);
    }
    private static MatchNetwork.View view(){
        var players=new ArrayList<SgsPlayer>();
        for(int i=0;i<6;i++){var p=new MatchPlayer(new UUID(8899,i),"玩家"+(i+1),i,true);p.assignIdentity(i==0?Identity.LORD:Identity.REBEL);players.add(p);}
        var state=GameState.startAssigned(players,Collections.nCopies(6,StandardHeroes.ZHANGFEI),new Deck(JunzhengDeckFactory.create(new IdSequencer()),new Random(17)),new Random(18));
        while(state.currentSeat()!=0 || state.phase()!=Phase.PLAY)state.advancePhase();
        var snapshot=ViewFilter.running(state,0,System.currentTimeMillis()+300000,null,GameOutcome.ONGOING,null).withHeroPresentations(HeroCatalog.builtins().present(List.of("zhangfei")));
        return new MatchNetwork.View(8899,stage+1,snapshot);
    }
}
