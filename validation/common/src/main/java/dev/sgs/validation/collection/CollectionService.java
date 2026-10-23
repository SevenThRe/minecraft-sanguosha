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
        var catalog=catalog();if(!catalog.containsAll(next.drawPool()) || !catalog.containsAll(next.prices().keySet()) || !catalog.containsAll(next.freeHeroes()))throw new IllegalArgumentException("养成配置包含未加载武将ID");
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
        try{return database(server).account(player,policy(server).startingTokens(),policy(server).startingSouls()).skins();}
        catch(java.sql.SQLException e){throw new IllegalStateException("读取皮肤权限失败",e);}
    }
    public static List<String> catalog(){return dev.sgs.core.addon.AddonRuntime.current().heroes().stream().map(dev.sgs.core.hero.HeroDefinition::id).sorted().toList();}
    public static void initialize() {
        CollectionNetwork.initialize();commands();
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.server.ServerStartedEvent.class,e->{policy(e.getServer());if(Boolean.getBoolean("sgs.collectionSmoke"))CollectionSmokeChecks.run(e.getServer());});
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.PlayerLoginEvent.class,e->{
            var player=e.getPlayer();
            if(Boolean.getBoolean("sgs.collectionGuiSmoke"))try {
                database(player.getServer()).apply("gui-hero:"+player.getUUID(),player.getUUID(),"hero","caocao",0,policy(player.getServer()),catalog(),RANDOM);
                database(player.getServer()).apply("gui-souls:"+player.getUUID(),player.getUUID(),"souls","",100,policy(player.getServer()),catalog(),RANDOM);
                database(player.getServer()).apply("gui-skin:"+player.getUUID(),player.getUUID(),"skin","caocao/action",0,policy(player.getServer()),catalog(),RANDOM);
            }catch(Exception ex){throw new IllegalStateException("GUI collection seed failed",ex);}
            sync(player,0,false,"");
        });
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.PlayerLogoutEvent.class,e->WINDOWS.remove(e.getPlayer().getUUID()));
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.server.ServerStoppedEvent.class,e->{
            for(var db:DATABASES.values())try{db.close();}catch(Exception ex){ProbeMod.LOG.error("Collection database close failed",ex);}
            DATABASES.clear();POLICIES.clear();WINDOWS.clear();
        });
    }
    static void reopenForSmoke(MinecraftServer server) throws java.sql.SQLException {var old=DATABASES.remove(server);if(old!=null)old.close();database(server);}
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
                    if(!request.action().equals("buy") && request.expectedPrice()!=Math.multiplyExact(policy(server).drawTokenCost(),request.action().equals("draw10")?10:1))throw new IllegalArgumentException("招募消耗已变化，请刷新后重试");
                    var result=database(server).apply(scope+":"+request.request(),player.getUUID(),request.action(),request.hero(),0,policy(server),catalog(),RANDOM);
                    String message=request.action().equals("buy")?"已购买 "+name(result.hero()):request.action().equals("draw10")?"招募完成，获得 "+result.soulsAward()+" "+policy(server).soulCurrency().name():result.duplicate()?"重复武将 "+name(result.hero())+"，获得 "+result.soulsAward()+" "+policy(server).soulCurrency().name():"获得新武将 "+name(result.hero());
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
            var server=player.getServer();var policy=policy(server);var account=database(server).account(player.getUUID(),policy.startingTokens(),policy.startingSouls());var pool=policy.pool(catalog());
            var rows=dev.sgs.core.addon.AddonRuntime.current().heroes().stream().map(h->{var p=dev.sgs.core.addon.AddonRuntime.current().present(List.of(h.id())).getFirst();return new CollectionNetwork.Hero(h.id(),p.name(),policy.playable(catalog(),account.heroes()).contains(h.id()),policy.price(h.id()),pool.contains(h.id()),h.kingdom().name(),h.maxHp(),p.portrait(),p.description(),p.skills(),dev.sgs.core.addon.DerivedSkillPresentations.resolve(dev.sgs.core.addon.AddonRuntime.current(),p.skills()));}).toList();
            var groups=new LinkedHashMap<String,String>();
            for(var h:dev.sgs.core.addon.AddonRuntime.current().heroes()){
                String group=dev.sgs.core.hero.MythicHeroes.ADDED.stream().anyMatch(e->e.hero().id().equals(h.id()))||Set.of("huangzhong","weiyan","pangde","xuhuang","dianwei").contains(h.id())?"神话再临":Set.of("yujin","huaxiong","gongsunzan").contains(h.id())?"其他武将":"标准包";
                if(h.id().contains(":")){String ns=h.id().split(":",2)[0];group=dev.sgs.core.addon.AddonRuntime.current().packs().stream().filter(p->p.id().equals(ns)).map(p->p.name()).findFirst().orElse("扩展武将");}
                groups.put(h.id(),group);
            }
            var state=new CollectionNetwork.State(request,open,policy.enabled(),policy.marketEnabled(),account.tokens(),account.souls(),policy.guaranteedNewBelow(),account.skins(),rows,message,draws,policy.tokenCurrency(),policy.soulCurrency(),policy.drawTokenCost(),groups,earningHints(server,player.getUUID()));
            Balm.getNetworking().sendTo(player,new CollectionNetwork.Snapshot(CollectionNetwork.JSON.toJson(state)));
        }catch(Exception e){ProbeMod.LOG.error("Collection sync failed; preserving account",e);
            try{Balm.getNetworking().sendTo(player,new CollectionNetwork.Failure(request,"藏品服务暂不可用，数据已保留；可刷新重试"));}catch(RuntimeException closed){ProbeMod.LOG.debug("Collection error delivery unavailable",closed);}}
    }
    public static Map<UUID,Set<String>> ownedPools(MinecraftServer server,List<UUID> humans,List<String> pool,int seats) {
        if(!policy(server).enabled())return Map.of();
        try {
            var result=new HashMap<UUID,Set<String>>();var eligible=new ArrayList<Set<String>>();
            for(UUID id:humans){var own=policy(server).playable(pool,database(server).account(id,policy(server).startingTokens(),policy(server).startingSouls()).heroes());result.put(id,own);eligible.add(own);}
            while(eligible.size()<seats)eligible.add(Set.copyOf(pool));
            OwnedHeroSelection.deal(pool,eligible,4,new Random(0));return Map.copyOf(result);
        }catch(java.sql.SQLException e){throw new IllegalStateException("读取武将藏品失败",e);}
    }
    public static void reward(MinecraftServer server,dev.sgs.core.social.MatchLedger.Receipt receipt,dev.sgs.core.social.MatchLedger.MatchRecord match) {
        var p=policy(server);
        try{
            var result=database(server).settle(receipt,match,p);if(result.replayed())return;
            var online=server.getPlayerList().getPlayer(receipt.player());
            if(online!=null){
                String message=result.tokens()+result.souls()==0?"":"完局及任务奖励："+result.tokens()+" "+p.tokenCurrency().name()+"，"+result.souls()+" "+p.soulCurrency().name();
                sync(online,0,false,message);if(!message.isEmpty())online.sendSystemMessage(Component.literal(message));
            }
        }catch(Exception e){throw new IllegalStateException("藏品奖励结算失败",e);}
    }
    private static List<String> earningHints(MinecraftServer server,UUID player) throws java.sql.SQLException {
        var p=policy(server);var r=p.rewards();var progress=database(server).progress(player,System.currentTimeMillis(),r);
        if(!p.enabled())return List.of("招募未启用，武将自由使用");
        var lines=new ArrayList<String>();
        if(r.enabled()){
            lines.add("有效完局至少 "+r.minimumSeconds()+" 秒；每日按 "+r.zone()+" 重置");
            for(boolean ai:List.of(false,true)){
                var rule=ai?r.ai():r.pvp();if(!rule.enabled())continue;
                lines.add((ai?"人机":"真人满员")+"完局："+rule.tokens()+" 招募令 + "+rule.souls()+" 将符；胜利另加 "+rule.winTokens()+" / "+rule.winSouls());
                lines.add("今日奖励局数 "+(ai?progress.ai():progress.pvp())+" / "+rule.dailyLimit());
            }
            if(r.daily().enabled())lines.add("每日完局 "+Math.min(progress.daily(),r.daily().matches())+" / "+r.daily().matches()+"："+r.daily().tokens()+" 招募令 + "+r.daily().souls()+" 将符"+(progress.dailyClaimed()?"（已到账）":""));
            if(r.weekly().enabled())lines.add("每周完局 "+Math.min(progress.weekly(),r.weekly().matches())+" / "+r.weekly().matches()+"："+r.weekly().tokens()+" 招募令 + "+r.weekly().souls()+" 将符"+(progress.weeklyClaimed()?"（已到账）":""));
            lines.add("任务达标自动到账；"+(r.aiTasks()?"人机局计入":"人机局不计入")+"；超过每日奖励局数不计入任务");
        }else lines.add("完局与任务奖励已关闭");
        lines.add("重复招募转为 "+p.duplicateSouls()+" 将符，可在商店兑换指定武将");
        lines.add("活动奖励由服主发放");
        return lines.stream().map(line->line.replace("招募令",p.tokenCurrency().name()).replace("将符",p.soulCurrency().name())).toList();
    }
    private static int showBalance(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c,boolean admin) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var server=c.getSource().getServer();
        try{
            var ids=admin?GameProfileArgument.getGameProfiles(c,"players").stream().map(com.mojang.authlib.GameProfile::getId).toList():List.of(c.getSource().getPlayerOrException().getUUID());
            var p=policy(server);
            for(var id:ids){var a=database(server).account(id,p.startingTokens(),p.startingSouls());c.getSource().sendSuccess(()->Component.literal(id+"："+a.tokens()+" "+p.tokenCurrency().name()+"，"+a.souls()+" "+p.soulCurrency().name()),false);}
            return ids.size();
        }catch(java.sql.SQLException e){c.getSource().sendFailure(Component.literal("读取余额失败，数据已保留"));return 0;}
    }
    private static int showTasks(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player=c.getSource().getPlayerOrException();
        try{for(String line:earningHints(player.getServer(),player.getUUID()))player.sendSystemMessage(Component.literal(line));return 1;}
        catch(java.sql.SQLException e){c.getSource().sendFailure(Component.literal("读取任务失败，数据已保留"));return 0;}
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
        var take=Commands.literal("take");
        for(String kind:List.of("tokens","souls"))take.then(Commands.literal(kind).then(Commands.argument("players",GameProfileArgument.gameProfile())
            .then(Commands.argument("amount",LongArgumentType.longArg(1,1000000)).executes(c->grant(c,"take_"+kind)))));
        Balm.getCommands().register(d->d.register(Commands.literal("sgs")
            .then(Commands.literal("collection").executes(c->{sync(c.getSource().getPlayerOrException(),0,true,"");return 1;}))
            .then(Commands.literal("balance").executes(c->showBalance(c,false)))
            .then(Commands.literal("tasks").executes(CollectionService::showTasks))
            .then(Commands.literal("admin").requires(s->s.hasPermission(2)).then(give).then(take)
                .then(Commands.literal("balance").then(Commands.argument("players",GameProfileArgument.gameProfile()).executes(c->showBalance(c,true)))))));
    }
    private static int grant(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c,String kind) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var server=c.getSource().getServer();String value="";long amount=0;
        if(kind.equals("hero") || kind.equals("skin"))value=StringArgumentType.getString(c,"hero");else amount=LongArgumentType.getLong(c,"amount");
        try {
            if(kind.equals("skin"))value=CollectionSkins.key(value,StringArgumentType.getString(c,"skin"));
            var targets=GameProfileArgument.getGameProfiles(c,"players");int count=0;
            for(var target:targets){database(server).apply("admin:"+UUID.randomUUID(),target.getId(),kind,value,amount,policy(server),catalog(),RANDOM);
                ProbeMod.LOG.info("SGS_COLLECTION_ADMIN actor={} target={} kind={} value={} amount={}",c.getSource().getTextName(),target.getId(),kind,value,amount);
                var online=server.getPlayerList().getPlayer(target.getId());if(online!=null)sync(online,0,false,"管理员已调整藏品资产");count++;}
            final int n=count;c.getSource().sendSuccess(()->Component.literal("已调整 "+n+" 位玩家资产"),true);return count;
        }catch(Exception e){c.getSource().sendFailure(Component.literal(e.getMessage()==null?"发放失败":e.getMessage()));return 0;}
    }
}
