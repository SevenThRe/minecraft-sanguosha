package dev.sgs.fabric;

import dev.sgs.client.SgsClient;
import net.fabricmc.api.ClientModInitializer;

public class SgsFabricClient implements ClientModInitializer {

  @Override
  public void onInitializeClient() {
    SgsClient.init();
  }
}
