package dev.sgs.validation.survival;

import dev.sgs.core.config.ServerPolicy;
import dev.sgs.validation.ProbeMod;
import java.util.*;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/** Survival access and vanilla merchant offers run solely on the server thread. */
public final class SurvivalService {
    private record Anchor(String dimension, BlockPos pos) {}
    private static final Map<MinecraftServer,ServerPolicy> POLICIES=new IdentityHashMap<>();
    private static final Map<UUID,Anchor> ANCHORS=new HashMap<>();
    private static java.nio.file.Path directory(MinecraftServer server) {
        return server.getServerDirectory().resolve("config/sgs");
    }
    public static ServerPolicy policy(MinecraftServer server) {
        return POLICIES.computeIfAbsent(server, SurvivalService::load);
    }
    private static ServerPolicy load(MinecraftServer server) {
        try {return ServerPolicy.load(directory(server));}
        catch(Exception e){throw new IllegalStateException("无法读取三国杀配置 "+directory(server),e);}
    }
    static void reload(MinecraftServer server) throws java.io.IOException {
        var next=ServerPolicy.load(directory(server));
        next.packs().validateCatalog(dev.sgs.core.addon.AddonRuntime.current());
        var collection=dev.sgs.validation.collection.CollectionService.preparePolicy(server);
        POLICIES.put(server,next);
        dev.sgs.validation.collection.CollectionService.installPolicy(server,collection);
        dev.sgs.validation.lobby.LobbyService.policyChanged(server);
        for(var level:server.getAllLevels())for(var entity:level.getAllEntities())if(entity instanceof Villager v) {
            if(v.getTradingPlayer() instanceof ServerPlayer trading && v.getTags().contains("sgs_card_merchant"))trading.closeContainer();
            merchant(v);
        }
    }
    public static void initialize() {
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.server.ServerStartingEvent.class,e->policy(e.getServer()));
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.server.ServerStartedEvent.class,e->{
            if(Boolean.getBoolean("sgs.survivalSmoke"))SurvivalSmokeChecks.run(e.getServer());
        });
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.server.ServerStoppedEvent.class,e->{POLICIES.clear();ANCHORS.clear();});
        Balm.getEvents().onEvent(PlayerLogoutEvent.class,e->ANCHORS.remove(e.getPlayer().getUUID()));
        Balm.getEvents().onEvent(EntityAddedEvent.class,e->{if(e.getEntity() instanceof Villager v && !e.getLevel().isClientSide)merchant(v);});
        Balm.getEvents().onTickEvent(TickType.Entity,TickPhase.End,entity->{
            if(entity instanceof Villager v && !v.level().isClientSide && v.tickCount%100==0)merchant(v);
        });
        Balm.getCommands().register(dispatcher->dispatcher.register(Commands.literal("sgs")
            .then(Commands.literal("packs").requires(source->source.hasPermission(2)).executes(context->{
                var source=context.getSource();var packs=policy(source.getServer()).packs();
                var catalog=dev.sgs.core.addon.AddonRuntime.current();
                var keys=new TreeSet<>(List.of("cards.standard","cards.ex","cards.junzheng","cards.zhulu",
                    "heroes.standard","heroes.wind","heroes.fire","heroes.forest","heroes.mountain","heroes.legacy"));
                catalog.packs().forEach(p->keys.add("heroes.addon."+p.id()));
                keys.addAll(packs.enabled().keySet());
                source.sendSuccess(()->Component.literal("下一局牌堆："+packs.contentPool().size()+" 张；可选武将："+
                    catalog.heroes().stream().filter(h->packs.allows(dev.sgs.core.config.PackPolicy.heroPack(h))).count()+" 名"),false);
                for(String key:keys)source.sendSuccess(()->Component.literal(key+" = "+packs.allows(key)),false);
                return 1;
            }))
            .then(Commands.literal("reload").requires(source->source.hasPermission(2)).executes(context->{
                var source=context.getSource();var server=source.getServer();
                try {
                    reload(server);
                    source.sendSuccess(()->Component.literal("三国杀配置已重载；内容包开关从下一局生效，已有对局保留原规则。关闭的玩法禁止新加入和开局。"),true);
                    return 1;
                } catch(Exception e) {source.sendFailure(Component.literal("重载失败，保留当前配置："+e.getMessage()));return 0;}
            }))));
    }
    public static void opened(ServerPlayer player,BlockPos pos) {
        ANCHORS.put(player.getUUID(),new Anchor(player.serverLevel().dimension().location().toString(),pos.immutable()));
    }
    public static String accessDenied(ServerPlayer player,boolean host) {
        String sanction=dev.sgs.validation.social.ModerationService.accessDenied(player);if(sanction!=null)return sanction;
        var p=policy(player.getServer());
        if(p.requireTable()) {
            var anchor=ANCHORS.get(player.getUUID());
            if(anchor==null || !player.serverLevel().dimension().location().toString().equals(anchor.dimension())
                || player.distanceToSqr(anchor.pos().getCenter())>64 || !player.serverLevel().hasChunkAt(anchor.pos())
                || !player.serverLevel().getBlockState(anchor.pos()).is(ProbeMod.table))return "请在牌桌旁右键牌桌进入";
        }
        if(host && p.requireDeck() && !player.getInventory().contains(new ItemStack(ProbeMod.deck)))return "房主需要携带三国杀卡组包";
        return null;
    }
    static void merchant(Villager villager) {
        if(villager.isBaby() || villager.getServer()==null || villager.getTradingPlayer()!=null)return;
        var p=policy(villager.getServer());
        // Stable across world saves and reloads; no reroll by unloading a chunk.
        boolean selected=Math.floorMod(villager.getUUID().hashCode(),100)<25;
        boolean active=p.merchants() && selected
            && villager.getVillagerData().getProfession()!=net.minecraft.world.entity.npc.VillagerProfession.NITWIT;
        if(!active && !villager.getTags().contains("sgs_card_merchant"))return;
        var offers=villager.getOffers();
        var existing=offers.stream().filter(o->o.getResult().is(ProbeMod.table.asItem()) || o.getResult().is(ProbeMod.deck)).toList();
        if(active && existing.size()==2 && existing.stream().allMatch(o->o.getBaseCostA().getCount()==(o.getResult().is(ProbeMod.deck)?4:8)))return;
        offers.removeAll(existing);
        if(active) {
            villager.addTag("sgs_card_merchant");
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD,8),new ItemStack(ProbeMod.table),12,1,0));
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD,4),new ItemStack(ProbeMod.deck),12,1,0));
            if(!villager.hasCustomName())villager.setCustomName(Component.translatable("entity.sgs.card_merchant"));
        } else {
            villager.removeTag("sgs_card_merchant");
            if(villager.getCustomName()!=null && villager.getCustomName().equals(Component.translatable("entity.sgs.card_merchant")))villager.setCustomName(null);
        }
    }
}
