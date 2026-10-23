package dev.sgs.validation.client.art;

import dev.sgs.validation.client.InkText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Small stepped seals; text uses the existing game font, never generated glyphs. */
public final class IdentitySeal {
    private IdentitySeal() {}

    public static String name(String label) {
        return dev.sgs.validation.client.ClientText.category(switch(label) {
            case "主" -> "主公";
            case "忠" -> "忠臣";
            case "反" -> "反贼";
            case "内" -> "内奸";
            case "友" -> "友方";
            case "敌" -> "敌方";
            default -> label;
        });
    }

    public static int ink(String label) {
        return switch(label) {
            case "主", "敌" -> 0xff8c3025;
            case "忠" -> 0xff795018;
            case "反", "友" -> 0xff385635;
            case "内" -> 0xff304e68;
            default -> 0xff554b3e;
        };
    }

    public static void draw(GuiGraphics g, Font font, String label, int x, int y, int size, boolean deduction) {
        draw(g,font,label,x,y,size,deduction,false);
    }

    /** Full role name is reserved for the large card shown when drawing an identity. */
    public static void drawCard(GuiGraphics g, Font font, String label, int x, int y, int size) {
        draw(g,font,label,x,y,size,false,true);
    }

    private static void draw(GuiGraphics g, Font font, String label, int x, int y, int size, boolean deduction, boolean fullName) {
        if(size < 4) return;
        int edge=Math.max(1,size/18);
        stepped(g,x,y,size,0xff3c2b1c);
        stepped(g,x+edge,y+edge,size-2*edge,0xffb68a42);
        stepped(g,x+2*edge,y+2*edge,size-4*edge,ink(label));
        stepped(g,x+3*edge,y+3*edge,size-6*edge,0xffead8ae);
        // A minimal highlight follows the rectilinear frame rather than a glossy bevel.
        g.fill(x+size/3,y+3*edge,x+size*2/3,y+4*edge,0xfff5e6c6);
        if(label.equals("?")) {
            int left=x+size/4, top=y+size*2/5, w=Math.max(4,size/2), h=Math.max(3,size/4);
            g.fill(left,top,left+w,top+h,ink(label));
            g.fill(left+edge,top+h,left+w-edge,top+h+edge,ink(label));
            int eye=Math.max(1,size/9);
            g.fill(left+edge,top+edge,left+edge+eye,top+edge+eye,0xffead8ae);
            g.fill(left+w-edge-eye,top+edge,left+w-edge,top+edge+eye,0xffead8ae);
        } else {
            String full=name(label);
            if(dev.sgs.validation.client.ClientText.english()){
                String english=switch(label){case "主"->"Lord";case "忠"->"Loyal";case "反"->"Rebel";case "内"->"Renegade";case "我"->"You";case "友"->"Ally";case "敌"->"Enemy";default->label;};
                InkText.fitted(g,font,english,x+size/2f,y+size*.30f,size*.80f,size*.35f,ink(label));
            } else
            // Compact portrait/menu seals use one large glyph; identity cards retain both.
            if(fullName && full.length()==2) {
                InkText.fitted(g,font,full.substring(0,1),x+size/2f,y+size*.14f,size*.58f,size*.34f,ink(label));
                InkText.fitted(g,font,full.substring(1),x+size/2f,y+size*.49f,size*.58f,size*.34f,ink(label));
            } else InkText.fitted(g,font,label,x+size/2f,y+size*.22f,size*.62f,size*.56f,ink(label));
        }
        // Local guesses remain visually distinct from publicly revealed identities.
        if(deduction)g.fill(x+size-edge*4,y+size-edge*4,x+size-edge*2,y+size-edge*2,0xffc48a35);
    }

    private static void stepped(GuiGraphics g,int x,int y,int size,int color) {
        if(size<=0)return;
        int cut=Math.max(1,size/5), step=Math.max(1,cut/2);
        g.fill(x+cut,y,x+size-cut,y+size,color);
        g.fill(x+step,y+step,x+size-step,y+size-step,color);
        g.fill(x,y+cut,x+size,y+size-cut,color);
    }
}
