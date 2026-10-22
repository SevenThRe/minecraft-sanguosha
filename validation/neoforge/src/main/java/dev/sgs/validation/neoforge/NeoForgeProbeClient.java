package dev.sgs.validation.neoforge;

import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.client.ProbeClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.blay09.mods.balm.api.client.BalmClient;
import net.blay09.mods.balm.neoforge.NeoForgeLoadContext;

@Mod(value = ProbeMod.ID, dist = Dist.CLIENT)
public final class NeoForgeProbeClient {
    public NeoForgeProbeClient(IEventBus bus) {
        BalmClient.initializeMod(ProbeMod.ID, new NeoForgeLoadContext(bus), ProbeClient::initialize);
    }
}
