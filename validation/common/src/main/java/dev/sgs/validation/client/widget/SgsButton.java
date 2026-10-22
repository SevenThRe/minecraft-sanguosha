package dev.sgs.validation.client.widget;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Shared parchment/bronze control. Uses vanilla input accessibility, never vanilla button sprites. */
public class SgsButton extends Button {
    private long pressedUntil;
    private boolean engaged;

    protected SgsButton(int x,int y,int width,int height,Component text,OnPress action){
        super(x,y,width,height,text,action,DEFAULT_NARRATION);
    }
    public static ControlBuilder make(Component text,OnPress action){return new ControlBuilder(text,action);}
    public static final class ControlBuilder {
        private final Component text;private final OnPress action;
        private int x,y,width=100,height=20;
        private ControlBuilder(Component text,OnPress action){this.text=text;this.action=action;}
        public ControlBuilder bounds(int x,int y,int width,int height){this.x=x;this.y=y;this.width=width;this.height=height;return this;}
        public SgsButton build(){return new SgsButton(x,y,width,height,text,action);}
    }
    protected void engaged(boolean value){engaged=value;}
    protected boolean parchment(){return true;}
    @Override public void onPress(){if(active){pressedUntil=Util.getMillis()+120;super.onPress();}}
    protected void frame(GuiGraphics g){
        int x=getX(),y=getY(),w=getWidth(),h=getHeight();
        boolean hover=isHoveredOrFocused() && active;
        if(parchment()){
            g.fill(x+1,y+h,x+w,y+h+1,0x80301a0c);
            dev.sgs.validation.client.art.BoardSkin.nine(g,dev.sgs.validation.client.art.BoardSkin.PAPER,x,y,w,h,192,64,12);
            if(!active)g.fill(x+2,y+2,x+w-2,y+h-2,0x70503825);
            else if(hover || engaged)g.renderOutline(x,y,w,h,0xffffd883);
            return;
        }
        int edge=!active?0xff66543b:engaged?0xffefc56e:hover?0xffedc981:0xffaa8047;
        int body=!active?0xff5b4b36:engaged?0xff78502d:hover?0xff63452b:0xff3e2d21;
        g.fill(x+2,y+h,x+w,y+h+2,0x650e0906);
        g.fill(x+2,y,x+w-2,y+h,edge);g.fill(x,y+2,x+w,y+h-2,edge);
        g.fill(x+2,y+2,x+w-2,y+h-2,body);
        g.hLine(x+4,x+w-5,y+3,!active?0xff746044:0xff9d794a);
        g.hLine(x+4,x+w-5,y+h-4,0xff241b14);
        if(hover || engaged){
            g.fill(x+3,y+h/2-1,x+5,y+h/2+1,edge);
            g.fill(x+w-5,y+h/2-1,x+w-3,y+h/2+1,edge);
        }
    }
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
        frame(g);var font=Minecraft.getInstance().font;
        String text=getMessage().getString();
        int down=Util.getMillis()<pressedUntil?1:0;
        float lineHeight=getHeight()*.62f;
        dev.sgs.validation.client.InkText.fitted(g,font,text,getX()+getWidth()/2f,
            getY()+(getHeight()-lineHeight)/2f+down,getWidth()-8f,lineHeight,
            active?(parchment()?0xff3b2517:0xffefd3a1):0xff65523e);
    }
}
