package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.TickType;
import net.blay09.mods.balm.api.event.TickPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** Opens the actual registered creative page, verifies its contents and captures its rendering. */
final class CreativeTabSmokeChecks {
    private static boolean started,opened,done;private static int frames;
    static void start(){started=true;}
    static boolean showing(){return GuanxingRowsSmokeChecks.enabled()&&started&&opened;}
    static void initialize(){if(GuanxingRowsSmokeChecks.enabled())Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,CreativeTabSmokeChecks::tick);}
    private static void tick(Minecraft c){
        if(!started||done||c.player==null||c.level==null)return;
        try{
            if(!opened){
                var tab=BuiltInRegistries.CREATIVE_MODE_TAB.get(ProbeMod.id("main"));if(tab==null)throw new IllegalStateException("missing sgs creative page");
                var screen=new CreativeModeInventoryScreen(c.player,c.player.connection.enabledFeatures(),true);c.setScreen(screen);
                // NeoForge pages are independent of the selected tab; show the page containing this tab too.
                try{var pages=CreativeModeInventoryScreen.class.getDeclaredField("pages");pages.setAccessible(true);
                    for(var page:(java.util.List<?>)pages.get(screen)){
                        var visible=(java.util.List<?>)page.getClass().getMethod("getVisibleTabs").invoke(page);
                        if(visible.contains(tab)){CreativeModeInventoryScreen.class.getMethod("setCurrentPage",page.getClass()).invoke(screen,page);break;}
                    }
                }catch(NoSuchFieldException ignored){/* Other loaders keep their native tab navigation. */}
                var select=CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab",CreativeModeTab.class);select.setAccessible(true);select.invoke(screen,tab);
                if(!tab.contains(new ItemStack(ProbeMod.table))||!tab.contains(new ItemStack(ProbeMod.deck)))throw new IllegalStateException("creative page missing items");
                opened=true;return;
            }
            if(++frames<10)return;
            Screenshot.grab(c.gameDirectory,"sgs-creative-main.png",c.getMainRenderTarget(),m->{});
            ProbeMod.LOG.info("SGS_CREATIVE_TAB_PASS tab=sgs:main items=sgs:card_table,sgs:deck_pack native_screen=true");done=true;
        }catch(Exception e){done=true;ProbeMod.LOG.error("SGS_GUANXING_UI_FAIL creative",e);}
    }
}
