package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import java.util.WeakHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Driver shared by opt-in native checks; progresses only through the real visible buttons. */
final class IdentityIntroSmokeChecks {
    private record Progress(int stage,long revision,int errors,int frames){}
    private static final Map<MatchScreen,Progress> PROGRESS=new WeakHashMap<>();
    private IdentityIntroSmokeChecks(){}
    static boolean advance(Minecraft client,MatchScreen screen){
        var view=screen.stateForSmoke();var intro=view.snapshot().identityIntro();
        if(intro==null)return false;
        if((PreparationWorldRestartProbe.enabled()||GuanxingRowsSmokeChecks.enabled()) && System.getProperty("sgs.matchRole","").equals("guest")
            && intro.stage()==0 && view.snapshot().seats().get(0).identityOrdinal()<0)return true;
        if(screen.identityPendingForSmoke())return true;
        var previous=PROGRESS.get(screen);
        if(previous==null || previous.stage()!=intro.stage()){
            PROGRESS.put(screen,new Progress(intro.stage(),-1,screen.errorsForSmoke(),0));return true;
        }
        if(previous.frames()<2){
            PROGRESS.put(screen,new Progress(intro.stage(),previous.revision(),previous.errors(),previous.frames()+1));return true;
        }
        if(previous.revision()>=0 && previous.errors()==screen.errorsForSmoke())return true;
        if(intro.stage()==0){
            Screenshot.grab(client.gameDirectory,"sgs-identity-backs.png",client.getMainRenderTarget(),m->{});
            int slot=Math.floorMod(view.snapshot().viewerSeat()+1,view.snapshot().seats().size());
            screen.clickForSmoke("identity-back:"+slot);
            ProbeMod.LOG.info("SGS_IDENTITY_NATIVE_REVEAL slot={} server_assigned=true native_ui=true",slot);
        }else if(intro.stage()==1){
            if(!screen.identityRevealReadyForSmoke())return true;
            Screenshot.grab(client.gameDirectory,"sgs-identity-revealed.png",client.getMainRenderTarget(),m->{});
            int identity=view.snapshot().seats().stream().filter(s->s.self()).findFirst().orElseThrow().identityOrdinal();
            if(identity<0)throw new IllegalStateException("Revealed identity is missing from authoritative view");
            screen.clickForSmoke("identity-confirm");
            ProbeMod.LOG.info("SGS_IDENTITY_NATIVE_CONFIRM identity={} server_receipt=true native_ui=true",identity);
        }
        PROGRESS.put(screen,new Progress(intro.stage(),view.revision(),screen.errorsForSmoke(),previous.frames()));
        return true;
    }
}
