package dev.sgs.validation.client;
import dev.sgs.core.social.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
public final class GiftSprites {
    public static void draw(GuiGraphics g,String kind,int x,int y,int size){draw(g,new GiftCatalog(java.util.List.of()).resolve(kind,kind.equals("TULIP")?10:1),x,y,size,0);}
    public static boolean available(GiftDefinition gift){return Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.parse(gift.texture())).isPresent();}
    public static void draw(GuiGraphics g,GiftDefinition gift,int x,int y,int size,long elapsed){
        int dw=Math.max(1,Math.round(size*gift.width()/(float)Math.max(gift.width(),gift.height()))),dh=Math.max(1,Math.round(size*gift.height()/(float)Math.max(gift.width(),gift.height())));
        int frame=(int)(Math.max(0,elapsed)/gift.frameMillis()%gift.frames());
        g.blit(ResourceLocation.parse(gift.texture()),x+(size-dw)/2,y+(size-dh)/2,dw,dh,gift.u()+frame*gift.width(),gift.v(),gift.width(),gift.height(),gift.textureWidth(),gift.textureHeight());
    }
}
