package dev.sgs.validation.client.widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
/** Engraved toolbar controls; all actions are supplied by the actual feature. */
public final class BoardIconButton extends SgsButton {
    public enum Icon { FLOWER,EGG,SMILE,REPORT,REWARDS,LOG,CARDS,HELP,LEAVE,SETTINGS,RECEIPT,HERO,MENU,AUTOPLAY,DISTANCE,CHAT }
    private final Icon icon;
    private final boolean diamond;
    public BoardIconButton(int x,int y,int size,Icon icon,String title,boolean diamond,OnPress action){super(x,y,size,size,Component.literal(title),action);this.icon=icon;this.diamond=diamond;}
    @Override protected boolean parchment(){return false;}
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
        int cx=getX()+getWidth()/2,cy=getY()+getHeight()/2;
        if(diamond) diamond(g,cx,cy);
        else if(isHoveredOrFocused()){
            g.fill(getX()+1,getY()+1,getX()+getWidth()-1,getY()+getHeight()-1,0x704c321e);
            g.hLine(getX()+4,getX()+getWidth()-5,getY()+getHeight()-2,0xffa8783a);
        }
        float scale=Math.min(getWidth()*(diamond?.48f:.7f)/17f,getHeight()*(diamond?.48f:.7f)/17f);
        scale=Math.max(.4f,scale);
        g.pose().pushPose();g.pose().translate(cx-8.5f*scale,cy-8.5f*scale,0);g.pose().scale(scale,scale,1);
        if(icon==Icon.FLOWER || icon==Icon.EGG)dev.sgs.validation.client.GiftSprites.draw(g,icon==Icon.FLOWER?"POPPY":"EGG",0,0,17);
        else if(icon==Icon.SMILE)dev.sgs.validation.client.EmoteAnimations.draw(g,"happy",0,0,17);
        else drawGlyph(g,glyph(icon));g.pose().popPose();
        // A minimized native window can briefly report a 1x1 GUI. No tooltip fits there.
        if(isHoveredOrFocused() && g.guiWidth()>=32 && g.guiHeight()>=40){
            var f=Minecraft.getInstance().font;int w=f.width(getMessage())+12;
            int x=Math.clamp(cx-w/2,4,Math.max(4,g.guiWidth()-w-4)),y=Math.clamp(getY()-17,20,g.guiHeight()-20);
            g.pose().pushPose();g.pose().translate(0,0,500);g.fill(x,y,x+w,y+15,0xffecd4a5);g.drawString(f,getMessage(),x+6,y+3,0xff4a301d,false);g.pose().popPose();
        }
    }

    private void diamond(GuiGraphics g,int cx,int cy){
        int radius=Math.max(3,Math.min(getWidth(),getHeight())/2-1);
        for(int row=-radius;row<=radius;row++){
            int half=radius-Math.abs(row),y=cy+row;
            g.fill(cx-half,y,cx+half+1,y+1,0xff281b13);
            if(half>=1)g.fill(cx-half+1,y,cx+half,y+1,row<0?0xffc29a59:0xff76502d);
            if(half>=3)g.fill(cx-half+3,y,cx+half-2,y+1,0xff382619);
            if(half>=4){
                int wood=isHoveredOrFocused()?0xff67442a:row<0?0xff513721:0xff432b1c;
                g.fill(cx-half+4,y,cx+half-3,y+1,wood);
            }
        }
        // Tiny corner studs keep the beveled diamond readable against the wooden side rail.
        g.fill(cx-1,cy-radius+1,cx+2,cy-radius+3,0xffedc477);
        g.fill(cx-radius+1,cy-1,cx-radius+3,cy+2,0xffa77b42);
        g.fill(cx+radius-2,cy-1,cx+radius,cy+2,0xffa77b42);
        g.fill(cx-1,cy+radius-2,cx+2,cy+radius,0xff8b6032);
    }

    private void drawGlyph(GuiGraphics g,String[] pixels){
        for(int y=0;y<pixels.length;y++)for(int x=0;x<pixels[y].length();x++)
            if(pixels[y].charAt(x)!=' ')g.fill(x,y+1,x+1,y+2,0xff3a230f);
        for(int y=0;y<pixels.length;y++)for(int x=0;x<pixels[y].length();x++){
            char pixel=pixels[y].charAt(x);if(pixel==' ')continue;
            int color=!active?0xff80694c:pixel=='+'?0xfff4d58c:pixel=='o'?0xffa9783b:
                isHoveredOrFocused()?0xfff0c976:y<7?0xffddb66d:0xffc0924e;
            g.fill(x,y,x+1,y+1,color);
        }
    }

    /** Seventeen-pixel engraved silhouettes; no font glyph substitution or vanilla button skin. */
    private static String[] glyph(Icon icon){
        return switch(icon){
            case FLOWER,EGG,SMILE->new String[0];
            case AUTOPLAY->new String[]{
                "        #        ","        #        ","      #####      ","                 ",
                "   ###########   ","   #         #   "," ###  ## ##  ### "," # #  ## ##  # # ",
                " # #         # # "," ###         ### ","   #  #####  #   ","   #         #   ",
                "   ###########   ","      #   #      ","      #   #      ","                 ","                 "};
            case DISTANCE->new String[]{
                "                 ","                 ","                 ","   #         #   ",
                "  ##         ##  "," ############### ","  ##         ##  ","   #         #   ",
                "                 "," ############### "," #  #  #  #  # # "," #  #  #  #  # # ",
                " #             # "," ############### ","                 ","                 ","                 "};
            case CHAT->new String[]{
                "                 ","                 "," ############### "," #+++++++++++++# ",
                " #+++++++++++++# "," #+++++++++++++# "," #++##++##++##+# "," #++##++##++##+# ",
                " #+++++++++++++# "," #+++++++++++++# "," #+++++++++++++# "," ######+++###### ",
                "      #++#       ","      #+#        ","      ##         ","                 ","                 "};
            case REWARDS->new String[]{
                "       ###       ","       #+#       ","   ##  #+#  ##   ","  ##+###o###+##  ",
                "   #++#####++#   ","   ##+##### +##  ","#####++###++#####","#++o##+#+##o++#  ",
                "#####++###++#####","   ##+#####+##   ","   #++#####++#   ","  ##+###o###+##  ",
                "   ##  #+#  ##   ","       #+#       ","       ###       ","                 ","                 "};
            case HELP->new String[]{
                "        +        ","       #+#       ","      #   #      ","     # ### #     ",
                "    # #   # #    ","   #      #  #   ","  #      #    #  "," #      #      # ",
                "+#      #      #+"," #             # ","  #     #     #  ","   #         #   ",
                "    #       #    ","     #     #     ","      #   #      ","       #o#       ","        o        "};
            case SETTINGS->new String[]{
                "       ###       ","       #+#       ","   ##  #+#  ##   ","  ##+###+###+##  ",
                "   #++#####++#   ","   ##       ##   ","  ###  ###  ###  ","##+## #   # ##+##",
                "#+++o #   # o+++#","##+## #   # ##+##","  ###  ###  ###  ","   ##       ##   ",
                "   #++#####++#   ","  ##+###+###+##  ","   ##  #+#  ##   ","       #o#       ","       ooo       "};
            case RECEIPT->new String[]{
                "                 ","                 "," +#############+ "," #++         ++# ",
                " # ##       ## # "," #  ##     ##  # "," #   ##   ##   # "," #    ##+##    # ",
                " #     ###     # "," #    ## ##    # "," #   ##   ##   # "," #  ##     ##  # ",
                " # ##       ## # "," #o###########o# ","                 ","                 ","                 "};
            case HERO->new String[]{
                "                 ","     ##   ##     ","   ##+#   #+##   ","  #+++#o#o#+++#  ",
                " #+++++###+++++# ","#++++++###++++++#","#+++++++#+++++++#"," ####+++++++#### ",
                "    #+++++++#    ","    #+++++++#    ","    #+++++++#    ","    #+++o+++#    ",
                "    #+++o+++#    ","    #+++o+++#    ","    #ooooooo#    ","    #########    ","                 "};
            case MENU,LOG->new String[]{
                "                 "," +#############+ "," #             # "," #  +########  # ",
                " #  ooooooooo  # "," #             # "," #             # "," #  #########  # ",
                " #  ooooooooo  # "," #             # "," #             # "," #  #########  # ",
                " #  ooooooooo  # "," #             # "," #o###########o# ","                 ","                 "};
            case CARDS->new String[]{
                "                 ","   +#########    ","   #        #    "," +######### #    ",
                " #        # #    "," #   ##   # #    "," #  ####  # #    "," # ###### # #    ",
                " #  ####  # #    "," #   ##   # #    "," #   ##   # #    "," #        # #    ",
                " #        #o#    "," #        #      "," #o#######o      ","                 ","                 "};
            case REPORT->new String[]{
                "                 ","    ##           ","    ##           ","    ##########   ",
                "    #++++++++#   ","    #+++++++#    ","    #++++++#     ","    #+++++++#    ",
                "    #++++++++#   ","    ##########   ","    ##           ","    ##           ",
                "    ##           ","    ##           ","    ##           ","                 ","                 "};
            case LEAVE->new String[]{
                "                 "," +#######        "," #      #        "," #      #   #    ",
                " #      #   ##   "," #      #    ##  "," #   ########### "," #   +###########",
                " #   o########## "," #      #    ##  "," #      #   ##   "," #      #   #    ",
                " #      #        "," #      #        "," #o#####o        ","                 ","                 "};
        };
    }
}
