package dev.sgs.platform;

import java.nio.file.Path;

import dev.architectury.injectables.annotations.ExpectPlatform;

public final class SgsPlatform {

  private SgsPlatform() {}

  @ExpectPlatform
  public static Path getConfigDirectory() {
    throw new AssertionError("Platform implementation missing");
  }
}
