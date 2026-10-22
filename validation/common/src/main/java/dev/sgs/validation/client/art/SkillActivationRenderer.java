package dev.sgs.validation.client.art;

import dev.sgs.validation.client.InkText;
import dev.sgs.validation.client.layout.ReferenceBattleLayout.Box;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Procedural ink disk, converging glyphs and fading light ring, measured from the reference clip. */
public final class SkillActivationRenderer {
    private SkillActivationRenderer(){}
    public static void draw(GuiGraphics g,Font font,String label,Box box,long elapsed,int tint){
        if(elapsed<0 || elapsed>=2200)return;
        float fade=Math.clamp((2200-elapsed)/650f,0,1),inkFade=fade*Math.clamp((1700-elapsed)/500f,0,1);
        int cx=box.x()+box.width()/2,cy=box.y()+box.height()/2,r=box.width()/2;
        float growth=.65f+.35f*Math.clamp(elapsed/300f,0,1);
        for(int dy=-r;dy<=r;dy++){
            int span=Math.round((float)Math.sqrt(Math.max(0,r*r-dy*dy))*growth);
            int rough=(int)(Math.sin(dy*2.1)*r*.035);
            int alpha=Math.round(225*inkFade);
            g.fill(cx-span+rough,cy+dy,cx+span+rough+1,cy+dy+1,(alpha<<24)|0x0b1310);
        }
        if(elapsed<850){
            int rr=Math.round(r*(.5f+.45f*Math.clamp(elapsed/330f,0,1)));
            for(int a=0;a<360;a+=5){
                if((a+(int)(elapsed/5))%100>73)continue;
                double angle=Math.toRadians(a+elapsed*.08);
                int px=cx+(int)(Math.cos(angle)*rr),py=cy+(int)(Math.sin(angle)*rr);
                int alpha=Math.round(210*fade*Math.clamp((850-elapsed)/400f,0,1));
                int dot=Math.max(1,r/18);g.fill(px,py,px+dot,py+dot,(alpha<<24)|(tint&0xffffff));
            }
        }
        int textH=Math.max(10,Math.round(box.height()*.30f));
        if(elapsed<280 && label.length()==2){
            float shift=box.width()*.42f*(1-elapsed/280f);
            InkText.fitted(g,font,label.substring(0,1),cx-shift,cy-textH/2f,box.width()/2f,textH,((int)(255*fade)<<24)|0xffefd1);
            InkText.fitted(g,font,label.substring(1),cx+shift,cy-textH/2f,box.width()/2f,textH,((int)(255*fade)<<24)|0xffefd1);
        }else InkText.fitted(g,font,label,cx,cy-textH/2f,box.width()*.94f,textH,((int)(255*fade)<<24)|0xffefd1);
    }
}
