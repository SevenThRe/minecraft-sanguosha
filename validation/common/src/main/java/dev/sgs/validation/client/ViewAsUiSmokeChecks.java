package dev.sgs.validation.client;

import dev.sgs.core.card.CardType;
import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.match.MatchNetwork;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Opt-in real owner card, rendered menu, mouse input and ordinary transport. No state injection. */
final class ViewAsUiSmokeChecks {
    private static int step,frames,menuFrameBase,paidFrameBase=-1;private static long card,nonce;private static MatchNetwork.View before;
    static boolean tick(Minecraft client,MatchScreen screen)throws Exception{
        if(!Boolean.getBoolean("sgs.viewAsUiSmoke") || !System.getProperty("sgs.worldRestartStage","").equals("initial"))return true;
        var view=screen.stateForSmoke();var s=view.snapshot();var me=s.seats().stream().filter(p->p.self()).findFirst().orElseThrow();
        if(step==0){
            for(int i=0;i<me.hand().size();i++){
                var c=me.hand().get(i);
                if(c.typeOrdinal()==CardType.SHA.ordinal() || c.playOptions().stream().noneMatch(o->o.available()&&o.declaredTypeOrdinal()==CardType.SHA.ordinal()))continue;
                var box=screen.handBoundsForSmoke(i);if(box==null)return false;
                before=view;card=c.instanceId();nonce=view.nonceFloor();
                screen.mouseClicked(box[0]+Math.max(1,box[2]/2d),box[1]+box[3]/2d,0);
                if(screen.selectedCountForSmoke()!=1)throw new IllegalStateException("actual convertible hand click failed");
                menuFrameBase=screen.nativeConversionFramesForSmoke();screen.clickForSmoke("convert");step=1;return false;
            }
            throw new IllegalStateException("ordinary opening hand has no convertible attack cost");
        }
        if(step==1){
            if(screen.nativeConversionFramesForSmoke()<menuFrameBase+3)return false;
            Screenshot.grab(client.gameDirectory,"sgs-view-as-menu.png",client.getMainRenderTarget(),m->{});
            if(view.nonceFloor()!=nonce)throw new IllegalStateException("opening conversion menu sent an input");
            screen.clickForSmoke("convert:"+CardType.SHA.ordinal());
            if(screen.declaredTypeForSmoke(card)!=CardType.SHA.ordinal())throw new IllegalStateException("generic conversion selection lost");
            step=2;frames=0;return false;
        }
        if(step==2){
            if(++frames<4)return false;
            screen.clickSeatForSmoke(1);screen.clickForSmoke("play");step=3;return false;
        }
        if(step==3){
            if(view.nonceFloor()<=nonce || s.prompt()!=null || s.counterspellWindow()!=null || !s.processing().isEmpty())return false;
            if(paidFrameBase<0){paidFrameBase=screen.nativeRenderedFramesForSmoke();return false;}
            if(screen.nativeRenderedFramesForSmoke()<paidFrameBase+3)return false;
            if(view.nonceFloor()!=nonce+1 || me.hand().stream().anyMatch(c->c.instanceId()==card))throw new IllegalStateException("conversion did not acknowledge one input and consume its physical source");
            if(s.skillActivations().stream().filter(a->a.skillId().equals("viewdemo:adapt")&&a.seat()==s.viewerSeat()).count()!=1)throw new IllegalStateException("external transform activation differs");
            var out=Path.of(System.getProperty("sgs.worldRestartEvidence")).resolve("view-as-ui.json");
            Files.writeString(out,MatchNetwork.JSON.toJson(new Receipt("PASS",card,nonce,view.nonceFloor(),before,view,true)));
            Screenshot.grab(client.gameDirectory,"sgs-view-as-paid.png",client.getMainRenderTarget(),m->{});
            ProbeMod.LOG.info("SGS_VIEW_AS_UI_PASS external=true native_mouse=true menu_sent_no_input=true cost={} input_count=1",card);step=4;
        }
        return true;
    }
    private record Receipt(String status,long physicalCard,long nonceBefore,long nonceAfter,MatchNetwork.View before,MatchNetwork.View after,boolean nativeMouse){}
}
