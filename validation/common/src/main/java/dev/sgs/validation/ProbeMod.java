package dev.sgs.validation;

import com.mojang.logging.LogUtils;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.server.ServerStartedEvent;
import net.blay09.mods.balm.api.event.PlayerLoginEvent;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;

public final class ProbeMod {
    public static final String ID = "sgs";
    public static final Logger LOG = LogUtils.getLogger();
    public static Block table;
    public static Item deck;

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }

    public static void initialize(String loader, String version) {
        Balm.getBlocks().register(id -> table = new TableBlock(BlockBehaviour.Properties.of().strength(2.0f).noOcclusion()),
                (block, id) -> new BlockItem(block, new Item.Properties()), id("card_table"), null);
        Balm.getItems().registerItem(id -> deck = new DeckPackItem(new Item.Properties().stacksTo(1)), id("deck_pack"));
        dev.sgs.validation.survival.SurvivalService.initialize();
        Balm.getNetworking().defineNetworkVersion(ProbeMod.ID, "1");
        dev.sgs.validation.lobby.LobbyNetwork.initialize();
        dev.sgs.validation.match.MatchNetwork.initialize();
        dev.sgs.validation.match.SkinNetwork.initialize();
        dev.sgs.validation.social.FeedbackNetwork.initialize();
        dev.sgs.validation.social.GiftNetwork.initialize();
        dev.sgs.validation.social.GiftService.initializeCommands();
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.server.ServerStoppedEvent.class,
                event -> dev.sgs.validation.social.GiftService.stopAll());
        dev.sgs.validation.social.FeedbackService.initializeCommands();
        dev.sgs.validation.social.ModerationService.initialize();
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.PlayerLogoutEvent.class,
                event -> dev.sgs.validation.match.MatchService.disconnect(event.getPlayer()));
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.server.ServerStoppedEvent.class,
                event -> dev.sgs.validation.match.MatchService.stopAll());
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.server.ServerStoppedEvent.class,
                event -> dev.sgs.validation.social.FeedbackService.stopAll());
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.PlayerLogoutEvent.class,
                event -> dev.sgs.validation.lobby.LobbyService.disconnect(event.getPlayer()));
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.server.ServerStoppedEvent.class,
                event -> dev.sgs.validation.lobby.LobbyService.stopAll());
        Balm.getEvents().onEvent(ServerStartedEvent.class, event -> dev.sgs.validation.addon.HeroPackService.start(event.getServer()));
        Balm.getEvents().onEvent(ServerStartedEvent.class, event -> dev.sgs.validation.match.WorldMatchPersistence.start(event.getServer()));
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.server.ServerStoppedEvent.class,
                event -> dev.sgs.validation.match.WorldMatchPersistence.stoppedAll());
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.server.ServerStoppedEvent.class,event -> dev.sgs.validation.addon.HeroPackService.stop());
        Balm.getEvents().onEvent(PlayerLoginEvent.class,
                event -> dev.sgs.validation.match.MatchService.reconnect(event.getPlayer()));
        Balm.getEvents().onEvent(PlayerLoginEvent.class,
                event -> dev.sgs.validation.social.FeedbackService.onLogin(event.getPlayer()));
        dev.sgs.validation.collection.CollectionService.initialize();
        LOG.info("Minecraft-SanGuoSha version={} minecraft={} loader={}", version,
                SharedConstants.getCurrentVersion().getName(), loader);
    }
}
