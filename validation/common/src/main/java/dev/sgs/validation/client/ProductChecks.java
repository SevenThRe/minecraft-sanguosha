package dev.sgs.validation.client;
import dev.sgs.validation.ProbeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
/** Real report/reward/third-party skill actions using only this connection's authorized views. */
public final class ProductChecks {
    private static boolean terminalValidated;
    private static int stage,frames;private static long started;private static boolean skillSent,skillConfirmed,rewardCaptured;
    private static int resultFrames,resultSeenStage=-1;
    private static boolean detailsCaptured;
    private ProductChecks(){}
    public static boolean tick(Minecraft client,MatchScreen screen){
        if(!Boolean.getBoolean("sgs.productSmoke"))return false;
        var view=screen.stateForSmoke();var s=view.snapshot();
        if(s.status()==0)return false;
        if(s.status()==2){
            if(Boolean.getBoolean("sgs.fangtianSmoke") && FangtianSmokeChecks.terminalProbeObserved() && !terminalValidated){
                if(screen.overlayForSmoke() || !screen.enabledForSmoke("result-next") || !FangtianSmokeChecks.terminalDraftPreserved(screen))throw new IllegalStateException("terminal modal still blocks result");
                terminalValidated=true;ProbeMod.LOG.info("SGS_TERMINAL_MODAL_PASS previous=SKINS verdict_controls_visible=true draft_not_applied=true");
            }
            var receipt=FeedbackClient.reward(view.matchId());
            if(receipt==null){if(started==0){started=System.currentTimeMillis();FeedbackClient.request(view.matchId(),"QUERY_RESULT",-1,"","");}if(System.currentTimeMillis()-started>10000)throw new IllegalStateException("no server reward receipt");return true;}
            int page=screen.resultStageForSmoke();
            if(page!=resultSeenStage){resultSeenStage=page;resultFrames=0;return true;}
            if(++resultFrames<4)return true;
            if(page==0){
                Screenshot.grab(client.gameDirectory,"sgs-product-verdict.png",client.getMainRenderTarget(),message->{});
                screen.clickForSmoke("result-next");return true;
            }
            if(page==1 && !rewardCaptured){
                Screenshot.grab(client.gameDirectory,"sgs-product-reward.png",client.getMainRenderTarget(),message->{});rewardCaptured=true;
                ProbeMod.LOG.info("SGS_PRODUCT_REWARD_PASS match={} xp={} honor={} receipt={}",view.matchId(),receipt.xpAward(),receipt.honorAward(),receipt.receiptId());
                screen.clickForSmoke("result-query");screen.clickForSmoke("result-details");return true;
            }
            if(page==2 && !detailsCaptured){
                Screenshot.grab(client.gameDirectory,"sgs-product-details.png",client.getMainRenderTarget(),message->{});detailsCaptured=true;
                ProbeMod.LOG.info("SGS_PRODUCT_RESULT_FLOW_PASS match={} seats={} verdict=true reward_receipt={} details=true native_ui=true",view.matchId(),s.seats().size(),receipt.receiptId());return true;
            }
            if(Boolean.getBoolean("sgs.addonSmoke") && System.getProperty("sgs.matchRole","").equals("host") && !skillConfirmed)throw new IllegalStateException("external skill never confirmed");
            return false;
        }
        if(stage==0){screen.pressForSmoke("report");stage=1;started=System.currentTimeMillis();return true;}
        if(stage==1){
            var other=s.seats().stream().filter(p->p.human() && !p.self()).findFirst().orElseThrow();
            screen.pressForSmoke("report-target:"+other.seat());screen.pressForSmoke("report-reason:OTHER");screen.pressForSmoke("report-submit");stage=2;return true;
        }
        if(stage==2){
            if(!screen.feedbackForSmoke().contains("举报已提交")){if(System.currentTimeMillis()-started>10000)throw new IllegalStateException("report not acknowledged: "+screen.feedbackForSmoke());return true;}
            if(++frames<3)return true;
            Screenshot.grab(client.gameDirectory,"sgs-product-report.png",client.getMainRenderTarget(),message->{});
            ProbeMod.LOG.info("SGS_PRODUCT_REPORT_PASS match={} server_acknowledged=true",view.matchId());screen.pressForSmoke("overlay-close");stage=3;frames=0;started=0;return true;
        }
        var me=s.seats().stream().filter(p->p.self()).findFirst().orElseThrow();
        if(Boolean.getBoolean("sgs.addonSmoke") && System.getProperty("sgs.matchRole","").equals("host")){
            if(!me.heroId().equals("example:quartermaster"))throw new IllegalStateException("external hero not selected from legal offer");
            if(!skillSent && s.prompt()==null && s.currentSeat()==s.viewerSeat() && s.phaseOrdinal()==dev.sgs.core.Phase.PLAY.ordinal()){
                screen.pressForSmoke("example:resupply");skillSent=true;return true;
            }
            if(skillSent && !skillConfirmed && s.skillActivations().stream().anyMatch(a->a.seat()==s.viewerSeat() && a.skillId().equals("example:resupply"))){
                skillConfirmed=true;ProbeMod.LOG.info("SGS_PRODUCT_ADDON_PASS namespaced_hero=true generic_widget=true paid_effect=true");
                Screenshot.grab(client.gameDirectory,"sgs-product-addon.png",client.getMainRenderTarget(),message->{});
            }
        }
        return false;
    }
}
