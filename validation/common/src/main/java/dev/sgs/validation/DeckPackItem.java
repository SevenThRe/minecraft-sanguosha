package dev.sgs.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A reusable deck: right click to enter the nearest loaded table within eight blocks. */
public final class DeckPackItem extends Item {
    public DeckPackItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            BlockPos nearest = null;
            double distance = 64;
            for (BlockPos candidate : BlockPos.betweenClosed(player.blockPosition().offset(-8, -8, -8),
                    player.blockPosition().offset(8, 8, 8))) {
                double next = player.distanceToSqr(candidate.getCenter());
                if (next <= distance && level.hasChunkAt(candidate) && level.getBlockState(candidate).is(ProbeMod.table)) {
                    nearest = candidate.immutable();
                    distance = next;
                }
            }
            if (nearest != null) RoomService.open(serverPlayer, nearest);
            else player.displayClientMessage(Component.translatable("item.sgs.deck_pack.no_table"), true);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
