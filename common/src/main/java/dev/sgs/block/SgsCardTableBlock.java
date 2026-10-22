package dev.sgs.block;

import dev.sgs.config.SgsConfig;
import dev.sgs.lobby.SgsLobbyService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/** The physical card table block. Right-clicking opens the fullscreen lobby GUI. */
public class SgsCardTableBlock extends Block {

  public SgsCardTableBlock() {
    super(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(2.5F)
            .sound(SoundType.WOOD));
  }

  @Override
  public InteractionResult use(
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    if (!SgsConfig.get().enableCardTable()) {
      return InteractionResult.PASS;
    }
    if (level.isClientSide) {
      return InteractionResult.SUCCESS;
    }
    if (player instanceof ServerPlayer serverPlayer && SgsLobbyService.get() != null) {
      SgsLobbyService.get().openLobby(serverPlayer);
    }
    return InteractionResult.CONSUME;
  }
}
