package dev.sgs.validation.client;

import dev.sgs.validation.ProbeNetwork;
import dev.sgs.validation.ProbeMod;
import net.minecraft.client.Minecraft;

public final class ProbeClient {
    public static void initialize() {
        EnglishLocalizationChecks.initialize();
        MusicSmokeChecks.initialize();
        TableModelSmokeChecks.initialize();
        CreativeTabSmokeChecks.initialize();
        dev.sgs.validation.client.art.HeroRenderer.initialize();
        CollectionClient.initialize();
        CollectionGuiSmokeChecks.initialize();
        LobbyClient.initialize();
        MatchClient.initialize();
        FeedbackClient.initialize();
        GiftClient.initialize();
        RoomMatchSmokeChecks.initialize();
        LobbySmokeChecks.initialize();
        LobbyMultiplayerChecks.initialize();
        LobbyFlowSmokeChecks.initialize();
        LobbyHomeArtSmokeChecks.initialize();
        HealthRailSmokeChecks.initialize();
        SeatWaitLimitedRenderChecks.initialize();
        ResponsePresentationRenderChecks.initialize();
        BetaTodoRenderChecks.initialize();
        PublicCardMotionRenderChecks.initialize();
        VideoEffectsRenderChecks.initialize();
        JudgmentPresentationRenderChecks.initialize();
        TuxiBanRenderChecks.initialize();JournalRenderChecks.initialize();MatchChatRenderChecks.initialize();
        HeroConfirmationChecks.initialize();
        WorldRestartSmokeChecks.initialize();
        ProbeNetwork.clientReceiver = snapshot -> {
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> {
                ProbeMod.LOG.info("SGS client received room={} value={} success={} reason={}",
                        snapshot.room(), snapshot.value(), snapshot.success(), snapshot.reason());
                if (client.screen instanceof ProbeScreen screen && screen.room().equals(snapshot.room())) screen.update(snapshot);
                else if (snapshot.open()) client.setScreen(new ProbeScreen(snapshot));
                ClientSmokeChecks.received(snapshot);
            });
        };
        ClientSmokeChecks.initialize();
        dev.sgs.validation.client.art.ArtSmokeChecks.initialize();
        dev.sgs.validation.client.art.MythicArtSmokeChecks.initialize();
    }
}
