package dev.sgs.validation.client;

import dev.sgs.core.*;
import dev.sgs.core.addon.HeroCatalog;
import dev.sgs.core.card.*;
import dev.sgs.core.game.GameState;
import dev.sgs.core.engine.GameOutcome;
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

/** Timed native visual fixture driven by real public card movement receipts. */
final class PublicCardMotionRenderChecks {
 private static int scale=1,stage;private static long at,revision;private static boolean done;
 private static GameState state;private static CardInstance aoe;private static MatchScreen screen;
 static void initialize(){if(Boolean.getBoolean("sgs.publicCardMotionRender"))Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,PublicCardMotionRenderChecks::tick);}
 private static void tick(Minecraft c){
  if(done||c.getOverlay()!=null||c.screen==null)return;
  try{
   c.getToasts().clear();
   if(screen==null){show(c);return;}
   long now=net.minecraft.Util.getMillis(),age=now-at;
   if(stage==0&&age>=450){
    state.cards().move(List.of(aoe),CardZone.hand(0),CardZone.PROCESSING,CardMove.Reason.USE,0);state.setChained(1,true);state.setChained(2,true);update();stage++;at=now;
   }else if(stage==1&&age>=150){capture(c,"arrival");stage++;}
   else if(stage==2&&age>=600){
    var reply=state.cards().cards(CardZone.DRAW).stream().filter(x->x.type()==CardType.SHA).findFirst().orElseThrow();
    state.cards().move(List.of(reply),CardZone.DRAW,CardZone.hand(1),CardMove.Reason.DRAW,1);
    state.cards().move(List.of(reply),CardZone.hand(1),CardZone.DISCARD,CardMove.Reason.RESPONSE,1);update();stage++;at=now;
   }else if(stage==3&&age>=110){if(screen.publicCardCountForSmoke()!=2)throw new IllegalStateException("response group lost");capture(c,"reflow");stage++;}
   else if(stage==4&&age>=500){capture(c,"settled");state.cards().move(List.of(aoe),CardZone.PROCESSING,CardZone.DISCARD,CardMove.Reason.FINISH,0);state.setChained(1,false);state.setChained(2,false);update();stage++;at=now;}
   else if(stage==5&&age>=120){if(screen.chainReleaseFrameForSmoke()==null)throw new IllegalStateException("chain release not rendered");capture(c,"chain-release");stage++;}
   else if(stage==6&&age>=700){if(screen.publicCardCountForSmoke()!=2)throw new IllegalStateException("finished group vanished early");capture(c,"hold");stage++;}
   else if(stage==7&&age>=1600){capture(c,"fade");stage++;}
   else if(stage==8&&age>=1900){
    if(screen.publicCardCountForSmoke()!=0)throw new IllegalStateException("stale public cards");capture(c,"cleared");
    if(++scale>3){done=true;ProbeMod.LOG.info("SGS_PUBLIC_CARD_MOTION_RENDER_PASS scales=3 stages=7 live_server=false");c.stop();}else show(c);
   }
  }catch(Exception e){done=true;ProbeMod.LOG.error("SGS_PUBLIC_CARD_MOTION_RENDER_FAIL",e);c.stop();}
 }
 private static void capture(Minecraft c,String name){Screenshot.grab(c.gameDirectory,"public-card-motion-gui"+scale+"-"+name+".png",c.getMainRenderTarget(),m->{});}
 private static MatchNetwork.View view(){return new MatchNetwork.View(8873,++revision,ViewFilter.running(state,0,System.currentTimeMillis()+300000,null,GameOutcome.ONGOING,null).withHeroPresentations(HeroCatalog.builtins().present(List.of("zhangfei")))) ;}
 private static void update(){screen.update(view());}
 private static void show(Minecraft c){
  var players=new ArrayList<SgsPlayer>();for(int i=0;i<6;i++){var p=new MatchPlayer(new UUID(8873,i),"玩家"+(i+1),i,true);p.assignIdentity(i==0?Identity.LORD:Identity.REBEL);players.add(p);}
  state=GameState.startAssigned(players,Collections.nCopies(6,StandardHeroes.ZHANGFEI),new Deck(JunzhengDeckFactory.create(new IdSequencer()),new Random(17)),new Random(18));
  while(state.currentSeat()!=0||state.phase()!=Phase.PLAY)state.advancePhase();
  aoe=state.cards().cards(CardZone.DRAW).stream().filter(x->x.type()==CardType.NANMAN).findFirst().orElseThrow();
  state.cards().move(List.of(aoe),CardZone.DRAW,CardZone.hand(0),CardMove.Reason.DRAW,0);
  c.options.guiScale().set(scale);c.resizeDisplay();screen=new MatchScreen(view());c.setScreen(screen);stage=0;at=net.minecraft.Util.getMillis();
 }
}
