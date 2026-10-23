package dev.sgs.validation.client;

import dev.sgs.core.*;
import dev.sgs.core.addon.HeroCatalog;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.*;
import dev.sgs.core.game.GameState;
import dev.sgs.core.hero.StandardHeroes;
import dev.sgs.core.match.*;
import dev.sgs.core.match.dto.MatchViews.TablePresentation;
import dev.sgs.core.player.SgsPlayer;
import dev.sgs.core.util.IdSequencer;
import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.match.MatchNetwork;
import java.util.*;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Native layout and real mouse-path checks; network acceptance is tested separately. */
final class BetaTodoRenderChecks {
    private static GameState fixtureState;
    private static final boolean DISCARD_ONLY=Boolean.getBoolean("sgs.discardOnlyRender");
    private static int scale=1,stage=DISCARD_ONLY?9:0,frames;private static boolean done;private static long shown;
    private static final String[] NAMES={"invoke","portrait-target","selected-target","victory","mvp","rewards","statistics","first-chain-released","second-chain-released","discard-limit"};
    static void initialize(){if(Boolean.getBoolean("sgs.betaTodoRender"))Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,BetaTodoRenderChecks::tick);}
    private static void tick(Minecraft c){
        if(done||c.getOverlay()!=null||c.screen==null)return;
        try{
            c.getToasts().clear();if(!(c.screen instanceof MatchScreen)){show(c);return;}
            var s=(MatchScreen)c.screen;if(++frames<12)return;
            if(stage==4&&net.minecraft.Util.getMillis()-shown<2200)return;
            if(stage>=3&&stage<=6){
                if(s.handBoundsForSmoke(0)!=null)throw new IllegalStateException("Result still exposes hand hitbox");
                s.mouseClicked(c.getWindow().getGuiScaledWidth()/2d,c.getWindow().getGuiScaledHeight()-20,0);
                if(s.selectedCountForSmoke()!=0)throw new IllegalStateException("Result click selected underlying card");
            }
            if(stage==0&&!s.enabledForSmoke("invoke"))throw new IllegalStateException("Missing invoke question");
            if(stage==1&&!s.portraitOnlyForSmoke())throw new IllegalStateException("Duplicate target buttons");
            if(stage==9){
                if(s.enabledForSmoke("discard"))throw new IllegalStateException("Empty discard enabled");
                for(int i=0;i<3;i++){var hit=s.handBoundsForSmoke(i);if(hit==null)throw new IllegalStateException("Missing discard hand");
                    s.mouseClicked(hit[0]+hit[2]/2d,hit[1]+hit[3]/2d,0);}
                if(s.selectedCountForSmoke()!=2||!s.enabledForSmoke("discard"))throw new IllegalStateException("Discard selection count="+s.selectedCountForSmoke()+" enabled="+s.enabledForSmoke("discard"));
                ProbeMod.LOG.info("SGS_DISCARD_LIMIT_INPUT_PASS gui={} selected=2 attempted=3",scale);
            }
            Screenshot.grab(c.gameDirectory,"beta-todo-gui"+scale+"-"+NAMES[stage]+".png",c.getMainRenderTarget(),m->{});
            if(stage==1)s.clickSeatForSmoke(2);
            if(stage==2){
                var card=fixtureState.players().getFirst().handView().getFirst();s.twoTargetsForSmoke(card.instanceId());
                fixtureState.cards().move(List.of(card),CardZone.hand(0),CardZone.DISCARD,CardMove.Reason.DISCARD,0);
                var after=ViewFilter.running(fixtureState,0,System.currentTimeMillis()+300000,null,GameOutcome.ONGOING,null);
                s.update(new MatchNetwork.View(9941,50,after));
                if(s.selectedCountForSmoke()!=0||!s.targetsClearedForSmoke())throw new IllegalStateException("Consumed card left stale targets");
                ProbeMod.LOG.info("SGS_BETA_TARGET_RESET_PASS gui={} same_phase=true same_prompt=true",scale);
            }
            if(stage==4){s.clickForSmoke("result-next");if(s.resultStageForSmoke()!=1)throw new IllegalStateException("Continue blocked");}
            if(stage==5){s.clickForSmoke("result-details");if(s.resultStageForSmoke()!=2)throw new IllegalStateException("Statistics button blocked");}
            if(stage==6){s.clickForSmoke("finish");s.clickForSmoke("result-rewards");if(s.resultStageForSmoke()!=1)throw new IllegalStateException("Rewards button blocked");}
            ProbeMod.LOG.info("SGS_BETA_TODO_RENDER_CAPTURE gui={} state={}",scale,NAMES[stage]);
            if(++stage==NAMES.length){stage=DISCARD_ONLY?9:0;scale++;}
            if(scale>3){done=true;ProbeMod.LOG.info("SGS_BETA_TODO_RENDER_PASS scales=3 states=10 live_server=false");c.stop();return;}show(c);
        }catch(Exception e){done=true;ProbeMod.LOG.error("SGS_BETA_TODO_RENDER_FAIL",e);c.stop();}
    }
    private static void show(Minecraft c){
        c.options.guiScale().set(scale);c.resizeDisplay();var screen=new MatchScreen(view());c.setScreen(screen);frames=0;shown=net.minecraft.Util.getMillis();
        if(stage==2)screen.selectForSmoke(List.of(),2);
        if(stage>=5&&stage<=6)screen.resultPageForSmoke(stage==5?1:2);
        if(stage==7||stage==8){
            fixtureState.setChained(2,false);fixtureState.players().get(2).setHp(fixtureState.players().get(2).hp()-1);
            if(stage==8){fixtureState.setChained(3,false);fixtureState.players().get(3).setHp(fixtureState.players().get(3).hp()-1);}
            screen.update(new MatchNetwork.View(9941,50,ViewFilter.running(fixtureState,0,System.currentTimeMillis()+300000,null,GameOutcome.ONGOING,null).withHeroPresentations(HeroCatalog.builtins().present(List.of("xunyu")))));
        }
        screen.hoverForSmoke(c.getWindow().getGuiScaledWidth()/2,c.getWindow().getGuiScaledHeight()-35);
    }
    private static MatchNetwork.View view(){
        var players=new ArrayList<SgsPlayer>();for(int i=0;i<6;i++){var p=new MatchPlayer(new UUID(9941,i),"玩家"+(i+1),i,true);p.assignIdentity(i==0?Identity.LORD:Identity.REBEL);players.add(p);}
        var hero=StandardHeroes.CATALOG.stream().filter(h->h.id().equals("xunyu")).findFirst().orElseThrow();
        var state=GameState.startAssigned(players,Collections.nCopies(6,hero),new Deck(JunzhengDeckFactory.create(new IdSequencer()),new Random(17)),new Random(18));
        fixtureState=state;
        while(state.currentSeat()!=0||state.phase()!=Phase.PLAY)state.advancePhase();
        var crossbow=state.cards().cards(CardZone.DRAW).stream().filter(card->card.type()==CardType.ZHUGE_NU).findFirst().orElseThrow();
        state.cards().move(List.of(crossbow),CardZone.DRAW,CardZone.hand(0),CardMove.Reason.DRAW,0);
        if(stage==7||stage==8){state.setChained(2,true);state.setChained(3,true);}
        if(stage==9){
            var excess=List.copyOf(players.getFirst().handView().subList(5,players.getFirst().handCount()));
            state.cards().move(excess,CardZone.hand(0),CardZone.DISCARD,CardMove.Reason.DISCARD,0);
            state.ruleHandLimit(p->3);state.advancePhase();
        }
        PromptRequest prompt=stage<2?new PromptRequest(1,stage==0?PromptType.SKILL_INVOKE:PromptType.EXPANSION_CHOICE,players.getFirst(),players.getFirst(),List.of(),List.of(),System.currentTimeMillis()+300000,"jieming",stage==1?List.of(0,2,3):List.of(),false):null;
        var snapshot=ViewFilter.running(state,0,System.currentTimeMillis()+300000,prompt,stage>=3&&stage<=6?GameOutcome.LORD_WIN:GameOutcome.ONGOING,null)
            .withHeroPresentations(HeroCatalog.builtins().present(List.of("xunyu")))
            .withTablePresentation(new TablePresentation(null,List.of(),List.of(8L,1L,2L,3L,4L,5L),true));
        return new MatchNetwork.View(9941,stage+1,snapshot);
    }
}
