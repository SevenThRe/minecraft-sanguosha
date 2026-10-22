package dev.sgs.validation.client.art;

import dev.sgs.validation.ProbeMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** One full-resolution source for every public and hidden card back. */
public final class CardBackRenderer {
    public static final ResourceLocation TEXTURE=ProbeMod.id("textures/gui/card_back_fangkuaisha_v4.png");
    private static final int SOURCE_WIDTH=1060,SOURCE_HEIGHT=1484;
    private CardBackRenderer(){}
    public static void draw(GuiGraphics g,int x,int y,int width,int height){
        g.blit(TEXTURE,x,y,width,height,0,0,SOURCE_WIDTH,SOURCE_HEIGHT,SOURCE_WIDTH,SOURCE_HEIGHT);
    }
}
