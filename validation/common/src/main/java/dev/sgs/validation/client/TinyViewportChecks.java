package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.client.widget.BoardIconButton;
import dev.sgs.validation.client.widget.SkillWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Opt-in regression of real focused widgets on the 1x1 canvas reported by minimized windows. */
final class TinyViewportChecks {
    private static boolean checked;
    static void run(Minecraft c,MatchScreen screen){
        if(checked)return;
        var graphics=new GuiGraphics(c,c.renderBuffers().bufferSource()){
            @Override public int guiWidth(){return 1;}
            @Override public int guiHeight(){return 1;}
        };
        graphics.enableScissor(0,0,1,1);
        try{
            var icon=new BoardIconButton(0,0,22,BoardIconButton.Icon.HELP,"帮助",false,button->{});
            icon.setFocused(true);icon.render(graphics,0,0,0);
            var skill=new SkillWidget(new SkillWidget.Spec("probe","测试","最小窗口下仍安全绘制技能控件",SkillWidget.Kind.ACTIVE),0,0,48,true,false,button->{});
            skill.setFocused(true);skill.render(graphics,0,0,0);screen.render(graphics,0,0,0);graphics.flush();
        }finally{graphics.disableScissor();}
        checked=true;
        ProbeMod.LOG.info("SGS_TINY_VIEWPORT_PASS canvas=1x1 real_widgets=BoardIconButton,SkillWidget focused=true full_match_screen=true");
    }
}
