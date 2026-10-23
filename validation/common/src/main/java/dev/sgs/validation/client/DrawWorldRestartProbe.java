package dev.sgs.validation.client;

import dev.sgs.core.Phase;
import dev.sgs.core.engine.PromptType;
import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.match.MatchNetwork;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Normal room Tuxi target/confirm/cancel controls; does not replace input or mutate rule state. */
final class DrawWorldRestartProbe {
    private static int stage;private static long renderBase=-1,observedAt,acceptedAt;private static boolean parked;
    private record Evidence(long capturedAtMillis,String uuid,MatchNetwork.View view,int clientNonce){}
    static boolean enabled(){return Boolean.getBoolean("sgs.drawWorldRestart")||PreparationWorldRestartProbe.enabled()||GuanxingRowsSmokeChecks.enabled();}
    private static boolean restoring(){return System.getProperty("sgs.turnEndStage","").equals("restore");}
    private static String role(){return System.getProperty("sgs.matchRole","");}
    private static Path directory(){return Path.of(System.getProperty("sgs.turnEndEvidence"));}
    private static void need(boolean b,String why){if(!b)throw new IllegalStateException("native draw: "+why);}
    private static Evidence read(String name){try{return MatchNetwork.JSON.fromJson(Files.readString(directory().resolve(name+".json")),Evidence.class);}catch(Exception e){throw new IllegalStateException(e);}}
    private static boolean rendered(MatchScreen s){if(renderBase<0)renderBase=s.completedRenderCountForSmoke();return s.completedRenderCountForSmoke()-renderBase>=3;}
    private static void next(int value){stage=value;renderBase=-1;}
    private static void capture(Minecraft c,MatchScreen s,String name){try{
        s.verifySkillGeometryForSmoke();Files.createDirectories(directory());var path=directory().resolve(role()+"-"+name+".json");var temp=path.resolveSibling(path.getFileName()+".new");
        Files.writeString(temp,MatchNetwork.JSON.toJson(new Evidence(System.currentTimeMillis(),c.player.getUUID().toString(),s.stateForSmoke(),MatchClient.nonceForSmoke())));
        Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        Screenshot.grab(c.gameDirectory,"sgs-turn-start-"+name+".png",c.getMainRenderTarget(),m->{});
    }catch(Exception e){throw new IllegalStateException(e);}}
    static boolean tick(Minecraft c,MatchScreen screen){
        if(GuanxingRowsSmokeChecks.enabled())return GuanxingRowsSmokeChecks.tick(c,screen);
        if(PreparationWorldRestartProbe.enabled())return PreparationWorldRestartProbe.tick(c,screen);
        if(!enabled())return false;var s=screen.stateForSmoke().snapshot();if(s.status()!=1||s.openingHand()!=null)return false;
        boolean host=role().equals("host");var p=s.prompt();
        boolean tuxi=p!=null&&p.typeOrdinal()==PromptType.TUXI_TARGET.ordinal()&&p.askerSeat()==0&&p.subjectSeat()==0&&p.skillId().equals("tuxi");
        need(s.seats().stream().map(v->v.heroId()).toList().equals(List.of("zhangliao","luxun","sunquan","diaochan","huanggai","xuchu")),"ordinary roster differs");
        need(s.seats().get(s.viewerSeat()).control().source().equals("MANUAL"),"human taken over");
        need(s.seats().stream().filter(v->!v.self()).allMatch(v->v.hand().isEmpty()),"opponent hand leaked");
        need(s.processing().isEmpty(),"unexpected processing");
        if(restoring()){
            var old=read(role()+"-before").view().snapshot();
            if(stage==0){need(tuxi&&p.promptId()==old.prompt().promptId(),"second Tuxi token lost");
                if(!rendered(screen))return true;capture(c,screen,"restored");next(1);return true;}
            if(host&&stage==1){
                if(!Files.exists(directory().resolve("guest-restored.json"))||!rendered(screen))return true;
                need(tuxi,"second target resolved before actual input");int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("decline");
                need(MatchClient.nonceForSmoke()==nonce+1,"decline duplicated");
                ProbeMod.LOG.info("SGS_TURN_START_CANCEL real_hit_test=true single_nonce=true");next(2);return true;
            }
            if(host&&stage==2||!host&&stage==1){
                if(p!=null||s.currentSeat()!=0||s.phaseOrdinal()!=Phase.PLAY.ordinal())return true;
                need(s.turnNumber()==old.turnNumber(),"unexpected handover");
                for(var before:old.seats()){var after=s.seats().get(before.seat());need(after.hp()==before.hp()&&after.handCount()==before.handCount(),"repeated transfer/draw/damage");}
                need(s.seats().get(0).handCount()==5&&s.seats().get(1).handCount()==3,"first transfer lost");
                need(s.skillActivations().stream().filter(a->a.seat()==0&&a.skillId().equals("tuxi")).count()==1,"activation missing/duplicated");
                if(acceptedAt==0){acceptedAt=System.currentTimeMillis()+2000;renderBase=-1;return true;}
                if(System.currentTimeMillis()<acceptedAt||!rendered(screen))return true;
                capture(c,screen,"accepted");ProbeMod.LOG.info("SGS_TURN_START_PASS role={} actual_human_inputs_after_restore=1 bot_actions=0",role());next(3);
            }
            return true;
        }
        if(stage==0){
            if(!tuxi||!rendered(screen))return true;
            need(s.currentSeat()==0&&s.phaseOrdinal()==Phase.DRAW.ordinal()&&s.seats().get(0).handCount()==4&&s.seats().get(1).handCount()==4
                &&(host?p.allowedTargetSeats().contains(1):p.allowedTargetSeats().isEmpty()&&!p.hiddenHandAvailable()),"ordinary first Tuxi differs");
            capture(c,screen,"first");next(1);return true;
        }
        if(host&&stage==1){
            if(!Files.exists(directory().resolve("guest-first.json"))||!rendered(screen))return true;
            need(tuxi,"first target resolved early");int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("skill-target:1");
            need(MatchClient.nonceForSmoke()==nonce,"local target selection sent input");next(2);return true;
        }
        if(host&&stage==2){
            if(!rendered(screen))return true;capture(c,screen,"target");int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("skill-choice-confirm");
            need(MatchClient.nonceForSmoke()==nonce+1,"first target confirmation duplicated");
            ProbeMod.LOG.info("SGS_TURN_START_TARGET seat=1 target_real_hit=true confirm_real_hit=true single_nonce=true");next(3);return true;
        }
        if(!tuxi||p.promptId()==read(role()+"-first").view().snapshot().prompt().promptId())return true;
        need(s.currentSeat()==0&&s.phaseOrdinal()==Phase.DRAW.ordinal()&&s.seats().get(0).handCount()==5&&s.seats().get(1).handCount()==3&&!p.allowedTargetSeats().contains(1),"first transfer not committed exactly once");
        need(s.skillActivations().stream().noneMatch(a->a.skillId().equals("tuxi")),"Tuxi finished before second answer");
        if(observedAt==0)observedAt=System.currentTimeMillis();
        if(!parked&&System.currentTimeMillis()-observedAt>=15000&&rendered(screen)){
            capture(c,screen,"before");parked=true;ProbeMod.LOG.info("SGS_TURN_START_PARKED role={} prompt={} held_ms={}",role(),p.promptId(),System.currentTimeMillis()-observedAt);
        }
        return true;
    }
}
