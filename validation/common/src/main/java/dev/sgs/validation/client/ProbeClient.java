package dev.sgs.validation.client;
public final class ProbeClient {
    public static void initialize() {
        dev.sgs.validation.client.art.HeroRenderer.initialize();
        CollectionClient.initialize();LobbyClient.initialize();MatchClient.initialize();
        FeedbackClient.initialize();GiftClient.initialize();
    }
}
