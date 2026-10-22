package dev.sgs.core.persistence;

import dev.sgs.core.engine.*;
import dev.sgs.core.engine.skill.*;
import java.io.*;
import java.security.*;
import java.util.*;

/** Whole damage subprogram, including each suspended parent's independent listener cursor. */
public final class DamageProgramCodec {
  private static final int MAGIC=0x53474450,VERSION=2;
  public static final int MAX_BYTES=524288;
  private DamageProgramCodec(){}
  public static byte[] encode(DamageProgramContinuation.Snapshot s){
    DamageProgramContinuation.validate(s);
    try(var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes)){
      int version=s.awaitRescueCosts()?2:1;out.writeInt(MAGIC);out.writeInt(version);out.writeUTF(s.step().name());ContinuationFrameIO.damage(out,s.damage());
      out.writeBoolean(s.distribution()!=null);if(s.distribution()!=null)ContinuationFrameIO.yiji(out,s.distribution());
      out.writeBoolean(s.reaction()!=null);if(s.reaction()!=null)ContinuationFrameIO.ganglie(out,s.reaction());
      effect(out,s.effect());out.writeInt(s.parents().size());for(var p:s.parents()){ContinuationFrameIO.damage(out,p.damage());ContinuationFrameIO.ganglie(out,p.reaction());effect(out,p.effect());listeners(out,p.listeners());}
      pending(out,s.pending());out.writeBoolean(s.taken());listeners(out,s.listeners());out.writeBoolean(s.ordered());
      if(version>=2){out.writeBoolean(s.awaitRescueCosts());var cost=s.rescueCost();out.writeBoolean(cost!=null);if(cost!=null){byte[] b=CardUseCostCodec.encode(cost.program());out.writeInt(b.length);out.write(b);out.writeLong(cost.inputId());out.writeLong(cost.inputRevision());out.writeInt(cost.hpBeforeRecovery());out.writeInt(cost.shaBefore());out.writeBoolean(cost.wineBefore());}}
      out.flush();
      byte[] body=bytes.toByteArray();if(body.length+32>MAX_BYTES)throw new IllegalArgumentException("damage program too large");bytes.write(digest(body));return bytes.toByteArray();
    }catch(IOException e){throw new IllegalArgumentException("cannot encode damage program",e);}
  }
  public static DamageProgramContinuation.Snapshot decode(byte[] bytes){
    if(bytes==null||bytes.length<40||bytes.length>MAX_BYTES)throw new IllegalArgumentException("invalid damage program size");
    byte[] body=Arrays.copyOf(bytes,bytes.length-32);if(!MessageDigest.isEqual(digest(body),Arrays.copyOfRange(bytes,bytes.length-32,bytes.length)))throw new IllegalArgumentException("damage program checksum mismatch");
    try(var in=new DataInputStream(new ByteArrayInputStream(body))){
      if(in.readInt()!=MAGIC)throw new IllegalArgumentException("unsupported damage program");int version=in.readInt();if(version<1||version>VERSION)throw new IllegalArgumentException("unsupported damage program version");
      var step=DamageProgramContinuation.Step.valueOf(in.readUTF());var damage=ContinuationFrameIO.damage(in);var distribution=bool(in)?ContinuationFrameIO.yiji(in):null;var reaction=bool(in)?ContinuationFrameIO.ganglie(in):null;var effect=effect(in);
      var parents=new ArrayList<DamageProgramContinuation.ReturnSnapshot>();int size=count(in,64);for(int i=0;i<size;i++)parents.add(new DamageProgramContinuation.ReturnSnapshot(ContinuationFrameIO.damage(in),ContinuationFrameIO.ganglie(in),effect(in),listeners(in)));
      var pending=pending(in);boolean taken=bool(in);var listeners=listeners(in);boolean ordered=bool(in);boolean awaited=version>=2&&bool(in);DamageProgramContinuation.RescueCostSnapshot cost=null;
      if(version>=2&&bool(in)){int n=count(in,CardUseCostCodec.MAX_BYTES);byte[] b=in.readNBytes(n);if(b.length!=n)throw new EOFException();cost=new DamageProgramContinuation.RescueCostSnapshot(CardUseCostCodec.decode(b),in.readLong(),in.readLong(),in.readInt(),in.readInt(),bool(in));}
      if(version>=2&&!awaited)throw new IllegalArgumentException("empty versioned rescue capability");
      var s=new DamageProgramContinuation.Snapshot(step,damage,distribution,reaction,effect,parents,pending,taken,listeners,ordered,awaited,cost);
      if(in.available()!=0)throw new IllegalArgumentException("trailing damage program data");DamageProgramContinuation.validate(s);return s;
    }catch(IOException|RuntimeException e){throw new IllegalArgumentException("invalid damage program: "+e.getMessage(),e);}
  }
  private static void listeners(DataOutputStream out,ListenerProgram.Snapshot s)throws IOException{out.writeBoolean(s!=null);if(s!=null){byte[] bytes=ListenerProgramCodec.encode(s);out.writeInt(bytes.length);out.write(bytes);}}
  private static ListenerProgram.Snapshot listeners(DataInputStream in)throws IOException{if(!bool(in))return null;int n=count(in,131072);byte[] bytes=in.readNBytes(n);if(bytes.length!=n)throw new EOFException();return ListenerProgramCodec.decode(bytes);}
  private static void effect(DataOutputStream out,AwaitableDamageEffect.Request e)throws IOException{out.writeBoolean(e!=null);if(e!=null){out.writeInt(e.owner());out.writeUTF(e.skill());out.writeUTF(e.kind().name());}}
  private static AwaitableDamageEffect.Request effect(DataInputStream in)throws IOException{return bool(in)?new AwaitableDamageEffect.Request(in.readInt(),in.readUTF(),AwaitableDamageEffect.Kind.valueOf(in.readUTF())):null;}
  private static void pending(DataOutputStream out,TrickContinuation.Pending p)throws IOException{out.writeBoolean(p!=null);if(p!=null){out.writeLong(p.id());out.writeUTF(p.type().name());out.writeInt(p.asker());out.writeInt(p.subject());out.writeLong(p.deadline());out.writeInt(p.choices().size());for(long id:p.choices())out.writeLong(id);out.writeBoolean(p.hidden());out.writeUTF(p.skill());out.writeLong(p.revision());}}
  private static TrickContinuation.Pending pending(DataInputStream in)throws IOException{if(!bool(in))return null;long id=in.readLong();var type=PromptType.valueOf(in.readUTF());int asker=in.readInt(),subject=in.readInt();long deadline=in.readLong();var cards=new ArrayList<Long>();int size=count(in,160);for(int i=0;i<size;i++)cards.add(in.readLong());return new TrickContinuation.Pending(id,type,asker,subject,deadline,cards,bool(in),in.readUTF(),in.readLong());}
  private static boolean bool(DataInputStream in)throws IOException{int b=in.readUnsignedByte();if(b>1)throw new IllegalArgumentException("invalid damage boolean");return b==1;}
  private static int count(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<0||n>max)throw new IllegalArgumentException("invalid damage collection length");return n;}
  private static byte[] digest(byte[] bytes){try{return MessageDigest.getInstance("SHA-256").digest(bytes);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
