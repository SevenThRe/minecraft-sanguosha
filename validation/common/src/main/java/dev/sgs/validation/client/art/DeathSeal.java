package dev.sgs.validation.client.art;

import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.client.InkText;
import dev.sgs.validation.client.layout.DeathSealLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Cinnabar pixel stamp over the desaturated portrait, with native readable glyphs. */
public final class DeathSeal {
    private DeathSeal() {}
    public static void draw(GuiGraphics g,Font font,String label,DeathSealLayout.Box box) {
        if(label.isEmpty())return;
        int x=box.x(),y=box.y(),s=box.size();
        g.fill(x+s/8,y+s/8,x+s-s/8,y+s-s/8,0xd02b201b);
        g.blit(ProbeMod.id("textures/gui/identity/death_border_v2.png"),x,y,s,s,0,0,1254,1254,1254,1254);
        if(label.length()>2) {
            InkText.fitted(g,font,label.substring(0,2),x+s/2f,y+s*.22f,s*.65f,s*.25f,0xffffaa81);
            InkText.fitted(g,font,"阵亡",x+s/2f,y+s*.51f,s*.65f,s*.25f,0xffffaa81);
        } else InkText.fitted(g,font,label,x+s/2f,y+s*.34f,s*.68f,s*.32f,0xffffaa81);
    }
}
