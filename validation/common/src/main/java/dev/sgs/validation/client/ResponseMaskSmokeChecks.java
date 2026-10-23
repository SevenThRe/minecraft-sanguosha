package dev.sgs.validation.client;

import dev.sgs.core.card.CardType;
import dev.sgs.core.engine.PromptType;
import dev.sgs.validation.ProbeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** A real counterspell request must mask and reject clicking a non-response hand card. */
final class ResponseMaskSmokeChecks {
    private static boolean done;
    private static long prompt=-1;
    private static int frames;
    static boolean tick(Minecraft client,MatchScreen screen){
        if(done)return false;
        var s=screen.stateForSmoke().snapshot();var request=s.prompt();
        if(request==null || request.askerSeat()!=s.viewerSeat() || request.typeOrdinal()!=PromptType.WUXIE_COUNTER.ordinal())return false;
        var own=s.seats().stream().filter(seat->seat.self()).findFirst().orElseThrow();
        int index=-1;for(int i=0;i<own.hand().size();i++)if(own.hand().get(i).typeOrdinal()!=CardType.WUXIE.ordinal()){index=i;break;}
        if(index<0)return false;
        if(prompt!=request.promptId()){prompt=request.promptId();frames=0;return true;}
        if(++frames<2)return true;
        var invalid=own.hand().get(index);if(screen.responseCardSelectableForSmoke(invalid.instanceId()))throw new IllegalStateException("Non-Wuxie is selectable in the actual counterspell request");
        for(var card:own.hand())if(card.typeOrdinal()==CardType.WUXIE.ordinal() && !screen.responseCardSelectableForSmoke(card.instanceId()))throw new IllegalStateException("Real Wuxie is masked");
        var hit=screen.handBoundsForSmoke(index);if(hit==null)return true;
        int before=screen.selectedCountForSmoke();screen.mouseClicked(hit[0]+Math.max(1,hit[2]/2d),hit[1]+hit[3]/2d,0);
        if(screen.selectedCountForSmoke()!=before)throw new IllegalStateException("Masked hand card was selected through its hitbox");
        Screenshot.grab(client.gameDirectory,"sgs-response-mask-wuxie.png",client.getMainRenderTarget(),m->{});
        ProbeMod.LOG.info("SGS_RESPONSE_MASK_NATIVE_PASS prompt={} blocked_card={} allowed_type=WUXIE actual_wuxie_count={} blocked_click=true native_ui=true",prompt,invalid.instanceId(),own.hand().stream().filter(c->c.typeOrdinal()==CardType.WUXIE.ordinal()).count());
        done=true;return true;
    }
}
