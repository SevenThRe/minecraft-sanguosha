package dev.sgs.validation.social;

import com.google.gson.Gson;
import dev.sgs.core.social.MatchLedger;
import dev.sgs.validation.ProbeMod;
import java.util.function.Consumer;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class FeedbackNetwork {
  public static final Gson JSON=new Gson();
  public record Command(long matchId,long requestId,String kind,int targetSeat,String reason,String detail){}
  public record RewardView(long matchId,boolean won,int xpAward,int honorAward,long totalXp,long totalHonor,String receiptId,String ruleVersion){
    public static RewardView of(MatchLedger.Receipt r){return r==null?null:new RewardView(r.matchId(),r.won(),r.xpAward(),r.honorAward(),r.totalXp(),r.totalHonor(),r.receiptId(),r.ruleVersion());}
  }
  public record Result(long requestId,String kind,boolean accepted,String reason,long matchId,RewardView reward){}
  public static Consumer<Result> receiver=ignored->{};
  public record Intent(String json) implements CustomPacketPayload {
    public static final Type<Intent> TYPE=new Type<>(ProbeMod.id("feedback_intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf,Intent> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,2000),b->new Intent(b.readUtf(2000)));
    public Type<Intent> type(){return TYPE;}
  }
  public record Response(String json) implements CustomPacketPayload {
    public static final Type<Response> TYPE=new Type<>(ProbeMod.id("feedback_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf,Response> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,4000),b->new Response(b.readUtf(4000)));
    public Type<Response> type(){return TYPE;}
  }
  public static void initialize(){
    Balm.getNetworking().registerServerboundPacket(Intent.TYPE,Intent.class,Intent.CODEC,FeedbackService::submit);
    Balm.getNetworking().registerClientboundPacket(Response.TYPE,Response.class,Response.CODEC,(p,response)->receiver.accept(JSON.fromJson(response.json,Result.class)));
  }
}
