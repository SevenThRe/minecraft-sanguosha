package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Real opening buttons only; card contents are supplied and changed by the server. */
final class OpeningHandSmokeChecks {
    private record Progress(int stage,int frames,int errors,boolean sent){}
    private static final Map<MatchScreen,Progress> PROGRESS=new WeakHashMap<>();
    private OpeningHandSmokeChecks(){}
    static boolean advance(Minecraft client,MatchScreen screen,boolean replace){
        var view=screen.stateForSmoke();var opening=view.snapshot().openingHand();
        if(opening==null)return false;
        if(screen.openingPendingForSmoke())return true;
        var old=PROGRESS.get(screen);
        if(old==null || old.stage()!=opening.stage()){
            PROGRESS.put(screen,new Progress(opening.stage(),0,screen.errorsForSmoke(),false));return true;
        }
        if(old.frames()<3){PROGRESS.put(screen,new Progress(opening.stage(),old.frames()+1,old.errors(),old.sent()));return true;}
        if(opening.stage()!=0 || old.sent() && old.errors()==screen.errorsForSmoke())return true;
        var own=view.snapshot().seats().stream().filter(s->s.self()).findFirst().orElseThrow();
        if(own.hand().isEmpty() || own.hand().size()!=own.handCount())throw new IllegalStateException("Initial private hand was not supplied");
        Screenshot.grab(client.gameDirectory,"sgs-opening-hand-choice.png",client.getMainRenderTarget(),m->{});
        screen.clickForSmoke(replace?"opening-replace":"opening-keep");
        ProbeMod.LOG.info("SGS_OPENING_HAND_NATIVE_INPUT action={} count={} native_ui=true",replace?"replace":"keep",own.handCount());
        PROGRESS.put(screen,new Progress(opening.stage(),old.frames(),screen.errorsForSmoke(),true));return true;
    }
}
