package dev.sgs.validation.match;

import dev.sgs.validation.ProbeMod;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class SkinNetwork {
    public static java.util.function.Consumer<Result> receiver=r->{};
    public record Intent(long match,long epoch,long request,String hero,String skin) implements CustomPacketPayload {
        public static final Type<Intent> TYPE=new Type<>(ProbeMod.id("skin_select"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Intent> CODEC=StreamCodec.of(
            (b,p)->{b.writeLong(p.match);b.writeLong(p.epoch);b.writeLong(p.request);b.writeUtf(p.hero,128);b.writeUtf(p.skin,16);},
            b->new Intent(b.readLong(),b.readLong(),b.readLong(),b.readUtf(128),b.readUtf(16)));
        public Type<Intent> type(){return TYPE;}
    }
    public record Result(long match,long request,boolean accepted,String reason) implements CustomPacketPayload {
        public static final Type<Result> TYPE=new Type<>(ProbeMod.id("skin_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Result> CODEC=StreamCodec.of(
            (b,p)->{b.writeLong(p.match);b.writeLong(p.request);b.writeBoolean(p.accepted);b.writeUtf(p.reason,256);},
            b->new Result(b.readLong(),b.readLong(),b.readBoolean(),b.readUtf(256)));
        public Type<Result> type(){return TYPE;}
    }
    public static void initialize(){
        Balm.getNetworking().registerServerboundPacket(Intent.TYPE,Intent.class,Intent.CODEC,MatchService::selectSkin);
        Balm.getNetworking().registerClientboundPacket(Result.TYPE,Result.class,Result.CODEC,(p,r)->receiver.accept(r));
    }
}
