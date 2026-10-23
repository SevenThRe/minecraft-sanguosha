package dev.sgs.validation.client.art;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** initial identity/team modes: one separate half-heart slot per integer HP; never merge slots. */
public final class HealthRenderer {
    private static final ResourceLocation EMPTY = ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation HALF = ResourceLocation.withDefaultNamespace("hud/heart/half");

    private HealthRenderer() {}

    /** A separately framed lower-right rail, leaving identity, statuses and local skills free. */
    public static void verticalBottom(GuiGraphics g,int right,int bottom,int health,int maximum,int availableHeight,float unit){
        int count=Math.clamp(maximum,0,20);if(count==0)return;
        if(availableHeight<count)return;
        int pad=Math.min(Math.max(1,Math.round(3*unit)),(availableHeight-count)/2);
        int gap=Math.min(Math.max(1,Math.round(3*unit)),Math.max(0,(availableHeight-2*pad-count)/Math.max(1,count-1)));
        int icon=Math.max(1,Math.min(Math.max(1,Math.round(16*unit)),(availableHeight-2*pad-(count-1)*gap)/count));
        int height=count*icon+(count-1)*gap+2*pad;
        rail(g,right-icon-2*pad,bottom-height,icon,pad,gap,count,health);
    }

    public static void vertical(GuiGraphics graphics, int x, int y, int health, int maximum) {
        vertical(graphics,x,y,health,maximum,Math.max(9,maximum*11));
    }
    public static void vertical(GuiGraphics graphics,int x,int y,int health,int maximum,int availableHeight){
        // Keep rendering bounded even while loading incomplete or future data.
        int count = Math.clamp(maximum,0,20);
        if(count==0 || availableHeight<count)return;
        int pad=Math.min(2,(availableHeight-count)/2);
        int gap=Math.min(2,Math.max(0,(availableHeight-2*pad-count)/Math.max(1,count-1)));
        int icon=Math.max(1,Math.min(9,(availableHeight-2*pad-(count-1)*gap)/count));
        rail(graphics,x-pad,y,icon,pad,gap,count,health);
    }

    private static void rail(GuiGraphics g,int x,int y,int icon,int pad,int gap,int count,int health){
        int width=icon+2*pad,height=count*icon+(count-1)*gap+2*pad;
        int right=x+width,bottom=y+height;
        g.flush();
        // Opaque backing separates the HP column from every portrait, regardless of its colours.
        g.fill(x,y,right,bottom,0xff241a12);
        g.renderOutline(x,y,width,height,0xffa47d43);
        if(width>4 && height>4){
            g.hLine(x+1,right-2,y+1,0xffcfad70);
            g.vLine(right-2,y+1,bottom-2,0xff654723);
        }
        for(int i=0;i<count;i++){
            int sy=y+pad+i*(icon+gap);
            g.fill(x+pad,sy,x+pad+icon,sy+icon,0xff3c2b1a);
        }
        g.flush();
        int filled=Math.clamp(health,0,count);
        for(int i=0;i<count;i++){
            int sy=y+pad+i*(icon+gap);
            g.blitSprite(EMPTY,x+pad,sy,icon,icon);
            if(i>=count-filled)g.blitSprite(HALF,x+pad,sy,icon,icon);
        }
    }
}
