package dev.sgs.forge;

import dev.sgs.client.SgsClient;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class SgsForgeClient {

  private SgsForgeClient() {}

  /** Called only when the physical side is the client. */
  public static void register() {
    FMLJavaModLoadingContext.get()
        .getModEventBus()
        .addListener((FMLClientSetupEvent event) -> event.enqueueWork(SgsClient::init));
  }
}
