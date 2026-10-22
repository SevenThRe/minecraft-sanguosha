package dev.sgs.validation.fabric;

import dev.sgs.validation.ProbeMod;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.EmptyLoadContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricProbe implements ModInitializer {
    @Override public void onInitialize() {
        String version = FabricLoader.getInstance().getModContainer(ProbeMod.ID).orElseThrow().getMetadata().getVersion().getFriendlyString();
        Balm.initializeMod(ProbeMod.ID, EmptyLoadContext.INSTANCE, () -> ProbeMod.initialize("Fabric", version));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(dev.sgs.validation.match.WorldMatchPersistence::save);
    }
}
