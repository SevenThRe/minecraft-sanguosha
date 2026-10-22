package dev.sgs.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.sgs.Sgs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class SgsItems {

  public static final DeferredRegister<Item> ITEMS =
      DeferredRegister.create(Sgs.MOD_ID, Registries.ITEM);

  public static final RegistrySupplier<Item> CARD_TABLE =
      ITEMS.register(
          "card_table", () -> new BlockItem(SgsBlocks.CARD_TABLE.get(), new Item.Properties()));

  private SgsItems() {}
}
