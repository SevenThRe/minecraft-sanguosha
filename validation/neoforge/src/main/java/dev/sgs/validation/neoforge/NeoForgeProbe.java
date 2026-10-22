package dev.sgs.validation.neoforge;

import dev.sgs.validation.ProbeMod;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.neoforge.NeoForgeLoadContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(ProbeMod.ID)
public final class NeoForgeProbe {
    public NeoForgeProbe(IEventBus bus, ModContainer container) {
        Balm.initializeMod(ProbeMod.ID, new NeoForgeLoadContext(bus),
                () -> ProbeMod.initialize("NeoForge", container.getModInfo().getVersion().toString()));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent event)->
                dev.sgs.validation.match.WorldMatchPersistence.save(event.getServer()));
    }
}
