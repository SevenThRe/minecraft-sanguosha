package dev.sgs.validation.client.art;
import dev.sgs.validation.ProbeMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
/** Native nine-slice panels with independent text and input hitboxes. */
public final class BoardSkin {
    public static final ResourceLocation PAPER=ProbeMod.id("textures/gui/panels/paper_button_v1.png");
    public static final ResourceLocation SLOT=ProbeMod.id("textures/gui/panels/equipment_slot_v2.png");
    private BoardSkin(){}
    public static void nine(GuiGraphics g,ResourceLocation t,int x,int y,int w,int h,int sw,int sh,int pad){
        int e=Math.max(2,Math.min(Math.min(w,h)/3,Math.round(pad*h/(float)sh)));
        int[] dx={x,x+e,x+w-e},dy={y,y+e,y+h-e},dw={e,Math.max(1,w-2*e),e},dh={e,Math.max(1,h-2*e),e};
        int[] sx={0,pad,sw-pad},sy={0,pad,sh-pad},uw={pad,sw-2*pad,pad},vh={pad,sh-2*pad,pad};
        for(int r=0;r<3;r++)for(int c=0;c<3;c++)g.blit(t,dx[c],dy[r],dw[c],dh[r],sx[c],sy[r],uw[c],vh[r],sw,sh);
    }
}
