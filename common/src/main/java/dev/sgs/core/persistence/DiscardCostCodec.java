package dev.sgs.core.persistence;

import dev.sgs.core.engine.*;
import dev.sgs.core.engine.skill.MovementListenerProgram;
import java.io.*;
import java.util.*;

/** Bounded atomic discard child payload, covered by its parent checkpoint checksum. */
public final class DiscardCostCodec {
  public static final int MAX_BYTES=524288;
  private DiscardCostCodec(){}
  public static byte[] encode(DiscardCostContinuation.Snapshot s){
    try(var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes)){
      int version=s.actor()==s.owner()?1:2;out.writeInt(version);out.writeInt(s.actor());if(version>=2)out.writeInt(s.owner());out.writeInt(s.cards().size());for(long id:s.cards())out.writeLong(id);out.writeUTF(s.step().name());writeQueue(out,s.queue());
      out.flush();if(bytes.size()>MAX_BYTES)throw new IllegalArgumentException("discard payment too large");return bytes.toByteArray();
    }catch(IOException error){throw new IllegalArgumentException("cannot encode discard payment",error);}
  }
  public static DiscardCostContinuation.Snapshot decode(byte[] bytes){
    if(bytes==null||bytes.length<8||bytes.length>MAX_BYTES)throw new IllegalArgumentException("invalid discard payment size");
    try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){
      int version=in.readInt();if(version<1||version>2)throw new IllegalArgumentException("unsupported discard payment format");int actor=in.readInt();int owner=version>=2?in.readInt():actor;int n=count(in,160);var cards=new ArrayList<Long>();for(int i=0;i<n;i++)cards.add(in.readLong());var step=DiscardCostContinuation.Step.valueOf(in.readUTF());var queue=readQueue(in);
      if(in.available()!=0)throw new IllegalArgumentException("trailing discard payment bytes");return new DiscardCostContinuation.Snapshot(actor,owner,cards,step,queue);
    }catch(IOException|RuntimeException error){throw new IllegalArgumentException("invalid discard payment",error);}
  }
  static void writeQueue(DataOutputStream out,CostMovementQueue.Snapshot q)throws IOException{
    out.writeLong(q.revision());out.writeInt(q.movements().size());
      for(var m:q.movements()){byte[] b=MovementListenerCodec.encode(m);out.writeInt(b.length);out.write(b);}var p=q.pending();out.writeBoolean(p!=null);
      if(p!=null){out.writeLong(p.id());out.writeInt(p.asker());out.writeInt(p.subject());out.writeLong(p.deadline());out.writeUTF(p.skill());out.writeLong(p.revision());}
  }
  static CostMovementQueue.Snapshot readQueue(DataInputStream in)throws IOException{
    long revision=in.readLong();int n=count(in,160);var moves=new ArrayList<MovementListenerProgram.Snapshot>();
      for(int i=0;i<n;i++){int size=count(in,MovementListenerCodec.MAX_BYTES);byte[] b=in.readNBytes(size);if(b.length!=size)throw new EOFException();moves.add(MovementListenerCodec.decode(b));}
      TrickContinuation.Pending p=null;if(bool(in))p=new TrickContinuation.Pending(in.readLong(),PromptType.SKILL_INVOKE,in.readInt(),in.readInt(),in.readLong(),List.of(),false,in.readUTF(),in.readLong());
    return new CostMovementQueue.Snapshot(moves,p,revision);
  }
  private static int count(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<0||n>max)throw new IllegalArgumentException("invalid discard payment length");return n;}
  private static boolean bool(DataInputStream in)throws IOException{int n=in.readUnsignedByte();if(n>1)throw new IllegalArgumentException("invalid discard payment boolean");return n==1;}
}
