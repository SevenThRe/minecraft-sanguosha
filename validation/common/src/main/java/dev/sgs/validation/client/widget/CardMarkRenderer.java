package dev.sgs.validation.client.widget;
import dev.sgs.core.card.CardSkillMark;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
/** Shared ribbon rendering for hand cards, public cards and authorised previews. */
public final class CardMarkRenderer {
    public static void draw(GuiGraphics g,Font font,List<CardSkillMark> marks,int x,int y,int w,int h){
        if(marks==null || marks.isEmpty())return;
        int row=Math.max(7,Math.round(h*.09f)),top=y+Math.round(h*.60f);
        for(int i=0;i<Math.min(3,marks.size());i++){
            var mark=marks.get(i);int yy=top+i*row;
            g.fill(x+1,yy,x+w-1,yy+row,0xf0e7d4af);g.hLine(x+1,x+w-2,yy,0xffa78351);
            String text=font.plainSubstrByWidth(mark.text(),Math.max(1,w-8));
            float scale=Math.min(1,(row-1)/9f);g.pose().pushPose();g.pose().translate(x+w/2f,yy+1,0);g.pose().scale(scale,scale,1);
            g.drawString(font,text,-font.width(text)/2,0,mark.color()|0xff000000,false);g.pose().popPose();
        }
    }
}
