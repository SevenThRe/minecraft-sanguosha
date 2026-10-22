package dev.sgs.validation.match;

import dev.sgs.core.persistence.*;
import dev.sgs.core.room.Room;
import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.lobby.LobbyService;
import dev.sgs.validation.social.FeedbackData;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Native lifecycle adapter. Rooms and supported live programs commit as one private world file. */
public final class WorldMatchPersistence {
    private static final Set<MinecraftServer> BLOCKED=Collections.newSetFromMap(new IdentityHashMap<>());
    private WorldMatchPersistence(){}
    private static RoomMatchArchiveStore store(MinecraftServer server)throws java.io.IOException{
        return new RoomMatchArchiveStore(server.getWorldPath(LevelResource.ROOT).resolve("data/sgs_rooms"));
    }
    public static void start(MinecraftServer server){
        try{
            var store=store(server);var archives=new ArrayList<RoomMatchArchive>();
            for(long id:store.ids())archives.add(store.load(id).orElseThrow());
            if(archives.isEmpty())return;
            // Fail before publishing any rooms if the settlement identity/seat assignment differs.
            var ledger=FeedbackData.get(server).ledger();
            for(var archive:archives)if(archive.match()!=null){
                var saved=archive.match();var record=ledger.match(saved.roomId());
                if(record==null || record.outcome()!=dev.sgs.core.engine.GameOutcome.ONGOING
                    || !new HashSet<>(record.humans()).equals(new HashSet<>(archive.room().seats().values())))
                    throw new IllegalArgumentException("archive does not match the original settlement roster");
                var state=saved.stateCheckpoint();
                for(var player:state.players())if(player.human() && record.seats().containsKey(player.id()) && record.seats().get(player.id())!=player.seat())
                    throw new IllegalArgumentException("archive settlement seat mismatch");
            }
            LobbyService.restoreRooms(server,archives.stream().map(RoomMatchArchive::room).toList());
            for(var archive:archives)if(archive.match()!=null)MatchService.restoreRoom(server,archive);
            ProbeMod.LOG.info("SGS_WORLD_ROOMS_RESTORED rooms={} matches={}",archives.size(),archives.stream().filter(a->a.match()!=null).count());
        }catch(Exception failure){
            BLOCKED.add(server);MatchService.discardRestoredServer(server);LobbyService.discardRestoredServer(server);
            ProbeMod.LOG.error("SGS world restoration blocked; original archive files preserved",failure);
        }
    }
    /** Called before Minecraft disconnects players, not after waiting-room owners are removed. */
    public static void save(MinecraftServer server){
        if(BLOCKED.contains(server)){ProbeMod.LOG.error("SGS world save blocked to preserve unreadable original archives");return;}
        try{
            var rooms=LobbyService.freezeForShutdown(server);var pending=MatchService.freezeForShutdown(server);
            var store=store(server);var retained=new HashSet<Long>();long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            for(var room:rooms){
                MatchCheckpoint match=null;
                store.invalidate(room.id());
                try{
                    if(room.state()==Room.State.PLAYING){
                        var future=pending.get(room.id());if(future==null)throw new IllegalStateException("playing room has no live session");
                        match=future.get(Math.max(0,until-System.nanoTime()),TimeUnit.NANOSECONDS);
                    }
                    store.save(new RoomMatchArchive(room,match));retained.add(room.id());
                    if(match!=null && match.trick()){
                        var saved=dev.sgs.core.persistence.TrickCheckpointCodec.decode(match.engine());var parent=saved.continuation();
                        long deadline=saved.pendingDeadlineMillis();
                        ProbeMod.LOG.info("SGS_WORLD_TRICK_SAVED room={} window={} remaining_ms={}",room.id(),parent.window()==null?0:parent.window().id(),Math.max(0,deadline-saved.capturedAtMillis()));
                    }
                    if(match!=null && match.activeSkill()){
                        var saved=dev.sgs.core.persistence.ActiveSkillCheckpointCodec.decode(match.engine());
                        var p=dev.sgs.core.persistence.ActiveSkillCheckpoint.pending(saved.continuation());
                        ProbeMod.LOG.info("SGS_WORLD_ACTIVE_SKILL_SAVED room={} skill={} prompt={} remaining_ms={}",room.id(),saved.continuation().plan().skillId(),p.id(),Math.max(0,p.deadlineMillis()-saved.capturedAtMillis()));
                    }
                    if(match!=null && match.turnStart()){
                        var saved=dev.sgs.core.persistence.TurnStartCheckpointCodec.decode(match.engine());
                        var p=dev.sgs.core.persistence.TurnStartCheckpoint.pending(saved.continuation());
                        ProbeMod.LOG.info("SGS_WORLD_TURN_START_SAVED room={} stage={} skill={} prompt={} remaining_ms={}",room.id(),saved.continuation().step(),p.skill(),p.id(),Math.max(0,p.deadline()-saved.capturedAtMillis()));
                    }
                    if(match!=null && match.turnEnd()){
                        var saved=dev.sgs.core.persistence.TurnEndCheckpointCodec.decode(match.engine());
                        var p=dev.sgs.core.persistence.TurnEndCheckpoint.pending(saved.continuation());
                        ProbeMod.LOG.info("SGS_WORLD_TURN_END_SAVED room={} stage={} skill={} prompt={} remaining_ms={}",room.id(),saved.continuation().step(),p.skill(),p.id(),Math.max(0,p.deadline()-saved.capturedAtMillis()));
                    }
                    if(match!=null && match.attack()){
                        var saved=dev.sgs.core.persistence.ContinuationCheckpointCodec.decode(match.engine());var p=saved.continuation().pending();
                        ProbeMod.LOG.info("SGS_WORLD_ATTACK_SAVED room={} prompt={} remaining_ms={}",room.id(),p.id(),Math.max(0,p.deadlineMillis()-saved.capturedAtMillis()));
                    }
                    ProbeMod.LOG.info("SGS_WORLD_ROOM_SAVED room={} playing={} pending={} remaining_ms={}",room.id(),match!=null,match!=null && (match.attack() || match.trick() || match.activeSkill() || match.turnEnd() || match.turnStart()),match==null?0:match.phaseRemainingMillis());
                }catch(Exception unsupported){
                    // Never restore a stale earlier action when the current synchronous program cannot be represented.
                    store.invalidate(room.id());retained.add(room.id());
                    ProbeMod.LOG.warn("SGS_WORLD_ROOM_NOT_RESUMABLE room={} reason={}",room.id(),unsupported.toString());
                }
            }
            for(long id:store.ids())if(!retained.contains(id))store.remove(id);
        }catch(Exception failure){ProbeMod.LOG.error("SGS world archive commit failed",failure);}
    }
    public static void stoppedAll(){BLOCKED.clear();}
}
