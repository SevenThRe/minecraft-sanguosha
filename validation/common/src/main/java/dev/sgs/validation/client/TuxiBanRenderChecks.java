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
import net.blay09.mods.balm.api.event.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Opt-in portrait input and ban-list filtering checks. */
final class TuxiBanRenderChecks {
    private static int scale=1,stage,frames;private static boolean done;
    static void initialize(){if(Boolean.getBoolean("sgs.tuxiBanRender"))Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,TuxiBanRenderChecks::tick);}
    private static void tick(Minecraft c){
        if(done||c.getOverlay()!=null||c.screen==null)return;
        try{
            c.getToasts().clear();if(!(c.screen instanceof MatchScreen)){show(c);return;}
            var s=(MatchScreen)c.screen;if(++frames<15)return;
            if(stage==0){
                if(!s.tuxiPortraitOnlyForSmoke()||s.tuxiTargetCountForSmoke()!=0)throw new IllegalStateException("Tuxi target buttons or stale targets");
                s.clickSeatForSmoke(0);if(s.tuxiTargetCountForSmoke()!=0)throw new IllegalStateException("Tuxi accepted self");
                s.clickSeatForSmoke(1);if(s.tuxiTargetCountForSmoke()!=1)throw new IllegalStateException("Tuxi first portrait failed");
                s.clickSeatForSmoke(1);if(s.tuxiTargetCountForSmoke()!=0)throw new IllegalStateException("Tuxi deselection failed");
                s.clickSeatForSmoke(1);s.clickSeatForSmoke(2);
                if(s.tuxiTargetCountForSmoke()!=2||s.enabledForSmoke("tuxi-confirm"))throw new IllegalStateException("Tuxi two portraits did not submit together");
            }else if(stage==1){
                if(s.controlsForSmoke().contains("ban:zhangliao")||!s.enabledForSmoke("ban:zhangfei"))throw new IllegalStateException("Taken ban remains visible");
            }
            if(stage==2){
                for(int cardIndex=0;cardIndex<2;cardIndex++){
                    clickHand(s,cardIndex);s.clickSeatForSmoke(1);s.clickSeatForSmoke(2);
                    if(s.targetsClearedForSmoke())throw new IllegalStateException("Missing selected targets");
                    clickHand(s,cardIndex);
                    if(s.selectedCountForSmoke()!=0||!s.targetsClearedForSmoke())throw new IllegalStateException("Deselected card retained targets");
                }
                clickHand(s,0);s.clickSeatForSmoke(1);s.clickSeatForSmoke(2);clickHand(s,1);
                if(s.selectedCountForSmoke()!=1||!s.targetsClearedForSmoke())throw new IllegalStateException("Switched card retained targets");
                ProbeMod.LOG.info("SGS_CARD_TARGET_RESET_PASS gui={} jiedao=true tiesuo=true switch=true",scale);
            }
            Screenshot.grab(c.gameDirectory,"tuxi-ban-gui"+scale+"-"+stage+".png",c.getMainRenderTarget(),m->{});
            if(++stage==3){stage=0;scale++;}
            if(scale>3){done=true;ProbeMod.LOG.info("SGS_TUXI_BAN_RENDER_PASS scales=1,2,3 portraits=true bans_hidden=true live_server=false");c.stop();return;}show(c);
        }catch(Exception e){done=true;ProbeMod.LOG.error("SGS_TUXI_BAN_RENDER_FAIL",e);c.stop();}
    }
    private static void clickHand(MatchScreen s,int index){var b=s.handBoundsForSmoke(index);if(b==null)throw new IllegalStateException("Missing card");s.mouseClicked(b[0]+b[2]/2d,b[1]+b[3]/2d,0);}
    private static void show(Minecraft c){c.options.guiScale().set(scale);c.resizeDisplay();c.setScreen(new MatchScreen(view()));frames=0;}
    private static MatchNetwork.View view(){
        var players=new ArrayList<SgsPlayer>();for(int i=0;i<4;i++)players.add(new MatchPlayer(new UUID(9952,i),"玩家"+(i+1),i,true));
        var heroes=List.of(StandardHeroes.ZHANGLIAO,StandardHeroes.ZHANGFEI,StandardHeroes.SIMAYI,StandardHeroes.ZHAOYUN);
        var state=GameState.startTwoVTwo(players,heroes,new Deck(JunzhengDeckFactory.create(new IdSequencer()),new Random(12)),new Random(13));
        while(state.phase()!=Phase.DRAW)state.advancePhase();
        var prompt=new PromptRequest(1,PromptType.TUXI_TARGET,players.getFirst(),players.getFirst(),List.of(),List.of(),System.currentTimeMillis()+300000,"tuxi",List.of(1,2,3),true);
        var snapshot=ViewFilter.running(state,0,System.currentTimeMillis()+300000,prompt,GameOutcome.ONGOING,null).withHeroPresentations(HeroCatalog.builtins().present(heroes.stream().map(h->h.id()).toList()));
        if(stage==1)snapshot=ViewFilter.selecting(players,false,0,List.of(),0)
            .withHeroBan(new HeroBanView(0,System.currentTimeMillis()+300000,List.of("zhangliao","zhangfei"),List.of(new HeroBanChoice(1,"zhangliao"))))
            .withHeroPresentations(HeroCatalog.builtins().present(List.of("zhangliao","zhangfei")));
        if(stage==2){
            state.advancePhase();state.cards().move(List.copyOf(players.getFirst().handView()),CardZone.hand(0),CardZone.DISCARD,CardMove.Reason.DISCARD,0);
            for(var type:List.of(CardType.JIEDAO,CardType.TIESUO)){
                var card=state.cards().cards(CardZone.DRAW).stream().filter(v->v.type()==type).findFirst().orElseThrow();
                state.cards().move(List.of(card),CardZone.DRAW,CardZone.hand(0),CardMove.Reason.DRAW,0);
            }
            var weapon=state.cards().cards(CardZone.DRAW).stream().filter(v->v.type()==CardType.QILIN_GONG).findFirst().orElseThrow();
            state.cards().move(List.of(weapon),CardZone.DRAW,CardZone.equipment(1),CardMove.Reason.EQUIP,1);
            var engine=new SgsGameEngine(state,(g,p)->Optional.empty(),System::currentTimeMillis,20);
            snapshot=ViewFilter.running(state,0,System.currentTimeMillis()+300000,null,GameOutcome.ONGOING,null)
                .withOwnCardPlayOptions(engine.cardPlayAvailability(players.getFirst()))
                .withHeroPresentations(HeroCatalog.builtins().present(heroes.stream().map(h->h.id()).toList()));
        }
        return new MatchNetwork.View(9952,stage+1,snapshot);
    }
}
