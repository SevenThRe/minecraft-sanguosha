package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Captures actual native skill feedback from authoritative snapshots, never fabricated notices. */
public final class SkillFeedbackChecks {
    private static boolean tooltip,timer,notice,passed;
    private static int tooltipFrames,timerFrames,noticeFrames;
    private SkillFeedbackChecks(){}
    public static boolean tick(Minecraft client,MatchScreen screen){
        if(!Boolean.getBoolean("sgs.feedbackSmoke"))return false;
        client.getToasts().clear();
        var snapshot=screen.stateForSmoke().snapshot();
        if(snapshot.status()!=1)return false;
        screen.verifySkillGeometryForSmoke();
        var layout=screen.decisionLayoutForSmoke();
        var portrait=dev.sgs.validation.client.layout.ReferenceBattleLayout.fit(client.getWindow().getGuiScaledWidth(),client.getWindow().getGuiScaledHeight()).self();
        if(layout.skillRow().bottom()>portrait.bottom() || layout.skillRow().y()<portrait.y()
            || layout.timer().bottom()>=layout.confirm().y())throw new IllegalStateException("skill/footer or timer geometry incorrect");
        if(!tooltip && screen.focusSkillForSmoke()){
            if(++tooltipFrames<3)return true;
            Screenshot.grab(client.gameDirectory,"sgs-skill-tooltip.png",client.getMainRenderTarget(),message->{});
            screen.clearSkillFocusForSmoke();tooltip=true;ProbeMod.LOG.info("SGS_SKILL_TOOLTIP_CAPTURED embedded_footer=true native_component=true");return true;
        }
        if(!timer && snapshot.prompt()!=null && snapshot.prompt().askerSeat()==snapshot.viewerSeat()){
            if(++timerFrames<3)return true;
            if(snapshot.prompt().deadlineMillis()<=System.currentTimeMillis())throw new IllegalStateException("captured expired decision");
            Screenshot.grab(client.gameDirectory,"sgs-skill-decision.png",client.getMainRenderTarget(),message->{});
            timer=true;ProbeMod.LOG.info("SGS_SKILL_TIMER_CAPTURED server_deadline={} prompt={}",snapshot.prompt().deadlineMillis(),snapshot.prompt().promptId());return true;
        }
        if(!notice && screen.skillNoticeForSmoke()){
            if(++noticeFrames<3)return true;
            if(!screen.skillPortraitSettledForSmoke())return true;
            if(!screen.skillPortraitForSmoke())throw new IllegalStateException("activation banner missing actor portrait overlay");
            Screenshot.grab(client.gameDirectory,"sgs-skill-announcement.png",client.getMainRenderTarget(),message->{});
            notice=true;ProbeMod.LOG.info("SGS_SKILL_ANNOUNCEMENT_CAPTURED authoritative_event=true actor_portrait=true");return true;
        }
        if(tooltip && timer && notice && !passed){passed=true;ProbeMod.LOG.info("SGS_SKILL_FEEDBACK_PASS tooltip=true embedded=true timer=true announcement=true");}
        return false;
    }
}
