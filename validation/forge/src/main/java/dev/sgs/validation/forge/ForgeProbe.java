package dev.sgs.validation.forge;

import dev.sgs.validation.ProbeMod;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.EmptyLoadContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ProbeMod.ID)
public final class ForgeProbe {
    public ForgeProbe(FMLJavaModLoadingContext context) {
        String version = context.getContainer().getModInfo().getVersion().toString();
        Balm.initializeMod(ProbeMod.ID, EmptyLoadContext.INSTANCE, () -> ProbeMod.initialize("Forge", version));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppingEvent event)->
                dev.sgs.validation.match.WorldMatchPersistence.save(event.getServer()));
        DistExecutor.runWhenOn(Dist.CLIENT, () -> ClientInit::initialize);
    }

    private static final class ClientInit {
        private static void initialize() {
            net.blay09.mods.balm.api.client.BalmClient.initializeMod(ProbeMod.ID, EmptyLoadContext.INSTANCE,
                    dev.sgs.validation.client.ProbeClient::initialize);
        }
    }
}
