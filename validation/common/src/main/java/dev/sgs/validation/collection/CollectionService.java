package dev.sgs.validation.collection;

import dev.sgs.core.collection.*;
import dev.sgs.validation.ProbeMod;
import java.util.*;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;

public final class CollectionService {
    private record Window(Object connection,String scope,long request){}
    private static final Map<MinecraftServer,CollectionPolicy> POLICIES=new IdentityHashMap<>();
    private static final Map<MinecraftServer,CollectionDatabase> DATABASES=new IdentityHashMap<>();
    private static final Map<UUID,Window> WINDOWS=new HashMap<>();
    private static final Random RANDOM=new java.security.SecureRandom();
    public static CollectionPolicy preparePolicy(MinecraftServer server) throws java.io.IOException {
        var next=CollectionPolicy.load(server.getServerDirectory().resolve("config/sgs"));
        var catalog=catalog();if(!catalog.containsAll(next.drawPool()) || !catalog.containsAll(next.prices().keySet()))throw new IllegalArgumentException("养成配置包含未加载武将ID");
        return next;
    }
    public static void installPolicy(MinecraftServer server,CollectionPolicy policy){POLICIES.put(server,policy);for(var p:server.getPlayerList().getPlayers())sync(p,0,false,"配置已更新");dev.sgs.validation.match.MatchService.refreshSkins(server);}
    public static CollectionPolicy policy(MinecraftServer server){return POLICIES.computeIfAbsent(server,s->{try{return preparePolicy(s);}catch(Exception e){throw new IllegalStateException("养成配置无效",e);}});}
    static CollectionDatabase database(MinecraftServer server) {
        return DATABASES.computeIfAbsent(server,s->{try {
            var path=s.getWorldPath(LevelResource.ROOT).resolve("sgs/collection");java.nio.file.Files.createDirectories(path.getParent());
            return new CollectionDatabase(path);
        }catch(Exception e){throw new IllegalStateException("藏品数据库不可用，原数据保留",e);}});
    }
    public static Set<String> ownedSkins(MinecraftServer server,UUID player) {
        if(!policy(server).enabled())return Set.of();
        try{return database(server).account(player,policy(server).startingTokens()).skins();}
        catch(java.sql.SQLException e){throw new IllegalStateException("读取皮肤权限失败",e);}
    }
    public static List<String> catalog(){return dev.sgs.core.addon.AddonRuntime.current().heroes().stream().map(dev.sgs.core.hero.HeroDefinition::id).sorted().toList();}
    public static void initialize() {
        CollectionNetwork.initialize();commands();
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.server.ServerStartedEvent.class,e->{policy(e.getServer());});
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.PlayerLoginEvent.class,e->{
            var player=e.getPlayer();

            sync(player,0,false,"");
        });
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.PlayerLogoutEvent.class,e->WINDOWS.remove(e.getPlayer().getUUID()));
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.server.ServerStoppedEvent.class,e->{
            for(var db:DATABASES.values())try{db.close();}catch(Exception ex){ProbeMod.LOG.error("Collection database close failed",ex);}
            DATABASES.clear();POLICIES.clear();WINDOWS.clear();
        });
    }

    public static void handle(ServerPlayer player,CollectionNetwork.Intent request) {
        var server=player.getServer();if(!server.isSameThread())throw new IllegalStateException("collection off server thread");
        if(server.getPlayerList().getPlayer(player.getUUID())!=player || request.request()<=0)return;
        var old=WINDOWS.get(player.getUUID());boolean same=old!=null && old.connection()==player.connection;
        if(same && request.request()<=old.request()){sync(player,request.request(),false,"请求已处理，请刷新");return;}
        String scope=same?old.scope():UUID.randomUUID().toString();WINDOWS.put(player.getUUID(),new Window(player.connection,scope,request.request()));
        try {
            switch(request.action()) {
                case "refresh" -> sync(player,request.request(),true,"");
                case "draw","draw10","buy" -> {
                    if(request.action().equals("buy") && request.expectedPrice()!=policy(server).price(request.hero()))throw new IllegalArgumentException("市场价格已变化，请刷新后购买");
                    var result=database(server).apply(scope+":"+request.request(),player.getUUID(),request.action(),request.hero(),0,policy(server),catalog(),RANDOM);
                    String message=request.action().equals("buy")?"已购买 "+name(result.hero()):request.action().equals("draw10")?"招募完成，获得 "+result.soulsAward()+" 将符":result.duplicate()?"重复武将 "+name(result.hero())+"，获得 "+result.soulsAward()+" 将符":"获得新武将 "+name(result.hero());
                    sync(player,request.request(),false,message,result.draws());
                }
                default -> throw new IllegalArgumentException("不支持的藏品操作");
            }
        }catch(IllegalArgumentException e){sync(player,request.request(),false,e.getMessage());}
        catch(Exception e){ProbeMod.LOG.error("Collection operation failed",e);player.sendSystemMessage(Component.literal("藏品服务暂不可用，未重置数据"));sync(player,request.request(),false,"藏品服务暂不可用");}
    }
    static String name(String id){try{return dev.sgs.core.addon.AddonRuntime.current().present(List.of(id)).getFirst().name();}catch(RuntimeException e){return id;}}
    public static void sync(ServerPlayer player,long request,boolean open,String message) {
        sync(player,request,open,message,List.of());
    }
    private static void sync(ServerPlayer player,long request,boolean open,String message,List<CollectionDatabase.Draw> draws) {
        try {
            var server=player.getServer();var policy=policy(server);var account=database(server).account(player.getUUID(),policy.startingTokens());var pool=policy.pool(catalog());
            var rows=dev.sgs.core.addon.AddonRuntime.current().heroes().stream().map(h->{var p=dev.sgs.core.addon.AddonRuntime.current().present(List.of(h.id())).getFirst();return new CollectionNetwork.Hero(h.id(),p.name(),account.heroes().contains(h.id()),policy.price(h.id()),pool.contains(h.id()),h.kingdom().name(),h.maxHp(),p.portrait(),p.description(),p.skills(),dev.sgs.core.addon.DerivedSkillPresentations.resolve(dev.sgs.core.addon.AddonRuntime.current(),p.skills()));}).toList();
            var state=new CollectionNetwork.State(request,open,policy.enabled(),policy.marketEnabled(),account.tokens(),account.souls(),policy.guaranteedNewBelow(),account.skins(),rows,message,draws);
            Balm.getNetworking().sendTo(player,new CollectionNetwork.Snapshot(CollectionNetwork.JSON.toJson(state)));
        }catch(Exception e){ProbeMod.LOG.error("Collection sync failed; preserving account",e);
            try{Balm.getNetworking().sendTo(player,new CollectionNetwork.Failure(request,"藏品服务暂不可用，数据已保留；可刷新重试"));}catch(RuntimeException closed){ProbeMod.LOG.debug("Collection error delivery unavailable",closed);}}
    }
    public static Map<UUID,Set<String>> ownedPools(MinecraftServer server,List<UUID> humans,List<String> pool,int seats) {
        if(!policy(server).enabled())return Map.of();
        try {
            var result=new HashMap<UUID,Set<String>>();var eligible=new ArrayList<Set<String>>();
            for(UUID id:humans){var own=database(server).account(id,policy(server).startingTokens()).heroes();result.put(id,own);eligible.add(own);}
            while(eligible.size()<seats)eligible.add(Set.copyOf(pool));
            OwnedHeroSelection.deal(pool,eligible,4,new Random(0));return Map.copyOf(result);
        }catch(java.sql.SQLException e){throw new IllegalStateException("读取武将藏品失败",e);}
    }
    public static void reward(MinecraftServer server,String settlement,UUID player) {
        var p=policy(server);if(!p.enabled() || p.tokensPerMatch()==0)return;
        try{if(database(server).hasReceipt("match:"+settlement,player))return;database(server).apply("match:"+settlement,player,"tokens","",p.tokensPerMatch(),p,catalog(),RANDOM);var online=server.getPlayerList().getPlayer(player);if(online!=null)sync(online,0,false,"对局奖励："+p.tokensPerMatch()+" 招募令");}
        catch(Exception e){throw new IllegalStateException("将符结算失败",e);}
    }
    private static void commands() {
        var give=Commands.literal("give");
        for(String kind:List.of("hero","skin","tokens","souls")) {
            var target=Commands.argument("players",GameProfileArgument.gameProfile());
            if(kind.equals("hero") || kind.equals("skin")) {
                var hero=Commands.argument("hero",StringArgumentType.string()).suggests((c,b)->SharedSuggestionProvider.suggest(catalog().stream().map(StringArgumentType::escapeIfRequired),b));
                if(kind.equals("skin"))hero.then(Commands.argument("skin",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(List.of("action"),b)).executes(c->grant(c,kind)));
                else hero.executes(c->grant(c,kind));
                target.then(hero);
            }else target.then(Commands.argument("amount",LongArgumentType.longArg(1,1000000)).executes(c->grant(c,kind)));
            give.then(Commands.literal(kind).then(target));
        }
        Balm.getCommands().register(d->d.register(Commands.literal("sgs")
            .then(Commands.literal("collection").executes(c->{sync(c.getSource().getPlayerOrException(),0,true,"");return 1;}))
            .then(Commands.literal("admin").requires(s->s.hasPermission(2)).then(give))));
    }
    private static int grant(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c,String kind) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var server=c.getSource().getServer();String value="";long amount=0;
        if(kind.equals("hero") || kind.equals("skin"))value=StringArgumentType.getString(c,"hero");else amount=LongArgumentType.getLong(c,"amount");
        try {
            if(kind.equals("skin"))value=CollectionSkins.key(value,StringArgumentType.getString(c,"skin"));
            var targets=GameProfileArgument.getGameProfiles(c,"players");int count=0;
            for(var target:targets){database(server).apply("admin:"+UUID.randomUUID(),target.getId(),kind,value,amount,policy(server),catalog(),RANDOM);
                ProbeMod.LOG.info("SGS_COLLECTION_ADMIN actor={} target={} kind={} value={} amount={}",c.getSource().getTextName(),target.getId(),kind,value,amount);
                var online=server.getPlayerList().getPlayer(target.getId());if(online!=null)sync(online,0,false,"管理员已发放藏品");count++;}
            final int n=count;c.getSource().sendSuccess(()->Component.literal("已向 "+n+" 位玩家发放"),true);return count;
        }catch(Exception e){c.getSource().sendFailure(Component.literal(e.getMessage()==null?"发放失败":e.getMessage()));return 0;}
    }
}
