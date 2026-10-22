package dev.sgs.validation.collection;

import dev.sgs.validation.ProbeMod;
import java.util.*;
import java.util.function.Consumer;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class CollectionNetwork {
    public static final com.google.gson.Gson JSON=new com.google.gson.Gson();
    public static Consumer<State> receiver=ignored->{};
    public static Consumer<Failure> failureReceiver=ignored->{};
    public record Failure(long request,String message) implements CustomPacketPayload {
        public static final Type<Failure> TYPE=new Type<>(ProbeMod.id("collection_error"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Failure> CODEC=StreamCodec.of((b,p)->{b.writeVarLong(p.request);b.writeUtf(p.message,256);},b->new Failure(b.readVarLong(),b.readUtf(256)));
        public Type<Failure> type(){return TYPE;}
    }
    public record Hero(String id,String name,boolean owned,long price,boolean forSale,String kingdom,int maxHp,
        String portrait,String description,List<dev.sgs.core.addon.SkillPresentation> skills,
        List<dev.sgs.core.addon.SkillPresentation> derivedSkills){
        public Hero {skills=skills==null?List.of():List.copyOf(skills);derivedSkills=derivedSkills==null?List.of():List.copyOf(derivedSkills);}
        public Hero(String id,String name,boolean owned,long price,boolean forSale,String kingdom,int maxHp,
            String portrait,String description,List<dev.sgs.core.addon.SkillPresentation> skills){
            this(id,name,owned,price,forSale,kingdom,maxHp,portrait,description,skills,List.of());
        }
    }
    public record State(long request,boolean open,boolean enabled,boolean market,long tokens,long souls,int guaranteedNewBelow,
        Set<String> skins,List<Hero> heroes,String message,List<dev.sgs.core.collection.CollectionDatabase.Draw> draws){}
    public record Intent(long request,String action,String hero,long expectedPrice) implements CustomPacketPayload {
        public Intent(long request,String action,String hero){this(request,action,hero,-1);}
        public static final Type<Intent> TYPE=new Type<>(ProbeMod.id("collection_intent"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Intent> CODEC=StreamCodec.of(
            (b,p)->{b.writeVarLong(p.request);b.writeUtf(p.action,16);b.writeUtf(p.hero,128);b.writeVarLong(p.expectedPrice);},b->new Intent(b.readVarLong(),b.readUtf(16),b.readUtf(128),b.readVarLong()));
        public Type<Intent> type(){return TYPE;}
    }
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(ProbeMod.id("collection_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,200000),b->new Snapshot(b.readUtf(200000)));
        public Type<Snapshot> type(){return TYPE;}
    }
    public static void initialize(){
        Balm.getNetworking().registerServerboundPacket(Intent.TYPE,Intent.class,Intent.CODEC,CollectionService::handle);
        Balm.getNetworking().registerClientboundPacket(Snapshot.TYPE,Snapshot.class,Snapshot.CODEC,(p,s)->receiver.accept(JSON.fromJson(s.json,State.class)));
        Balm.getNetworking().registerClientboundPacket(Failure.TYPE,Failure.class,Failure.CODEC,(p,s)->failureReceiver.accept(s));
    }
}
