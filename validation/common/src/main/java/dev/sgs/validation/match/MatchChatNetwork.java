package dev.sgs.validation.match;

import dev.sgs.core.social.MatchChat;
import dev.sgs.validation.ProbeMod;
import java.util.List;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class MatchChatNetwork {
    public record Update(long match,long epoch,long request,boolean accepted,String reason,boolean enabled,List<MatchChat.Message> messages) {}
    public static java.util.function.Consumer<Update> receiver=r->{};
    public record Intent(long match,long epoch,long request,String text) implements CustomPacketPayload {
        public static final Type<Intent> TYPE=new Type<>(ProbeMod.id("match_chat_send"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Intent> CODEC=StreamCodec.of(
            (b,p)->{b.writeLong(p.match);b.writeLong(p.epoch);b.writeLong(p.request);b.writeUtf(p.text,256);},
            b->new Intent(b.readLong(),b.readLong(),b.readLong(),b.readUtf(256)));
        public Type<Intent> type(){return TYPE;}
    }
    public record Packet(String json) implements CustomPacketPayload {
        public static final Type<Packet> TYPE=new Type<>(ProbeMod.id("match_chat_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Packet> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,60000),b->new Packet(b.readUtf(60000)));
        public Type<Packet> type(){return TYPE;}
    }
    public static void send(net.minecraft.server.level.ServerPlayer player,Update update){Balm.getNetworking().sendTo(player,new Packet(MatchNetwork.JSON.toJson(update)));}
    public static void initialize(){
        Balm.getNetworking().registerServerboundPacket(Intent.TYPE,Intent.class,Intent.CODEC,MatchService::chat);
        Balm.getNetworking().registerClientboundPacket(Packet.TYPE,Packet.class,Packet.CODEC,(p,r)->receiver.accept(MatchNetwork.JSON.fromJson(r.json,Update.class)));
    }
}
