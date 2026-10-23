package dev.sgs.validation.lobby;

import com.google.gson.Gson;
import dev.sgs.core.room.*;
import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.survival.SurvivalService;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Server-thread bridge to the existing pure-Java room implementation. No game rules here. */
public final class LobbyService {
    private static final Gson JSON = new Gson();
    private static final Map<MinecraftServer,LobbyService> SERVICES = new IdentityHashMap<>();
    private final RoomManager rooms = new RoomManager(new AtomicLong(1)::getAndIncrement);
    private final Set<UUID> viewers = new HashSet<>();
    private final Map<UUID,Long> lastRequest = new HashMap<>();
    private final Map<UUID,LobbyNetwork.Snapshot> replies = new HashMap<>();
    private final Map<Long,Deque<String>> chat = new HashMap<>();
    private final Map<UUID,Long> lastChat = new HashMap<>();
    private long revision;
    private boolean stopping;

    /** Server-thread snapshot before logout callbacks can dismantle waiting rooms. */
    public static java.util.List<dev.sgs.core.persistence.RoomCheckpoint> freezeForShutdown(MinecraftServer server){
        var service=SERVICES.get(server);if(service==null)return java.util.List.of();
        service.stopping=true;return service.rooms.checkpoints();
    }
    public static void restoreRooms(MinecraftServer server,java.util.List<dev.sgs.core.persistence.RoomCheckpoint> rooms){
        var service=SERVICES.computeIfAbsent(server,ignored->new LobbyService());
        service.rooms.restoreAll(rooms);service.revision++;
    }
    public static void discardRestoredServer(MinecraftServer server){SERVICES.remove(server);}

    public static void stopAll() { SERVICES.clear(); }
    public static void policyChanged(MinecraftServer server) {
        var service=SERVICES.get(server);if(service==null)return;
        // Flush all queues: their old admission conditions no longer apply.
        for(var player:server.getPlayerList().getPlayers())service.rooms.leaveQueue(player.getUUID());
        service.replies.clear();service.revision++;service.broadcast(server);
    }
    public static void disconnect(ServerPlayer player) {
        LobbyService service = SERVICES.get(player.getServer());
        if (service == null) return;
        UUID id = player.getUUID(); service.viewers.remove(id);service.lastRequest.remove(id);service.replies.remove(id);
        var room=service.rooms.roomOf(id).orElse(null);
        if(room==null || room.state()!=Room.State.PLAYING)service.rooms.handleDisconnect(id);
        service.revision++;service.broadcast(player.getServer());
    }
    public static void handle(ServerPlayer player, LobbyNetwork.Intent intent) {
        MinecraftServer server = player.getServer();
        if (!server.isSameThread()) throw new IllegalStateException("Lobby handler off server thread");
        SERVICES.computeIfAbsent(server,ignored -> new LobbyService()).apply(player,intent);
    }
    private void apply(ServerPlayer player, LobbyNetwork.Intent intent) {
        if(stopping)return;
        UUID id = player.getUUID();
        if(player.getServer().getPlayerList().getPlayer(id)!=player || intent.action()==null
            || intent.data()==null || intent.data().length()>8192)return;
        if (intent.action().equals("close")) {viewers.remove(id);dev.sgs.validation.match.MatchService.stopSpectating(player);return;}
        if(intent.action().equals("unwatch")){dev.sgs.validation.match.MatchService.stopSpectating(player);return;}
        viewers.add(id);
        long last = lastRequest.getOrDefault(id,0L);
        if (intent.request() == last && replies.containsKey(id)) {
            ProbeMod.LOG.info("SGS_LOBBY_DUPLICATE actor={} request={}",id,intent.request());
            Balm.getNetworking().sendTo(player,replies.get(id));return;
        }
        if (intent.request() <= last || intent.request() <= 0) {
            send(player,intent.request(),false,"请求已过期",false);return;
        }
        lastRequest.put(id,intent.request());
        if (intent.action().equals("refresh")) {send(player,intent.request(),true,"房间信息已更新",true);return;}
        if (intent.revision() != revision) {
            ProbeMod.LOG.info("SGS_LOBBY_STALE actor={} request={} expected={} actual={}",id,intent.request(),intent.revision(),revision);
            send(player,intent.request(),false,"房间状态已变化，请重试",true);return;
        }
        RoomMode mode = switch(intent.mode()) {case "six" -> RoomMode.SIX;case "eight" -> RoomMode.EIGHT;case "two_v_two" -> RoomMode.TWO_V_TWO;default -> null;};
        RoomResult<?> result;
        var policy=SurvivalService.policy(player.getServer());
        if(Set.of("create","queue","join","accept","start","ready").contains(intent.action())) {
            String denial=SurvivalService.accessDenied(player,intent.action().equals("create") || intent.action().equals("queue") || intent.action().equals("start"));
            var target=switch(intent.action()) {
                case "join" -> rooms.room(intent.room()).orElse(null);
                case "accept" -> rooms.pendingInvite(id).flatMap(rooms::room).orElse(null);
                default -> rooms.roomOf(id).orElse(null);
            };
            String requested=target==null?intent.mode():modeId(target.mode());
            if(denial==null && !policy.modeEnabled(requested))denial="服务器已关闭此房间玩法";
            if(denial==null && intent.action().equals("queue") && !policy.matchmaking())denial="服务器已关闭快速匹配";
            if(denial==null && intent.action().equals("start") && target!=null) {
                for(UUID member:target.members()) {
                    var online=player.getServer().getPlayerList().getPlayer(member);
                    if(online==null){denial="请等待所有玩家上线";break;}
                    String blocked=SurvivalService.accessDenied(online,member.equals(target.owner()));
                    if(blocked!=null){denial=online.getGameProfile().getName()+"："+blocked;break;}
                }
            }
            if(denial!=null){send(player,intent.request(),false,denial,true);return;}
        }
        try { switch(intent.action()) {
            case "start" -> {
                var room=rooms.roomOf(id).orElse(null);
                result=room==null?RoomResult.fail(RoomError.NOT_IN_ROOM):rooms.validateConfiguredStart(id,room.id());
                if(result.isOk()) {
                    if(!dev.sgs.validation.match.MatchService.startRoom(player.getServer(),room)){
                        send(player,intent.request(),false,"玩家离线或已在其他对局中",true);return;
                    }
                    result=rooms.beginConfiguredMatch(id,room.id());
                }
            }
            case "share_on", "share_off" -> {
                result=rooms.setTeammateSharing(id,intent.action().equals("share_on"));
                if(result.isOk())dev.sgs.validation.match.MatchService.updateTeamSharing(player.getServer(),rooms.roomOf(id).orElseThrow());
            }
            case "ai_fill" -> {
                var room=rooms.roomOf(id).orElse(null);
                result=room==null?RoomResult.fail(RoomError.NOT_IN_ROOM):rooms.fillAllAiSlots(id,room.id());
            }
            case "ai_clear" -> {
                var room=rooms.roomOf(id).orElse(null);
                result=room==null?RoomResult.fail(RoomError.NOT_IN_ROOM):rooms.clearAiSlots(id,room.id());
            }
            case "sit" -> {
                if(intent.room()<0 || intent.room()>7)throw new IllegalArgumentException("invalid seat");
                result=rooms.sit(id,(int)intent.room());
            }
            case "kick" -> result=rooms.kick(id,UUID.fromString(intent.data()));
            case "invite" -> {
                UUID target=UUID.fromString(intent.data());
                var online=player.getServer().getPlayerList().getPlayer(target);
                if(online==null){send(player,intent.request(),false,"该玩家已离线",true);return;}
                var source=rooms.roomOf(id).orElse(null);
                if(source==null || source.state()!=Room.State.WAITING || source.isFull()) {
                    send(player,intent.request(),false,"房间需在等待中且留有空位",true);return;
                }
                result=rooms.invite(id,target);
                if(result.isOk()) {
                    online.sendSystemMessage(net.minecraft.network.chat.Component.literal(player.getGameProfile().getName()+" 邀请你加入三国杀房间，打开大厅即可接受。"));
                    // An open lobby receives the invitation in its own filtered snapshot.
                    viewers.add(target);
                }
            }
            case "accept" -> result=rooms.acceptInvite(id);
            case "decline" -> result=rooms.declineInvite(id);
            case "update" -> {
                var room=rooms.roomOf(id).orElse(null);
                var fields=JSON.fromJson(intent.data(),com.google.gson.JsonObject.class);
                result=room==null?RoomResult.fail(RoomError.NOT_IN_ROOM):rooms.updateRoom(id,
                    fields.has("name")?fields.get("name").getAsString():room.name(),settings(intent.data(),room.settings()));
            }
            case "chat" -> {
                var room=rooms.roomOf(id).orElse(null);
                if(room==null || !room.settings().allowChat()) {send(player,intent.request(),false,"当前房间不允许聊天",true);return;}
                String message=intent.data().strip();
                if(message.isEmpty() || message.length()>120 || message.codePoints().anyMatch(c->Character.isISOControl(c) || c==0x00a7))
                    throw new IllegalArgumentException("invalid chat");
                long now=System.currentTimeMillis();
                if(now-lastChat.getOrDefault(id,0L)<700){send(player,intent.request(),false,"发送过快，请稍后重试",true);return;}
                lastChat.put(id,now);
                var log=chat.computeIfAbsent(room.id(),ignored->new ArrayDeque<>());
                log.addLast(player.getGameProfile().getName()+"："+message);
                while(log.size()>24)log.removeFirst();
                result=RoomResult.ok(room);
            }
            case "spectate" -> {
                var room=rooms.room(intent.room()).orElse(null);
                if(room==null || !room.settings().allowSpectators() || room.state()!=Room.State.PLAYING || rooms.roomOf(id).isPresent()) {
                    send(player,intent.request(),false,"仅可旁观允许观战的进行中房间；请先离开自己的房间",true);return;
                }
                if(room.settings().passwordProtected() && !room.settings().password().equals(intent.data())) {
                    send(player,intent.request(),false,"房间密码错误",true);return;
                }
                boolean watched=dev.sgs.validation.match.MatchService.spectate(player,room.id());
                send(player,intent.request(),watched,watched?"已进入旁观，只接收公开信息":"对局已结束，请刷新",true);return;
            }
            case "unwatch" -> {
                dev.sgs.validation.match.MatchService.stopSpectating(player);
                send(player,intent.request(),true,"已退出旁观",true);return;
            }
            case "ai_add", "ai_remove" -> {
                var room=rooms.roomOf(id).orElse(null);
                if(intent.room()<0 || intent.room()>7){send(player,intent.request(),false,"座位无效",true);return;}
                result=room==null?RoomResult.fail(RoomError.NOT_IN_ROOM):
                    rooms.reserveAiSlot(id,room.id(),(int)intent.room(),intent.action().equals("ai_add"));
            }
            case "create" -> {
                if (mode == null) {send(player,intent.request(),false,"此模式尚未接入",true);return;}
                if (rooms.allRooms().size() >= policy.maxRooms()) {send(player,intent.request(),false,"房间数量已达上限",true);return;}
                var fields=intent.data().isEmpty()?new com.google.gson.JsonObject():JSON.fromJson(intent.data(),com.google.gson.JsonObject.class);
                String name=fields.has("name")?fields.get("name").getAsString().strip():"私人房间";
                if(name.isEmpty() || name.length()>32 || name.codePoints().anyMatch(c->Character.isISOControl(c) || c==0x00a7))
                    throw new IllegalArgumentException("invalid name");
                var configuration=settings(intent.data(),RoomSettings.defaults());
                if(mode==RoomMode.TWO_V_TWO && configuration.doubleRenegade())throw new IllegalArgumentException("double renegade outside identity mode");
                result = rooms.createRoom(id,name,mode,configuration);
            }
            case "join" -> result = rooms.joinRoom(id,intent.room(),intent.data());
            case "leave" -> {
                var room=rooms.roomOf(id).orElse(null);
                if(room!=null && room.state()==Room.State.PLAYING){send(player,intent.request(),false,"对局进行中，可暂离界面或返回对局",true);return;}
                result=rooms.leaveRoom(id);
            }
            case "ready" -> result = rooms.setReady(id,true);
            case "unready" -> result = rooms.setReady(id,false);
            case "queue" -> {
                if (mode == null) {send(player,intent.request(),false,"此模式尚未接入",true);return;}
                if (rooms.allRooms().size() >= policy.maxRooms()) {send(player,intent.request(),false,"房间数量已达上限",true);return;}
                result = rooms.joinQueue(id,mode);
            }
            case "cancel" -> result = rooms.leaveQueue(id);
            default -> {send(player,intent.request(),false,"不支持的操作",true);return;}
        }} catch(dev.sgs.core.collection.OwnedHeroSelection.Unavailable error) {
            send(player,intent.request(),false,error.getMessage(),true);return;
        } catch(dev.sgs.core.match.MatchConfig.HeroOfferCapacityException | dev.sgs.core.match.MatchConfig.ContentCapacityException error) {
            send(player,intent.request(),false,error.getMessage(),true);return;
        } catch(RuntimeException error) {
            send(player,intent.request(),false,"设置或输入无效，请检查名称、密码、时间和禁将",true);return;
        }
        if (result.isOk()) revision++;
        String reason = result.isOk() ? "操作成功" : switch(result.error()) {
            case ALREADY_IN_ROOM -> "你已在房间中";case ROOM_FULL -> "房间已满";
            case ROOM_NOT_FOUND -> "房间已关闭";case ALREADY_QUEUED -> "你已在匹配队列中";
            case NOT_QUEUED -> "当前没有匹配任务";case NOT_IN_ROOM -> "当前不在房间中";
            case NOT_OWNER -> "只有房主可以调整 AI";case SEAT_TAKEN -> "该座位已有玩家";
            case ROOM_NOT_FULL -> "请等待真人加入或添加 AI 补满空位";case MEMBERS_NOT_READY -> "请所有真人准备后再开始";
            case WRONG_PASSWORD -> "房间密码错误";case INVALID_SETTINGS -> "房间设置无效";
            case NOT_INVITED -> "邀请已失效";case CANNOT_INVITE_SELF -> "不能邀请自己";
            case TARGET_IN_ROOM -> "该玩家已在其他房间";case CANNOT_KICK_OWNER -> "不能移除房主";
            default -> "当前状态不允许此操作";
        };
        send(player,intent.request(),result.isOk(),reason,true);
        if (result.isOk()) broadcast(player.getServer(),id);
        ProbeMod.LOG.info("SGS_LOBBY_RECEIPT actor={} request={} action={} success={} revision={}",id,intent.request(),intent.action(),result.isOk(),revision);
    }
    private RoomSettings settings(String json,RoomSettings previous) {
        var fields=json.isEmpty()?new com.google.gson.JsonObject():JSON.fromJson(json,com.google.gson.JsonObject.class);
        if(fields==null)throw new IllegalArgumentException("missing settings");
        String password=fields.has("clearPassword") && fields.get("clearPassword").getAsBoolean()?"":
            fields.has("password")?fields.get("password").getAsString():previous.password();
        Set<String> banned=previous.bannedHeroes();
        if(fields.has("bannedHeroes")) {
            banned=new LinkedHashSet<>();
            var array=fields.getAsJsonArray("bannedHeroes");if(array.size()>6)throw new IllegalArgumentException("too many bans");
            for(var item:array)banned.add(item.getAsString());
        }
        var available=dev.sgs.core.addon.AddonRuntime.current().heroes().stream().map(dev.sgs.core.hero.HeroDefinition::id).collect(java.util.stream.Collectors.toSet());
        if(!available.containsAll(banned) || available.size()-banned.size()<3)throw new IllegalArgumentException("invalid hero pool");
        return new RoomSettings(password,integer(fields,"turnSeconds",previous.turnSeconds()),integer(fields,"responseSeconds",previous.responseSeconds()),
            integer(fields,"selectionSeconds",previous.selectionSeconds()),bool(fields,"allowChat",previous.allowChat()),bool(fields,"allowSpectators",previous.allowSpectators()),
            bool(fields,"doubleRenegade",previous.doubleRenegade()),bool(fields,"fixedSeats",previous.fixedSeats()),banned);
    }
    private static int integer(com.google.gson.JsonObject fields,String key,int fallback){return fields.has(key)?fields.get(key).getAsInt():fallback;}
    private static boolean bool(com.google.gson.JsonObject fields,String key,boolean fallback){return fields.has(key)?fields.get(key).getAsBoolean():fallback;}
    private void send(ServerPlayer player,long request,boolean success,String reason,boolean cache) {
        if(!success)ProbeMod.LOG.info("SGS_LOBBY_REJECT actor={} request={} reason={}",player.getUUID(),request,reason);
        var own = rooms.roomOf(player.getUUID());
        var list = rooms.allRooms().stream().map(summary -> {
            var room=rooms.room(summary.id()).orElseThrow();
            var seats=new ArrayList<LobbyNetwork.SeatView>();
            for(int slot=0;slot<room.capacity();slot++) {
                UUID occupant=room.occupant(slot);var online=occupant==null?null:player.getServer().getPlayerList().getPlayer(occupant);
                int kind=occupant!=null?Room.SLOT_HUMAN:room.aiSlots().contains(slot)?Room.SLOT_AI:Room.SLOT_EMPTY;
                seats.add(new LobbyNetwork.SeatView(slot,kind,kind==Room.SLOT_AI?"AI "+(slot+1):online!=null?online.getGameProfile().getName():occupant!=null?"离线玩家":"空位",
                    occupant==null?"":occupant.toString(),kind==Room.SLOT_AI || occupant!=null && room.isReady(occupant),room.owner().equals(occupant)));
            }
            var s=room.settings();
            return new LobbyNetwork.RoomView(room.id(),room.name(),modeId(room.mode()),room.memberCount(),room.capacity(),
                room.readyCount(),room.aiSlots().size(),room.owner().equals(player.getUUID()),room.state()==Room.State.PLAYING,List.copyOf(seats),room.teammateSharing(),
                new LobbyNetwork.SettingsView(s.turnSeconds(),s.responseSeconds(),s.selectionSeconds(),s.allowChat(),s.allowSpectators(),s.doubleRenegade(),s.fixedSeats(),s.bannedHeroes(),s.passwordProtected()));
        }).toList();
        var catalog=dev.sgs.core.addon.AddonRuntime.current();
        var heroes=catalog.heroes().stream().map(h->{var p=catalog.present(List.of(h.id())).getFirst();
            String kingdom=switch(h.kingdom().name()){case "WEI"->"魏";case "SHU"->"蜀";case "WU"->"吴";case "QUN"->"群";default->"神";};
            return new LobbyNetwork.HeroView(h.id(),p.name(),kingdom,p.portrait(),p.description(),p.skills());}).toList();
        long xp=-1,honor=-1;
        try {var account=dev.sgs.validation.social.FeedbackData.get(player.getServer()).ledger().account(player.getUUID());xp=account.xp();honor=account.honor();}
        catch(RuntimeException error){ProbeMod.LOG.error("SGS lobby account unavailable",error);}
        var view = new LobbyNetwork.View(revision,request,success,reason,own.map(Room::id).orElse(-1L),
            rooms.queuedMode(player.getUUID()).map(LobbyService::modeId).orElse(""),
            rooms.queueSize(RoomMode.SIX),rooms.queueSize(RoomMode.EIGHT),own.map(room -> room.isReady(player.getUUID())).orElse(false),list,rooms.queueSize(RoomMode.TWO_V_TWO),
            player.getGameProfile().getName(),player.getServer().getPlayerList().getPlayers().stream().map(p->new LobbyNetwork.PlayerView(p.getUUID().toString(),p.getGameProfile().getName())).toList(),
            rooms.pendingInvite(player.getUUID()).orElse(-1L),own.map(r->List.copyOf(chat.getOrDefault(r.id(),new ArrayDeque<>()))).orElse(List.of()),heroes,xp,honor,
            java.util.stream.Stream.of("six","eight","two_v_two").filter(SurvivalService.policy(player.getServer())::modeEnabled).toList(),SurvivalService.policy(player.getServer()).matchmaking(),
            LobbyNetwork.cardPacks(SurvivalService.policy(player.getServer()).packs()));
        var packet = new LobbyNetwork.Snapshot(JSON.toJson(view));
        if (cache) replies.put(player.getUUID(),packet);
        Balm.getNetworking().sendTo(player,packet);
    }
    private void broadcast(MinecraftServer server) {broadcast(server,null);}
    public static void matchFinished(MinecraftServer server,long roomId){
        var service=SERVICES.get(server);if(service!=null){service.rooms.finishConfiguredMatch(roomId);service.revision++;service.broadcast(server);}
    }
    private static String modeId(RoomMode mode){return switch(mode){case SIX->"six";case EIGHT->"eight";case TWO_V_TWO->"two_v_two";};}
    private void broadcast(MinecraftServer server,UUID except) {
        for (UUID viewer:viewers) {
            if (viewer.equals(except)) continue;
            ServerPlayer player=server.getPlayerList().getPlayer(viewer);
            if (player!=null) send(player,0,true,"房间信息已更新",false);
        }
    }
}
