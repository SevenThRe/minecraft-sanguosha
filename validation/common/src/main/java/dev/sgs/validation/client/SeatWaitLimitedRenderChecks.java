package dev.sgs.validation.client;

import dev.sgs.core.*;
import dev.sgs.core.addon.HeroCatalog;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.*;
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
import net.blay09.mods.balm.api.event.TickPhase;
import net.blay09.mods.balm.api.event.TickType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Opt-in native rendering of core-produced snapshots; no live server/gameplay acceptance claim. */
final class SeatWaitLimitedRenderChecks {
    private static int scale=1,stage,frames;
    private static boolean done;
    private static final String[] NAMES={"ready","blocked","spent"};
    static void initialize(){
        if(Boolean.getBoolean("sgs.seatWaitLimitedRender"))Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,SeatWaitLimitedRenderChecks::tick);
    }
    private static void tick(Minecraft client){
        if(done || client.getOverlay()!=null || client.screen==null)return;
        try{
            client.getToasts().clear();
            if(!(client.screen instanceof MatchScreen)){
                client.options.guiScale().set(scale);client.resizeDisplay();client.setScreen(new MatchScreen(view()));frames=0;return;
            }
            if(++frames<24)return;
            if(frames==24){
                Screenshot.grab(client.gameDirectory,"seat-wait-limited-gui"+scale+"-"+NAMES[stage]+".png",client.getMainRenderTarget(),m->{});
                ((MatchScreen)client.screen).hoverLimitedForSmoke(2);return;
            }
            if(frames<36)return;
            if(!((MatchScreen)client.screen).hasSeatClockForSmoke())throw new IllegalStateException("seat countdown absent");
            String tip=((MatchScreen)client.screen).visiblePaperTipForSmoke();
            String expected=HeroCatalog.builtins().skill("luanwu").orElseThrow().description();
            if(!tip.contains(expected) || tip.contains("未满足条件") || tip.contains("未满足发动条件"))throw new IllegalStateException("Limited tooltip must show the full skill description: "+tip);
            Screenshot.grab(client.gameDirectory,"seat-wait-limited-gui"+scale+"-"+NAMES[stage]+"-hover.png",client.getMainRenderTarget(),m->{});
            ProbeMod.LOG.info("SGS_SEAT_WAIT_LIMITED_RENDER_CAPTURE gui={} state={} core_snapshot=true live_server=false",scale,NAMES[stage]);
            if(++stage==NAMES.length){stage=0;scale++;}
            if(scale>3){done=true;ProbeMod.LOG.info("SGS_SEAT_WAIT_LIMITED_RENDER_PASS scales=3 states=3 live_server=false");client.stop();return;}
            client.options.guiScale().set(scale);client.resizeDisplay();client.setScreen(new MatchScreen(view()));frames=0;
        }catch(Exception error){done=true;ProbeMod.LOG.error("SGS_SEAT_WAIT_LIMITED_RENDER_FAIL",error);client.stop();}
    }
    private static MatchNetwork.View view(){
        var players=new ArrayList<SgsPlayer>();
        for(int i=0;i<8;i++){var p=new MatchPlayer(new UUID(8791,i),"玩家"+(i+1),i,true);p.assignIdentity(i==0?Identity.LORD:Identity.REBEL);players.add(p);}
        var catalog=HeroCatalog.builtins();
        var heroes=new ArrayList<>(List.of(StandardHeroes.GUOJIA,catalog.hero("pangtong").orElseThrow(),catalog.hero("jiaxu").orElseThrow(),StandardHeroes.ZHENJI,StandardHeroes.ZHUGELIANG,StandardHeroes.ZHANGFEI,StandardHeroes.CAOCAO,StandardHeroes.DIAOCHAN));
        var state=GameState.startAssigned(players,heroes,new Deck(JunzhengDeckFactory.create(new IdSequencer()),new Random(10)),new Random(11));
        if(Boolean.getBoolean("sgs.typographySmoke")){
            var cards=JunzhengDeckFactory.create(new IdSequencer());
            for(var player:players)for(var type:List.of(CardType.QINGLONG,CardType.BAIYIN,CardType.JUEYING,CardType.CHITU))
                player.equipment().equip(cards.stream().filter(c->c.type()==type).findFirst().orElseThrow());
        }
        while(state.currentSeat()!=2 || state.phase()!=Phase.PLAY)state.advancePhase();
        var engine=new SgsGameEngine(state,new PromptResponder(){public Optional<PlayDecision> respond(GameState s,PromptRequest p){return Optional.empty();}},System::currentTimeMillis,30);
        if(stage==2 && !engine.useActiveSkill(players.get(2),"luanwu",null,List.of()).accepted())throw new IllegalStateException("limited fixture not spent");
        if(stage==1)while(state.currentSeat()!=3 || state.phase()!=Phase.PLAY)state.advancePhase();
        var snapshot=ViewFilter.running(state,3,System.currentTimeMillis()+30_000,null,GameOutcome.ONGOING,null)
            .withHeroPresentations(catalog.present(heroes.stream().map(h->h.id()).toList()));
        var peach=new CardView(99001,CardType.TAO.ordinal(),Suit.HEART.ordinal(),Rank.FIVE.ordinal());
        snapshot=new MatchSnapshot(snapshot.status(),snapshot.viewerSeat(),snapshot.turnNumber(),snapshot.phaseOrdinal(),snapshot.currentSeat(),snapshot.lordSeatOrdinal(),snapshot.drawPileCount(),snapshot.discardPileCount(),snapshot.phaseDeadlineMillis(),snapshot.outcomeOrdinal(),snapshot.logLines(),snapshot.seats(),null,List.of(),0,null,List.of(),List.of(new PublicMoveView(1,3,"USE",List.of(peach))),List.of(),snapshot.heroPresentations());
        return new MatchNetwork.View(8791,stage+1,snapshot);
    }
}
