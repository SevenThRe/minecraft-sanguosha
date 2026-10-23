package dev.sgs.validation.social;
import dev.sgs.core.social.GiftDatabase;
import dev.sgs.core.engine.GameOutcome;
import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.match.MatchService;
import java.util.*;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/** Server-thread validation, transactional virtual currency debit, then public animation. */
public final class GiftService {
    private static final Map<MinecraftServer,GiftDatabase> DATABASES=new IdentityHashMap<>();
    private record Window(Object connection,long highest,long lastGift) {}
    private static final Map<UUID,Window> WINDOWS=new HashMap<>();
    private static GiftDatabase database(MinecraftServer server) throws Exception {
        var db=DATABASES.get(server);if(db==null){db=new GiftDatabase(server.getWorldPath(LevelResource.ROOT).resolve("data/sgs_gifts"));DATABASES.put(server,db);}return db;
    }
    public static void submit(ServerPlayer player,GiftNetwork.Intent packet){
        var server=player.getServer();if(!server.isSameThread()){server.execute(()->submit(player,packet));return;}
        GiftNetwork.Command c=null;
        try{
            c=FeedbackNetwork.JSON.fromJson(packet.json(),GiftNetwork.Command.class);
            if(c==null || c.requestId()<=0 || c.kind()==null)throw new IllegalArgumentException("INVALID_REQUEST");
            var view=MatchService.currentView(player);
            if(view==null || view.matchId()!=c.matchId() || view.snapshot().status()!=1)throw new IllegalArgumentException("NOT_IN_ACTIVE_MATCH");
            var match=FeedbackData.get(server).ledger().match(c.matchId());
            if(match==null || match.outcome()!=GameOutcome.ONGOING || !match.seats().containsKey(player.getUUID()))throw new IllegalArgumentException("NOT_IN_ACTIVE_MATCH");
            var old=WINDOWS.get(player.getUUID());boolean same=old!=null && old.connection()==player.connection;
            if(same && c.requestId()<=old.highest())throw new IllegalArgumentException("REQUEST_REPLAY");
            long now=System.currentTimeMillis();WINDOWS.put(player.getUUID(),new Window(player.connection,c.requestId(),same?old.lastGift():0));
            if(c.kind().equals("QUERY")){
                send(player,result(player,c,match,-1,-1,"",0,""));return;
            }
            if(c.kind().startsWith("EMOTE:")){
                String emote=c.kind().substring(6);dev.sgs.core.social.Emotes.name(emote);
                if(same && now-old.lastGift()<300)throw new IllegalArgumentException("RATE_LIMITED");
                WINDOWS.put(player.getUUID(),new Window(player.connection,c.requestId(),now));
                String receipt=UUID.randomUUID().toString();int seat=match.seats().get(player.getUUID());
                for(var id:match.humans()){
                    var online=server.getPlayerList().getPlayer(id);if(online==null)continue;
                    var current=MatchService.currentView(online);if(current!=null && current.matchId()==c.matchId())send(online,new GiftNetwork.Result(c.matchId(),c.requestId(),true,"OK",seat,seat,c.kind(),0,receipt,List.of(),-1,List.of()));
                }
                ProbeMod.LOG.info("SGS_EMOTE_BROADCAST match={} seat={} emote={} charged=0",c.matchId(),seat,emote);return;
            }
            var definition=GiftPackService.catalog(server).resolve(c.kind(),c.amount());int targetSeat=c.targetSeat();
            var target=match.seats().entrySet().stream().filter(e->e.getValue()==targetSeat).map(Map.Entry::getKey).findFirst().orElseThrow(()->new IllegalArgumentException("TARGET_HUMAN_ONLY"));
            if(target.equals(player.getUUID()))throw new IllegalArgumentException("SELF_TARGET");
            if(same && now-old.lastGift()<100)throw new IllegalArgumentException("RATE_LIMITED");
            var receipt=database(server).recordGift(UUID.randomUUID().toString(),player.getUUID(),target,definition);
            WINDOWS.put(player.getUUID(),new Window(player.connection,c.requestId(),now));
            ProbeMod.LOG.info("SGS_GIFT_COMMITTED receipt={} sender={} target={} kind={} amount={} flowers={} eggs={}",receipt.id(),player.getUUID(),target,definition.id(),definition.received(),receipt.flowers(),receipt.eggs());
            // No rollback after this point: the durable receipt is the authoritative success.
            for(var id:match.humans()){
                var online=server.getPlayerList().getPlayer(id);if(online==null)continue;
                var current=MatchService.currentView(online);if(current==null || current.matchId()!=c.matchId())continue;
                try{send(online,result(online,c,match,match.seats().get(player.getUUID()),c.targetSeat(),definition.id(),definition.received(),receipt.id()));}
                catch(Exception delivery){ProbeMod.LOG.error("Gift committed but delivery failed: {}",receipt.id(),delivery);}
            }
        }catch(Exception failure){
            String reason=failure instanceof IllegalArgumentException?failure.getMessage():"DATA_UNAVAILABLE";
            if(!(failure instanceof IllegalArgumentException))ProbeMod.LOG.error("Gift database request failed",failure);
            send(player,new GiftNetwork.Result(c==null?0:c.matchId(),c==null?0:c.requestId(),false,reason,-1,-1,"",0,"",List.of(),0,List.of()));
        }
    }
    private static GiftNetwork.Result result(ServerPlayer viewer,GiftNetwork.Command c,dev.sgs.core.social.MatchLedger.MatchRecord match,int from,int to,String kind,int amount,String receipt) throws Exception {
        var totals=new ArrayList<GiftNetwork.Count>();var db=database(viewer.getServer());
        for(var e:match.seats().entrySet()){var t=db.totals(e.getKey());totals.add(new GiftNetwork.Count(e.getValue(),t.flowers(),t.eggs()));}
        var wallet=db.wallet(viewer.getUUID());
        return new GiftNetwork.Result(c.matchId(),c.requestId(),true,"OK",from,to,kind,amount,receipt,totals,wallet.flowers(),GiftPackService.catalog(viewer.getServer()).gifts());
    }
    private static void send(ServerPlayer p,GiftNetwork.Result r){Balm.getNetworking().sendTo(p,new GiftNetwork.Response(FeedbackNetwork.JSON.toJson(r)));}
    public static void stopAll(){for(var db:DATABASES.values())try{db.close();}catch(Exception e){ProbeMod.LOG.error("Gift database close failed",e);}DATABASES.clear();WINDOWS.clear();GiftPackService.stop();}
    public static void seedSmoke(MinecraftServer server,long match,List<UUID> humans){
        if(!Boolean.getBoolean("sgs.giftSmoke"))throw new IllegalStateException("test grants disabled");
        try{for(var id:humans)database(server).grant("smoke-"+match+"-"+id,id,25);}
        catch(Exception e){throw new IllegalStateException("gift smoke seed failed",e);}
    }
    public static void initializeCommands(){
        Balm.getCommands().register(dispatcher->dispatcher.register(net.minecraft.commands.Commands.literal("sgsgift").requires(s->s.hasPermission(2))
            .then(net.minecraft.commands.Commands.literal("grant")
            .then(net.minecraft.commands.Commands.argument("uuid",com.mojang.brigadier.arguments.StringArgumentType.word())
            .then(net.minecraft.commands.Commands.argument("flowers",com.mojang.brigadier.arguments.LongArgumentType.longArg(0,1_000_000))
                        .then(net.minecraft.commands.Commands.argument("grantId",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx->{
                try{
                    var id=UUID.fromString(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,"uuid"));
                    long flowers=com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx,"flowers");
                    String grant=com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,"grantId");
                    var balance=database(ctx.getSource().getServer()).grant(grant,id,flowers);
                    ctx.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal("虚拟余额 "+id+" 鲜花="+balance.flowers()),false);
                    ProbeMod.LOG.info("SGS_GIFT_GRANT actor={} player={} flowers={} grant={}",ctx.getSource().getTextName(),id,flowers,grant);return 1;
                }catch(Exception failure){ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal("发放失败："+failure.getMessage()));return 0;}
            })))))));
    }
}
