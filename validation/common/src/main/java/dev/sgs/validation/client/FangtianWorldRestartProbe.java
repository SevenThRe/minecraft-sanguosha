package dev.sgs.validation.client;

import dev.sgs.core.engine.PromptType;
import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.match.MatchNetwork;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Opt-in native probe: records authorized views; never restores or injects game state. */
final class FangtianWorldRestartProbe {
    private static long observedAt;
    private static boolean parked,restored,declined;
    private static int frames;
    private record Evidence(long capturedAtMillis,String uuid,MatchNetwork.View view,int clientNonce){}
    static boolean enabled(){return Boolean.getBoolean("sgs.fangtianWorldRestart");}
    static boolean restoring(){return enabled() && System.getProperty("sgs.fangtianRestartStage","").equals("restore");}
    private static String role(){return System.getProperty("sgs.matchRole","");}
    private static Path directory(){return Path.of(System.getProperty("sgs.fangtianRestartEvidence"));}
    static MatchNetwork.View selected(){
        try{return MatchNetwork.JSON.fromJson(Files.readString(directory().resolve("host-selected.json")),Evidence.class).view();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    static void verifyCost(MatchScreen screen,long sha){
        try{
            var initial=MatchNetwork.JSON.fromJson(Files.readString(directory().resolve("host-before.json")),Evidence.class).view().snapshot();
            var end=screen.stateForSmoke().snapshot();
            if(initial.publicMoves().stream().filter(m->m.reason().equals("USE") && m.actorSeat()==0 && m.cards().size()==1 && m.cards().getFirst().instanceId()==sha).count()!=1
                || end.publicMoves().stream().anyMatch(m->m.reason().equals("USE"))
                || end.publicMoves().stream().filter(m->m.reason().equals("FINISH") && m.actorSeat()==0 && m.cards().size()==1 && m.cards().getFirst().instanceId()==sha).count()!=1)
                throw new IllegalStateException("resumed Fangtian replayed or lost physical use/cleanup");
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    static void capture(Minecraft c,MatchScreen screen,String name){
        if(!enabled())return;
        try{
            Files.createDirectories(directory());
            var dest=directory().resolve(role()+"-"+name+".json");var tmp=dest.resolveSibling(dest.getFileName()+".new");
            Files.writeString(tmp,MatchNetwork.JSON.toJson(new Evidence(System.currentTimeMillis(),c.player.getUUID().toString(),screen.stateForSmoke(),MatchClient.nonceForSmoke())));
            Files.move(tmp,dest,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            Screenshot.grab(c.gameDirectory,"sgs-fangtian-world-"+name+".png",c.getMainRenderTarget(),m->{});
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    static boolean tick(Minecraft c,MatchScreen screen){
        if(!enabled())return false;
        var s=screen.stateForSmoke().snapshot();var p=s.prompt();
        boolean pending=p!=null && p.askerSeat()==1 && p.typeOrdinal()==PromptType.DODGE_SHA.ordinal()
            && s.processing().size()==1 && s.seats().get(0).weapon()!=null;
        if(!restoring()){
            if(!pending)return false;
            if(observedAt==0)observedAt=System.currentTimeMillis();
            if(!parked && System.currentTimeMillis()-observedAt>=15000){capture(c,screen,"before");parked=true;ProbeMod.LOG.info("SGS_FANGTIAN_WORLD_PARKED role={} prompt={} elapsed_ms={}",role(),p.promptId(),System.currentTimeMillis()-observedAt);}
            return true;
        }
        if(!restored){
            if(!pending)throw new IllegalStateException("restored Fangtian first target prompt/cost missing");
            capture(c,screen,"restored");restored=true;return true;
        }
        if(role().equals("guest")){
            if(!declined && ++frames>=5){
                if(!pending)throw new IllegalStateException("restored Fangtian prompt expired before input");
                capture(c,screen,"guest-response");screen.clickForSmoke("decline");declined=true;
                ProbeMod.LOG.info("SGS_FANGTIAN_WORLD_RESPONSE prompt={} real_hit_test=true",p.promptId());
            }
            return true;
        }
        return false;
    }
}
