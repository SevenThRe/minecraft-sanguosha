package dev.sgs.validation.client;

import dev.sgs.core.Phase;
import dev.sgs.core.engine.PromptType;
import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.match.MatchNetwork;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Opt-in ordinary two-human room: real attack, discard/recipient clicks and full world restart. */
final class TargetRedirectWorldRestartProbe {
  private static int stage;private static long renderBase=-1,observedAt,acceptedAt;private static boolean parked;
  private record Evidence(long capturedAtMillis,String uuid,MatchNetwork.View view,int clientNonce){}
  static boolean enabled(){return Boolean.getBoolean("sgs.targetRedirectWorldRestart");}
  private static boolean restoring(){return System.getProperty("sgs.targetRedirectStage","").equals("restore");}
  private static String role(){return System.getProperty("sgs.matchRole","");}
  private static Path directory(){return Path.of(System.getProperty("sgs.targetRedirectEvidence"));}
  private static long attack(){return Long.parseLong(System.getProperty("sgs.targetRedirectAttack"));}
  private static long fee(){return Long.parseLong(System.getProperty("sgs.targetRedirectFee"));}
  private static void require(boolean yes,String why){if(!yes)throw new IllegalStateException("native target redirect: "+why);}
  private static Evidence read(String name){try{return MatchNetwork.JSON.fromJson(Files.readString(directory().resolve(name+".json")),Evidence.class);}catch(Exception error){throw new IllegalStateException(error);}}
  private static boolean rendered(MatchScreen s){if(renderBase<0)renderBase=s.completedRenderCountForSmoke();return s.completedRenderCountForSmoke()-renderBase>=3;}
  private static void next(int value){stage=value;renderBase=-1;}
  private static void capture(Minecraft c,MatchScreen s,String name){try{
    s.verifySkillGeometryForSmoke();Files.createDirectories(directory());var path=directory().resolve(role()+"-"+name+".json");var tmp=path.resolveSibling(path.getFileName()+".new");
    Files.writeString(tmp,MatchNetwork.JSON.toJson(new Evidence(System.currentTimeMillis(),c.player.getUUID().toString(),s.stateForSmoke(),MatchClient.nonceForSmoke())));
    Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);Screenshot.grab(c.gameDirectory,"sgs-target-redirect-"+name+".png",c.getMainRenderTarget(),m->{});
  }catch(Exception error){throw new IllegalStateException(error);}}
  private static void card(MatchScreen screen,long id){
    var s=screen.stateForSmoke().snapshot();var hand=s.seats().get(s.viewerSeat()).hand();int index=-1;for(int i=0;i<hand.size();i++)if(hand.get(i).instanceId()==id)index=i;
    require(index>=0,"source card missing");var box=screen.handBoundsForSmoke(index);require(box!=null&&screen.mouseClicked(box[0]+box[2]/2d,box[1]+box[3]/2d,0),"real hand hit test failed");
  }
  static boolean tick(Minecraft c,MatchScreen screen){
    if(!enabled())return false;var view=screen.stateForSmoke();var s=view.snapshot();if(s.status()!=1||s.openingHand()!=null)return false;
    c.getToasts().clear();screen.hoverForSmoke(0,0);boolean guest=role().equals("guest");require(guest||role().equals("host"),"unknown role");
    require(s.viewerSeat()==(guest?1:0)&&s.seats().size()==6&&s.seats().stream().filter(p->p.human()).count()==2,"normal seats changed");
    require(s.seats().stream().map(p->p.heroId()).toList().equals(List.of("caocao","daqiao","sunquan","huangyueying","zhouyu","lvbu")),"normal seed roster changed");
    require(s.seats().get(s.viewerSeat()).control().source().equals("MANUAL"),"human taken over");require(s.seats().stream().filter(p->!p.self()).allMatch(p->p.hand().isEmpty()),"private hand leak");
    var p=s.prompt();boolean choosing=p!=null&&p.typeOrdinal()==PromptType.LIULI_TARGET.ordinal()&&p.askerSeat()==1&&p.subjectSeat()==0&&p.skillId().equals("liuli");
    if(restoring()){
      var old=read(role()+"-before").view().snapshot();
      if(stage==0){require(choosing&&p.promptId()==old.prompt().promptId(),"original redirect token lost");require(s.processing().size()==1&&s.processing().getFirst().instanceId()==attack(),"original physical attack lost");if(!rendered(screen))return true;capture(c,screen,"restored");next(1);return true;}
      if(guest&&stage==1){
        if(!Files.exists(directory().resolve("host-restored.json"))||!rendered(screen))return true;require(choosing,"redirect resolved before actual choice");capture(c,screen,"response");
        require(screen.ownedChoiceUsesCardsForSmoke(),"owned choice duplicated as text buttons");
        int nonce=MatchClient.nonceForSmoke();card(screen,fee());
        require(screen.skillChoiceCardForSmoke()==fee() && screen.selectedCountForSmoke()==1,"real hand not lifted");
        card(screen,fee());require(screen.skillChoiceCardForSmoke()==-1 && screen.selectedCountForSmoke()==0,"second click did not cancel");
        require(!screen.enabledForSmoke("skill-choice-confirm"),"empty choice can submit");
        card(screen,fee());screen.clickSeatForSmoke(0);require(screen.targetForSmoke()==-1,"forbidden source target selected");
        screen.clickSeatForSmoke(2);require(MatchClient.nonceForSmoke()==nonce,"local selection sent input early");next(2);return true;
      }
      if(guest&&stage==2){
        if(!rendered(screen))return true;require(choosing&&screen.skillChoiceCardForSmoke()==fee()&&screen.targetForSmoke()==2,"real cost/recipient selection failed");
        require(screen.attackTargetMarkersForSmoke().containsKey(2),"recipient has no visible marker after frame");
        ProbeMod.LOG.info("SGS_OWNED_CHOICE_UI hand_hit=true cancel=true no_duplicate_buttons=true forbidden_target_rejected=true marker_after_frame=true");capture(c,screen,"chosen");
        int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("skill-choice-confirm");require(MatchClient.nonceForSmoke()==nonce+1,"selection duplicated");
        ProbeMod.LOG.info("SGS_TARGET_REDIRECT_SELECTION fee={} target=2 real_hit_test=true single_nonce=true",fee());next(3);return true;
      }
      if(guest&&stage==3||!guest&&stage==1){
        if(p!=null||!s.processing().isEmpty()||s.skillActivations().stream().noneMatch(a->a.seat()==1&&a.skillId().equals("liuli")))return true;
        require(s.currentSeat()==0&&s.phaseOrdinal()==Phase.PLAY.ordinal()&&s.turnNumber()==old.turnNumber(),"attack parent did not return");
        for(var before:old.seats()){
          var after=s.seats().get(before.seat());require(after.hp()==before.hp()-(before.seat()==2?1:0),"wrong damage recipient");
          require(after.handCount()==before.handCount()-(before.seat()==1?1:0),"wrong physical cost or unexpected AI card");
        }
        require(s.skillActivations().stream().filter(a->a.seat()==1&&a.skillId().equals("liuli")).count()==1,"redirect receipt duplicated");
        if(guest)require(s.seats().get(1).hand().stream().noneMatch(card->card.instanceId()==fee()),"fee still owned");
        if(acceptedAt==0){acceptedAt=System.currentTimeMillis()+750;renderBase=-1;return true;}if(System.currentTimeMillis()<acceptedAt||!rendered(screen))return true;
        capture(c,screen,"accepted");ProbeMod.LOG.info("SGS_TARGET_REDIRECT_PASS role={} root={} fee={} original=1 actual=2 damage=1 human_choices=1 automatic_dodge=1",role(),attack(),fee());next(4);
      }
      return true;
    }
    if(!guest&&stage==0){
      if(s.currentSeat()!=0||s.phaseOrdinal()!=Phase.PLAY.ordinal()||p!=null||!s.processing().isEmpty()||!rendered(screen))return true;
      require(s.seats().get(0).identityOrdinal()==0,"normal lord missing");card(screen,attack());screen.clickSeatForSmoke(1);next(1);return true;
    }
    if(!guest&&stage==1){if(!rendered(screen))return true;capture(c,screen,"selected");int nonce=MatchClient.nonceForSmoke();screen.clickForSmoke("play");require(MatchClient.nonceForSmoke()==nonce+1,"attack duplicated");ProbeMod.LOG.info("SGS_TARGET_REDIRECT_START root={} real_hand_hit_test=true real_target_hit_test=true single_nonce=true",attack());next(2);return true;}
    if(!choosing)return true;require(s.processing().size()==1&&s.processing().getFirst().instanceId()==attack(),"parked attack is not physical");require(s.seats().get(0).handCount()==5&&s.seats().get(1).handCount()==4,"fee paid before selection or wrong source use");
    if(guest)require(p.choices().stream().anyMatch(card->card.instanceId()==fee())&&p.allowedTargetSeats().contains(2),"real cost/recipient not offered");
    if(observedAt==0)observedAt=System.currentTimeMillis();if(!parked&&System.currentTimeMillis()-observedAt>=15000&&rendered(screen)){capture(c,screen,"before");parked=true;ProbeMod.LOG.info("SGS_TARGET_REDIRECT_PARKED role={} prompt={} held_ms={}",role(),p.promptId(),System.currentTimeMillis()-observedAt);}
    return true;
  }
}
