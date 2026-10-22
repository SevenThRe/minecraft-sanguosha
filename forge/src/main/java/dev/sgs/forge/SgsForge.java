package dev.sgs.forge;

import dev.architectury.platform.forge.EventBuses;
import dev.sgs.Sgs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(Sgs.MOD_ID)
public class SgsForge {

  public SgsForge() {
    EventBuses.registerModEventBus(Sgs.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
    Sgs.init();
    if (FMLEnvironment.dist == Dist.CLIENT) {
      SgsForgeClient.register();
    }
  }
}
