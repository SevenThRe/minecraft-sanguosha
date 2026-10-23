package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.TickPhase;
import net.blay09.mods.balm.api.event.TickType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Opt-in native capture of restored lobby layout and replacement art; no simulated room state. */
public final class LobbyHomeArtSmokeChecks {
    private static int ticks,stage,after,scale=1,sample;
    private static boolean done;
    public static void initialize(){
        if(Boolean.getBoolean("sgs.lobbyHomeArtSmoke"))Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,LobbyHomeArtSmokeChecks::tick);
    }
    private static void next(int value){stage=value;after=ticks+12;}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static void click(LobbyScreen s,String id){
        int[] b=s.smokeBounds(id);double x=b[0]+b[2]/2.0,y=b[1]+b[3]/2.0;
        s.mouseMoved(x,y);require(s.mouseClicked(x,y,0),"native input rejected "+id);s.mouseReleased(x,y,0);
    }
    private static void tick(Minecraft c){
        if(done)return;
        try{
            if(++ticks>2400)throw new IllegalStateException("lobby art capture timeout");
            if(c.player==null || c.level==null || ticks<after)return;
            c.getToasts().clear();
            if(stage==0){c.options.guiScale().set(scale);c.resizeDisplay();LobbyClient.open();next(1);return;}
            if(!(c.screen instanceof LobbyScreen s) || s.smokeView()==null || s.smokePending())return;
            if(Boolean.getBoolean("sgs.lobbyModalSmoke")){
                if(stage==1){click(s,"open_rooms");next(10);}
                else if(stage==10){click(s,"mode:six");next(11);}
                else if(stage==11){click(s,"create");sample=0;next(12);}
                else if(stage==12){
                    require(s.smokeModal().equals("SETTINGS"),"settings dismissed during capture");
                    Screenshot.grab(c.gameDirectory,"sgs-modal-gui"+scale+"-"+sample+".png",c.getMainRenderTarget(),m->{});
                    if(++sample<12){after=ticks+3;return;}
                    ProbeMod.LOG.info("SGS_LOBBY_MODAL_SCALE_PASS gui={} samples={}",scale,sample);
                    if(++scale<=3){LobbyClient.open();c.options.guiScale().set(scale);c.resizeDisplay();next(1);}
                    else{done=true;ProbeMod.LOG.info("SGS_LOBBY_MODAL_PASS");c.stop();}
                }
                return;
            }
            switch(stage){
                case 1 -> {
                    require(s.smokePage().equals("HOME"),"home not restored");
                    require(c.getResourceManager().getResource(ProbeMod.id("textures/gui/lobby/lobby_home_minecraft_v1.png")).isPresent(),"replacement art missing");
                    try{s.smokeBounds("skin_filter");throw new IllegalStateException("rejected showroom controls survived rollback");}catch(IllegalArgumentException expected){}
                    Screenshot.grab(c.gameDirectory,"sgs-lobby-minecraft-gui"+scale+".png",c.getMainRenderTarget(),m->{});
                    click(s,"open_rooms");next(2);
                }
                case 2 -> {require(s.smokePage().equals("MODES"),"room entry broken");click(s,"back");next(3);}
                case 3 -> {
                    require(s.smokePage().equals("HOME"),"home navigation broken");
                    ProbeMod.LOG.info("SGS_LOBBY_HOME_ART_SCALE_PASS gui={} native_input=true restored_layout=true",scale);
                    if(++scale<=3){c.options.guiScale().set(scale);c.resizeDisplay();next(1);}
                    else{done=true;ProbeMod.LOG.info("SGS_LOBBY_HOME_ART_PASS gui=1,2,3");c.stop();}
                }
                default -> throw new IllegalStateException("unknown stage");
            }
        }catch(Exception ex){done=true;ProbeMod.LOG.error("SGS_LOBBY_HOME_ART_FAIL",ex);c.stop();}
    }
}
