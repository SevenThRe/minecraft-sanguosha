package dev.sgs.validation.match;

import dev.sgs.core.card.CardType;
import dev.sgs.core.match.*;
import dev.sgs.core.match.dto.MatchViews.MatchSnapshot;
import dev.sgs.core.util.SystemClock;
import dev.sgs.validation.ProbeMod;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Minecraft adapter only: the existing shared core owns turns, cards, AI and victory. */
public final class MatchService {
    public static MatchNetwork.View currentView(ServerPlayer player){
        var service=SERVICES.get(player.getServer());if(service==null)return null;
        var c=service.players.get(player.getUUID());
        return c!=null && c.activeConnection==player.connection?c.latest:null;
    }
    private static final Map<MinecraftServer,MatchService> SERVICES=new IdentityHashMap<>();
    private final MinecraftServer server;
    private final Map<UUID,Connection> players=new HashMap<>();
    private final Map<Long,MatchSession> roomSessions=new HashMap<>();
    private final Map<Long,Long> roomMatchIds=new HashMap<>();
    private final Map<UUID,SpectatorConnection> observers=new HashMap<>();
    private volatile boolean closing;
    public static void discardRestoredServer(MinecraftServer server){
        var service=SERVICES.remove(server);if(service==null)return;service.closing=true;
        new HashSet<>(service.roomSessions.values()).forEach(MatchSession::shutdown);
    }
    public static Map<Long,java.util.concurrent.CompletableFuture<dev.sgs.core.persistence.MatchCheckpoint>> freezeForShutdown(MinecraftServer server){
        var service=SERVICES.get(server);if(service==null)return Map.of();service.closing=true;
        var saves=new HashMap<Long,java.util.concurrent.CompletableFuture<dev.sgs.core.persistence.MatchCheckpoint>>();
        service.roomSessions.forEach((room,session)->saves.put(room,session.checkpointAndPause()));return saves;
    }
    /** Restores registered humans through fresh connection endpoints; no new ledger match is created. */
    public static void restoreRoom(MinecraftServer server,dev.sgs.core.persistence.RoomMatchArchive archive){
        var saved=java.util.Objects.requireNonNull(archive.match());long matchId=saved.roomId(),roomId=archive.room().id();
        var ledger=dev.sgs.validation.social.FeedbackData.get(server).ledger().match(matchId);
        if(ledger==null || ledger.outcome()!=dev.sgs.core.engine.GameOutcome.ONGOING)
            throw new IllegalArgumentException("saved game has no ongoing settlement ledger");
        var service=SERVICES.computeIfAbsent(server,MatchService::new);
        var endpoints=new HashMap<UUID,MatchEndpoint>();var connections=new ArrayList<Connection>();
        for(UUID player:archive.room().seats().values()){
            if(service.players.containsKey(player))throw new IllegalArgumentException("restored player already in match");
            var connection=service.new Connection(player,matchId,roomId);connections.add(connection);endpoints.put(player,connection);
        }
        var session=MatchSession.restoreAwaitingReconnect(saved,saved.config(),endpoints,SystemClock.INSTANCE,
            line->ProbeMod.LOG.info("[SGS_MATCH {}] {}",matchId,line),()->{
                if(service.closing)return;server.execute(()->{
                    if(service.closing)return;
                    for(var connection:connections)service.players.remove(connection.player,connection);
                    service.roomSessions.remove(roomId);service.roomMatchIds.remove(roomId);
                    service.observers.entrySet().removeIf(e->e.getValue().roomId==roomId);
                    dev.sgs.validation.lobby.LobbyService.matchFinished(server,roomId);
                });
            });
        session.setDisconnectGraceMillis(disconnectGraceMillis());
        for(var connection:connections){connection.session=session;service.players.put(connection.player,connection);}
        service.roomSessions.put(roomId,session);service.roomMatchIds.put(roomId,matchId);
        ProbeMod.LOG.info("SGS_WORLD_MATCH_RESTORED room={} match={} humans={} pending={} content={}",roomId,matchId,connections.size(),saved.attack() || saved.trick() || saved.activeSkill() || saved.turnEnd() || saved.turnStart(),saved.contentId());
    }
    /** Decorates only already-authorized visible heroes, on the server thread. */
    private MatchNetwork.View withSkins(MatchNetwork.View view){
        var skins=new HashMap<Integer,String>();
        try {
            var data=dev.sgs.validation.social.SocialData.get(server);
            var match=dev.sgs.validation.social.FeedbackData.get(server).ledger().match(view.matchId());
            boolean restricted=dev.sgs.validation.collection.CollectionService.policy(server).enabled();
            for(var seat:view.snapshot().seats())if(seat.heroId()!=null && !seat.heroId().isBlank()){
                UUID owner=match==null?null:match.seats().entrySet().stream().filter(e->e.getValue()==seat.seat()).map(Map.Entry::getKey).findFirst().orElse(null);
                skins.put(seat.seat(),owner==null?"classic":data.skins().selected(owner,seat.heroId(),restricted,
                    dev.sgs.validation.collection.CollectionService.ownedSkins(server,owner)));
            }
        }catch(RuntimeException error){ProbeMod.LOG.error("Skin view unavailable; using classic portraits",error);}
        return new MatchNetwork.View(view.matchId(),view.revision(),view.snapshot(),view.nonceFloor(),view.connectionEpoch(),view.roomId(),skins);
    }
    public static void refreshSkins(MinecraftServer server){
        var service=SERVICES.get(server);if(service==null || service.closing)return;
        for(var c:service.players.values()){
            var online=server.getPlayerList().getPlayer(c.player);var view=c.latest;
            if(online!=null && online.connection==c.activeConnection && view!=null)
                Balm.getNetworking().sendTo(online,new MatchNetwork.Snapshot(MatchNetwork.JSON.toJson(service.withSkins(view))));
        }
        for(var c:service.observers.values())if(c.latest!=null && server.getPlayerList().getPlayer(c.viewer.getUUID())==c.viewer)
            Balm.getNetworking().sendTo(c.viewer,new MatchNetwork.Snapshot(MatchNetwork.JSON.toJson(service.withSkins(c.latest))));
    }
    public static void selectSkin(ServerPlayer player,SkinNetwork.Intent request){
        var server=player.getServer();if(!server.isSameThread()){server.execute(()->selectSkin(player,request));return;}
        if(server.getPlayerList().getPlayer(player.getUUID())!=player)return;
        boolean accepted=false;String reason="皮肤已更新";
        try {
            var service=SERVICES.get(server);var c=service==null?null:service.players.get(player.getUUID());
            if(c==null || c.activeConnection!=player.connection || c.latest==null || c.matchId!=request.match()
                || c.connectionEpoch.get()!=request.epoch() || c.latest.snapshot().status()!=1)throw new IllegalArgumentException("当前无法更换皮肤");
            if(request.request()<=0 || c.skinConnection==player.connection && request.request()<=c.skinRequest)throw new IllegalArgumentException("请求已处理");
            c.skinConnection=player.connection;c.skinRequest=request.request();
            var own=c.latest.snapshot().seats().stream().filter(seat->seat.self()).findFirst().orElseThrow();
            if(!Objects.equals(own.heroId(),request.hero()))throw new IllegalArgumentException("只能更换自己的当前武将皮肤");
            long now=System.currentTimeMillis();if(now-c.skinChangedAt<500)throw new IllegalArgumentException("更换太快，请稍后重试");
            var data=dev.sgs.validation.social.SocialData.get(server);
            data.skins().select(player.getUUID(),request.hero(),request.skin(),dev.sgs.validation.collection.CollectionService.policy(server).enabled(),
                dev.sgs.validation.collection.CollectionService.ownedSkins(server,player.getUUID()));
            data.persist(server);c.skinChangedAt=now;accepted=true;refreshSkins(server);
        }catch(IllegalArgumentException error){reason=error.getMessage();}
        catch(RuntimeException error){ProbeMod.LOG.error("Skin selection failed",error);reason="皮肤服务暂不可用";}
        Balm.getNetworking().sendTo(player,new SkinNetwork.Result(request.match(),request.request(),accepted,reason));
    }
    private MatchService(MinecraftServer server){this.server=server;}
    private final class SpectatorConnection implements MatchEndpoint {
        final ServerPlayer viewer;final MatchSession session;final long roomId,matchId;
        volatile MatchNetwork.View latest;
        final AtomicLong revision=new AtomicLong();
        SpectatorConnection(ServerPlayer viewer,MatchSession session,long roomId,long matchId){this.viewer=viewer;this.session=session;this.roomId=roomId;this.matchId=matchId;}
        public void sendSnapshot(MatchSnapshot snapshot){
            var publicView=new MatchNetwork.View(matchId,revision.incrementAndGet(),snapshot,0,0,roomId);latest=publicView;
            server.execute(()->{if(!closing && observers.get(viewer.getUUID())==this && server.getPlayerList().getPlayer(viewer.getUUID())==viewer)
                Balm.getNetworking().sendTo(viewer,new MatchNetwork.Snapshot(MatchNetwork.JSON.toJson(withSkins(publicView))));});
        }
        public void sendError(int nonce,String reason){}
    }
    /** Spectators receive only MatchSession's already filtered public snapshots. */
    public static boolean spectate(ServerPlayer viewer,long roomId){
        var service=SERVICES.get(viewer.getServer());if(service==null || service.players.containsKey(viewer.getUUID()))return false;
        var session=service.roomSessions.get(roomId);if(session==null)return false;
        stopSpectating(viewer);
        var observer=service.new SpectatorConnection(viewer,session,roomId,service.roomMatchIds.get(roomId));
        service.observers.put(viewer.getUUID(),observer);session.addSpectator(observer);return true;
    }
    public static void stopSpectating(ServerPlayer viewer){
        var service=SERVICES.get(viewer.getServer());if(service==null)return;
        var old=service.observers.remove(viewer.getUUID());if(old!=null)old.session.removeSpectator(old);
    }
    private final class Connection implements MatchEndpoint {
        final UUID player; final long matchId; final long roomId;
        volatile MatchSession session; volatile MatchNetwork.View latest;
        final AtomicLong revision=new AtomicLong();
        final AtomicLong connectionEpoch=new AtomicLong();
        net.minecraft.server.network.ServerGamePacketListenerImpl activeConnection;
        Object skinConnection;long skinRequest,skinChangedAt;
        Connection(UUID player,long matchId,long roomId){this.player=player;this.matchId=matchId;this.roomId=roomId;}
        public void sendSnapshot(MatchSnapshot snapshot){
            if(closing)return;
            var view=new MatchNetwork.View(matchId,revision.incrementAndGet(),snapshot,session==null?0:session.nonceFloor(player),connectionEpoch.get(),roomId);latest=view;
            server.execute(()->{if(closing)return;var online=server.getPlayerList().getPlayer(player);
                try{dev.sgs.validation.social.FeedbackService.observe(server,matchId,player,snapshot);}
                catch(RuntimeException error){ProbeMod.LOG.error("SGS settlement/report roster update failed for match {}",matchId,error);}
                if(online!=null && online.connection==activeConnection && view.connectionEpoch()==connectionEpoch.get())
                    Balm.getNetworking().sendTo(online,new MatchNetwork.Snapshot(MatchNetwork.JSON.toJson(withSkins(view))));});
        }
        public void sendError(int nonce,String reason){if(closing)return;long epoch=connectionEpoch.get();

            server.execute(()->{if(closing)return;var online=server.getPlayerList().getPlayer(player);
            if(online!=null && online.connection==activeConnection && epoch==connectionEpoch.get()){

                Balm.getNetworking().sendTo(online,new MatchNetwork.Error(reason));}});}
    }
    public static boolean startRoom(MinecraftServer server,dev.sgs.core.room.Room room){
        var service=SERVICES.computeIfAbsent(server,MatchService::new);
        if(room.memberCount()+room.aiSlots().size()!=room.capacity())return false;
        for(UUID member:room.members()) {
            var online=server.getPlayerList().getPlayer(member);
            if(online==null || service.players.containsKey(member)
                || dev.sgs.validation.survival.SurvivalService.accessDenied(online,member.equals(room.owner()))!=null)return false;
        }
        var humans=new ArrayList<MatchSession.Human>();var connections=new ArrayList<Connection>();
        // Build the complete rules before allocating a match or registering connections.
        var base=MatchConfig.standard();
        var checked=base.withRoomSettings(room.settings());
        checked.validateHeroOffers(room.capacity());
        var ownedPools=dev.sgs.validation.collection.CollectionService.ownedPools(server,List.copyOf(room.members()),
            checked.heroPool().stream().map(dev.sgs.core.hero.HeroDefinition::id).toList(),room.capacity());
        long id=dev.sgs.validation.social.FeedbackService.begin(server,room.capacity(),List.copyOf(room.members()));
        for(UUID member:room.members()) {
            var player=server.getPlayerList().getPlayer(member);
            if(player==null || service.players.containsKey(member))return false;
            var connection=service.new Connection(member,id,room.id());connection.activeConnection=player.connection;connections.add(connection);
            humans.add(new MatchSession.Human(member,player.getGameProfile().getName(),connection));
        }
        // Ordinary-room acceptance must use the settings the host actually submitted.
        // The named legacy flag remains an alias; new probes use the common timing contract.
        var config=checked;






        var s=room.settings();
        var applied=s;
        config=config.withRoomSettings(applied).withHeroBans(true);
        var random=new Random();
        var session=MatchSession.startWithSeats(id,humans,room.capacity(),config,SystemClock.INSTANCE,random,
            line->ProbeMod.LOG.info("[SGS_MATCH {}] {}",id,line),()->{if(service.closing)return;server.execute(()->{
                if(service.closing)return;
                for(var connection:connections)service.players.remove(connection.player,connection);
                service.roomSessions.remove(room.id());service.roomMatchIds.remove(room.id());
                service.observers.entrySet().removeIf(e->e.getValue().roomId==room.id());
                dev.sgs.validation.lobby.LobbyService.matchFinished(server,room.id());
                ProbeMod.LOG.info("SGS_MATCH_CLOSED match={}",id);
            });},room.members().stream().collect(java.util.stream.Collectors.toMap(member->member,room::seatOf)),ownedPools);
        session.setDisconnectGraceMillis(disconnectGraceMillis());
        for(var connection:connections){connection.session=session;service.players.put(connection.player,connection);}
        service.roomSessions.put(room.id(),session);service.roomMatchIds.put(room.id(),id);
        session.setTeammateSharing(room.teammateSharing());
        ProbeMod.LOG.info("SGS_ROOM_MATCH_STARTED match={} humans={} ai={} content={} turn={} response={} selection={} bans={} fixed={} double_renegade={}",
            id,humans.size(),room.aiSlots().size(),config.contentPool().id(),config.turnTimeoutSeconds(),config.responseTimeoutSeconds(),config.selectionTimeoutSeconds(),
            s.bannedHeroes().size(),s.fixedSeats(),s.doubleRenegade());
        return true;
    }
    public static void updateTeamSharing(MinecraftServer server,dev.sgs.core.room.Room room){
        var service=SERVICES.get(server);if(service==null)return;
        room.members().stream().map(service.players::get).filter(Objects::nonNull).map(c->c.session)
          .filter(Objects::nonNull).distinct().forEach(s->s.setTeammateSharing(room.teammateSharing()));
    }
    public static void submit(ServerPlayer player,MatchNetwork.Intent packet){
        if(!player.getServer().isSameThread()){player.getServer().execute(()->submit(player,packet));return;}
        try {
            var command=MatchNetwork.JSON.fromJson(packet.json(),MatchNetwork.Command.class);
            if(command==null || command.nonce()<=0 || command.kind()==null || command.kind().length()>24
                || command.target() < -1 || command.target()>7 || command.secondary() < -1 || command.secondary()>7
                || command.declaredType() < -1 || command.declaredType()>=CardType.values().length
                || command.promptId()<0 || command.controlVersion()<0 || (command.cards()!=null && command.cards().size()>200)
                || (command.hero()!=null && command.hero().length()>32) || (command.skill()!=null && command.skill().length()>32))
                throw new IllegalArgumentException("invalid_command");
            var action=command.action();
            var service=SERVICES.get(player.getServer());if(service!=null && service.closing)return;
            var c=service==null?null:service.players.get(player.getUUID());
            // Both participants can leave the result screen after the session's cleanup callback.
            if(c==null && action.kind()==MatchAction.Kind.LEAVE){
                ProbeMod.LOG.info("SGS_MATCH_LEAVE_ALREADY_CLOSED actor={} match={}",player.getUUID(),command.matchId());return;
            }
            if(c==null || c.matchId!=command.matchId() || c.latest==null || c.activeConnection!=player.connection)throw new IllegalArgumentException("not_in_match");
            // Peer progress can publish another selection/identity revision in transit.
            // Revalidate those phase-bound actions on the owning worker; all game input stays strict.
            boolean heroChoice=action.kind()==MatchAction.Kind.SELECT_HERO && c.latest.snapshot().status()==0
                && c.latest.snapshot().identityIntro()==null && c.latest.snapshot().heroBan()==null
                && command.revision()>0 && command.revision()<=c.latest.revision();
            boolean identityChoice=(action.kind()==MatchAction.Kind.REVEAL_IDENTITY || action.kind()==MatchAction.Kind.CONFIRM_IDENTITY)
                && c.latest.snapshot().status()==0 && c.latest.snapshot().identityIntro()!=null
                && command.revision()>0 && command.revision()<=c.latest.revision();
            boolean openingChoice=(action.kind()==MatchAction.Kind.REPLACE_OPENING_HAND || action.kind()==MatchAction.Kind.KEEP_OPENING_HAND)
                && c.latest.snapshot().openingHand()!=null && command.revision()>0 && command.revision()<=c.latest.revision();
            boolean banChoice=action.kind()==MatchAction.Kind.BAN_HERO && c.latest.snapshot().heroBan()!=null
                && command.revision()>0 && command.revision()<=c.latest.revision();
            var currentPrompt=c.latest.snapshot().prompt();
            boolean counterspellChoice=(action.kind()==MatchAction.Kind.PROMPT_RESPONSE || action.kind()==MatchAction.Kind.PASS_WUXIE_SCOPE)
                && c.latest.snapshot().counterspellWindow()!=null && currentPrompt!=null
                && currentPrompt.typeOrdinal()==dev.sgs.core.engine.PromptType.WUXIE_COUNTER.ordinal()
                && currentPrompt.askerSeat()==c.latest.snapshot().viewerSeat() && currentPrompt.promptId()==action.promptId()
                && command.revision()>0 && command.revision()<=c.latest.revision();
            if(command.revision()!=c.latest.revision() && action.kind()!=MatchAction.Kind.LEAVE && !heroChoice && !identityChoice && !openingChoice && !counterspellChoice && !banChoice){
                c.sendError(action.nonce(),"STATE_CHANGED");
                Balm.getNetworking().sendTo(player,new MatchNetwork.Snapshot(MatchNetwork.JSON.toJson(service.withSkins(c.latest))));return;
            }
            c.session.submit(player.getUUID(),action,c.connectionEpoch.get(),command.controlVersion());
            ProbeMod.LOG.info("SGS_MATCH_INTENT actor={} match={} kind={} nonce={}",player.getUUID(),c.matchId,action.kind(),action.nonce());
            if(action.kind()==MatchAction.Kind.LEAVE)service.players.remove(player.getUUID());
        }catch(RuntimeException error){Balm.getNetworking().sendTo(player,new MatchNetwork.Error("INVALID_INTENT"));}
    }
    private static long disconnectGraceMillis(){return Math.max(0,Math.min(300_000,Long.getLong("sgs.disconnectGraceMillis",10_000L)));}
    public static void control(ServerPlayer player,MatchNetwork.ControlIntent intent){
        if(!player.getServer().isSameThread()){player.getServer().execute(()->control(player,intent));return;}
        var service=SERVICES.get(player.getServer());var c=service==null?null:service.players.get(player.getUUID());
        if(service==null || service.closing || c==null || c.matchId!=intent.matchId() || c.activeConnection!=player.connection
            || c.connectionEpoch.get()!=intent.epoch() || intent.nonce()<=0 || intent.version()<0){
          Balm.getNetworking().sendTo(player,new MatchNetwork.Error("CONTROL_CHANGED"));return;}
        c.session.setAutoplay(player.getUUID(),intent.enabled(),intent.epoch(),intent.version(),intent.nonce());
    }
    public static void disconnect(ServerPlayer player){
        if(!player.getServer().isSameThread()){player.getServer().execute(()->disconnect(player));return;}
        stopSpectating(player);
        var s=SERVICES.get(player.getServer());if(s==null)return;var c=s.players.get(player.getUUID());
        if(c!=null && c.activeConnection==player.connection){c.activeConnection=null;c.session.markOffline(player.getUUID(),c.connectionEpoch.get());
            ProbeMod.LOG.info("SGS_MATCH_DISCONNECTED actor={} match={} epoch={}",player.getUUID(),c.matchId,c.connectionEpoch.get());}
    }
    public static void reconnect(ServerPlayer player){
        if(!player.getServer().isSameThread()){player.getServer().execute(()->reconnect(player));return;}
        var s=SERVICES.get(player.getServer());var c=s==null?null:s.players.get(player.getUUID());
        Balm.getNetworking().sendTo(player,new MatchNetwork.ResumeStatus(c!=null && (c.latest==null || c.latest.snapshot().status()!=2)));
        if(c==null)return;
        c.activeConnection=player.connection;long epoch=c.connectionEpoch.incrementAndGet();
        c.session.markOnline(player.getUUID(),epoch);
        ProbeMod.LOG.info("SGS_MATCH_RECONNECTED actor={} match={} epoch={} nonce_floor={}",player.getUUID(),c.matchId,epoch,c.session.nonceFloor(player.getUUID()));
    }
    public static void stopAll(){
        var services=List.copyOf(SERVICES.values());SERVICES.clear();
        for(var service:services){
            service.closing=true;var sessions=new HashSet<MatchSession>();
            for(var c:service.players.values())if(c.session!=null)sessions.add(c.session);
            sessions.addAll(service.roomSessions.values());service.roomSessions.clear();service.roomMatchIds.clear();service.observers.clear();
            service.players.clear();for(var session:sessions)session.shutdown();
        }
    }
}
