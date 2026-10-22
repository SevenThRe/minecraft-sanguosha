package dev.sgs.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.sgs.Sgs;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class SgsTabs {

  public static final DeferredRegister<CreativeModeTab> TABS =
      DeferredRegister.create(Sgs.MOD_ID, Registries.CREATIVE_MODE_TAB);

  public static final RegistrySupplier<CreativeModeTab> MAIN =
      TABS.register(
          "main",
          () ->
              CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                  .title(Component.translatable("itemGroup.sgs.main"))
                  .icon(() -> new ItemStack(SgsItems.CARD_TABLE.get()))
                  .displayItems((parameters, output) -> output.accept(SgsItems.CARD_TABLE.get()))
                  .build());

  private SgsTabs() {}
}
