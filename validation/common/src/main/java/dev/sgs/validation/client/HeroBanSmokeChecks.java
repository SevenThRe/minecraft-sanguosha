package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import java.util.*;

/** Opt-in native ban flow; uses offered IDs and real widget clicks. */
final class HeroBanSmokeChecks {
    private static boolean submitted,resultCaptured,done;
    private static int frames,resultFrames,offerFrames;
    private static String chosen;
    private static final Set<String> banned=new HashSet<>();
    static boolean advance(Minecraft client,MatchScreen screen){
        if(!Boolean.getBoolean("sgs.heroBanSmoke") || done)return false;
        var s=screen.stateForSmoke().snapshot();var ban=s.heroBan();
        if(ban==null){
            if(s.heroCandidates().isEmpty())return true;
            if(++offerFrames<3)return true;
            if(!submitted || !resultCaptured || s.heroCandidates().size()!=4 || !Collections.disjoint(s.heroCandidates(),banned))
                throw new IllegalStateException("Ban flow did not produce four unbanned candidates");
            Screenshot.grab(client.gameDirectory,"sgs-ban-filtered-offer.png",client.getMainRenderTarget(),m->{});
            ProbeMod.LOG.info("SGS_HERO_BAN_NATIVE_PASS choice={} results={} candidates=4 real_widgets=true",chosen,banned.size());
            done=true;if(Boolean.getBoolean("sgs.heroBanOnly"))client.stop();return true;
        }
        if(ban.stage()==0){
            if(++frames<4 || submitted)return true;
            if(ban.candidates().size()!=10)throw new IllegalStateException("Expected ten ban candidates");
            client.getToasts().clear();Screenshot.grab(client.gameDirectory,"sgs-ban-candidates.png",client.getMainRenderTarget(),m->{});
            chosen=ban.candidates().getFirst();screen.clickForSmoke("ban:"+chosen);submitted=true;return true;
        }
        if(++resultFrames<3 || resultCaptured)return true;
        if(ban.choices().size()!=s.seats().size() || ban.choices().stream().noneMatch(c->c.seat()==s.viewerSeat()&&c.heroId().equals(chosen)))
            throw new IllegalStateException("Server did not acknowledge the chosen ban");
        ban.choices().forEach(c->banned.add(c.heroId()));
        Screenshot.grab(client.gameDirectory,"sgs-ban-results.png",client.getMainRenderTarget(),m->{});resultCaptured=true;return true;
    }
}
