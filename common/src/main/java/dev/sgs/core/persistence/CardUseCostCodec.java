package dev.sgs.core.persistence;

import dev.sgs.core.card.*;
import dev.sgs.core.engine.*;
import dev.sgs.core.engine.skill.MovementListenerProgram;
import java.io.*;
import java.util.*;

/** Bounded physical-payment child payload, covered by its containing checkpoint checksum. */
public final class CardUseCostCodec {
  public static final int MAX_BYTES=524288;
  private CardUseCostCodec(){}
  public static byte[] encode(CardUseCostContinuation.Snapshot s){
    try(var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes)){
      out.writeInt(s.transformSkill().isEmpty()?1:2);out.writeInt(s.actor());out.writeUTF(s.type().name());out.writeInt(s.cards().size());for(long id:s.cards())out.writeLong(id);
      out.writeBoolean(s.virtual());out.writeBoolean(s.recast());out.writeUTF(s.reason().name());out.writeUTF(s.step().name());out.writeLong(s.revision());out.writeInt(s.movements().size());
      for(var move:s.movements()){byte[] b=MovementListenerCodec.encode(move);out.writeInt(b.length);out.write(b);}
      var p=s.pending();out.writeBoolean(p!=null);if(p!=null){out.writeLong(p.id());out.writeInt(p.asker());out.writeInt(p.subject());out.writeLong(p.deadline());out.writeUTF(p.skill());out.writeLong(p.revision());}
      if(!s.transformSkill().isEmpty())out.writeUTF(s.transformSkill());
      out.flush();if(bytes.size()>MAX_BYTES)throw new IllegalArgumentException("use cost payload too large");return bytes.toByteArray();
    }catch(IOException error){throw new IllegalArgumentException("cannot encode use cost",error);}
  }
  public static CardUseCostContinuation.Snapshot decode(byte[] bytes){
    if(bytes==null||bytes.length<8||bytes.length>MAX_BYTES)throw new IllegalArgumentException("invalid use cost payload size");
    try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){
      int version=in.readInt();if(version<1||version>2)throw new IllegalArgumentException("unsupported use cost format");int actor=in.readInt();var type=CardType.valueOf(in.readUTF());
      int n=count(in,160);var cards=new ArrayList<Long>();for(int i=0;i<n;i++)cards.add(in.readLong());boolean virtual=bool(in),recast=bool(in);var reason=CardMove.Reason.valueOf(in.readUTF());var step=CardUseCostContinuation.Step.valueOf(in.readUTF());long revision=in.readLong();
      var moves=new ArrayList<MovementListenerProgram.Snapshot>();n=count(in,160);for(int i=0;i<n;i++){int size=count(in,MovementListenerCodec.MAX_BYTES);byte[] b=in.readNBytes(size);if(b.length!=size)throw new EOFException();moves.add(MovementListenerCodec.decode(b));}
      TrickContinuation.Pending pending=null;if(bool(in))pending=new TrickContinuation.Pending(in.readLong(),PromptType.SKILL_INVOKE,in.readInt(),in.readInt(),in.readLong(),List.of(),false,in.readUTF(),in.readLong());
      String transform=version>=2?in.readUTF():"";if(version>=2&&transform.isEmpty())throw new IllegalArgumentException("empty versioned transform receipt");
      if(in.available()!=0)throw new IllegalArgumentException("trailing use cost bytes");return new CardUseCostContinuation.Snapshot(actor,type,cards,virtual,recast,reason,step,moves,pending,revision,transform);
    }catch(IOException|RuntimeException error){throw new IllegalArgumentException("invalid use cost payload",error);}
  }
  private static int count(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<0||n>max)throw new IllegalArgumentException("invalid use cost length");return n;}
  private static boolean bool(DataInputStream in)throws IOException{int n=in.readUnsignedByte();if(n>1)throw new IllegalArgumentException("invalid use cost boolean");return n==1;}
}
