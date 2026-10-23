package dev.sgs.validation.client;

import dev.sgs.core.addon.HeroCatalog;
import dev.sgs.core.match.*;
import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.match.MatchNetwork;
import java.util.*;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Opt-in native UI regression fixture, without a multiplayer connection. */
final class HeroConfirmationChecks {
    private static final List<String> HEROES=List.of("zhangfei","zhouyu","simayi","zhaoyun");
    private static int scale=2,frames,stage,nonce;
    private static boolean done;
    static void initialize(){if(Boolean.getBoolean("sgs.heroConfirmation"))Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,HeroConfirmationChecks::tick);}
    private static MatchNetwork.View view(long revision){
        var players=new ArrayList<MatchPlayer>();
        for(int i=0;i<4;i++)players.add(new MatchPlayer(new UUID(9922,i),"玩家"+(i+1),i,true));
        var snapshot=ViewFilter.selecting(players,true,0,HEROES,System.currentTimeMillis()+300000)
            .withHeroPresentations(HeroCatalog.builtins().present(HEROES));
        return new MatchNetwork.View(9922,revision,snapshot);
    }
    private static void show(Minecraft client){
        client.options.guiScale().set(scale);client.resizeDisplay();client.setScreen(new MatchScreen(view(1)));
        frames=0;stage=0;nonce=MatchClient.nonceForSmoke();
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private static void tick(Minecraft client){
        if(done||client.getOverlay()!=null||client.screen==null)return;
        try{
            if(!(client.screen instanceof MatchScreen)){show(client);return;}
            var screen=(MatchScreen)client.screen;if(++frames<20)return;
            if(stage==0){
                require(!screen.enabledForSmoke("hero-confirm"),"confirmation enabled without a choice scale="+scale+" preview="+screen.heroPreviewForSmoke()+" pending="+screen.heroPendingForSmoke()+"");
                screen.clickForSmoke("hero:zhangfei");
                require(screen.heroPreviewForSmoke().equals("zhangfei") && !screen.heroPendingForSmoke(),"portrait must only preview");
                screen.clickForSmoke("hero:zhouyu");
                screen.update(view(2));
                require(screen.heroPreviewForSmoke().equals("zhouyu") && screen.enabledForSmoke("hero-confirm"),"choice must survive unrelated revision");
                require(MatchClient.nonceForSmoke()==nonce+2,"portrait previews must sync without confirming");
                stage=1;frames=0;return;
            }
            Screenshot.grab(client.gameDirectory,"hero-confirmation-gui"+scale+".png",client.getMainRenderTarget(),m->{});
            screen.clickForSmoke("hero-confirm");
            require(screen.heroPendingForSmoke() && !screen.enabledForSmoke("hero-confirm") && !screen.enabledForSmoke("hero:zhangfei"),"pending submission must lock controls");
            require(MatchClient.nonceForSmoke()==nonce+3,"confirm must submit once");
            screen.update(view(3));
            require(screen.heroPendingForSmoke(),"unrelated revision unlocked pending submission");
            screen.error("HERO_TAKEN");
            require(!screen.heroPendingForSmoke() && screen.enabledForSmoke("hero:zhangfei"),"rejection must unlock selection");
            screen.clickForSmoke("hero:simayi");screen.clickForSmoke("hero-confirm");
            require(MatchClient.nonceForSmoke()==nonce+5,"retry did not submit selected hero");
            if(scale==2){scale=3;show(client);return;}
            done=true;ProbeMod.LOG.info("SGS_HERO_CONFIRMATION_PASS gui=2,3 preview=true explicit_confirm=true retry=true live_server=false");client.stop();
        }catch(Exception error){done=true;ProbeMod.LOG.error("SGS_HERO_CONFIRMATION_FAIL",error);client.stop();}
    }
}
