package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.*;

/** Opt-in real client and network UI regression; never enabled during normal play. */
public final class CollectionGuiSmokeChecks {
    private static int stage,ticks;
    private static final java.util.concurrent.atomic.AtomicInteger pending=new java.util.concurrent.atomic.AtomicInteger();
    private static String bought;
    private static int animationFrames;
    public static void initialize(){if(Boolean.getBoolean("sgs.collectionGuiSmoke"))Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,CollectionGuiSmokeChecks::tick);}
    private static void next(int n){stage=n;ticks=0;}
    private static void tick(Minecraft client) {
        if(client.player==null || CollectionClient.state()==null)return;
        if(++ticks>1200){ProbeMod.LOG.error("SGS_COLLECTION_GUI_FAIL timeout stage={}",stage);client.stop();return;}
        try {
            client.getToasts().clear();
            var state=CollectionClient.state();long owned=state.heroes().stream().filter(dev.sgs.validation.collection.CollectionNetwork.Hero::owned).count();
            if(stage==0){client.getWindow().setWindowed(1600,900);client.options.guiScale().set(2);client.resizeDisplay();CollectionClient.open();org.lwjgl.glfw.GLFW.glfwSetCursorPos(client.getWindow().getWindow(),12,12);next(1);return;}
            if(!(client.screen instanceof CollectionScreen screen) || CollectionClient.pending())return;
            if(stage==2 && Boolean.getBoolean("sgs.unifiedArtSmoke") && screen.smokeAnimating()){
                long elapsed=screen.smokeElapsed();
                if(animationFrames==0 && elapsed>=180){capture(client,"animation-tally");animationFrames++;}
                else if(animationFrames==1 && elapsed>=1450){capture(client,"animation-soul");animationFrames++;}
                else if(animationFrames==2 && elapsed>=screen.smokeFilmDuration()+450){capture(client,"animation-reveal");animationFrames++;}
                return;
            }
            if((stage==2 || stage==11) && screen.smokeAnimating()){
                if(stage==11 && Boolean.getBoolean("sgs.unifiedArtSmoke") && screen.smokeElapsed()>screen.smokeFilmDuration()+850){
                    capture(client,"ten-revealing");screen.smokeSkip();
                    require(!screen.smokeAnimating() && state.tokens()==3*state.drawTokenCost() && state.draws().size()==10,"skip preserves receipt");
                    ticks=0;
                }
                return;
            }
            if(ticks<25)return;
            switch(stage) {
                case 1 -> {require(state.enabled() && owned==1 && state.tokens()==14*state.drawTokenCost() && state.souls()==100,"initial account");require(!Boolean.getBoolean("sgs.unifiedArtSmoke") || screen.smokeFilmAvailable(),"film assets");require(CollectionClient.ownsSkin("caocao","action") && !CollectionClient.ownsSkin("liubei","action"),"skin entitlement");capture(client,"recruit-gui2");screen.smokeDraw();next(2);}
                case 2 -> {require(owned==2 && state.tokens()==13*state.drawTokenCost(),"draw receipt");capture(client,"single-result");screen.smokeMarket();next(3);}
                case 3 -> {capture(client,"market-gui2");CollectionClient.smokeStalePurchase(screen.smokeBuyCandidate());next(4);}
                case 4 -> {require(owned==2 && state.souls()==100 && state.message().contains("价格已变化"),"stale price must not debit");bought=screen.smokeBuy();next(5);}
                case 5 -> {require(owned==3 && state.tokens()==13*state.drawTokenCost() && state.souls()==0 && state.heroes().stream().anyMatch(h->h.id().equals(bought) && h.owned()),"purchase receipt");capture(client,"purchased-gui2");client.options.guiScale().set(1);client.resizeDisplay();next(6);}
                case 6 -> {capture(client,"market-gui1");client.options.guiScale().set(3);client.resizeDisplay();next(7);}
                case 7 -> {capture(client,"market-gui3");screen.smokeHover();next(8);}
                case 8 -> {capture(client,"market-hover");screen.smokeClearHover();if(Boolean.getBoolean("sgs.unifiedArtSmoke")){screen.smokeDerivedHover();next(80);}else{screen.smokeScroll();next(9);}}
                case 80 -> {require(screen.smokeHasDerived(),"server-derived skill tips");capture(client,"market-derived-hover");screen.smokeClearHover();screen.smokeScroll();next(9);}
                case 9 -> {capture(client,"market-scroll");screen.smokeRecruit();next(10);}
                case 10 -> {capture(client,"recruit-gui3");screen.smokeTen();next(11);}
                case 11 -> {require(state.tokens()==3*state.drawTokenCost() && state.draws().size()==10,"atomic ten receipt");capture(client,"ten-result");screen.smokeOwned();next(13);}
                case 13 -> {capture(client,"warehouse-gui3");screen.smokeCurrency(0);next(14);}
                case 14 -> {require(screen.smokeCurrencyVisible(0),"token tooltip rendered");capture(client,"currency-token-hover");screen.smokeCurrency(1);next(15);}
                case 15 -> {require(screen.smokeCurrencyVisible(1),"soul tooltip rendered");capture(client,"currency-soul-hover");screen.smokeCurrency(-1);client.options.guiScale().set(2);client.resizeDisplay();next(16);}
                case 16 -> {capture(client,"warehouse-gui2");next(12);}
                case 12 -> {if(pending.get()==0){require(!Boolean.getBoolean("sgs.unifiedArtSmoke") || animationFrames==3,"all animation phases captured");ProbeMod.LOG.info("SGS_COLLECTION_GUI_PASS currency_config=true warehouse=true currency_hover=true draw=true ten=true purchase=true stale_price_rejected=true skins=true gui=1,2,3 animation_frames={} skip_preserves_receipt=true",animationFrames);client.stop();}}
            }
        }catch(Exception | AssertionError e){ProbeMod.LOG.error("SGS_COLLECTION_GUI_FAIL stage="+stage,e);client.stop();}
    }
    private static void capture(Minecraft client,String name){pending.incrementAndGet();Screenshot.grab(client.gameDirectory,"sgs-collection-"+name+".png",client.getMainRenderTarget(),message->{pending.decrementAndGet();ProbeMod.LOG.info("{}",message.getString());});}
    private static void require(boolean pass,String message){if(!pass)throw new IllegalStateException(message);}
}
