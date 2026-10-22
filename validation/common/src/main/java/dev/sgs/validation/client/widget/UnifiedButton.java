package dev.sgs.validation.client.widget;

import dev.sgs.validation.client.InkText;
import dev.sgs.validation.client.art.UnifiedSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Native narration, focus and hit-testing with the approved parchment/wood materials. */
public final class UnifiedButton extends Button {
    private final boolean primary;
    public UnifiedButton(int x,int y,int w,int h,String text,boolean primary,Runnable action){
        super(x,y,w,h,Component.literal(text),b->action.run(),DEFAULT_NARRATION);
        this.primary=primary;
    }
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){
        UnifiedSkin.panel(g,primary?UnifiedSkin.PAPER:UnifiedSkin.DARK,getX(),getY(),getWidth(),getHeight());
        if(!active)g.fill(getX()+3,getY()+3,getX()+getWidth()-3,getY()+getHeight()-3,0x77463824);
        if(active && isHoveredOrFocused())g.renderOutline(getX()+1,getY()+1,getWidth()-2,getHeight()-2,UnifiedSkin.GOLD);
        float line=getHeight()*.48f;
        InkText.fitted(g,Minecraft.getInstance().font,getMessage().getString(),getX()+getWidth()/2f,
            getY()+(getHeight()-line)/2,getWidth()*.85f,line,
            primary?(active?UnifiedSkin.INK:0xff756451):UnifiedSkin.CREAM);
    }
}
