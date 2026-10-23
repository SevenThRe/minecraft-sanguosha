package dev.sgs.validation.client;

import dev.sgs.core.Phase;
import dev.sgs.core.card.CardType;
import dev.sgs.core.engine.PromptType;
import dev.sgs.core.match.dto.MatchViews.*;
import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.match.MatchNetwork;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Normal two-human military-six room. Only owned snapshots and real UI hit tests. */
final class FangtianSmokeChecks {
    private static int stage,frames,acceptedFrames,originalScale;private static long sha,nonceFloor;private static boolean guestDone,terminalProbe,terminalCaptured;
    private static MatchSnapshot before;
    private static String terminalSkin="";
    private static final Set<CardType> SHA=Set.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA);
    private FangtianSmokeChecks(){}
    static boolean tick(Minecraft c,MatchScreen screen){
        if(!Boolean.getBoolean("sgs.fangtianSmoke"))return false;
        var view=screen.stateForSmoke();var s=view.snapshot();var me=s.seats().stream().filter(SeatView::self).findFirst().orElseThrow();
        if(s.status()!=1 || s.openingHand()!=null)return false;
        if(c.getWindow().getGuiScaledWidth()<80 || c.getWindow().getGuiScaledHeight()<80)return true;
        c.getToasts().clear();
        if(FangtianWorldRestartProbe.tick(c,screen))return true;
        if(FangtianWorldRestartProbe.restoring() && before==null && !System.getProperty("sgs.matchRole","").equals("guest")){
            var initial=FangtianWorldRestartProbe.selected();before=initial.snapshot();nonceFloor=initial.nonceFloor();sha=before.seats().get(0).hand().getFirst().instanceId();stage=5;
        }
        if(System.getProperty("sgs.matchRole","").equals("guest")){
            if(guestDone){
                if(!terminalProbe && s.prompt()!=null && s.prompt().askerSeat()==s.viewerSeat()
                    && Set.of(PromptType.DODGE_SHA.ordinal(),PromptType.DUEL_SHA.ordinal(),PromptType.RESPOND_SHA_NANMAN.ordinal()).contains(s.prompt().typeOrdinal())){
                    terminalSkin=screen.skinForSmoke();int[] strip=screen.skinStripForSmoke();require(screen.mouseClicked(strip[0],strip[1],0) && screen.overlayForSmoke(),"real skin strip did not open");
                    screen.clickForSmoke("skin:"+(terminalSkin.equals("classic")?"action":"classic"));require(screen.skinForSmoke().equals(terminalSkin),"skin preview was committed without Apply");
                    terminalProbe=true;frames=0;ProbeMod.LOG.info("SGS_TERMINAL_MODAL_PROBE role=guest modal=SKINS real_hit_test=true hold_until_finish=true");
                }
                if(terminalProbe){if(!terminalCaptured && ++frames>=4){capture(c,screen,"guest-terminal-skins");terminalCaptured=true;}return true;}
                return false;
            }
            var p=s.prompt();
            if(p!=null && p.askerSeat()==s.viewerSeat() && p.typeOrdinal()==PromptType.DODGE_SHA.ordinal()
                && s.seats().get(0).weapon()!=null && s.seats().get(0).weapon().typeOrdinal()==CardType.FANGTIAN.ordinal()){
                if(++frames<5)return true;
                capture(c,screen,"guest-response");screen.clickForSmoke("decline");guestDone=true;
                ProbeMod.LOG.info("SGS_FANGTIAN_NATIVE_GUEST prompt={} real_hit_test=true decline_once=true",p.promptId());
            }
            return true;
        }
        if(stage==6)return FangtianWorldRestartProbe.enabled();
        require(me.seat()==0 && me.heroId().equals("liubei") && me.identityOrdinal()==0,"not actual human lord Liu Bei");
        require(s.seats().size()==6 && s.seats().stream().filter(SeatView::human).count()==2,"not normal two-human military-six");
        if(stage==5){
            if(s.prompt()!=null || !s.processing().isEmpty())return true;
            if(++acceptedFrames<4)return true;
            require(me.handCount()==0,"last physical Sha was not consumed");require(view.nonceFloor()==nonceFloor+1,"not one source intent");
            require(s.turnNumber()==before.turnNumber() && s.currentSeat()==0 && s.phaseOrdinal()==Phase.PLAY.ordinal(),"changed turn during one Sha");
            for(int seat=1;seat<=3;seat++)require(s.seats().get(seat).hp()==before.seats().get(seat).hp()-1,"target not independently damaged: "+seat);
            for(int seat=4;seat<6;seat++)require(s.seats().get(seat).hp()==before.seats().get(seat).hp(),"unselected target damaged");
            long previous=before.publicMoves().stream().mapToLong(PublicMoveView::sequence).max().orElse(0);
            if(FangtianWorldRestartProbe.restoring())FangtianWorldRestartProbe.verifyCost(screen,sha);
            else require(s.publicMoves().stream().filter(m->m.sequence()>previous && m.reason().equals("USE") && m.actorSeat()==0 && m.cards().stream().anyMatch(card->card.instanceId()==sha)).count()==1,"not one real use cost");
            require(s.skillActivations().stream().filter(v->v.seat()==0&&v.skillId().equals("fangtian")).count()==1,"not one Fangtian activation");
            for(int seat=1;seat<=3;seat++){int target=seat;require(s.logLines().stream().filter(line->line.startsWith("seat "+target+" was hit by ")&&line.endsWith("sha from seat 0 for 1")).count()==1,"missing unique damage log");}
            require(screen.attackTargetMarkersForSmoke().isEmpty(),"target markers remain after payment");
            capture(c,screen,"accepted");ProbeMod.LOG.info("SGS_FANGTIAN_NATIVE_PASS targets=1,2,3 last_hand=true one_cost=true one_nonce=true each_hp_delta=-1 unselected_unchanged=true real_hit_test=true");stage=6;return true;
        }
        if(s.currentSeat()!=0 || s.phaseOrdinal()!=Phase.PLAY.ordinal() || s.prompt()!=null || !s.processing().isEmpty())return true;
        if(screen.handBoundsForSmoke(0)==null)return true;
        if(stage==0){
            originalScale=c.options.guiScale().get();
            var weapon=me.hand().stream().filter(card->card.typeOrdinal()==CardType.FANGTIAN.ordinal()).findFirst().orElseThrow(()->new IllegalStateException("legal seed has no Fangtian"));
            sha=me.hand().stream().filter(card->SHA.contains(CardType.values()[card.typeOrdinal()])).findFirst().orElseThrow().instanceId();
            clickCard(screen,me,weapon.instanceId());screen.clickForSmoke("play");stage=1;return true;
        }
        if(stage==1){
            if(me.weapon()==null || me.weapon().typeOrdinal()!=CardType.FANGTIAN.ordinal())return true;
            screen.clickForSmoke("cancel");screen.clickForSmoke("skill");stage=2;return true;
        }
        if(stage==2){
            for(var card:me.hand())if(card.instanceId()!=sha)clickCard(screen,me,card.instanceId());
            require(screen.selectedCountForSmoke()==me.handCount()-1,"Rende did not select exact other hand cards");
            screen.clickSeatForSmoke(5);screen.clickForSmoke("skill");stage=3;return true;
        }
        if(stage==3){
            if(me.handCount()!=1)return true;require(me.hand().getFirst().instanceId()==sha,"Rende lost the intended Sha");
            screen.clickForSmoke("cancel");clickCard(screen,me,sha);require(screen.fangtianSelectionForSmoke(),"not real last-hand multi-target UI");
            for(int seat:List.of(3,1,2))screen.clickSeatForSmoke(seat);
            require(screen.attackTargetsForSmoke().size()==3,"three targets not selected");stage=31;frames=0;return true;
        }
        if(stage==31){
            if(++frames<3)return true;
            require(screen.attackTargetMarkersForSmoke().equals(Map.of(1,1,2,2,3,3)),"marker order follows clicks instead of settlement");
            capture(c,screen,"selected-initial");screen.clickSeatForSmoke(2);stage=32;frames=0;return true;
        }
        if(stage==32){
            if(++frames<3)return true;
            require(screen.attackTargetsForSmoke().equals(Set.of(1,3)) && screen.attackTargetMarkersForSmoke().equals(Map.of(1,1,3,2)),"deselected target marker remains or order not refreshed");
            capture(c,screen,"selected-deselected");screen.clickSeatForSmoke(2);stage=33;frames=0;return true;
        }
        if(stage==33){
            if(++frames<3)return true;
            require(screen.attackTargetMarkersForSmoke().equals(Map.of(1,1,2,2,3,3)),"reselected target labels not restored");
            screen.clickSeatForSmoke(4);require(screen.attackTargetsForSmoke().equals(Set.of(1,2,3)),"fourth target was accepted");
            if(Boolean.getBoolean("sgs.targetMarkersSmoke")){c.options.guiScale().set(1);c.resizeDisplay();stage=34;}else stage=4;frames=0;return true;
        }
        if(stage>=34 && stage<=36){
            if(++frames<3)return true;int scale=stage-33;
            require(screen.attackTargetMarkersForSmoke().equals(Map.of(1,1,2,2,3,3)),"target labels missing at GUI scale "+scale);
            capture(c,screen,"target-markers-scale-"+scale);
            ProbeMod.LOG.info("SGS_TARGET_MARKERS_SCALE_PASS gui_scale={} labels=1,2,3 visible=true",scale);
            if(stage==36){c.options.guiScale().set(originalScale);c.resizeDisplay();stage=4;}else{c.options.guiScale().set(scale+1);c.resizeDisplay();stage++;}
            frames=0;return true;
        }
        if(stage==4){
            if(++frames<4)return true;require(screen.attackTargetMarkersForSmoke().equals(Map.of(1,1,2,2,3,3)),"final markers changed");ProbeMod.LOG.info("SGS_TARGET_MARKERS_NATIVE_PASS click_order=3,1,2 label_order=1,2,3 deselect_renumber=true reselect=true fourth_rejected=true");before=s;nonceFloor=view.nonceFloor();capture(c,screen,"selected");
            screen.clickForSmoke("play");ProbeMod.LOG.info("SGS_FANGTIAN_NATIVE_INPUT card={} targets=1,2,3 toggle=true fourth_rejected=true real_hit_test=true",sha);stage=5;return true;
        }
        return true;
    }
    static boolean completed(){return stage==6;}
    static boolean terminalProbeObserved(){return terminalProbe;}
    static boolean terminalDraftPreserved(MatchScreen screen){return screen.skinForSmoke().equals(terminalSkin);}
    private static void clickCard(MatchScreen screen,SeatView me,long id){
        int index=-1;for(int i=0;i<me.hand().size();i++)if(me.hand().get(i).instanceId()==id)index=i;
        var box=screen.handBoundsForSmoke(index);require(box!=null,"card hitbox not rendered");
        require(screen.mouseClicked(box[0]+box[2]/2d,box[1]+box[3]/2d,0),"real hand mouse click rejected");
    }
    private static void capture(Minecraft c,MatchScreen screen,String name){
        FangtianWorldRestartProbe.capture(c,screen,name);
        Screenshot.grab(c.gameDirectory,"sgs-fangtian-"+name+".png",c.getMainRenderTarget(),m->{});
        try{Files.writeString(c.gameDirectory.toPath().resolve("sgs-fangtian-"+name+".json"),MatchNetwork.JSON.toJson(screen.stateForSmoke()));}catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
