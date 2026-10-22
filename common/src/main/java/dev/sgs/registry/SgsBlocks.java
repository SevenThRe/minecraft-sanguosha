package dev.sgs.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.sgs.Sgs;
import dev.sgs.block.SgsCardTableBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;

public final class SgsBlocks {

  public static final DeferredRegister<Block> BLOCKS =
      DeferredRegister.create(Sgs.MOD_ID, Registries.BLOCK);

  public static final RegistrySupplier<Block> CARD_TABLE =
      BLOCKS.register("card_table", SgsCardTableBlock::new);

  private SgsBlocks() {}
}
