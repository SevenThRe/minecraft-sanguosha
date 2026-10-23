package dev.sgs.validation.client;

import dev.sgs.core.*;
import dev.sgs.core.addon.HeroCatalog;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.*;
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

/** Opt-in rendering/input fixtures. No live server or transport acceptance claim. */
final class ResponsePresentationRenderChecks {
    private static int scale=1,stage,frames;private static boolean done;
    private static final String[] NAMES={"duel","owned-choice","discard"};
    static void initialize(){if(Boolean.getBoolean("sgs.responsePresentationRender"))Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,ResponsePresentationRenderChecks::tick);}
    private static void tick(Minecraft client){
        if(done || client.getOverlay()!=null || client.screen==null)return;
        try{
            client.getToasts().clear();
            if(!(client.screen instanceof MatchScreen)){show(client);return;}
            var screen=(MatchScreen)client.screen;
            if(++frames<20)return;
            if(frames==20){
                var hand=screen.stateForSmoke().snapshot().seats().stream().filter(p->p.self()).findFirst().orElseThrow().hand();
                if(stage==1 && !screen.ownedChoiceUsesCardsForSmoke())throw new IllegalStateException("Owned choice still uses duplicate buttons");
                int index=stage==0?java.util.stream.IntStream.range(0,hand.size()).filter(i->hand.get(i).typeOrdinal()==CardType.SHA.ordinal()).findFirst().orElseThrow():0;
                if(stage==0){
                    int blocked=java.util.stream.IntStream.range(0,hand.size()).filter(i->hand.get(i).typeOrdinal()==CardType.SHAN.ordinal()).findFirst().orElseThrow();
                    if(screen.handCardSelectableForSmoke(hand.get(blocked).instanceId()))throw new IllegalStateException("Shan can answer Duel");
                    var b=screen.handBoundsForSmoke(blocked);screen.mouseClicked(b[0]+Math.max(1,b[2]/2d),b[1]+b[3]/2d,0);
                    if(screen.selectedCountForSmoke()!=0)throw new IllegalStateException("Blocked response card was selected");
                }
                if(!screen.handCardSelectableForSmoke(hand.get(index).instanceId()))throw new IllegalStateException("Offered hand card blocked");
                var box=screen.handBoundsForSmoke(index);screen.mouseClicked(box[0]+Math.max(1,box[2]/2d),box[1]+box[3]/2d,0);
                if(screen.selectedCountForSmoke()!=1)throw new IllegalStateException("Hand mouse click failed");return;
            }
            if(frames<25)return;
            Screenshot.grab(client.gameDirectory,"response-presentation-gui"+scale+"-"+NAMES[stage]+".png",client.getMainRenderTarget(),m->{});
            ProbeMod.LOG.info("SGS_RESPONSE_PRESENTATION_RENDER_CAPTURE gui={} state={} mouse_selected=true live_server=false",scale,NAMES[stage]);
            if(++stage==3){stage=0;scale++;}
            if(scale>3){done=true;ProbeMod.LOG.info("SGS_RESPONSE_PRESENTATION_RENDER_PASS scales=3 states=3 live_server=false");client.stop();return;}
            show(client);
        }catch(Exception e){done=true;ProbeMod.LOG.error("SGS_RESPONSE_PRESENTATION_RENDER_FAIL",e);client.stop();}
    }
    private static void show(Minecraft c){c.options.guiScale().set(scale);c.resizeDisplay();c.setScreen(new MatchScreen(view()));frames=0;}
    private static MatchNetwork.View view(){
        var players=new ArrayList<SgsPlayer>();
        for(int i=0;i<6;i++){var p=new MatchPlayer(new UUID(8872,i),"玩家"+(i+1),i,true);p.assignIdentity(i==0?Identity.LORD:Identity.REBEL);players.add(p);}
        var heroes=Collections.nCopies(6,StandardHeroes.ZHANGFEI);
        var state=GameState.startAssigned(players,heroes,new Deck(JunzhengDeckFactory.create(new IdSequencer()),new Random(17)),new Random(18));
        while(state.currentSeat()!=0 || state.phase()!=Phase.PLAY)state.advancePhase();
        int viewer=stage==2?0:1;var owner=players.get(viewer);
        // Ensure both an eligible and an ineligible response card in the real physical hand.
        for(var type:List.of(CardType.SHA,CardType.SHAN)){
            var card=state.cards().cards(CardZone.DRAW).stream().filter(c->c.type()==type).findFirst().orElseThrow();
            state.cards().move(List.of(card),CardZone.DRAW,CardZone.hand(viewer),CardMove.Reason.DRAW,viewer);
        }
        var engine=new SgsGameEngine(state,(s,p)->Optional.empty(),System::currentTimeMillis,300);
        PromptRequest prompt=null;
        if(stage==0){
            var duel=state.cards().cards(CardZone.DRAW).stream().filter(c->c.type()==CardType.JUEDOU).findFirst().orElseThrow();
            state.cards().move(List.of(duel),CardZone.DRAW,CardZone.hand(0),CardMove.Reason.DRAW,0);
            if(!engine.beginResumableTrick(players.get(0),duel.instanceId(),null,owner).accepted())throw new IllegalStateException("Duel fixture rejected");
            for(int i=0;i<20 && engine.pendingTrickPrompt().isEmpty();i++){
                engine.pollResumableTrick();
                if(engine.pendingTrickPrompt().isPresent())break;
                var w=engine.pendingTrickWindow().orElseThrow();engine.resumeTrickWindow(w.windowId(),engine.trickDecisionRevision(),new CounterspellResult(Optional.empty(),Set.of()));
            }
            prompt=engine.pendingTrickPrompt().orElseThrow();
        }else if(stage==1){
            prompt=new PromptRequest(1,PromptType.EXPANSION_CHOICE,owner,owner,List.of(),owner.handView(),System.currentTimeMillis()+300000,"test_owned");
        }else if(!engine.endPlayPhase(owner).accepted())throw new IllegalStateException("Discard fixture rejected");
        var snapshot=ViewFilter.running(state,viewer,System.currentTimeMillis()+300000,prompt,GameOutcome.ONGOING,null)
            .withHeroPresentations(HeroCatalog.builtins().present(List.of("zhangfei")));
        return new MatchNetwork.View(8872,stage+1,snapshot);
    }
}
