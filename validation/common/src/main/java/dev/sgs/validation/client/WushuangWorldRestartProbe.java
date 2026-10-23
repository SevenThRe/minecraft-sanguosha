package dev.sgs.validation.client;

import dev.sgs.core.Phase;
import dev.sgs.core.card.CardType;
import dev.sgs.core.engine.PromptType;
import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.match.MatchNetwork;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Opt-in two-client evidence driver. Only real legal mouse input; never writes game state. */
final class WushuangWorldRestartProbe {
    private static int stage;
    private static long renderBase=-1,observedAt;
    private static boolean parked;
    private record Evidence(long capturedAtMillis,String uuid,MatchNetwork.View view,int clientNonce){}
    static boolean enabled(){return Boolean.getBoolean("sgs.wushuangWorldRestart");}
    static boolean restoring(){return enabled() && System.getProperty("sgs.wushuangRestartStage","").equals("restore");}
    private static String role(){return System.getProperty("sgs.matchRole","");}
    private static Path directory(){return Path.of(System.getProperty("sgs.wushuangRestartEvidence"));}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private static Evidence read(String file){
        try{return MatchNetwork.JSON.fromJson(Files.readString(directory().resolve(file+".json")),Evidence.class);}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private static boolean rendered(MatchScreen screen){
        if(renderBase<0)renderBase=screen.completedRenderCountForSmoke();
        return screen.completedRenderCountForSmoke()-renderBase>=3;
    }
    private static void next(int value){stage=value;renderBase=-1;}
    private static void capture(Minecraft c,MatchScreen screen,String name){
        try{
            Files.createDirectories(directory());var file=directory().resolve(role()+"-"+name+".json");var temp=file.resolveSibling(file.getFileName()+".new");
            Files.writeString(temp,MatchNetwork.JSON.toJson(new Evidence(System.currentTimeMillis(),c.player.getUUID().toString(),screen.stateForSmoke(),MatchClient.nonceForSmoke())));
            Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            Screenshot.grab(c.gameDirectory,"sgs-wushuang-world-"+name+".png",c.getMainRenderTarget(),m->{});
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void card(MatchScreen screen,long id){
        var s=screen.stateForSmoke().snapshot();var hand=s.seats().get(s.viewerSeat()).hand();int index=-1;
        for(int i=0;i<hand.size();i++)if(hand.get(i).instanceId()==id)index=i;
        require(index>=0,"expected real owned card missing: "+id);var bounds=screen.handBoundsForSmoke(index);require(bounds!=null,"hand hitbox absent");
        require(screen.mouseClicked(bounds[0]+bounds[2]/2d,bounds[1]+bounds[3]/2d,0) && screen.selectedCountForSmoke()==1,"real single card selection failed");
    }
    static boolean tick(Minecraft c,MatchScreen screen){
        if(!enabled())return false;
        var view=screen.stateForSmoke();var s=view.snapshot();
        if(s.status()!=1 || s.openingHand()!=null)return false;
        if(c.getWindow().getGuiScaledWidth()<80 || c.getWindow().getGuiScaledHeight()<80)return true;
        c.getToasts().clear();
        boolean guest=role().equals("guest");require(guest || role().equals("host"),"unknown Wushuang role");
        require(s.viewerSeat()==(guest?1:0) && s.seats().size()==6 && s.seats().stream().filter(p->p.human()).count()==2,"ordinary human seats changed");
        require(s.seats().stream().map(p->p.heroId()).toList().equals(List.of("lvbu","guanyu","caocao","sunquan","lumeng","xuchu")),"normal seed roster changed");
        require(s.seats().get(s.viewerSeat()).control().source().equals("MANUAL"),"manual human was put into autoplay");
        require(s.seats().stream().filter(p->!p.self()).allMatch(p->p.hand().isEmpty()),"private hand leaked");
        var prompt=s.prompt();boolean dodge=prompt!=null && prompt.askerSeat()==1 && prompt.typeOrdinal()==PromptType.DODGE_SHA.ordinal();
        if(restoring()){
            if(stage==0){
                require(dodge && s.processing().size()==1 && s.processing().getFirst().instanceId()==25 && s.seats().get(1).handCount()==3,"restored second Shan/root missing");
                require(prompt.promptId()==read(role()+"-before").view().snapshot().prompt().promptId(),"restored second token changed");
                if(!rendered(screen))return true;capture(c,screen,"restored");next(1);return true;
            }
            if(guest && stage==1){
                if(!Files.exists(directory().resolve("host-restored.json")) || !rendered(screen))return true;
                require(dodge,"second request disappeared before input");capture(c,screen,"response");card(screen,41);int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("respond");require(MatchClient.nonceForSmoke()==nonce+1,"second Shan duplicate/missing submission");
                ProbeMod.LOG.info("SGS_WUSHUANG_WORLD_RESPONSE card=41 prompt={} real_hit_test=true single_nonce=true",prompt.promptId());next(2);return true;
            }
            if((guest && stage==2) || (!guest && stage==1)){
                if(prompt!=null || !s.processing().isEmpty())return true;
                require(s.currentSeat()==0 && s.phaseOrdinal()==Phase.PLAY.ordinal() && s.seats().get(1).hp()==4 && s.seats().get(1).handCount()==2,"two Shan did not finish without damage");
                require(s.seats().get(0).handCount()==5,"source Sha cost changed");if(!rendered(screen))return true;
                capture(c,screen,"accepted");ProbeMod.LOG.info("SGS_WUSHUANG_WORLD_PASS role={} first_paid=145 second_paid=41 root=25 target_hp=4 real_hit_test=true",role());next(3);
            }
            return true;
        }
        if(!guest && stage==0){
            if(s.currentSeat()!=0 || s.phaseOrdinal()!=Phase.PLAY.ordinal() || prompt!=null || !s.processing().isEmpty() || !rendered(screen))return true;
            require(s.seats().get(0).identityOrdinal()==0 && s.seats().get(0).hand().stream().anyMatch(v->v.instanceId()==25 && v.typeOrdinal()==CardType.SHA.ordinal()),"not legal lord Lvbu and physical Sha");
            card(screen,25);screen.clickSeatForSmoke(1);next(1);return true;
        }
        if(!guest && stage==1){
            if(!rendered(screen))return true;capture(c,screen,"selected");int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("play");require(MatchClient.nonceForSmoke()==nonce+1,"source attack duplicate/missing submission");next(2);return true;
        }
        if(guest && stage==0){
            if(!dodge || !rendered(screen))return true;
            require(s.seats().get(1).handCount()==4 && s.processing().size()==1 && s.processing().getFirst().instanceId()==25,"not original first Shan");
            capture(c,screen,"first");card(screen,145);int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("respond");require(MatchClient.nonceForSmoke()==nonce+1,"first Shan duplicate/missing submission");
            ProbeMod.LOG.info("SGS_WUSHUANG_WORLD_FIRST card=145 prompt={} real_hit_test=true single_nonce=true",prompt.promptId());next(1);return true;
        }
        if(!Files.exists(directory().resolve("guest-first.json")) || !dodge || prompt.promptId()==read("guest-first").view().snapshot().prompt().promptId())return true;
        require(s.seats().get(1).handCount()==3 && s.seats().get(1).hp()==4 && s.processing().size()==1 && s.processing().getFirst().instanceId()==25,"first Shan did not remain paid");
        if(observedAt==0)observedAt=System.currentTimeMillis();
        if(!parked && System.currentTimeMillis()-observedAt>=15000 && rendered(screen)){
            capture(c,screen,"before");parked=true;ProbeMod.LOG.info("SGS_WUSHUANG_WORLD_PARKED role={} prompt={} elapsed_ms={}",role(),prompt.promptId(),System.currentTimeMillis()-observedAt);
        }
        return true;
    }
}
