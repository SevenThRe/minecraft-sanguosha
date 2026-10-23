package dev.sgs.validation.client;

import dev.sgs.core.Phase;
import dev.sgs.core.engine.PromptType;
import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.match.MatchNetwork;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Opt-in ordinary-room private order clicks; all state changes travel through the real controls. */
final class PreparationWorldRestartProbe {
    private static int stage; private static long renders=-1,held; private static boolean parked;
    private record Evidence(long capturedAtMillis,String uuid,MatchNetwork.View view,int clientNonce){}
    static boolean enabled(){return Boolean.getBoolean("sgs.preparationWorldRestart");}
    private static Path directory(){return Path.of(System.getProperty("sgs.turnEndEvidence"));}
    private static String role(){return System.getProperty("sgs.matchRole","");}
    private static void need(boolean ok,String why){if(!ok)throw new IllegalStateException("native preparation: "+why);}
    private static boolean rendered(MatchScreen s){if(renders<0)renders=s.completedRenderCountForSmoke();return s.completedRenderCountForSmoke()-renders>=3;}
    private static void next(int value){stage=value;renders=-1;}
    private static Evidence read(String name){try{return MatchNetwork.JSON.fromJson(Files.readString(directory().resolve(name+".json")),Evidence.class);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void capture(Minecraft c,MatchScreen s,String name){try{
        Files.createDirectories(directory());var path=directory().resolve(role()+"-"+name+".json");var tmp=path.resolveSibling(path.getFileName()+".new");
        Files.writeString(tmp,MatchNetwork.JSON.toJson(new Evidence(System.currentTimeMillis(),c.player.getUUID().toString(),s.stateForSmoke(),MatchClient.nonceForSmoke())));
        Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        Screenshot.grab(c.gameDirectory,"sgs-turn-start-"+name+".png",c.getMainRenderTarget(),m->{});
    }catch(Exception e){throw new IllegalStateException(e);}}
    static boolean tick(Minecraft c,MatchScreen screen){
        if(!enabled())return false;var s=screen.stateForSmoke().snapshot();if(s.status()!=1||s.openingHand()!=null)return false;
        boolean host=role().equals("host"),restore=System.getProperty("sgs.turnEndStage","").equals("restore");var p=s.prompt();
        need(s.seats().get(0).heroId().equals("zhugeliang")&&s.seats().get(1).heroId().equals("zhangfei"),"ordinary hero choice differs");
        need(s.seats().get(s.viewerSeat()).control().source().equals("MANUAL"),"human taken over");
        need(s.seats().stream().filter(v->!v.self()).allMatch(v->v.hand().isEmpty()),"opponent hand leaked");
        need(s.processing().isEmpty(),"private processing publicly exposed");
        if(!host&&p!=null)need(p.choices().isEmpty()&&p.allowedTargetSeats().isEmpty(),"private order leaked to peer");
        boolean invoke=p!=null&&p.skillId().equals("guanxing")&&p.typeOrdinal()==PromptType.SKILL_INVOKE.ordinal();
        boolean order=p!=null&&p.skillId().equals("guanxing")&&p.typeOrdinal()==PromptType.GUANXING_ORDER.ordinal();
        if(restore){
            if(stage>=10){
                if(!rendered(screen))return true;
                if(stage==10){screen.clickForSmoke("menu");next(11);}
                else if(stage==11){screen.clickForSmoke("menu:2");next(12);}
                else if(stage==12){need(!s.logLines().isEmpty(),"public journal empty");capture(c,screen,"journal");screen.clickForSmoke("log-close");next(13);}
                else if(stage==13){capture(c,screen,"accepted");ProbeMod.LOG.info("SGS_TURN_START_PASS role={} actual_human_inputs_after_restore=1 bot_actions=0 private_order=true journal_native=true",role());next(14);}
                return true;
            }
            if(stage==0){need(order&&p.promptId()==read(role()+"-before").view().snapshot().prompt().promptId(),"original private token lost");
                if(!rendered(screen))return true;capture(c,screen,"restored");next(1);return true;}
            if(host&&stage==1){if(!Files.exists(directory().resolve("guest-restored.json"))||!rendered(screen))return true;
                int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("decline");need(MatchClient.nonceForSmoke()==nonce+1,"cancel duplicated");next(2);return true;}
            if(host&&stage==2||!host&&stage==1){
                if(p!=null||s.phaseOrdinal()!=Phase.PLAY.ordinal()||s.currentSeat()!=0)return true;
                need(s.seats().get(0).handCount()==6&&s.seats().get(1).handCount()==4,"draw lost or repeated");
                need(s.skillActivations().stream().filter(a->a.seat()==0&&a.skillId().equals("guanxing")).count()==1,"activation lost or duplicated");
                if(!rendered(screen))return true;next(10);
            }return true;
        }
        if(stage==0){if(!invoke||!rendered(screen))return true;capture(c,screen,"first");next(1);return true;}
        if(host&&stage==1){if(!Files.exists(directory().resolve("guest-first.json"))||!rendered(screen))return true;
            int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("invoke");need(MatchClient.nonceForSmoke()==nonce+1,"invoke duplicated");next(2);return true;}
        if(host&&(stage==2||stage==3)){
            if(!order||!rendered(screen))return true;int nonce=MatchClient.nonceForSmoke();
            need(p.choices().size()==(stage==2?5:4),"ordinary private count differs");
            if(stage==3)capture(c,screen,"target");
            screen.clickForSmoke("skill-choice:"+p.choices().getLast().instanceId());screen.clickForSmoke("skill-destination:"+(stage==2?1:0));
            need(MatchClient.nonceForSmoke()==nonce,"local selection sent input");screen.clickForSmoke("skill-choice-confirm");need(MatchClient.nonceForSmoke()==nonce+1,"order confirmation duplicated");
            next(stage+1);return true;
        }
        if(!order||host&&p.choices().size()!=3)return true;
        // Guest waits for the host's partial-order evidence to avoid parking at an earlier token.
        if(!host&&!Files.exists(directory().resolve("host-before.json")))return true;
        if(held==0)held=System.currentTimeMillis();
        if(!parked&&System.currentTimeMillis()-held>= (host?15000:0)&&rendered(screen)){
            capture(c,screen,"before");parked=true;ProbeMod.LOG.info("SGS_TURN_START_PARKED role={} prompt={} held_ms={} private_order=true",role(),p.promptId(),System.currentTimeMillis()-held);
        }return true;
    }
}
