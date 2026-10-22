package dev.sgs.validation.social;
import dev.sgs.validation.ProbeMod;
import java.util.*;
import java.util.function.Consumer;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class GiftNetwork {
    public record Command(long matchId,long requestId,int targetSeat,String kind,int amount) {}
    public record Count(int seat,long flowers,long eggs) {}
    public record Result(long matchId,long requestId,boolean accepted,String reason,int from,int to,String kind,int amount,String receipt,List<Count> totals,long flowerBalance,List<dev.sgs.core.social.GiftDefinition> catalog) {
        public dev.sgs.core.social.GiftDefinition definition(){return catalog==null?null:catalog.stream().filter(g->g.id().equals(kind)).findFirst().orElse(null);}
    }
    public static Consumer<Result> receiver=ignored->{};
    public record Intent(String json) implements CustomPacketPayload {
        public static final Type<Intent> TYPE=new Type<>(ProbeMod.id("gift_intent"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Intent> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,1000),b->new Intent(b.readUtf(1000)));
        public Type<Intent> type(){return TYPE;}
    }
    public record Response(String json) implements CustomPacketPayload {
        public static final Type<Response> TYPE=new Type<>(ProbeMod.id("gift_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Response> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,32000),b->new Response(b.readUtf(32000)));
        public Type<Response> type(){return TYPE;}
    }
    public static void initialize(){
        Balm.getNetworking().registerServerboundPacket(Intent.TYPE,Intent.class,Intent.CODEC,GiftService::submit);
        Balm.getNetworking().registerClientboundPacket(Response.TYPE,Response.class,Response.CODEC,(p,r)->receiver.accept(FeedbackNetwork.JSON.fromJson(r.json,Result.class)));
    }
}
