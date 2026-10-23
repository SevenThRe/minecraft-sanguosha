package dev.sgs.validation.client.widget;

import static dev.sgs.validation.client.ClientText.tr;
import dev.sgs.validation.client.InkText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Borderless takeover control, with the same native focus and activation semantics as other buttons. */
public final class AutoplayCancelButton extends SgsButton {
    public AutoplayCancelButton(int x,int y,int width,int height,OnPress action){
        super(x,y,width,height,Component.literal(tr("sgs.ui.stop_autoplay_3cc51234")),action);
    }
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
        var font=Minecraft.getInstance().font;float h=getHeight()*.55f;
        float cx=getX()+getWidth()/2f,top=getY()+(getHeight()-h)/2f;
        float offset=1f/InkText.pixels();
        g.pose().pushPose();g.pose().translate(0,0,215);
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(dx!=0||dy!=0)
            InkText.fitted(g,font,tr("sgs.ui.stop_autoplay_3cc51234"),cx+dx*offset,top+dy*offset,getWidth()-8,h,0xff75451d);
        InkText.fitted(g,font,tr("sgs.ui.stop_autoplay_3cc51234"),cx,top,getWidth()-8,h,isHoveredOrFocused()?0xffffedbb:0xffffe3a7);
        if(isFocused())g.hLine(getX()+getWidth()/4,getX()+getWidth()*3/4,getY()+getHeight()-2,0xffe9bd70);
        g.pose().popPose();
    }
}
