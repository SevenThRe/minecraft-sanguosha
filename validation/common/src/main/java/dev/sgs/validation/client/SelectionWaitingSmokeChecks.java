package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** One real human deliberately waits before choosing; the peer must see authoritative waiting. */
final class SelectionWaitingSmokeChecks {
    private static long offerAt;
    private static int frames;
    private static boolean captured;
    private SelectionWaitingSmokeChecks(){}
    static boolean tick(Minecraft client,MatchScreen screen){
        if(!Boolean.getBoolean("sgs.selectionWaitSmoke"))return false;
        var snapshot=screen.stateForSmoke().snapshot();
        if(snapshot.status()!=0 || snapshot.identityIntro()!=null || snapshot.seats().size()==4)return false;
        var own=snapshot.seats().stream().filter(s->s.self()).findFirst().orElseThrow();
        if(System.getProperty("sgs.matchRole","").equals("guest") && own.heroId()==null && !snapshot.heroCandidates().isEmpty()){
            if(offerAt==0)offerAt=System.currentTimeMillis();
            return System.currentTimeMillis()-offerAt<2500;
        }
        if(!System.getProperty("sgs.matchRole","").equals("host") || own.heroId()==null || captured)return false;
        if(++frames<3)return true;
        if(screen.candidateControlCountForSmoke()!=0)throw new IllegalStateException("Hidden candidate buttons remain clickable in waiting table");
        if(snapshot.seats().stream().anyMatch(s->!s.self() && s.heroId()!=null))throw new IllegalStateException("Private opponent selection leaked");
        Screenshot.grab(client.gameDirectory,"sgs-selection-waiting.png",client.getMainRenderTarget(),m->{});captured=true;
        ProbeMod.LOG.info("SGS_SELECTION_WAIT_NATIVE_PASS seats={} hidden_candidate_controls=0 own_hero={} opponent_heroes_private=true native_ui=true",snapshot.seats().size(),own.heroId());return true;
    }
}
