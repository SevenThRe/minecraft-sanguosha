package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Checks the actual filtered selection and rendered enemy hover, without inventing teams. */
public final class TeamPresentationChecks {
    private static boolean selection,hover;private static int selectionFrames,hoverFrames,seat=-1;
    private TeamPresentationChecks(){}
    public static boolean tick(Minecraft client,MatchScreen screen){
        if(!Boolean.getBoolean("sgs.teamPresentationSmoke"))return false;
        client.getToasts().clear();
        var s=screen.stateForSmoke().snapshot();
        var me=s.seats().stream().filter(p->p.self()).findFirst().orElseThrow();
        if(s.seats().size()!=4 || me.teamId()<0)throw new IllegalStateException("missing authoritative selection seating");
        if(s.status()==0 && !selection){
            seat=me.seat();
            for(var other:s.seats())if(other.teamId()!=me.teamId() && (other.heroId()!=null || other.kingdomOrdinal()!=-1 || other.identityOrdinal()!=-1))
                throw new IllegalStateException("enemy selection leaked");
            if(++selectionFrames<4)return true;
            Screenshot.grab(client.gameDirectory,"sgs-team-selection.png",client.getMainRenderTarget(),message->{});
            selection=true;ProbeMod.LOG.info("SGS_TEAM_SELECTION_PASS seat={} public_teams=true enemy_picks_hidden=true",seat);return true;
        }
        if(s.status()!=1 || hover)return false;
        if(!selection || me.seat()!=seat)throw new IllegalStateException("selection action order changed at game start");
        if(!screen.hoverEnemyHandForSmoke())return true;
        if(++hoverFrames<4)return true;
        String tip=screen.visiblePaperTipForSmoke();
        if(!tip.startsWith("手牌 ") || tip.contains("队友") || tip.contains("共享") || tip.contains("授权"))throw new IllegalStateException("incorrect enemy hover: "+tip);
        var badges=screen.relationBadgesForSmoke();
        for(var other:s.seats())if(!(other.self()?"我":other.teamId()==me.teamId()?"友":"敌").equals(badges.get(other.seat())))throw new IllegalStateException("wrong friend/enemy identity seal");
        Screenshot.grab(client.gameDirectory,"sgs-team-enemy-hover.png",client.getMainRenderTarget(),message->{});
        screen.clearSkillFocusForSmoke();hover=true;
        ProbeMod.LOG.info("SGS_TEAM_PRESENTATION_PASS enemy_hover=count_only relative_identity_seals=true selection_seat_preserved=true");return true;
    }
}
