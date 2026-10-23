package dev.sgs.validation.client;

import dev.sgs.core.Phase;
import dev.sgs.validation.ProbeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Actual owner snapshot, actual hand hitbox, no manufactured usage counter or card. */
final class CardAvailabilityChecks {
    private static boolean done;private static long revision=-1;private static int frames;
    private static final java.util.Set<String> limits=new java.util.HashSet<>();
    private static long limitRevision=-1;private static int limitFrames;
    static boolean tick(Minecraft client,MatchScreen screen){
        if(!Boolean.getBoolean("sgs.cardAvailabilitySmoke"))return false;
        var view=screen.stateForSmoke();var s=view.snapshot();
        if(s.status()!=1 || s.prompt()!=null || s.counterspellWindow()!=null || !s.processing().isEmpty()
            || s.currentSeat()!=s.viewerSeat() || s.phaseOrdinal()!=Phase.PLAY.ordinal())return false;
        var me=s.seats().stream().filter(p->p.self()).findFirst().orElseThrow();
        if(done){
            for(int i=0;i<me.hand().size();i++){
                var card=me.hand().get(i);var quota=card.playOptions().stream().filter(o->o.declaredTypeOrdinal()==card.typeOrdinal()
                    && !o.available() && java.util.Set.of("SHA_LIMIT","JIU_LIMIT").contains(o.reason()) && !limits.contains(o.reason())).findFirst();
                if(quota.isEmpty())continue;
                if(screen.handCardSelectableForSmoke(card.instanceId()))throw new IllegalStateException("spent quota card remains selectable");
                if(limitRevision!=view.revision()){limitRevision=view.revision();limitFrames=0;return true;}
                if(++limitFrames<3)return true;
                var hit=screen.handBoundsForSmoke(i);if(hit==null)return true;
                int before=screen.selectedCountForSmoke();screen.mouseClicked(hit[0]+Math.max(1,hit[2]/2d),hit[1]+hit[3]/2d,0);
                if(screen.selectedCountForSmoke()!=before)throw new IllegalStateException("spent quota card selected by click");
                String reason=quota.get().reason();limits.add(reason);
                Screenshot.grab(client.gameDirectory,"sgs-card-disabled-"+reason.toLowerCase(java.util.Locale.ROOT)+".png",client.getMainRenderTarget(),m->{});
                ProbeMod.LOG.info("SGS_CARD_LIMIT_NATIVE_PASS card={} reason={} disabled_click=true actual_match=true",card.instanceId(),reason);return true;
            }
            return false;
        }
        int blocked=-1;
        for(int i=0;i<me.hand().size();i++){
            var c=me.hand().get(i);var physical=c.playOptions().stream().filter(o->o.declaredTypeOrdinal()==c.typeOrdinal()).findFirst();
            if(physical.isPresent() && !physical.get().available() && !screen.handCardSelectableForSmoke(c.instanceId())){blocked=i;break;}
        }
        if(blocked<0)return false;
        if(revision!=view.revision()){revision=view.revision();frames=0;screen.selectForSmoke(java.util.List.of(),-1);return true;}
        if(++frames<3)return true;
        var card=me.hand().get(blocked);var bounds=screen.handBoundsForSmoke(blocked);if(bounds==null)return true;
        screen.mouseClicked(bounds[0]+Math.max(1,bounds[2]/2d),bounds[1]+bounds[3]/2d,0);
        if(screen.selectedCountForSmoke()!=0)throw new IllegalStateException("disabled play card selected through actual hitbox");
        boolean costModeChecked=false;
        var costSkill=s.heroPresentations().stream().filter(h->h.id().equals(me.heroId())).flatMap(h->h.skills().stream())
            .filter(m->m.minimumCards()>0 && (m.kind()==dev.sgs.core.addon.SkillPresentation.Kind.ACTIVE || m.kind()==dev.sgs.core.addon.SkillPresentation.Kind.LIMITED))
            .filter(m->s.selfSkillAvailability().stream().noneMatch(a->a.skillId().equals(m.id()) && !a.available())).findFirst();
        if(costSkill.isPresent()){
            screen.pressForSmoke(costSkill.get().id().equals("rende")?"skill":costSkill.get().id());
            if(!screen.handCardSelectableForSmoke(card.instanceId()))throw new IllegalStateException("legal skill cost remained disabled");
            screen.pressForSmoke("cancel");
            if(screen.handCardSelectableForSmoke(card.instanceId()))throw new IllegalStateException("cancelling cost mode did not restore disabled play state");
            costModeChecked=true;
        }
        Screenshot.grab(client.gameDirectory,"sgs-card-availability.png",client.getMainRenderTarget(),m->{});
        ProbeMod.LOG.info("SGS_CARD_AVAILABILITY_PASS card={} type={} reason={} disabled_click=true source=server_play_options native_ui=true skill_cost_mode={}",
            card.instanceId(),card.typeOrdinal(),card.playOptions().stream().filter(o->o.declaredTypeOrdinal()==card.typeOrdinal()).findFirst().orElseThrow().reason(),costModeChecked);
        screen.pressForSmoke("cancel");done=true;return true;
    }
}
