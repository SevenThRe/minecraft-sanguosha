package dev.sgs.validation;

import net.blay09.mods.balm.api.Balm;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public final class RoomService {
    public static void open(ServerPlayer player, BlockPos pos) {
        if (!nearTable(player, player.serverLevel().dimension().location().toString(), pos)) return;
        if (!Boolean.getBoolean("sgs.probeTable")) {
            dev.sgs.validation.survival.SurvivalService.opened(player,pos);
            Balm.getNetworking().sendTo(player,new dev.sgs.validation.lobby.LobbyNetwork.Open());
            return;
        }
        RoomData data = RoomData.get(player.getServer());
        RoomData.Entry entry = data.at(player.serverLevel().dimension().location().toString(), pos, player.getUUID());
        Balm.getNetworking().sendTo(player, new ProbeNetwork.Snapshot(entry.room().id(), entry.room().value(), true, true, "opened"));
    }

    static boolean nearTable(ServerPlayer player, String dimension, BlockPos pos) {
        return player.serverLevel().dimension().location().toString().equals(dimension)
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64
                && player.serverLevel().hasChunkAt(pos)
                && player.serverLevel().getBlockState(pos).is(ProbeMod.table);
    }

    public static void submit(ServerPlayer player, ProbeNetwork.Intent intent) {
        if (!player.getServer().isSameThread()) throw new IllegalStateException("SGS handler off server thread");
        RoomData data = RoomData.get(player.getServer());
        RoomData.Entry entry = data.find(intent.room());
        if (entry == null || !nearTable(player, entry.dimension(), entry.table())) {
            Balm.getNetworking().sendTo(player, new ProbeNetwork.Snapshot(intent.room(), 0, false, false, "room_unavailable_or_too_far"));
            return;
        }
        var result = entry.room().submit(player.getUUID(), intent.delta());
        if (result.success()) data.setDirty();
        Balm.getNetworking().sendTo(player, new ProbeNetwork.Snapshot(entry.room().id(), result.value(), false, result.success(), result.reason()));
        ProbeMod.LOG.info("SGS intent actor={} room={} delta={} result={} value={}", player.getUUID(),
                entry.room().id(), intent.delta(), result.reason(), result.value());
    }
}
