package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Original screenshots of the real running match at three GUI scales, with no sample state. */
public final class TypographyChecks {
    private static int scale=1,frames,original;private static boolean started,done;
    private TypographyChecks(){}
    public static boolean tick(Minecraft client,MatchScreen screen){
        if(!Boolean.getBoolean("sgs.typographySmoke") || done || screen.stateForSmoke().snapshot().status()!=1)return false;
        if(!started){original=client.options.guiScale().get();started=true;client.options.guiScale().set(scale);client.resizeDisplay();return true;}
        if(++frames<4)return true;
        int actual=client.getWindow().getGuiScaledWidth();
        if(actual<100 || InkText.width(client.font,"仁德")<=0)throw new IllegalStateException("invalid typography metrics");
        Screenshot.grab(client.gameDirectory,"sgs-typography-gui"+scale+".png",client.getMainRenderTarget(),message->{});
        ProbeMod.LOG.info("SGS_TYPOGRAPHY_CAPTURED gui={} width={} height={} ink_name_width={} actual_match=true",scale,actual,client.getWindow().getGuiScaledHeight(),InkText.width(client.font,"仁德"));
        if(++scale<=3){frames=0;client.options.guiScale().set(scale);client.resizeDisplay();return true;}
        done=true;client.options.guiScale().set(original);client.resizeDisplay();
        ProbeMod.LOG.info("SGS_TYPOGRAPHY_PASS native_gui_scales=3 original_scale_restored=true");return true;
    }
}
