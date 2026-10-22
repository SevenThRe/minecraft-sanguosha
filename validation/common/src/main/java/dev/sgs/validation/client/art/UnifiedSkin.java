package dev.sgs.validation.client.art;

import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.client.InkText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Shared native materials for the approved block-built Three Kingdoms screens. */
public final class UnifiedSkin {
    public static final int CREAM=0xfff2ddb0, INK=0xff382416, GOLD=0xffd5ab62;
    public static final ResourceLocation WOOD=id("wood_panel"), PAPER=id("paper_button"), DARK=id("dark_button");
    public static final ResourceLocation RECRUIT=id("recruit_courtyard"), SHOP=id("shop_hall"), TALLY=id("recruit_tally"), TITLE=id("lobby_title");
    private UnifiedSkin(){}
    private static ResourceLocation id(String name){return ProbeMod.id("textures/gui/unified/"+name+".png");}
    public static void panel(GuiGraphics g,ResourceLocation texture,int x,int y,int w,int h){
        g.flush();
        BoardSkin.nine(g,texture,x,y,w,h,384,64,24);
    }
    public static void label(GuiGraphics g,Font font,String text,int x,int y,int w,int h,int color){
        InkText.fitted(g,font,text,x+w/2f,y,w,h,color);
    }
    public static void art(GuiGraphics g,ResourceLocation texture,int x,int y,int w,int h){
        g.flush();
        // Uses the real PNG dimensions and preserves aspect ratio, including external art.
        HeroRenderer.portraitCrop(g,texture,x,y,w,h);
    }
    public static void header(GuiGraphics g,Font font,String title,int width,long tokens,long souls){
        panel(g,WOOD,0,0,width,42);
        label(g,font,title,65,10,150,22,CREAM);
        if(tokens>=0){
            art(g,TALLY,width-184,8,18,26);
            label(g,font,Long.toString(tokens),width-160,12,52,18,CREAM);
            var icons=ProbeMod.id("textures/gui/collection/currency_icons.png");
            g.blit(icons,width-98,8,25,25,724,0,724,724,2172,724);
            label(g,font,Long.toString(souls),width-71,12,62,18,CREAM);
        }
    }
}
