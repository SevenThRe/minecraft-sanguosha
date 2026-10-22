package dev.sgs.validation.client.art;

import com.mojang.math.Axis;
import dev.sgs.validation.client.layout.ChainMotion;
import net.minecraft.client.gui.GuiGraphics;

/** Hollow pixel iron links and two shallow draped strands. */
public final class ChainRenderer {
    public static final long WRAP_MILLIS=ChainMotion.WRAP_MILLIS, RELEASE_MILLIS=ChainMotion.RELEASE_MILLIS;
    private ChainRenderer() {}
    public static void drawWrapping(GuiGraphics g,int x,int y,int width,int height,long elapsed){drawState(g,x,y,width,elapsed,false);}
    public static void drawReleasing(GuiGraphics g,int x,int y,int width,long elapsed){drawState(g,x,y,width,elapsed,true);}
    public static void draw(GuiGraphics g,int x,int y,int width){drawState(g,x,y,width,WRAP_MILLIS,false);}
    private static int color(int rgb,float opacity){return (Math.round(255*opacity)<<24)|(rgb&0xffffff);}
    private static void drawState(GuiGraphics g,int x,int y,int width,long elapsed,boolean releasing){
        if(width<8)return;
        var m=ChainMotion.at(elapsed,releasing);if(m.opacity()<=0)return;
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(width/150f,width/150f,1);
        strand(g,m,true);strand(g,m,false);
        if(m.clasp()>0){
            float opening=releasing?m.split()*9:6*(1-m.clasp());
            g.pose().pushPose();g.pose().translate(75,6+m.drop(),0);
            for(int side:new int[]{-1,1}){
                g.pose().pushPose();g.pose().translate(side*opening,0,0);
                int left=side<0?-8:0,right=side<0?0:8;
                g.fill(left,-5,right,6,color(0x171c1d,m.opacity()));
                g.fill(left+1,-4,right-1,5,color(0x8b9184,m.opacity()));
                g.fill(left+2,-2,right-2,3,color(0x414d4d,m.opacity()));
                g.hLine(left+1,right-2,-4,color(0xd2c9a6,m.opacity()));
                g.fill(left+3,0,left+4,1,color(0xe0d3ad,m.opacity()));g.pose().popPose();
            }
            if(!releasing)g.fill(-1,-3,1,4,color(0xbba369,m.opacity()*m.clasp()));
            g.pose().popPose();
        }
        if(m.flash()>0){
            float f=m.flash();int c=color(0xffdfa0,f);g.hLine(67,83,6,c);g.vLine(75,-2,14,c);
            for(int i=0;i<4;i++){
                double a=Math.PI*.25+i*Math.PI*.5;
                int px=75+(int)(Math.cos(a)*(9+(1-f)*12)),py=6+(int)(Math.sin(a)*(5+(1-f)*7));
                g.fill(px,py,px+2,py+2,c);
            }
        }
        g.pose().popPose();
    }
    private static void strand(GuiGraphics g,ChainMotion.Frame m,boolean rear){
        for(int i=0;i<23;i++){
            float t=i/22f,travel=rear?1-t:t;if(travel>m.reveal())continue;
            float px=5+t*140+(t<.5f?-1:1)*m.split()*10;
            float py=(rear?-5:0)+(float)Math.sin(Math.PI*t)*(rear?5:12)+m.drop();
            float slope=(float)(Math.cos(Math.PI*t)*Math.PI*(rear?5:12)/140);
            g.pose().pushPose();g.pose().translate(px,py,0);g.pose().mulPose(Axis.ZP.rotation((float)Math.atan(slope)));
            link(g,i%2==0,rear,m.opacity());g.pose().popPose();
        }
    }
    private static void link(GuiGraphics g,boolean face,boolean rear,float opacity){
        int h=face?3:2,dark=color(0x151b1e,opacity),mid=color(rear?0x64716e:0x87958e,opacity);
        int light=color(rear?0xaaa990:0xd9d3b6,opacity),shade=color(0x394644,opacity);
        g.hLine(-3,3,-h-1,dark);g.hLine(-3,3,h+1,dark);
        g.vLine(-5,-h+1,h-1,dark);g.vLine(5,-h+1,h-1,dark);
        g.hLine(-3,3,-h,light);g.hLine(-3,3,h,shade);
        g.vLine(-4,-h+1,h-1,mid);g.vLine(4,-h+1,h-1,mid);
        g.fill(-4,-h,-2,-h+1,mid);g.fill(3,h-1,5,h,shade);
        if(!face)g.hLine(-2,2,0,mid);
    }
}
