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

/** Real lord/other-Shu clients, actual skill/target/card hit tests and server-owned restoration. */
final class ActiveJijiangWorldRestartProbe {
    private static int stage;
    private static long renderBase=-1,observedAt,acceptedAt;
    private static boolean parked;
    private record Evidence(long capturedAtMillis,String uuid,MatchNetwork.View view,int clientNonce){}
    static boolean enabled(){return Boolean.getBoolean("sgs.activeJijiangWorldRestart");}
    static boolean restoring(){return enabled()&&System.getProperty("sgs.activeJijiangStage","").equals("restore");}
    private static String role(){return System.getProperty("sgs.matchRole","");}
    private static Path directory(){return Path.of(System.getProperty("sgs.activeJijiangEvidence"));}
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
    private static Evidence read(String name){try{return MatchNetwork.JSON.fromJson(Files.readString(directory().resolve(name+".json")),Evidence.class);}catch(Exception e){throw new IllegalStateException(e);}}
    private static boolean rendered(MatchScreen screen){if(renderBase<0)renderBase=screen.completedRenderCountForSmoke();return screen.completedRenderCountForSmoke()-renderBase>=3;}
    private static void next(int value){stage=value;renderBase=-1;}
    private static void capture(Minecraft c,MatchScreen screen,String name){try{
        screen.verifySkillGeometryForSmoke();Files.createDirectories(directory());var path=directory().resolve(role()+"-"+name+".json");var temp=path.resolveSibling(path.getFileName()+".new");
        Files.writeString(temp,MatchNetwork.JSON.toJson(new Evidence(System.currentTimeMillis(),c.player.getUUID().toString(),screen.stateForSmoke(),MatchClient.nonceForSmoke())));
        Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        Screenshot.grab(c.gameDirectory,"sgs-active-jijiang-"+name+".png",c.getMainRenderTarget(),m->{});
    }catch(Exception e){throw new IllegalStateException(e);}}
    static boolean tick(Minecraft c,MatchScreen screen){
        if(!enabled())return false;var view=screen.stateForSmoke();var s=view.snapshot();if(s.status()!=1||s.openingHand()!=null)return false;
        if(c.getWindow().getGuiScaledWidth()<80||c.getWindow().getGuiScaledHeight()<80)return true;
        c.getToasts().clear();boolean guest=role().equals("guest");require(guest||role().equals("host"),"unknown active supplied role");
        require(s.viewerSeat()==(guest?1:0)&&s.seats().size()==6&&s.seats().stream().filter(p->p.human()).count()==2,"ordinary six-seat humans changed");
        require(s.seats().get(s.viewerSeat()).control().source().equals("MANUAL"),"active supplied human taken over");
        require(s.seats().stream().filter(p->!p.self()).allMatch(p->p.hand().isEmpty()),"active supplied private hand leaked");
        require(s.seats().get(0).heroId().equals("liubei")&&(!guest||s.seats().get(1).heroId().equals(System.getProperty("sgs.preferredHero"))),"ordinary hero selection changed");
        var p=s.prompt();boolean supply=p!=null&&p.typeOrdinal()==PromptType.JIJIANG_SHA.ordinal()&&p.askerSeat()==1&&p.subjectSeat()==0;
        if(restoring()){
            var before=read(role()+"-before");var b=before.view().snapshot();
            if(stage==0){
                require(supply&&s.processing().isEmpty()&&p.promptId()==b.prompt().promptId(),"active provider/root/token not restored");
                require(view.nonceFloor()==before.view().nonceFloor(),"provider restored floor changed");
                if(!rendered(screen))return true;capture(c,screen,"restored");next(1);return true;
            }
            if(guest&&stage==1){
                if(!Files.exists(directory().resolve("host-restored.json"))||!rendered(screen))return true;
                require(supply,"actual provider question disappeared");
                long cost=Long.parseLong(System.getProperty("sgs.activeJijiangCost"));var hand=s.seats().get(1).hand();int index=-1;
                for(int i=0;i<hand.size();i++)if(hand.get(i).instanceId()==cost&&hand.get(i).typeOrdinal()==CardType.SHA.ordinal())index=i;
                require(index>=0,"actual provider Sha missing");var bounds=screen.handBoundsForSmoke(index);require(bounds!=null,"provider card hitbox missing");
                capture(c,screen,"response");require(screen.mouseClicked(bounds[0]+bounds[2]/2d,bounds[1]+bounds[3]/2d,0)&&screen.selectedCountForSmoke()==1,"provider real card selection failed");
                int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("respond");require(MatchClient.nonceForSmoke()==nonce+1,"provider response duplicated");
                ProbeMod.LOG.info("SGS_ACTIVE_JIJIANG_MOUSE cost={} real_hit_test=true single_nonce=true",cost);next(2);return true;
            }
            if(guest&&stage==2){
                if(p==null||p.typeOrdinal()!=PromptType.DODGE_SHA.ordinal()||p.askerSeat()!=1||!rendered(screen))return true;
                int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("decline");require(MatchClient.nonceForSmoke()==nonce+1,"actual dodge decline duplicated");
                ProbeMod.LOG.info("SGS_ACTIVE_JIJIANG_DODGE_MOUSE decline=true real_hit_test=true single_nonce=true");next(3);return true;
            }
            if(guest&&stage==3||!guest&&stage==1){
                long cost=Long.parseLong(System.getProperty("sgs.activeJijiangCost"));
                boolean finished=s.publicMoves().stream().anyMatch(m->m.reason().equals("FINISH")&&m.actorSeat()==0&&m.cards().size()==1&&m.cards().getFirst().instanceId()==cost);
                if(p!=null||!s.processing().isEmpty()||!finished)return true;
                require(s.currentSeat()==0&&s.phaseOrdinal()==Phase.PLAY.ordinal()&&s.turnNumber()==b.turnNumber(),"supplied caller did not return to original play");
                for(var old:b.seats()){
                    var now=s.seats().get(old.seat());require(now.hp()==old.hp()-(old.seat()==1?1:0),"supplied attack HP attribution changed");
                    require(now.handCount()==old.handCount()-(old.seat()==1?1:0),"supplied attack wrong hand payment");
                }
                require(s.skillActivations().stream().filter(a->a.seat()==0&&a.skillId().equals("jijiang")).count()==1,"supplied activation duplicated or lost");
                if(acceptedAt==0){acceptedAt=System.currentTimeMillis()+750;renderBase=-1;return true;}if(System.currentTimeMillis()<acceptedAt||!rendered(screen))return true;
                capture(c,screen,"accepted");ProbeMod.LOG.info("SGS_ACTIVE_JIJIANG_PASS role={} cost={} target=1 damage=1 source=0 native_mouse=true",role(),cost);next(4);
            }
            return true;
        }
        if(!guest&&stage==0){
            if(s.currentSeat()!=0||s.phaseOrdinal()!=Phase.PLAY.ordinal()||p!=null||!rendered(screen))return true;
            require(s.seats().get(0).identityOrdinal()==0,"actual lord role missing");screen.clickSeatForSmoke(1);next(1);return true;
        }
        if(!guest&&stage==1){
            if(!rendered(screen))return true;capture(c,screen,"selected");int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("jijiang");
            require(MatchClient.nonceForSmoke()==nonce+1,"actual lord skill submission duplicated");ProbeMod.LOG.info("SGS_ACTIVE_JIJIANG_START target=1 real_skill_button=true real_target_hit_test=true single_nonce=true");next(2);return true;
        }
        if(!supply)return true;require(s.processing().isEmpty(),"unpaid provider acquired fictitious processing root");
        if(observedAt==0)observedAt=System.currentTimeMillis();
        if(!parked&&System.currentTimeMillis()-observedAt>=15000&&rendered(screen)){
            capture(c,screen,"before");parked=true;ProbeMod.LOG.info("SGS_ACTIVE_JIJIANG_PARKED role={} prompt={} held_ms={}",role(),p.promptId(),System.currentTimeMillis()-observedAt);
        }
        return true;
    }
}
