package dev.sgs.validation.client;

import dev.sgs.core.Phase;
import dev.sgs.core.engine.PromptType;
import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.match.MatchNetwork;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Ordinary physical hand/discard/invoke clicks; no rule-state injection or substitute input. */
final class TurnEndWorldRestartProbe {
    private static int stage;private static long renderBase=-1,observedAt,acceptedAt;private static boolean parked;
    private record Evidence(long capturedAtMillis,String uuid,MatchNetwork.View view,int clientNonce){}
    static boolean enabled(){return Boolean.getBoolean("sgs.turnEndWorldRestart");}
    private static boolean restoring(){return System.getProperty("sgs.turnEndStage","").equals("restore");}
    private static boolean keji(){return System.getProperty("sgs.turnEndBoundary","biyue").equals("keji");}
    private static String role(){return System.getProperty("sgs.matchRole","");}
    private static Path directory(){return Path.of(System.getProperty("sgs.turnEndEvidence"));}
    private static void need(boolean b,String why){if(!b)throw new IllegalStateException("native turn end: "+why);}
    private static Evidence read(String name){try{return MatchNetwork.JSON.fromJson(Files.readString(directory().resolve(name+".json")),Evidence.class);}catch(Exception e){throw new IllegalStateException(e);}}
    private static boolean rendered(MatchScreen s){if(renderBase<0)renderBase=s.completedRenderCountForSmoke();return s.completedRenderCountForSmoke()-renderBase>=3;}
    private static void next(int value){stage=value;renderBase=-1;}
    private static void capture(Minecraft c,MatchScreen s,String name){try{
        s.verifySkillGeometryForSmoke();Files.createDirectories(directory());var path=directory().resolve(role()+"-"+name+".json");var temp=path.resolveSibling(path.getFileName()+".new");
        Files.writeString(temp,MatchNetwork.JSON.toJson(new Evidence(System.currentTimeMillis(),c.player.getUUID().toString(),s.stateForSmoke(),MatchClient.nonceForSmoke())));
        Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        Screenshot.grab(c.gameDirectory,"sgs-turn-end-"+name+".png",c.getMainRenderTarget(),m->{});
    }catch(Exception e){throw new IllegalStateException(e);}}
    private static void card(MatchScreen screen,long id){
        var s=screen.stateForSmoke().snapshot();var hand=s.seats().get(s.viewerSeat()).hand();int index=-1;
        for(int i=0;i<hand.size();i++)if(hand.get(i).instanceId()==id)index=i;
        need(index>=0,"actual fee not in own hand");var hit=screen.handBoundsForSmoke(index);
        need(hit!=null&&screen.mouseClicked(hit[0]+hit[2]/2d,hit[1]+hit[3]/2d,0),"actual hand hit failed");
    }
    static boolean tick(Minecraft c,MatchScreen screen){
        if(!enabled())return false;var s=screen.stateForSmoke().snapshot();if(s.status()!=1||s.openingHand()!=null)return false;
        boolean host=role().equals("host");
        need(s.seats().stream().map(p->p.heroId()).toList().equals(List.of("diaochan","lumeng","zhugeliang","guanyu","machao","caocao")),"ordinary roster changed");
        need(s.seats().get(s.viewerSeat()).control().source().equals("MANUAL"),"human taken over");
        need(s.seats().stream().filter(p->!p.self()).allMatch(p->p.hand().isEmpty()),"opponent hand leaked");
        var p=s.prompt();boolean biyue=p!=null&&p.typeOrdinal()==PromptType.SKILL_INVOKE.ordinal()&&p.askerSeat()==0&&p.subjectSeat()==0&&p.skillId().equals("biyue");
        boolean keji=p!=null&&p.typeOrdinal()==PromptType.SKILL_INVOKE.ordinal()&&p.askerSeat()==1&&p.subjectSeat()==1&&p.skillId().equals("keji");
        if(keji()&&restoring()){
            var old=read(role()+"-before").view().snapshot();
            if(stage==0){need(keji&&p.promptId()==old.prompt().promptId(),"original Keji token lost");
                if(!rendered(screen))return true;capture(c,screen,"restored");next(1);return true;}
            if(!host&&stage==1){
                if(!Files.exists(directory().resolve("host-restored.json"))||!rendered(screen))return true;
                need(keji,"Keji resolved before actual input");int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("decline");
                need(MatchClient.nonceForSmoke()==nonce+1,"decline duplicated");
                ProbeMod.LOG.info("SGS_TURN_END_DECLINE skill=keji real_hit_test=true single_nonce=true");next(2);return true;
            }
            if(host&&stage==1||!host&&stage==2){
                if(p!=null||s.currentSeat()!=1||s.phaseOrdinal()!=Phase.DISCARD.ordinal()||!s.processing().isEmpty())return true;
                need(s.turnNumber()==old.turnNumber(),"decline handed over the turn");
                for(var before:old.seats()){var after=s.seats().get(before.seat());
                    need(after.hp()==before.hp()&&after.handCount()==before.handCount(),"decline discarded/drew before human choice");}
                need(s.seats().get(1).handCount()==6,"ordinary Lvmeng hand changed");
                need(s.skillActivations().equals(old.skillActivations()),"declined skill activated");
                if(acceptedAt==0){acceptedAt=System.currentTimeMillis()+2000;renderBase=-1;return true;}
                if(System.currentTimeMillis()<acceptedAt||!rendered(screen))return true;
                capture(c,screen,"accepted");ProbeMod.LOG.info("SGS_TURN_END_PASS role={} actual_human_inputs_after_restore=1 automatic_phase_opens=0 bot_actions=0 boundary=keji",role());next(3);
            }
            return true;
        }
        if(restoring()){
            var old=read(role()+"-before").view().snapshot();
            if(stage==0){need(biyue&&p.promptId()==old.prompt().promptId(),"original Biyue token lost");
                if(!rendered(screen))return true;capture(c,screen,"restored");next(1);return true;}
            if(host&&stage==1){
                if(!Files.exists(directory().resolve("guest-restored.json"))||!rendered(screen))return true;
                need(biyue,"Biyue resolved before actual input");int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("invoke");
                need(MatchClient.nonceForSmoke()==nonce+1,"invoke duplicated");
                ProbeMod.LOG.info("SGS_TURN_END_INVOKE skill=biyue real_hit_test=true single_nonce=true");next(2);return true;
            }
            if(host&&stage==2||!host&&stage==1){
                if(p!=null||s.currentSeat()!=1||s.phaseOrdinal()!=Phase.PLAY.ordinal()||!s.processing().isEmpty())return true;
                need(s.turnNumber()==old.turnNumber()+1,"wrong turn count");
                for(var before:old.seats()){
                    var after=s.seats().get(before.seat());need(after.hp()==before.hp(),"unexpected damage");
                    need(after.handCount()==before.handCount()+(before.seat()==0?1:before.seat()==1?2:0),"duplicate/missing draw or extra Bot action");
                }
                need(s.skillActivations().stream().filter(a->a.seat()==0&&a.skillId().equals("biyue")).count()==1,"Biyue receipt duplicated");
                if(acceptedAt==0){acceptedAt=System.currentTimeMillis()+750;renderBase=-1;return true;}
                if(System.currentTimeMillis()<acceptedAt||!rendered(screen))return true;
                capture(c,screen,"accepted");ProbeMod.LOG.info("SGS_TURN_END_PASS role={} actual_human_inputs_after_restore=1 automatic_phase_opens=1 bot_actions=0",role());next(3);
            }
            return true;
        }
        if(host&&stage==0){
            if(s.currentSeat()!=0||s.phaseOrdinal()!=Phase.PLAY.ordinal()||p!=null||!rendered(screen))return true;
            need(s.seats().get(0).identityOrdinal()==0&&s.seats().get(0).hp()==4&&s.seats().get(0).handCount()==6,"ordinary lord opening differs");
            capture(c,screen,"play");int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("end");need(MatchClient.nonceForSmoke()==nonce+1,"end-play duplicated");next(1);return true;
        }
        if(host&&stage==1){
            if(s.phaseOrdinal()!=Phase.DISCARD.ordinal()||p!=null||!rendered(screen))return true;
            capture(c,screen,"discard");int nonce=MatchClient.nonceForSmoke();card(screen,3);card(screen,1);
            need(screen.selectedCountForSmoke()==2&&MatchClient.nonceForSmoke()==nonce,"local fees sent early");next(2);return true;
        }
        if(host&&stage==2){
            if(!rendered(screen))return true;need(s.phaseOrdinal()==Phase.DISCARD.ordinal()&&screen.selectedCountForSmoke()==2,"physical discard selection lost");
            capture(c,screen,"selected");int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("discard");need(MatchClient.nonceForSmoke()==nonce+1,"discard duplicated");
            ProbeMod.LOG.info("SGS_TURN_END_DISCARD fees=3,1 end_play_real_hit=true hand_real_hit=true confirm_real_hit=true two_human_inputs=true");next(3);return true;
        }
        if(keji()){
            if(host&&stage==3&&biyue){
                if(!rendered(screen))return true;int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("invoke");
                need(MatchClient.nonceForSmoke()==nonce+1,"initial Biyue duplicated");next(10);return true;
            }
            if(!host&&stage==0&&p==null&&s.currentSeat()==1&&s.phaseOrdinal()==Phase.PLAY.ordinal()){
                if(!rendered(screen))return true;need(s.seats().get(1).handCount()==6,"ordinary Lvmeng draw differs");
                capture(c,screen,"play");int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("end");
                need(MatchClient.nonceForSmoke()==nonce+1,"Lvmeng end-play duplicated");next(10);return true;
            }
            if(!keji)return true;
            need(s.currentSeat()==1&&s.phaseOrdinal()==Phase.PLAY.ordinal()&&s.seats().get(0).handCount()==5&&s.seats().get(1).handCount()==6,"incorrect Keji parent state");
            need(s.processing().isEmpty()&&s.skillActivations().stream().noneMatch(a->a.skillId().equals("keji")),"Keji already executed");
            if(observedAt==0)observedAt=System.currentTimeMillis();
            if(!parked&&System.currentTimeMillis()-observedAt>=15000&&rendered(screen)){
                capture(c,screen,"before");parked=true;ProbeMod.LOG.info("SGS_TURN_END_PARKED role={} prompt={} held_ms={} boundary=keji",role(),p.promptId(),System.currentTimeMillis()-observedAt);
            }
            return true;
        }
        if(!biyue)return true;
        need(s.currentSeat()==0&&s.phaseOrdinal()==Phase.END.ordinal()&&s.seats().get(0).handCount()==4&&s.seats().get(1).handCount()==4,"incorrect unpaid Biyue state");
        need(s.processing().isEmpty()&&s.skillActivations().stream().noneMatch(a->a.skillId().equals("biyue")),"Biyue already executed");
        if(observedAt==0)observedAt=System.currentTimeMillis();
        if(!parked&&System.currentTimeMillis()-observedAt>=15000&&rendered(screen)){
            capture(c,screen,"before");parked=true;ProbeMod.LOG.info("SGS_TURN_END_PARKED role={} prompt={} held_ms={}",role(),p.promptId(),System.currentTimeMillis()-observedAt);
        }
        return true;
    }
}
