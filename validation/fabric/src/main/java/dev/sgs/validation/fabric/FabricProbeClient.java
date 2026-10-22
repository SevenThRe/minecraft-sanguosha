package dev.sgs.validation.fabric;

import dev.sgs.validation.client.ProbeClient;
import dev.sgs.validation.ProbeMod;
import net.blay09.mods.balm.api.EmptyLoadContext;
import net.blay09.mods.balm.api.client.BalmClient;
import net.fabricmc.api.ClientModInitializer;

public final class FabricProbeClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        BalmClient.initializeMod(ProbeMod.ID, EmptyLoadContext.INSTANCE, ProbeClient::initialize);
    }
}
