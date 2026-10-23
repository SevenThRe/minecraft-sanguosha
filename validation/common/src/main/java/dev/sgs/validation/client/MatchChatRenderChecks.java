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
import dev.sgs.validation.match.*;
import java.util.*;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Explicit native UI fixture, not a multiplayer test. */
final class MatchChatRenderChecks {
    private static int scale=1,stage,frames;private static boolean done;
    static void initialize(){if(Boolean.getBoolean("sgs.matchChatRender"))Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,MatchChatRenderChecks::tick);}
    private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private static void tick(Minecraft client){
        if(done||client.getOverlay()!=null||client.screen==null)return;
        try{
            client.getToasts().clear();
            if(!(client.screen instanceof MatchScreen)){show(client);return;}
            var screen=(MatchScreen)client.screen;if(++frames<25)return;
            Screenshot.grab(client.gameDirectory,"match-chat-gui"+scale+"-"+stage+".png",client.getMainRenderTarget(),m->{});
            if(stage==0){
                screen.chatDraftForSmoke("中文草稿 /help");
                var initial=view();screen.update(new MatchNetwork.View(initial.matchId(),2,initial.snapshot()));
                check(MatchChatClient.open&&MatchChatClient.draft.equals("中文草稿 /help"),"snapshot lost chat draft");
                int before=screen.selectedCountForSmoke();screen.keyPressed(32,0,0);check(screen.selectedCountForSmoke()==before,"typing triggered card selection");
                screen.keyPressed(256,0,0);check(!MatchChatClient.open&&client.screen==screen,"Escape left match");
                MatchChatClient.receive(new MatchChatNetwork.Update(9922,0,-1,true,"",true,List.of(new dev.sgs.core.social.MatchChat.Message(31,new UUID(9922,1),1,"玩家二","收起后的新消息",0))));
                check(MatchChatClient.unread()==1,"unread count wrong");stage=1;frames=0;return;
            }
            if(stage==1){screen.clickForSmoke("chat");check(MatchChatClient.draft.equals("中文草稿 /help"),"reopen lost draft");screen.mouseScrolled(20,50,0,3);check(!MatchChatClient.follow,"history wheel failed");stage=2;frames=0;return;}
            if(scale<3){scale++;stage=0;show(client);return;}
            done=true;ProbeMod.LOG.info("SGS_MATCH_CHAT_RENDER_PASS gui=1,2,3 draft=true unread=true key_isolation=true scroll=true live_server=false");client.stop();
        }catch(Exception error){done=true;ProbeMod.LOG.error("SGS_MATCH_CHAT_RENDER_FAIL",error);client.stop();}
    }
    private static void show(Minecraft client){
        MatchChatClient.clear();client.options.guiScale().set(scale);client.resizeDisplay();var screen=new MatchScreen(view());client.setScreen(screen);
        var messages=new ArrayList<dev.sgs.core.social.MatchChat.Message>();
        for(int i=1;i<=30;i++)messages.add(new dev.sgs.core.social.MatchChat.Message(i,new UUID(9922,i%4),i%4,"玩家"+(i%4+1),i%3==0?"这轮先留一张闪，我来配合你。长消息自动换行，不影响牌局继续。":"打得漂亮！",0));
        MatchChatClient.receive(new MatchChatNetwork.Update(9922,0,0,true,"",true,messages));screen.clickForSmoke("chat");frames=0;
    }
    private static MatchNetwork.View view(){
        var players=new ArrayList<SgsPlayer>();for(int i=0;i<4;i++)players.add(new MatchPlayer(new UUID(9922,i),"玩家"+(i+1),i,true));
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
        return new MatchNetwork.View(9922,1,snapshot);
    }
}
