package dev.sgs.validation;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
public final class RoomService {
    public static void open(ServerPlayer player, BlockPos pos) {
        if (!nearTable(player,player.serverLevel().dimension().location().toString(),pos)) return;
        dev.sgs.validation.survival.SurvivalService.opened(player,pos);
        Balm.getNetworking().sendTo(player,new dev.sgs.validation.lobby.LobbyNetwork.Open());
    }
    static boolean nearTable(ServerPlayer player,String dimension,BlockPos pos) {
        return player.serverLevel().dimension().location().toString().equals(dimension)
            && player.distanceToSqr(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5)<=64
            && player.serverLevel().hasChunkAt(pos)
            && player.serverLevel().getBlockState(pos).is(ProbeMod.table);
    }
}
