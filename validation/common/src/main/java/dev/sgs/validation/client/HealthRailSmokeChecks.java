package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.client.art.CardRenderer;
import dev.sgs.validation.client.art.HealthRenderer;
import dev.sgs.validation.client.art.HeroRenderer;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.TickPhase;
import net.blay09.mods.balm.api.event.TickType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Explicit component fixtures. These HP values are never installed into a match. */
public final class HealthRailSmokeChecks {
    private static int scale=1,frames;
    private static boolean done;
    public static void initialize(){
        if(Boolean.getBoolean("sgs.healthRailSmoke"))Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,HealthRailSmokeChecks::tick);
    }
    private static void tick(Minecraft c){
        if(done || c.player==null || c.level==null)return;
        if(!(c.screen instanceof Preview)) {c.options.guiScale().set(scale);c.resizeDisplay();c.setScreen(new Preview());frames=0;return;}
        c.getToasts().clear();if(++frames<16)return;frames=0;
        Screenshot.grab(c.gameDirectory,"sgs-health-rail-gui"+scale+".png",c.getMainRenderTarget(),m->{});
        ProbeMod.LOG.info("SGS_HEALTH_RAIL_SCALE_PASS gui={} native_component=true match_state=false",scale);
        if(++scale<=3){c.options.guiScale().set(scale);c.resizeDisplay();}
        else{done=true;ProbeMod.LOG.info("SGS_HEALTH_RAIL_PASS gui=1,2,3 native_component=true match_state=false");c.stop();}
    }
    private static final class Preview extends Screen {
        Preview(){super(Component.literal("体力槽组件预览"));}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            CardRenderer.background(g,width,height);
            float unit=Math.min(width/800f,height/450f);
            g.pose().pushPose();g.pose().translate((width-800*unit)/2,(height-450*unit)/2,0);g.pose().scale(unit,unit,1);
            g.drawCenteredString(font,"体力槽组件预览 · 示例数值，非实际对局状态",400,24,0xffffdca5);
            int[] hp={4,3,1,0,5},max={4,4,4,4,5};String[] heroes={"liubei","caocao","zhaoyun","sunshangxiang","lvbu"};
            for(int i=0;i<5;i++){
                int x=58+i*139,y=85,w=128,h=190;
                HeroRenderer.portrait(g,font,heroes[i],x,y,w,h,0xffb68a48,true);
                HealthRenderer.verticalBottom(g,x+w-2,y+h-3,hp[i],max[i],Math.round(h*.55f),1);
                g.drawCenteredString(font,hp[i]+" / "+max[i],x+w/2,y+h+15,0xffffe7bf);
            }
            g.drawCenteredString(font,"独立底板 · 空槽在上 · 剩余体力从底部亮起 · 每格1点体力",400,337,0xffe1c79c);
            g.pose().popPose();
        }
        @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
    }
}
