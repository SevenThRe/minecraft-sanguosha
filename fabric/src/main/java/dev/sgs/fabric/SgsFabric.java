package dev.sgs.fabric;

import dev.sgs.Sgs;
import net.fabricmc.api.ModInitializer;

public class SgsFabric implements ModInitializer {

  @Override
  public void onInitialize() {
    Sgs.init();
  }
}
