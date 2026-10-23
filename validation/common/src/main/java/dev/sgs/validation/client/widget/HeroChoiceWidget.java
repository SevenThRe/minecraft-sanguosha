package dev.sgs.validation.client.widget;

import static dev.sgs.validation.client.ClientText.tr;
import dev.sgs.core.addon.HeroPresentation;
import dev.sgs.validation.client.InkText;
import dev.sgs.validation.client.art.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** The same portrait card is used for candidates and the confirmed selection preview. */
public final class HeroChoiceWidget extends SgsButton {
    private final HeroPresentation hero;private final boolean chosen;private final String chosenLabel;
    public HeroChoiceWidget(HeroPresentation hero,int x,int y,int width,int height,boolean chosen,boolean available,OnPress action){
        this(hero,x,y,width,height,chosen,available,action,tr("sgs.ui.confirmed_cc4d48b2"));
    }
    public HeroChoiceWidget(HeroPresentation hero,int x,int y,int width,int height,boolean chosen,boolean available,OnPress action,String chosenLabel){
        super(x,y,width,height,Component.literal(dev.sgs.validation.client.ClientText.heroName(hero.id(),hero.name())),action);this.hero=dev.sgs.validation.client.ClientText.hero(hero);this.chosen=chosen;this.chosenLabel=chosenLabel;active=available;
    }
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
        drawCard(g,hero,getX(),getY(),getWidth(),getHeight(),chosen,isHoveredOrFocused(),chosenLabel);
        if(isHoveredOrFocused()){
            var font=Minecraft.getInstance().font;int w=Math.min(280,Math.max(160,g.guiWidth()-20));
            String description=hero.skills().stream().map(s->s.name()+(s.lordOnly()?tr("sgs.ui.lord_skill_bd748281"):"")+"："+s.description()).collect(java.util.stream.Collectors.joining("\n"));
            var lines=font.split(Component.literal(description),w-16);int h=24+lines.size()*10;
            float fit=Math.min(1f,Math.min((g.guiWidth()-16f)/w,(g.guiHeight()-16f)/Math.max(1,h)));
            int actualW=(int)Math.ceil(w*fit),actualH=(int)Math.ceil(h*fit);
            int left=Math.clamp(mx+8,8,Math.max(8,g.guiWidth()-actualW-8)),top=Math.clamp(my-actualH-5,8,Math.max(8,g.guiHeight()-actualH-8));
            g.pose().pushPose();g.pose().translate(left,top,500);g.pose().scale(fit,fit,1);
            g.fill(-1,-1,w+1,h+1,0xffa98049);g.fill(0,0,w,h,0xffecd6ad);
            InkText.draw(g,font,hero.name(),8,6,0xff4c301e);int y=22;
            for(var line:lines){g.drawString(font,line,8,y,0xff443426,false);y+=10;}g.pose().popPose();
        }
    }
    public static void drawCard(GuiGraphics g,HeroPresentation hero,int x,int y,int w,int h,boolean chosen,boolean hovered){
        drawCard(g,hero,x,y,w,h,chosen,hovered,tr("sgs.ui.confirmed_cc4d48b2"));
    }
    private static void drawCard(GuiGraphics g,HeroPresentation hero,int x,int y,int w,int h,boolean chosen,boolean hovered,String chosenLabel){
        hero=dev.sgs.validation.client.ClientText.hero(hero);
        var font=Minecraft.getInstance().font;
        var classic=HeroRenderer.classic(hero.id());
        if(!hero.portrait().isEmpty()){
            var texture=net.minecraft.resources.ResourceLocation.parse(hero.portrait());
            if(Minecraft.getInstance().getResourceManager().getResource(texture).isPresent())classic=texture;
        }
        HeroRenderer.portrait(g,font,hero.id(),x+2,y+2,w-4,h-4,hovered?0xffefcd78:0xffa27b44,true,classic);
        BoardSkin.nine(g,dev.sgs.validation.ProbeMod.id("textures/gui/panels/hero_frame_v1.png"),x-1,y-1,w+2,h+2,320,448,64);
        int footer=Math.max(12,Math.round(h*.16f));g.fill(x+3,y+h-footer,x+w-3,y+h-3,0xea38271c);
        int strip=Math.max(8,Math.round(w*.13f));g.fill(x+3,y+4,x+strip+3,y+h-footer,0xe23a3430);
        var characters=hero.name().codePoints().mapToObj(cp->new String(Character.toChars(cp))).toList();
        float line=Math.min(strip,Math.max(4,(h-footer-12f)/Math.max(1,characters.size())));
        if(!dev.sgs.validation.client.ClientText.english())for(int i=0;i<characters.size();i++)InkText.fitted(g,font,characters.get(i),x+3+strip/2f,y+10+i*line,strip-1,line,0xfff5e2bd);
        InkText.fitted(g,font,hero.name(),x+w/2f,y+h-footer+2,w-10,footer-5,0xfff3d9a6);
        if(hovered)g.renderOutline(x-2,y-2,w+4,h+4,0xffffd173);
        if(chosen){int mid=y+h/2;g.fill(x+4,mid-9,x+w-4,mid+10,0xee5b3a22);InkText.fitted(g,font,chosenLabel,x+w/2f,mid-6,w-12,13,0xffffdf9c);}
    }
}
