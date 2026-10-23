package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.client.art.HeroSkins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Native name-strip input, preview, cancel, apply and disk reload; no fake game state. */
public final class HeroSkinSmokeChecks {
    private static int stage,frames,scale=1,originalScale;
    private static boolean done,started;
    private static String originalSkin;
    private HeroSkinSmokeChecks(){}
    public static boolean tick(Minecraft client,MatchScreen screen){
        if(!Boolean.getBoolean("sgs.heroSkinSmoke") || done || screen.stateForSmoke().snapshot().status()!=1)return false;
        client.getToasts().clear();
        var state=screen.stateForSmoke().snapshot();
        if(state.openingHand()!=null || state.identityIntro()!=null || (state.prompt()!=null && state.prompt().askerSeat()==state.viewerSeat() && (state.prompt().typeOrdinal()==dev.sgs.core.engine.PromptType.CARD_ZONE_SELECT.ordinal() || state.prompt().typeOrdinal()==dev.sgs.core.engine.PromptType.WUGU_PICK.ordinal())))return false;
        if(!started){started=true;originalScale=client.options.guiScale().get();originalSkin=screen.skinForSmoke();client.options.guiScale().set(scale);client.resizeDisplay();return true;}
        if(++frames<5)return true;frames=0;
        switch(stage){
            case 0 -> {
                if(Boolean.getBoolean("sgs.equipmentMetadataSmoke"))screen.portraitFixtureForSmoke(true);
                var p=screen.skinStripForSmoke();screen.hoverForSmoke(p[0],p[1]);stage=1;
            }
            case 1 -> {
                require(screen.renderedSelfNameForSmoke().equals("换肤"),"hover did not replace the whole hero name");
                capture(client,"hover");var p=screen.skinStripForSmoke();
                require(screen.mouseClicked(p[0],p[1],0) && screen.overlayForSmoke(),"name strip did not open skin picker");stage=2;
            }
            case 2 -> {
                if(Boolean.getBoolean("sgs.equipmentMetadataSmoke")){
                    capture(client,"picker");screen.clickForSmoke("overlay-close");screen.hoverForSmoke(0,0);stage=6;break;
                }
                require(screen.controlsForSmoke().contains("skin:classic") && screen.controlsForSmoke().contains("skin:action"),"missing actual skin choices");
                capture(client,"picker");screen.clickForSmoke("skin:classic");stage=3;
            }
            case 3 -> {
                require(screen.skinForSmoke().equals(originalSkin),"preview changed applied skin before confirmation");
                screen.clickForSmoke("overlay-close");require(screen.skinForSmoke().equals(originalSkin),"cancel changed applied skin");
                var p=screen.skinStripForSmoke();screen.mouseClicked(p[0],p[1],0);screen.clickForSmoke("skin:classic");screen.clickForSmoke("skin-apply");
                stage=30;
                screen.hoverForSmoke(0,0);
            }
            case 30 -> {if(!screen.skinForSmoke().equals("classic") || screen.overlayForSmoke())return true;stage=4;}
            case 4 -> {
                require(screen.renderedSelfNameForSmoke().equals(screen.heroName(screen.heroForSkinSmoke())),"hero name not restored after hover");
                capture(client,"classic");var p=screen.skinStripForSmoke();screen.mouseClicked(p[0],p[1],0);
                screen.clickForSmoke("skin:action");screen.clickForSmoke("skin-apply");
                stage=40;
            }
            case 40 -> {if(!screen.skinForSmoke().equals("action") || screen.overlayForSmoke())return true;stage=5;}
            case 5 -> {
                capture(client,"action");ProbeMod.LOG.info("SGS_HERO_SKIN_SCALE_PASS gui={} hero={} left_strip=true preview_cancel=true classic_apply=true action_apply=true server_ack=true",scale,screen.heroForSkinSmoke());
                if(++scale<=3){originalSkin=screen.skinForSmoke();stage=0;client.options.guiScale().set(scale);client.resizeDisplay();}
                else{done=true;screen.clearSkillFocusForSmoke();client.options.guiScale().set(originalScale);client.resizeDisplay();ProbeMod.LOG.info("SGS_HERO_SKIN_PASS native_scales=3 server_cosmetic=true");if(Boolean.getBoolean("sgs.heroSkinSmokeOnly"))client.stop();}
            }
            case 6 -> {
                require(screen.renderedSelfNameForSmoke().equals(screen.heroName(screen.heroForSkinSmoke())),"hero name not restored after hover");
                capture(client,"restored");
                ProbeMod.LOG.info("SGS_HERO_SKIN_SCALE_PASS gui={} whole_name_hover=true restored=true equipment_metadata_fixture=true",scale);
                if(++scale<=3){stage=0;client.options.guiScale().set(scale);client.resizeDisplay();}
                else{done=true;screen.portraitFixtureForSmoke(false);screen.clearSkillFocusForSmoke();client.options.guiScale().set(originalScale);client.resizeDisplay();ProbeMod.LOG.info("SGS_HERO_SKIN_PASS native_scales=3 equipment_metadata_fixture=true");if(Boolean.getBoolean("sgs.heroSkinSmokeOnly"))client.stop();}
            }
            default -> throw new IllegalStateException("unknown skin smoke stage");
        }
        return true;
    }
    private static void capture(Minecraft client,String label){Screenshot.grab(client.gameDirectory,"sgs-hero-skin-gui"+scale+"-"+label+".png",client.getMainRenderTarget(),message->{});}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
