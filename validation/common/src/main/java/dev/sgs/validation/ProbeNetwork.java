package dev.sgs.validation;

import java.util.UUID;
import java.util.function.Consumer;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ProbeNetwork {
    // Set only by the physical client entrypoint. No client class is referenced here.
    public static Consumer<Snapshot> clientReceiver = snapshot -> {};

    public record Intent(UUID room, int delta) implements CustomPacketPayload {
        public static final Type<Intent> TYPE = new Type<>(ProbeMod.id("test_intent"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Intent> CODEC = StreamCodec.of(
                (buf, packet) -> { buf.writeUUID(packet.room); buf.writeVarInt(packet.delta); },
                buf -> new Intent(buf.readUUID(), buf.readVarInt()));
        @Override public Type<Intent> type() { return TYPE; }
    }

    public record Snapshot(UUID room, int value, boolean open, boolean success, String reason) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ProbeMod.id("test_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    buf.writeUUID(packet.room); buf.writeVarInt(packet.value); buf.writeBoolean(packet.open);
                    buf.writeBoolean(packet.success); buf.writeUtf(packet.reason, 128);
                }, buf -> new Snapshot(buf.readUUID(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readUtf(128)));
        @Override public Type<Snapshot> type() { return TYPE; }
    }

    public static void initialize() {
        Balm.getNetworking().defineNetworkVersion(ProbeMod.ID, "1");
        Balm.getNetworking().registerServerboundPacket(Intent.TYPE, Intent.class, Intent.CODEC, RoomService::submit);
        Balm.getNetworking().registerClientboundPacket(Snapshot.TYPE, Snapshot.class, Snapshot.CODEC,
                (player, snapshot) -> clientReceiver.accept(snapshot));
    }
}
