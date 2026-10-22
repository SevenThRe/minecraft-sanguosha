package dev.sgs.core.persistence;

import dev.sgs.core.card.CardType;
import dev.sgs.core.engine.DamageNature;
import dev.sgs.core.engine.skill.*;
import java.io.*;
import java.security.*;
import java.util.*;

/** Bounded standalone event-frame codec. Parent checkpoint formats opt in explicitly. */
public final class ListenerProgramCodec {
  private static final int MAGIC=0x53474c50,VERSION=1,MAX_BYTES=131072;
  private ListenerProgramCodec(){}
  public static byte[] encode(ListenerProgram.Snapshot s){
    try(var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes)){
      out.writeInt(MAGIC);out.writeInt(VERSION);out.writeUTF(s.timing().name());out.writeUTF(s.strategy().name());
      out.writeInt(s.registry().size());for(var b:s.registry()){out.writeUTF(b.id());out.writeInt(b.owner());out.writeInt(b.priority());out.writeLong(b.sequence());}
      out.writeInt(s.candidates().size());for(long candidate:s.candidates())out.writeLong(candidate);out.writeInt(s.cursor());
      out.writeBoolean(s.awaiting()!=null);if(s.awaiting()!=null){var a=s.awaiting();out.writeInt(a.owner());out.writeUTF(a.skill());out.writeUTF(a.kind().name());}
      out.writeBoolean(s.damageEvent()!=null);if(s.damageEvent()!=null){var e=s.damageEvent();out.writeInt(e.source());out.writeInt(e.target());out.writeInt(e.amount());out.writeBoolean(e.cardType()!=null);if(e.cardType()!=null)out.writeUTF(e.cardType().name());out.writeInt(e.cards().size());for(long id:e.cards())out.writeLong(id);out.writeUTF(e.nature().name());out.writeBoolean(e.chain());out.writeInt(e.turn());out.writeInt(e.currentSeat());}
      out.flush();byte[] body=bytes.toByteArray();if(body.length+32>MAX_BYTES)throw new IllegalArgumentException("listener program too large");bytes.write(digest(body));return bytes.toByteArray();
    }catch(IOException e){throw new IllegalArgumentException("cannot encode listener program",e);}
  }
  public static ListenerProgram.Snapshot decode(byte[] bytes){
    if(bytes==null||bytes.length<40||bytes.length>MAX_BYTES)throw new IllegalArgumentException("invalid listener payload size");
    byte[] body=Arrays.copyOf(bytes,bytes.length-32);if(!MessageDigest.isEqual(digest(body),Arrays.copyOfRange(bytes,bytes.length-32,bytes.length)))throw new IllegalArgumentException("listener checksum mismatch");
    try(var in=new DataInputStream(new ByteArrayInputStream(body))){
      if(in.readInt()!=MAGIC||in.readInt()!=VERSION)throw new IllegalArgumentException("unsupported listener payload");
      var timing=Timing.valueOf(in.readUTF());var strategy=OrderStrategy.valueOf(in.readUTF());
      var registry=new ArrayList<ListenerProgram.Binding>();int size=count(in,256);for(int i=0;i<size;i++)registry.add(new ListenerProgram.Binding(in.readUTF(),in.readInt(),in.readInt(),in.readLong()));
      var candidates=new ArrayList<Long>();size=count(in,256);for(int i=0;i<size;i++)candidates.add(in.readLong());int cursor=in.readInt();
      AwaitableDamageEffect.Request awaited=null;if(bool(in))awaited=new AwaitableDamageEffect.Request(in.readInt(),in.readUTF(),AwaitableDamageEffect.Kind.valueOf(in.readUTF()));
      ListenerProgram.DamageEvent event=null;if(bool(in)){int source=in.readInt(),target=in.readInt(),amount=in.readInt();var type=bool(in)?CardType.valueOf(in.readUTF()):null;var cards=new ArrayList<Long>();size=count(in,160);for(int i=0;i<size;i++)cards.add(in.readLong());event=new ListenerProgram.DamageEvent(source,target,amount,type,cards,DamageNature.valueOf(in.readUTF()),bool(in),in.readInt(),in.readInt());}
      if(in.available()!=0)throw new IllegalArgumentException("trailing listener payload");return new ListenerProgram.Snapshot(timing,strategy,registry,candidates,cursor,awaited,event);
    }catch(IOException|RuntimeException e){throw new IllegalArgumentException("invalid listener payload: "+e.getMessage(),e);}
  }
  private static boolean bool(DataInputStream in)throws IOException{int b=in.readUnsignedByte();if(b>1)throw new IllegalArgumentException("invalid listener boolean");return b==1;}
  private static int count(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<0||n>max)throw new IllegalArgumentException("invalid listener collection length");return n;}
  private static byte[] digest(byte[] bytes){try{return MessageDigest.getInstance("SHA-256").digest(bytes);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
