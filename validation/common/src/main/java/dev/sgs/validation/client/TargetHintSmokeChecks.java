package dev.sgs.validation.client;

import dev.sgs.core.card.CardType;
import dev.sgs.validation.ProbeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import java.util.List;

/** Native visual and hitbox check from a real owner-only server hint, never a fabricated card. */
final class TargetHintSmokeChecks {
    private static boolean done;
    private static long card=-1;
    private static int seat=-1,frames,selectedSeat=-1;
    static boolean tick(Minecraft client,MatchScreen screen){
        if(done)return true;
        var snapshot=screen.stateForSmoke().snapshot();
        if(selectedSeat>=0){
            if(++frames<3)return false;
            if(screen.targetForSmoke()!=selectedSeat || !screen.attackTargetMarkersForSmoke().containsKey(selectedSeat))
                throw new IllegalStateException("Ordinary selected target has no visible marker");
            Screenshot.grab(client.gameDirectory,"sgs-selected-target.png",client.getMainRenderTarget(),m->{});
            ProbeMod.LOG.info("SGS_SELECTED_TARGET_NATIVE_PASS card={} target={} real_hit_test=true",card,selectedSeat);
            screen.selectForSmoke(List.of(),-1);done=true;return false;
        }
        var hint=snapshot.selfTargetAvailability().stream().filter(h->h.reason().equals("OUT_OF_RANGE")
            && snapshot.seats().stream().filter(s->s.self()).flatMap(s->s.hand().stream()).anyMatch(c->c.instanceId()==h.cardId()&&c.typeOrdinal()==CardType.SHUNSHOU.ordinal())).findFirst();
        if(hint.isEmpty()){card=-1;frames=0;return true;}
        if(card!=hint.get().cardId() || seat!=hint.get().targetSeat()){
            card=hint.get().cardId();seat=hint.get().targetSeat();frames=0;screen.selectForSmoke(List.of(card),-1);return false;
        }
        if(++frames<3)return false;
        if(!"OUT_OF_RANGE".equals(screen.targetHintForSmoke(seat)))throw new IllegalStateException("Server distance hint not rendered for selected card");
        screen.clickSeatForSmoke(seat);
        if(screen.targetForSmoke()!=-1)throw new IllegalStateException("Out of range seat accepted by native hitbox");
        Screenshot.grab(client.gameDirectory,"sgs-target-distance-hint.png",client.getMainRenderTarget(),m->{});
        ProbeMod.LOG.info("SGS_TARGET_HINT_NATIVE_PASS card={} target={} reason=OUT_OF_RANGE blocked_click=true server_receipt=true native_ui=true",card,seat);
        var allowed=snapshot.selfTargetAvailability().stream().filter(h->h.cardId()==card && h.reason().isEmpty()).findFirst();
        if(allowed.isPresent()){
            selectedSeat=allowed.get().targetSeat();frames=0;screen.clickSeatForSmoke(selectedSeat);return false;
        }
        screen.selectForSmoke(List.of(),-1);done=true;return false;
    }
}
