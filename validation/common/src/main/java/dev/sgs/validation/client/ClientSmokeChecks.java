package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.ProbeNetwork;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.TickPhase;
import net.blay09.mods.balm.api.event.TickType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Optional real-client acceptance probe; submits through the normal UI and transport. */
public final class ClientSmokeChecks {
    private static final boolean ENABLED = Boolean.getBoolean("sgs.clientSmoke");
    private static int ticks;
    private static int stage;
    private static int placedAt;
    private static int completedAt;

    static void initialize() {
        if (ENABLED) {
            ProbeMod.LOG.info("SGS_CLIENT_SMOKE_ARMED");
            Balm.getEvents().onTickEvent(TickType.Client, TickPhase.End, ClientSmokeChecks::tick);
        }
    }

    private static void tick(Minecraft client) {
        if (ticks % 200 == 0) ProbeMod.LOG.info("SGS_CLIENT_SMOKE_PROGRESS stage={} screen={} connected={}",
                stage, client.screen == null ? "none" : client.screen.getClass().getSimpleName(), client.player != null);
        if (++ticks > 2400 && stage < 5) { fail(client, "timeout stage=" + stage); return; }
        if (client.player == null || client.level == null || client.gameMode == null) return;
        BlockPos table = new BlockPos(1, 90, 0);
        if (stage == 0 && client.player.getInventory().getItem(0).is(ProbeMod.table.asItem())
                && client.player.distanceToSqr(0.5, 90, 3.5) < 4 && ticks > 100) {
            client.player.getInventory().selected = 0;
            client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(new Vec3(1.5, 90, 0.5), Direction.UP, table.below(), false));
            stage = 1;
            placedAt = ticks;
        } else if (stage == 1 && ticks > placedAt + 40 && client.level.getBlockState(table).is(ProbeMod.table)) {
            client.player.getInventory().selected = 1;
            stage = 2;
            client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(table), Direction.SOUTH, table, false));
        } else if (stage == 2 && client.screen instanceof ProbeScreen screen) {
            stage = 3;
            screen.pressSubmitForSmoke();
        } else if (stage == 5 && ticks > completedAt + 20) {
            if (!(client.screen instanceof ProbeScreen screen) || screen.snapshotForSmoke().value() != 1
                    || screen.snapshotForSmoke().success()) {
                fail(client, "final screen state differs from rejected value=1");
                return;
            }
            stage = 6;
            Screenshot.grab(client.gameDirectory, "sgs-smoke.png", client.getMainRenderTarget(), message -> ProbeMod.LOG.info("{}", message.getString()));
            ProbeMod.LOG.info("SGS_CLIENT_SMOKE_PASS table_placed=true ui_button=true server_value=1 invalid_delta_rejected=true");
            if (dev.sgs.validation.client.art.ArtSmokeChecks.enabled()) dev.sgs.validation.client.art.ArtSmokeChecks.begin(client);
            else client.stop();
        }
    }

    static void received(ProbeNetwork.Snapshot snapshot) {
        if (!ENABLED) return;
        if (stage == 3 && !snapshot.open()) {
            if (!snapshot.success() || snapshot.value() != 1) { fail(Minecraft.getInstance(), "bad success reply"); return; }
            stage = 4;
            Balm.getNetworking().sendToServer(new ProbeNetwork.Intent(snapshot.room(), 0));
        } else if (stage == 4 && !snapshot.open()) {
            if (snapshot.success() || snapshot.value() != 1 || !snapshot.reason().equals("delta_must_be_1_to_5")) {
                fail(Minecraft.getInstance(), "bad rejection reply"); return;
            }
            stage = 5;
            completedAt = ticks;
            Minecraft.getInstance().getToasts().clear();
        }
    }

    private static void fail(Minecraft client, String reason) {
        stage = 6;
        ProbeMod.LOG.error("SGS_CLIENT_SMOKE_FAIL {}", reason);
        client.stop();
    }
}
