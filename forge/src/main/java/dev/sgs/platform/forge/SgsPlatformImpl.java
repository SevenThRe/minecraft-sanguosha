package dev.sgs.platform.forge;

import java.nio.file.Path;

import net.minecraftforge.fml.loading.FMLPaths;

public class SgsPlatformImpl {

  public static Path getConfigDirectory() {
    return FMLPaths.CONFIGDIR.get();
  }
}
