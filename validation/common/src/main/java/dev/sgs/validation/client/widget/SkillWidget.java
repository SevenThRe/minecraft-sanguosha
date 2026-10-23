package dev.sgs.validation.client.widget;

import static dev.sgs.validation.client.ClientText.tr;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** One skill component: presentation data + availability + a server-intent callback. No rules here. */
public final class SkillWidget extends SgsButton {
    public enum Kind { ACTIVE, TOGGLE, PASSIVE, LIMITED, TRIGGERED }
    public record Spec(String id,String name,String description,Kind kind) {}
    private final Spec spec;
    private final boolean selected;
    private String unavailableReason="";
    public SkillWidget(Spec spec,int x,int y,int width,boolean available,boolean selected,OnPress action){
        super(x,y,width,22,Component.literal(spec.name()),action);
        this.spec=spec;this.selected=selected;
        active=available && spec.kind()!=Kind.PASSIVE;engaged(selected);
    }
    public void disableWithReason(String reason){active=false;unavailableReason=reason==null?"":reason;}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==1 && Minecraft.getInstance().screen instanceof dev.sgs.validation.client.MatchScreen screen && screen.optionalSkillSettingsAvailable() && visible && x>=getX() && x<getX()+getWidth() && y>=getY() && y<getY()+getHeight() && dev.sgs.validation.client.OptionalSkillPreferences.supports(spec.id())){dev.sgs.validation.client.OptionalSkillPreferences.toggle(spec.id());return true;}
        return super.mouseClicked(x,y,button);
    }
    private String status(){if(dev.sgs.validation.client.OptionalSkillPreferences.supports(spec.id()))return tr(dev.sgs.validation.client.OptionalSkillPreferences.enabled(spec.id())?"sgs.ui.skill_auto_on":"sgs.ui.skill_auto_off");if(!unavailableReason.isEmpty())return unavailableReason;return switch(spec.kind()){
        case PASSIVE->tr("sgs.ui.locked_automatic_22889e77");
        case TOGGLE->!active?tr("sgs.ui.cannot_activate_now_67a7b7c8"):selected?tr("sgs.ui.conversion_enabled_6fb06139"):tr("sgs.ui.click_to_enable_conversion_ea8066df");
        case LIMITED->active?tr("sgs.ui.limited_available_7d7a6d0a"):tr("sgs.ui.limited_unavailable_a3319507");
        case ACTIVE->active?tr("sgs.ui.click_to_activate_5ce1ae91"):tr("sgs.ui.cannot_activate_now_67a7b7c8");
        case TRIGGERED->active?tr("sgs.ui.optional_activation_358e2c4a"):tr("sgs.ui.prompts_when_conditions_are_met_04111543");
    };}
    @Override protected boolean parchment(){return false;}
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
        var font=Minecraft.getInstance().font;
        int x=getX(),y=getY(),w=getWidth(),h=getHeight();
        boolean hover=isHoveredOrFocused();int cut=Math.max(1,Math.min(3,h/5));
        int edge=selected?0xffffd37d:hover?0xffd8bc80:0xff8f794f;
        int body=selected?0xff65713b:active?0xff485b32:0xff48443d;
        int light=active?0xff859464:0xff777165;
        // Shallow stepped tabs: the reference uses quiet grey/green skill tiles, not bulky buttons.
        g.fill(x+cut,y+h,x+w-cut,y+h+1,0x88150f09);
        g.fill(x+cut,y,x+w-cut,y+h,edge);g.fill(x,y+cut,x+w,y+h-cut,edge);
        g.fill(x+cut,y+1,x+w-cut,y+h-1,body);g.fill(x+1,y+cut,x+w-1,y+h-cut,body);
        g.hLine(x+cut+1,x+w-cut-2,y+1,light);
        if(selected)g.hLine(x+cut+1,x+w-cut-2,y+h-2,0xffffd37d);
        if(spec.kind()==Kind.LIMITED)g.fill(x+w-cut-3,y+2,x+w-cut-1,y+4,0xffb7674d);
        int nameColor=selected?0xffffedbd:active || spec.kind()==Kind.PASSIVE?0xfff5e8ce:0xffc6c0b3;
        float padding=2/dev.sgs.validation.client.InkText.pixels();
        dev.sgs.validation.client.InkText.label(g,font,spec.name(),x+w/2f,
            y+padding,w-padding*2,h-padding*2,nameColor);
        if(isHoveredOrFocused() && g.guiWidth()>=36 && g.guiHeight()>=40){
            int panelWidth=Math.min(188,g.guiWidth()-16);
            var lines=font.split(Component.literal(spec.description()),panelWidth-18);
            int panelHeight=38+lines.size()*10;
            int left=Math.clamp(x-panelWidth+getWidth(),8,g.guiWidth()-panelWidth-8);
            int top=Math.clamp(y-panelHeight-5,8,Math.max(8,g.guiHeight()-panelHeight-8));
            g.pose().pushPose();g.pose().translate(0,0,400);
            g.fill(left+3,top+3,left+panelWidth+3,top+panelHeight+3,0x990b0805);
            g.fill(left,top,left+panelWidth,top+panelHeight,0xffa98048);
            g.fill(left+2,top+2,left+panelWidth-2,top+panelHeight-2,0xffe9d6ad);
            g.drawString(font,spec.name(),left+9,top+7,0xff50321e,false);
            g.hLine(left+9,left+panelWidth-10,top+19,0xffb59a69);
            int row=top+23;
            for(var line:lines){g.drawString(font,line,left+9,row,0xff443426,false);row+=10;}
            g.drawString(font,status(),left+9,row+3,0xff7a5632,false);g.pose().popPose();
        }
    }
}
