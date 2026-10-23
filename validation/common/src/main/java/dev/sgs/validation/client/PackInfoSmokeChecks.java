package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.lobby.LobbyNetwork;
import dev.sgs.validation.match.MatchNetwork;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Native read-only pack panel; the external harness changes the real server config and reloads. */
final class PackInfoSmokeChecks {
    private static int step,frames;
    static boolean tick(Minecraft client,LobbyScreen screen){
        if(!Boolean.getBoolean("sgs.packInfoSmoke") || !System.getProperty("sgs.matchRole","").equals("host") || step==6)return false;
        if(screen.smokeView()==null || screen.smokePending())return true;
        try{
            if(step==0){if(!screen.smokePage().equals("ROOMS"))return false;screen.smokeAction("create");step=1;return true;}
            if(step==1){if(!screen.smokeModal().equals("SETTINGS"))return true;screen.smokeAction("pack-info");step=2;frames=0;return true;}
            var packs=screen.smokeView().cardPacks();
            if(packs.size()!=4)throw new IllegalStateException("Server pack capability list missing");
            var ex=packs.stream().filter(p->p.id().equals("cards.ex")).findFirst().orElseThrow();
            var zhulu=packs.stream().filter(p->p.id().equals("cards.zhulu")).findFirst().orElseThrow();
            if(zhulu.available() || zhulu.enabled() || !zhulu.reason().contains("首批三种规则"))throw new IllegalStateException("Partial Zhulu misreported as absent or complete");
            boolean wanted=step!=3;
            if(step<=4){
                if(ex.enabled()!=wanted){frames=0;return true;}
                if(screen.packRenderedFramesForSmoke()<4)return true;
                String name=step==2?"default":step==3?"ex-disabled":"ex-restored";
                Screenshot.grab(client.gameDirectory,"sgs-pack-info-"+name+".png",client.getMainRenderTarget(),m->{});
                Files.writeString(Path.of(System.getProperty("sgs.packInfoEvidence")).resolve("pack-info-"+name+".json"),MatchNetwork.JSON.toJson(packs));
                ProbeMod.LOG.info("SGS_PACK_INFO_CAPTURE state={} actual_server_view=true",name);step++;frames=0;return true;
            }
            screen.smokeAction("pack-back");screen.smokeAction("modal_close");step=6;
            ProbeMod.LOG.info("SGS_PACK_INFO_PASS actual_config_reload=true enabled_disabled_restored=true native_ui=true");return true;
        }catch(Exception e){throw new IllegalStateException("Native server pack info failed",e);}
    }
}
