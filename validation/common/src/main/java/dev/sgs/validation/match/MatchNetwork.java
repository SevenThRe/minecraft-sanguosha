package dev.sgs.validation.match;

import com.google.gson.Gson;
import dev.sgs.core.match.MatchAction;
import dev.sgs.core.match.dto.MatchViews.MatchSnapshot;
import dev.sgs.validation.ProbeMod;
import java.util.List;
import java.util.function.Consumer;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class MatchNetwork {
    public static final Gson JSON=new Gson();
    public record View(long matchId,long revision,MatchSnapshot snapshot,int nonceFloor,long connectionEpoch,long roomId,java.util.Map<Integer,String> skins) {
        public View { skins=skins==null?java.util.Map.of():java.util.Map.copyOf(skins); }
        public View(long matchId,long revision,MatchSnapshot snapshot,int nonceFloor,long connectionEpoch,long roomId){this(matchId,revision,snapshot,nonceFloor,connectionEpoch,roomId,java.util.Map.of());}
        public View(long matchId,long revision,MatchSnapshot snapshot){this(matchId,revision,snapshot,0,0L,matchId);}
        public View(long matchId,long revision,MatchSnapshot snapshot,int nonceFloor,long connectionEpoch){this(matchId,revision,snapshot,nonceFloor,connectionEpoch,matchId);}
    }
    public record Command(long matchId,long revision,String kind,int nonce,long promptId,boolean accept,
        long cardId,int declaredType,int target,int secondary,String skill,List<Long> cards,String hero,List<Integer> targets,long controlVersion) {
        public MatchAction action() {return MatchAction.of(MatchAction.Kind.valueOf(kind),nonce,promptId,accept,
            cardId,declaredType,target,secondary,skill,cards==null?List.of():cards,hero,targets==null?List.of():targets);}
        public static Command of(View view,MatchAction action) {return new Command(view.matchId,view.revision,
            action.kind().name(),action.nonce(),action.promptId(),action.accept(),action.cardId(),action.declaredTypeOrdinal(),
            action.targetSeat(),action.secondarySeat(),action.skillId(),action.cardIds(),action.heroId(),action.targetSeats(),view.snapshot().seats().stream().filter(seat->seat.self()).findFirst().map(seat->seat.control().version()).orElse(0L));}
    }
    public static Consumer<View> receiver=ignored->{};
    public static Consumer<String> errorReceiver=ignored->{};
    public static Consumer<Boolean> resumeReceiver=ignored->{};
    public record ResumeStatus(boolean available) implements CustomPacketPayload {
        public static final Type<ResumeStatus> TYPE=new Type<>(ProbeMod.id("match_resume"));
        public static final StreamCodec<RegistryFriendlyByteBuf,ResumeStatus> CODEC=StreamCodec.of((b,p)->b.writeBoolean(p.available),b->new ResumeStatus(b.readBoolean()));
        public Type<ResumeStatus> type(){return TYPE;}
    }
    public record ControlIntent(long matchId,long epoch,long version,int nonce,boolean enabled) implements CustomPacketPayload {
        public static final Type<ControlIntent> TYPE=new Type<>(ProbeMod.id("match_control"));
        public static final StreamCodec<RegistryFriendlyByteBuf,ControlIntent> CODEC=StreamCodec.of((b,p)->{b.writeLong(p.matchId);b.writeLong(p.epoch);b.writeLong(p.version);b.writeInt(p.nonce);b.writeBoolean(p.enabled);},
            b->new ControlIntent(b.readLong(),b.readLong(),b.readLong(),b.readInt(),b.readBoolean()));
        public Type<ControlIntent> type(){return TYPE;}
    }
    public record Intent(String json) implements CustomPacketPayload {
        public static final Type<Intent> TYPE=new Type<>(ProbeMod.id("match_intent"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Intent> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,12000),b->new Intent(b.readUtf(12000)));
        public Type<Intent> type(){return TYPE;}
    }
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(ProbeMod.id("match_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,60000),b->new Snapshot(b.readUtf(60000)));
        public Type<Snapshot> type(){return TYPE;}
    }
    public record Error(String reason) implements CustomPacketPayload {
        public static final Type<Error> TYPE=new Type<>(ProbeMod.id("match_error"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Error> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.reason,256),b->new Error(b.readUtf(256)));
        public Type<Error> type(){return TYPE;}
    }
    public static void initialize() {
        Balm.getNetworking().registerServerboundPacket(ControlIntent.TYPE,ControlIntent.class,ControlIntent.CODEC,MatchService::control);
        Balm.getNetworking().registerServerboundPacket(Intent.TYPE,Intent.class,Intent.CODEC,MatchService::submit);
        Balm.getNetworking().registerClientboundPacket(ResumeStatus.TYPE,ResumeStatus.class,ResumeStatus.CODEC,(p,s)->resumeReceiver.accept(s.available()));
        Balm.getNetworking().registerClientboundPacket(Snapshot.TYPE,Snapshot.class,Snapshot.CODEC,(p,s)->receiver.accept(JSON.fromJson(s.json,View.class)));
        Balm.getNetworking().registerClientboundPacket(Error.TYPE,Error.class,Error.CODEC,(p,e)->errorReceiver.accept(e.reason));
    }
}
