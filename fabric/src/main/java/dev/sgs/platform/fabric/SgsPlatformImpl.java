package dev.sgs.platform.fabric;

import java.nio.file.Path;

import net.fabricmc.loader.api.FabricLoader;

public class SgsPlatformImpl {

  public static Path getConfigDirectory() {
    return FabricLoader.getInstance().getConfigDir();
  }
}
