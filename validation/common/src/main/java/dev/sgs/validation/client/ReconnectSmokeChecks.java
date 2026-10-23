package dev.sgs.validation.client;

import dev.sgs.core.match.MatchAction;
import dev.sgs.core.match.dto.MatchViews.*;
import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.match.MatchNetwork;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Opt-in native reconnect probe. Its local file is evidence only, never a source of game state. */
public final class ReconnectSmokeChecks {
    private static final String ROLE=System.getProperty("sgs.reconnectStage","");
    private record Checkpoint(int schema,MatchNetwork.View view,int clientNonce){}
    private static Checkpoint checkpoint;
    private static boolean restored,captured,exiting,peerOffline,peerOnline,replaySent,replayRejected,actionConfirmed;
    private static int frames;
    private static long replayStarted;
    public static boolean isResume(){return ROLE.equals("resume");}
    private static Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("sgs-reconnect-checkpoint.json");}
    private static SeatView self(MatchSnapshot snapshot){return snapshot.seats().stream().filter(SeatView::self).findFirst().orElseThrow();}
    private static void require(boolean value,String reason){if(!value)throw new IllegalStateException(reason);}

    public static boolean received(MatchNetwork.View view){
        if(ROLE.isEmpty())return true;
        try{
            var snapshot=view.snapshot();
            if(ROLE.equals("peer") && snapshot.status()!=0){
                var host=snapshot.seats().stream().filter(s->s.name().equals("SgsLobbyHost")).findFirst();
                if(host.isPresent() && !host.get().online() && !peerOffline){peerOffline=true;ProbeMod.LOG.info("SGS_RECONNECT_PEER_OFFLINE seat={}",host.get().seat());}
                if(host.isPresent() && host.get().online() && peerOffline && !peerOnline){peerOnline=true;ProbeMod.LOG.info("SGS_RECONNECT_PEER_STATUS_PASS offline_then_online=true seat={}",host.get().seat());}
            }
            if(!isResume())return true;
            if(checkpoint==null){checkpoint=MatchNetwork.JSON.fromJson(Files.readString(file()),Checkpoint.class);require(checkpoint.schema()==1,"checkpoint schema");}
            var old=checkpoint.view();
            if(view.connectionEpoch()<=old.connectionEpoch())return false; // An already queued old connection publication.
            if(!restored){
                require(view.matchId()==old.matchId(),"reconnect changed match");
                var before=old.snapshot();
                require(snapshot.viewerSeat()==before.viewerSeat() && snapshot.currentSeat()==before.currentSeat(),"reconnect changed seats");
                require(snapshot.turnNumber()==before.turnNumber() && snapshot.phaseOrdinal()==before.phaseOrdinal(),"reconnect advanced parked settlement");
                require(Objects.equals(snapshot.prompt(),before.prompt()),"pending request/options/deadline were replaced");
                require(snapshot.phaseDeadlineMillis()==before.phaseDeadlineMillis(),"phase deadline reset");
                var me=self(snapshot);var previous=self(before);
                require(me.online() && me.hand().equals(previous.hand()) && me.identityOrdinal()==previous.identityOrdinal(),"private hand/identity not restored");
                require(me.hp()==previous.hp() && me.maxHp()==previous.maxHp() && Objects.equals(me.heroId(),previous.heroId()),"hero or health changed");
                require(view.nonceFloor()>=old.nonceFloor() && view.nonceFloor()>0,"nonce floor not retained");
                require(snapshot.prompt()!=null && snapshot.prompt().deadlineMillis()>System.currentTimeMillis(),"reconnected after request expired");
                restored=true;
                ProbeMod.LOG.info("SGS_RECONNECT_RESTORED match={} seat={} prompt={} deadline={} old_epoch={} new_epoch={} nonce_floor={} hand_count={} exact_pending_view=true",
                    view.matchId(),snapshot.viewerSeat(),snapshot.prompt().promptId(),snapshot.prompt().deadlineMillis(),old.connectionEpoch(),view.connectionEpoch(),view.nonceFloor(),me.handCount());
            }else if(!actionConfirmed && view.nonceFloor()>old.nonceFloor()
                && (snapshot.prompt()==null || snapshot.prompt().promptId()!=old.snapshot().prompt().promptId())){
                actionConfirmed=true;ProbeMod.LOG.info("SGS_RECONNECT_ACTION_CONFIRMED nonce_advanced=true old_prompt_resolved=true");
            }
            return true;
        }catch(Exception error){ProbeMod.LOG.error("SGS_RECONNECT_FAIL",error);Minecraft.getInstance().stop();return false;}
    }

    /** Return true while the probe holds a request; the ordinary UI driver then submits the reply. */
    public static boolean tick(Minecraft client,MatchScreen screen){
        if(ROLE.isEmpty() || ROLE.equals("peer"))return false;
        var view=screen.stateForSmoke();var snapshot=view.snapshot();var prompt=snapshot.prompt();
        try{
            if(ROLE.equals("initial")){
                if(exiting)return true;
                if(prompt==null || prompt.askerSeat()!=snapshot.viewerSeat())return false;
                if(++frames<3)return true;
                checkpoint=new Checkpoint(1,view,MatchClient.nonceForSmoke());
                Files.writeString(file(),MatchNetwork.JSON.toJson(checkpoint));exiting=true;
                ProbeMod.LOG.info("SGS_RECONNECT_CHECKPOINT match={} prompt={} deadline={} nonce_floor={} schema=1",view.matchId(),prompt.promptId(),prompt.deadlineMillis(),view.nonceFloor());
                Screenshot.grab(client.gameDirectory,"sgs-reconnect-before.png",client.getMainRenderTarget(),message->client.execute(client::stop));
                return true;
            }
            if(!restored)return true;
            if(!captured){
                if(++frames<3)return true;
                require(MatchClient.nonceForSmoke()>=view.nonceFloor(),"client restarted nonce below server floor");
                Screenshot.grab(client.gameDirectory,"sgs-reconnect-restored.png",client.getMainRenderTarget(),message->{});captured=true;
                MatchClient.send(MatchAction.promptResponse(checkpoint.view().nonceFloor(),prompt.promptId(),true,-1,-1));replaySent=true;replayStarted=System.currentTimeMillis();
                return true;
            }
            if(replaySent && !replayRejected){
                if(!screen.errorForSmoke().equals("REPLAY")){
                    require(System.currentTimeMillis()-replayStarted<10_000,"no replay rejection; last_error="+screen.errorForSmoke());return true;
                }
                require(prompt!=null && prompt.equals(checkpoint.view().snapshot().prompt()),"replayed message changed the restored request");
                replayRejected=true;ProbeMod.LOG.info("SGS_RECONNECT_OLD_NONCE_REJECTED prompt_preserved=true");
            }
            return false;
        }catch(Exception error){ProbeMod.LOG.error("SGS_RECONNECT_FAIL",error);client.stop();return true;}
    }
}
