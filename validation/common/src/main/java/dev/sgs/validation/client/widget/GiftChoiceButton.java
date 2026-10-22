package dev.sgs.validation.client.widget;
import dev.sgs.core.social.GiftDefinition;
import dev.sgs.validation.client.GiftSprites;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
/** Small reference-style icon tile with a shared flower price, no paper dialog skin. */
public final class GiftChoiceButton extends SgsButton {
    private final GiftDefinition gift;private final boolean selected;
    public GiftChoiceButton(int x,int y,int size,GiftDefinition gift,boolean selected,boolean enabled,OnPress action){
        super(x,y,size,size,Component.literal(gift.name()),action);this.gift=gift;this.selected=selected;active=enabled;
        setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(gift.name()+" · "+gift.cost()+"鲜花"+(GiftSprites.available(gift)?"":" · 缺少客户端素材"))));
    }
    @Override protected boolean parchment(){return false;}
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
        int x=getX(),y=getY(),w=getWidth(),h=getHeight();
        g.fill(x,y,x+w,y+h,active?0xee302016:0xee372e25);g.renderOutline(x,y,w,h,selected?0xffffc55b:isHoveredOrFocused()?0xffd0a365:0xff745333);
        if(GiftSprites.available(gift))GiftSprites.draw(g,gift,x+3,y+2,w-6-Math.max(6,w/5),net.minecraft.Util.getMillis());
        else g.drawCenteredString(Minecraft.getInstance().font,"?",x+w/2,y+4,0xffa08a6e);
        var f=Minecraft.getInstance().font;int ty=y+h-10;
        GiftSprites.draw(g,"POPPY",x+2,ty,9);g.drawString(f,Integer.toString(gift.cost()),x+11,ty,active?0xffffe4b2:0xff9e8c74,false);
    }
}
