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

/** Test driver only: waits for an actual opponent request, never manufactures a prompt or card. */
final class ViewAsResponseSmokeChecks {
    private static int step,menuFrames,paidFrames=-1;private static long sentRevision=-1,setupPrompt=-1,card,nonce,lastActivation;
    private static MatchNetwork.View before;
    static void tick(Minecraft client)throws Exception{
        if(!Boolean.getBoolean("sgs.viewAsResponseSmoke") || !System.getProperty("sgs.worldRestartStage","").equals("restore") || step==4
            || !(client.screen instanceof MatchScreen screen))return;
        var dir=Path.of(System.getProperty("sgs.worldRestartEvidence"));if(!Files.exists(dir.resolve("accepted.json")))return;
        var view=screen.stateForSmoke();var s=view.snapshot();if(s.status()!=1)throw new IllegalStateException("ordinary match ended before converted response");
        var me=s.seats().stream().filter(p->p.self()).findFirst().orElseThrow();if(!me.alive())throw new IllegalStateException("owner died before converted response");
        if(step==1){
            if(screen.nativeConversionFramesForSmoke()<menuFrames+3)return;
            if(view.nonceFloor()!=nonce)throw new IllegalStateException("response menu sent input");
            Screenshot.grab(client.gameDirectory,"sgs-view-as-response-menu.png",client.getMainRenderTarget(),m->{});
            screen.clickForSmoke("convert:"+CardType.SHAN.ordinal());
            if(screen.declaredTypeForSmoke(card)!=CardType.SHAN.ordinal())throw new IllegalStateException("response conversion lost");
            step=2;paidFrames=screen.nativeRenderedFramesForSmoke();return;
        }
        if(step==2){if(screen.nativeRenderedFramesForSmoke()<paidFrames+3)return;screen.clickForSmoke("respond");step=3;paidFrames=-1;return;}
        if(step==3){
            if(view.nonceFloor()<=nonce || me.hand().stream().anyMatch(c->c.instanceId()==card))return;
            if(view.nonceFloor()!=nonce+1)throw new IllegalStateException("response acknowledged extra input");
            if(s.skillActivations().stream().filter(a->a.sequence()>lastActivation && a.seat()==s.viewerSeat() && a.skillId().equals("viewdemo:adapt")).count()!=1)throw new IllegalStateException("response activation differs");
            if(paidFrames<0){paidFrames=screen.nativeRenderedFramesForSmoke();return;}if(screen.nativeRenderedFramesForSmoke()<paidFrames+3)return;
            Files.writeString(dir.resolve("view-as-response.json"),MatchNetwork.JSON.toJson(new Receipt("PASS",card,nonce,view.nonceFloor(),before,view,true)));
            Screenshot.grab(client.gameDirectory,"sgs-view-as-response-paid.png",client.getMainRenderTarget(),m->{});
            ProbeMod.LOG.info("SGS_VIEW_AS_RESPONSE_PASS cost={} native_mouse=true actual_prompt=true one_input=true",card);step=4;return;
        }
        if(sentRevision==view.revision())return;
        var prompt=s.prompt();
        if(prompt!=null && prompt.askerSeat()==s.viewerSeat()){
            if(setupPrompt==prompt.promptId())return;
            if(prompt.typeOrdinal()==PromptType.DODGE_SHA.ordinal()){
                for(int i=0;i<me.hand().size();i++){
                    var cost=me.hand().get(i);if(cost.typeOrdinal()==CardType.SHAN.ordinal() || cost.playOptions().stream().noneMatch(o->o.available()&&o.declaredTypeOrdinal()==CardType.SHAN.ordinal()))continue;
                    var box=screen.handBoundsForSmoke(i);if(box==null)return;
                    before=view;card=cost.instanceId();nonce=view.nonceFloor();lastActivation=s.skillActivations().stream().mapToLong(a->a.sequence()).max().orElse(0);
                    screen.mouseClicked(box[0]+Math.max(1,box[2]/2d),box[1]+box[3]/2d,0);
                    if(screen.selectedCountForSmoke()!=1)throw new IllegalStateException("real response cost was not selectable");
                    menuFrames=screen.nativeConversionFramesForSmoke();screen.clickForSmoke("convert");step=1;return;
                }
            }
            // Drive only ordinary controls until an actual Shan request is offered.
            var physical=me.hand().stream().filter(c->prompt.allowedTypeOrdinals().contains(c.typeOrdinal())).findFirst();
            if(physical.isPresent() && screen.enabledForSmoke("respond")){screen.selectForSmoke(List.of(physical.get().instanceId()),-1);screen.clickForSmoke("respond");}
            else if(screen.enabledForSmoke("decline"))screen.clickForSmoke("decline");
            else throw new IllegalStateException("unsupported setup prompt "+prompt.typeOrdinal());
            setupPrompt=prompt.promptId();sentRevision=view.revision();return;
        }
        if(prompt!=null || s.counterspellWindow()!=null || !s.processing().isEmpty() || s.currentSeat()!=s.viewerSeat())return;
        if(s.phaseOrdinal()==Phase.PLAY.ordinal()){screen.clickForSmoke("end");sentRevision=view.revision();}
        else if(s.phaseOrdinal()==Phase.DISCARD.ordinal()){
            int count=Math.max(0,me.handCount()-Math.max(0,me.hp()));screen.selectForSmoke(me.hand().stream().limit(count).map(c->c.instanceId()).toList(),-1);screen.clickForSmoke("discard");sentRevision=view.revision();
        }
    }
    private record Receipt(String status,long physicalCard,long nonceBefore,long nonceAfter,MatchNetwork.View before,MatchNetwork.View after,boolean nativeMouse){}
}
