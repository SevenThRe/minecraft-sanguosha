package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Shared readable Noto Sans SC Medium. Logical em stays 10; oversampling preserves small glyph detail. */
public final class InkText {
    public static final float EM = 10f;
    // Bundled Noto Sans SC Medium at 10px / 8x oversampling: typical CJK ink
    // occupies y=-1.5..8 relative to Minecraft's draw origin (baseline 7).
    // Center the visible ink (3.25), not the nominal em box (5).
    private static final float INK_CENTER_Y = 3.25f;
    private static float centeredTop(float top,float height,float scale){
        return snap(top+height/2-INK_CENTER_Y*scale);
    }
    public static float pixels() {
        return (float)net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
    }
    public static float snap(float value) { float p=pixels();return Math.round(value*p)/p; }
    /** Integer physical glyph heights and aligned baselines, independent of board zoom. */
    public static void label(GuiGraphics g,Font font,String text,float centerX,float top,float width,float height,int color){
        float p=pixels();
        int px=Math.max(1,Math.min(18,(int)Math.floor(height*p)));
        // Stable size steps avoid changing the raster on every window resize.
        if(px>=16)px=16;else if(px>=14)px=14;else if(px>=12)px=12;
        float scale=px/(EM*p);
        String value=text;
        if(width(font,value)*scale>width){
            while(!value.isEmpty() && width(font,value+"…")*scale>width)
                value=value.substring(0,value.offsetByCodePoints(value.length(),-1));
            value=value.isEmpty()?"":value+"…";
        }
        float left=snap(centerX-width(font,value)*scale/2);
        g.pose().pushPose();g.pose().translate(left,centeredTop(top,height,scale),0);
        g.pose().scale(scale,scale,1);draw(g,font,value,0,0,color);g.pose().popPose();
    }
    private InkText() {}
    public static Component component(String text) {
        return Component.literal(text).withStyle(style -> style.withFont(ProbeMod.id("ink")));
    }
    public static int width(Font font, String text) { return font.width(component(text)); }
    public static int draw(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        return graphics.drawString(font, component(text), x, y, color, false);
    }
    public static int drawCentered(GuiGraphics graphics, Font font, String text, int centerX, int y, int color) {
        var styled=component(text);
        return graphics.drawString(font,styled,centerX-font.width(styled)/2,y,color,false);
    }
    /** One fitted label path for parchment buttons and embedded skill names. */
    public static void fitted(GuiGraphics graphics,Font font,String text,float centerX,float top,
                              float availableWidth,float lineHeight,int color){
        float p=pixels();
        float scale=Math.max(1,(float)Math.floor(Math.min(lineHeight/EM,Math.max(1,availableWidth)/Math.max(1,width(font,text)))*EM*p))/(EM*p);
        graphics.pose().pushPose();
        graphics.pose().translate(snap(centerX-width(font,text)*scale/2),centeredTop(top,lineHeight,scale),0);
        graphics.pose().scale(scale,scale,1);
        draw(graphics,font,text,0,0,color);
        graphics.pose().popPose();
    }
}
