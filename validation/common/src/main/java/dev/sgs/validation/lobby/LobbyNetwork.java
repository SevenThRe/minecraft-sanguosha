package dev.sgs.validation.lobby;

import dev.sgs.validation.ProbeMod;
import java.util.List;
import java.util.function.Consumer;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Connection identity is supplied by Balm; no client-claimed player ID on this wire. */
public final class LobbyNetwork {
    public static Consumer<Snapshot> clientReceiver = ignored -> {};
    public static Runnable openReceiver = () -> {};
    public record Open() implements CustomPacketPayload {
        public static final Type<Open> TYPE=new Type<>(ProbeMod.id("open_lobby"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Open> CODEC=StreamCodec.unit(new Open());
        @Override public Type<Open> type(){return TYPE;}
    }
    public record SeatView(int slot,int kind,String name,String uuid,boolean ready,boolean owner) {}
    /** Public room settings never contain a password or its verifier. */
    public record SettingsView(int turnSeconds,int responseSeconds,int selectionSeconds,boolean allowChat,
        boolean allowSpectators,boolean doubleRenegade,boolean fixedSeats,java.util.Set<String> bannedHeroes,boolean locked) {}
    public record PlayerView(String uuid,String name) {}
    public record HeroView(String id,String name,String kingdom,String portrait,String description,
        List<dev.sgs.core.addon.SkillPresentation> skills) {}
    public record RoomView(long id, String name, String mode, int members, int capacity, int ready,
                           int ai,boolean owner,boolean playing,List<SeatView> seats,boolean teammateSharing,SettingsView settings) {}
    public record View(long revision, long request, boolean success, String reason, long ownRoom,
                       String queued, int sixQueued, int eightQueued, boolean ownReady, List<RoomView> rooms,int twoVTwoQueued,
                       String selfName,List<PlayerView> players,long inviteRoom,List<String> chat,List<HeroView> heroes,long selfXp,long selfHonor,
                       List<String> enabledModes,boolean matchmaking) {}

    public record Intent(long request, long revision, String action, String mode, long room,String data) implements CustomPacketPayload {
        public Intent(long request,long revision,String action,String mode,long room){this(request,revision,action,mode,room,"");}
        public static final Type<Intent> TYPE = new Type<>(ProbeMod.id("lobby_intent"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Intent> CODEC = StreamCodec.of(
            (buf,p) -> {buf.writeVarLong(p.request);buf.writeVarLong(p.revision);buf.writeUtf(p.action,16);buf.writeUtf(p.mode,16);buf.writeVarLong(p.room);buf.writeUtf(p.data,8192);},
            buf -> new Intent(buf.readVarLong(),buf.readVarLong(),buf.readUtf(16),buf.readUtf(16),buf.readVarLong(),buf.readUtf(8192)));
        @Override public Type<Intent> type() { return TYPE; }
    }
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ProbeMod.id("lobby_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC = StreamCodec.of(
            (buf,p) -> buf.writeUtf(p.json,250000),buf -> new Snapshot(buf.readUtf(250000)));
        @Override public Type<Snapshot> type() { return TYPE; }
    }
    public static void initialize() {
        Balm.getNetworking().registerClientboundPacket(Open.TYPE,Open.class,Open.CODEC,(player,p)->openReceiver.run());
        Balm.getNetworking().registerServerboundPacket(Intent.TYPE,Intent.class,Intent.CODEC,LobbyService::handle);
        Balm.getNetworking().registerClientboundPacket(Snapshot.TYPE,Snapshot.class,Snapshot.CODEC,(player,p) -> clientReceiver.accept(p));
    }
}
