package dev.sgs.validation;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;

/** Opt-in dedicated server check; never runs during normal gameplay. */
public final class SmokeChecks {
    public static void onLogin(net.minecraft.server.level.ServerPlayer player) {
        if (!System.getProperty("sgs.smoke", "").equals("interactive")) return;
        var level = player.getServer().overworld();
        for (int x = -2; x <= 3; x++) for (int z = -2; z <= 5; z++)
            level.setBlockAndUpdate(new BlockPos(x, 89, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        if(!level.getBlockState(new BlockPos(1,90,0)).is(ProbeMod.table))level.setBlockAndUpdate(new BlockPos(1,90,0),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(ProbeMod.table));
        player.getInventory().setItem(1, net.minecraft.world.item.ItemStack.EMPTY);
        if(Boolean.getBoolean("sgs.worldRestartTrick")||Boolean.getBoolean("sgs.matchSmoke"))
            player.getInventory().setItem(2,new net.minecraft.world.item.ItemStack(ProbeMod.deck));
        player.teleportTo(level, 0.5, 90, 3.5, 180, 0);
        player.inventoryMenu.broadcastChanges();
    }

    public static void run(MinecraftServer server) {
        String phase = System.getProperty("sgs.smoke", "");
        if (phase.isEmpty()) return;
        if (phase.equals("interactive")) {
            ProbeMod.LOG.info("SGS_INTERACTIVE_READY");
            return;
        }
        server.execute(() -> {
            try {
                if (BuiltInRegistries.BLOCK.get(ProbeMod.id("card_table")) != ProbeMod.table
                        || BuiltInRegistries.ITEM.get(ProbeMod.id("card_table")) == net.minecraft.world.item.Items.AIR)
                    throw new IllegalStateException("Missing table registration");
                var level = server.overworld();
                BlockPos pos = new BlockPos(0, 90, 0);
                UUID owner = UUID.fromString("8ae03836-b985-4d1e-b4e4-3957b5825900");
                RoomData data = RoomData.get(server);
                UUID checkedRoom;
                if (phase.equals("seed")) {
                    level.setBlockAndUpdate(pos, ProbeMod.table.defaultBlockState());
                    var entry = data.at("minecraft:overworld", pos, owner);
                    checkedRoom = entry.room().id();
                    if (entry.room().value() != 0 || !entry.room().submit(owner, 3).success())
                        throw new IllegalStateException("Seed needs a fresh smoke world");
                    data.setDirty();
                } else if (phase.equals("restore")) {
                    var entry = data.at("minecraft:overworld", pos, owner);
                    checkedRoom = entry.room().id();
                    if (entry.room().value() != 3 || !entry.room().owner().equals(owner))
                        throw new IllegalStateException("Persistent room was not restored");
                    if (!level.getBlockState(pos).is(ProbeMod.table)) throw new IllegalStateException("Table not persisted");
                } else throw new IllegalArgumentException("Unknown smoke phase");
                server.saveEverything(false, true, true);
                ProbeMod.LOG.info("SGS_SMOKE_PASS phase={} data_version={} value=3 room={}", phase, RoomData.DATA_VERSION, checkedRoom);
            } catch (Exception e) {
                ProbeMod.LOG.error("SGS_SMOKE_FAIL phase={}", phase, e);
            } finally {
                server.halt(false);
            }
        });
    }
}
