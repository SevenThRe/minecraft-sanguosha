package dev.sgs.validation.client.art;

import dev.sgs.validation.client.InkText;
import dev.sgs.validation.client.layout.ChainMotion;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Reference-video chain: one horizontal bronze strand and a glowing red-gold lock seal. */
public final class ChainRenderer {
    public static final long WRAP_MILLIS=ChainMotion.WRAP_MILLIS, RELEASE_MILLIS=ChainMotion.RELEASE_MILLIS;
    private static final int SEAL_X=94;
    private ChainRenderer() {}
    public static void drawWrapping(GuiGraphics g,Font font,int x,int y,int width,int height,long elapsed){drawState(g,font,x,y,width,elapsed,false);}
    public static void drawReleasing(GuiGraphics g,Font font,int x,int y,int width,long elapsed){drawState(g,font,x,y,width,elapsed,true);}
    public static void draw(GuiGraphics g,Font font,int x,int y,int width){drawState(g,font,x,y,width,WRAP_MILLIS,false);}
    private static int color(int rgb,float opacity){return (Math.round(255*Math.max(0,Math.min(1,opacity)))<<24)|(rgb&0xffffff);}
    private static void drawState(GuiGraphics g,Font font,int x,int y,int width,long elapsed,boolean releasing){
        if(width<8)return;
        var m=ChainMotion.at(elapsed,releasing);if(m.opacity()<=0)return;
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(width/150f,width/100f,1);
        // Links remain level across the chest. Alternating face/edge links give a single interlocked strand.
        for(int i=0;i<21;i++){
            float px=4+i*7.1f;
            float travel=px<SEAL_X?px/SEAL_X:(150-px)/(150-SEAL_X);
            if(travel>m.reveal())continue;
            float direction=px<SEAL_X?-1:1;
            g.pose().pushPose();g.pose().translate(px+direction*m.split()*13,m.drop(),0);
            link(g,i%2==0,m.opacity(),m.flash());g.pose().popPose();
        }
        if(m.clasp()>0){
            float opacity=m.opacity()*m.clasp();
            g.pose().pushPose();g.pose().translate(SEAL_X,m.drop(),1);
            // Small diamond, not a padlock icon: the reference uses the actual Chinese lock character.
            for(int row=-9;row<=9;row++){
                int half=10-Math.abs(row);
                g.fill(-half,row,half+1,row+1,color(0x553218,opacity));
                if(half>2)g.fill(-half+2,row,half-1,row+1,color(0x9f431b,opacity));
            }
            g.pose().pushPose();g.pose().scale(1.7f,1.7f,1);
            InkText.drawCentered(g,font,"锁",0,-3,color(0xffe7a0,opacity));
            g.pose().popPose();
            g.pose().popPose();
        }
        if(m.flash()>0){
            int c=color(0xffe7a0,m.flash()*.75f);
            g.hLine(1,148,-1,c);g.hLine(SEAL_X-10,SEAL_X+10,0,c);
            g.vLine(SEAL_X,-11,11,c);
        }
        g.pose().popPose();
    }
    private static void link(GuiGraphics g,boolean face,float opacity,float flash){
        int dark=color(0x3b2917,opacity),mid=color(0x98713c,opacity);
        int light=color(flash>.1f?0xffe2a0:0xd4ae68,opacity),shade=color(0x654521,opacity);
        if(face){
            // Hollow elongated links with bevel highlights, matching the brass rectangular loops in the video.
            g.hLine(-5,5,-4,dark);g.hLine(-5,5,4,dark);
            g.vLine(-7,-2,2,dark);g.vLine(7,-2,2,dark);
            g.hLine(-5,5,-3,light);g.hLine(-5,5,3,shade);
            g.hLine(-4,4,-2,mid);g.hLine(-4,4,2,mid);
            g.vLine(-6,-2,2,mid);g.vLine(6,-2,2,mid);
            g.fill(-6,-3,-4,-1,light);g.fill(5,1,7,3,shade);
        }else{
            g.fill(-7,-2,8,3,dark);g.fill(-6,-1,7,2,mid);
            g.hLine(-5,5,-1,light);g.hLine(-5,5,2,shade);
        }
    }
}
