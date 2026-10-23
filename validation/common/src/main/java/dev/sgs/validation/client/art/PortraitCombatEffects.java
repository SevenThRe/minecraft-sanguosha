package dev.sgs.validation.client.art;

import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;

/** Geometry reconstructed from the operator's 2026-10-22 reference recording. */
public final class PortraitCombatEffects {
    private PortraitCombatEffects() {}

    private static int color(float alpha,int rgb) {
        return (Math.clamp(Math.round(alpha*255),0,255)<<24)|rgb;
    }

    /** A tapered, white-hot orange cut, travelling from upper right to lower left. */
    public static void hit(GuiGraphics g,long age,int x,int y,int w,int h) {
        if(age<0 || age>=650)return;
        float progress=Math.clamp(age/140f,0,1);
        float fade=Math.clamp((650-age)/430f,0,1);
        g.pose().pushPose();
        g.pose().translate(x+w*.56f,y+h*.48f,29);
        g.pose().mulPose(Axis.ZP.rotationDegrees(34));
        float length=h*.86f;
        // Nested tapered strokes avoid an opaque rectangle over the portrait.
        for(int layer=5;layer>=0;layer--){
            int half=Math.max(1,Math.round(w*(.012f+layer*.018f)));
            int from=Math.round(-length*.5f),to=Math.round(from+length*progress);
            for(int row=from;row<to;row++){
                float taper=(float)Math.pow(Math.max(0,1-Math.abs(row/(length*.5f))),.65);
                int radius=Math.max(1,Math.round(half*taper));
                int rgb=layer==0?0xfff9d8:layer<2?0xffc655:layer<4?0xff6a16:0xf22c08;
                g.fill(-radius,row,radius+1,row+1,color(fade*(layer==0?.95f:layer<2?.65f:.13f),rgb));
            }
        }
        g.pose().popPose();
    }

    public static void target(GuiGraphics g,long age,int x,int y,int w,int h,boolean swords) {
        float beat=(float)(.5+.5*Math.sin(age*.006));
        g.fill(x,y,x+w,y+h,color(swords?.17f:.08f,0xc92d12));
        int glow=Math.max(4,Math.round(w*.065f));
        for(int i=glow;i>=1;i--){
            float alpha=(1-i/(float)(glow+1))*(.24f+.10f*beat);
            g.renderOutline(x-i,y-i,w+2*i,h+2*i,color(alpha,0xff6508));
        }
        // Persistent selected contour contrasts with the gold decorative frame, even between pulses.
        g.renderOutline(x-3,y-3,w+6,h+6,0xff10252b);
        g.renderOutline(x-2,y-2,w+4,h+4,0xff37e9ff);
        g.renderOutline(x-1,y-1,w+2,h+2,0xff37e9ff);
        g.renderOutline(x,y,w,h,0xffe2ffff);
        // Stable deterministic sparks crawl around the four edges; no per-frame random jitter.
        int perimeter=2*(w+h);
        for(int i=0;i<16;i++){
            int p=(int)Math.floorMod(age/9+i*perimeter/16,perimeter);
            int px,py;
            if(p<w){px=x+p;py=y-3;}
            else if(p<w+h){px=x+w+3;py=y+p-w;}
            else if(p<2*w+h){px=x+2*w+h-p;py=y+h+3;}
            else{px=x-3;py=y+perimeter-p;}
            int a=color(.3f+.5f*(float)(.5+.5*Math.sin(age*.012+i)),0xffd34c);
            g.fill(px,py,px+1,py+2,a);
        }
        if(!swords)return;
        float cycle=Math.floorMod(age,1000)/1000f;
        // Crossed resting pose -> open -> inward strike -> crossed resting pose.
        float angle=cycle<.12f?42:cycle<.38f?42-112*(cycle-.12f)/.26f:
            cycle<.52f?-70+112*(cycle-.38f)/.14f:42;
        float scale=Math.min(w/100f,h/150f);
        g.pose().pushPose();
        g.pose().translate(x+w*.55f,y+h*.48f,25);
        g.pose().scale(scale,scale,1);
        sword(g,-1,angle);sword(g,1,angle);
        float flare=1-Math.abs(cycle-.53f)/.12f;
        if(flare>0){
            for(int layer=4;layer>=0;layer--){
                int radius=3+layer*4;
                g.fill(-radius,-radius,radius+1,radius+1,color(flare*(layer==0?.95f:.13f),layer==0?0xffffff:0x9897ff));
            }
            g.fill(-28,-1,29,2,color(flare,0xeeedff));
            g.fill(-1,-23,2,24,color(flare,0xffffff));
        }
        g.pose().popPose();
    }

    private static void sword(GuiGraphics g,int direction,float angle) {
        g.pose().pushPose();
        g.pose().translate(direction*12,14,0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(direction*angle));
        // Long silver blade with dark fuller, gold guard, wrapped grip and pommel.
        for(int row=-62;row<-8;row++){
            int half=Math.min(4,Math.max(1,(row+63)/3));
            g.fill(-half-1,row,half+2,row+1,0xffedf0ef);
            g.fill(-half,row,0,row+1,0xff97a4b0);
            g.fill(0,row,half+1,row+1,0xffc8d0d5);
        }
        g.fill(-1,-49,1,-12,0xff52616e);
        g.fill(-11,-9,12,-3,0xff725b36);
        g.fill(-10,-8,11,-5,0xffe3c48a);
        g.fill(-3,-3,4,16,0xff42372e);
        for(int row=0;row<15;row+=4)g.fill(-3,row,4,row+1,0xff9c8e77);
        g.fill(-5,15,6,19,0xffd2b178);
        g.pose().popPose();
    }
}
