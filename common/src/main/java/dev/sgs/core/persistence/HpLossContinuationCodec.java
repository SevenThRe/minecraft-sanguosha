package dev.sgs.core.persistence;

import dev.sgs.core.engine.*;
import java.io.*;
import java.util.*;

/** Bounded HP-loss/rescue child payload; covered by the enclosing active-parent checksum. */
public final class HpLossContinuationCodec {
  public static final int MAX_BYTES=CardUseCostCodec.MAX_BYTES+4096;
  private HpLossContinuationCodec(){}
  public static byte[] encode(HpLossContinuation.Snapshot s){
    try(var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes)){
      out.writeInt(1);out.writeInt(s.actor());out.writeInt(s.amount());out.writeInt(s.drawCount());out.writeUTF(s.step().name());out.writeInt(s.hpBefore());
      out.writeBoolean(s.dying()!=null);
      if(s.dying()!=null){var d=s.dying();out.writeInt(d.victim());out.writeInt(d.source());out.writeUTF(d.step().name());
        out.writeInt(d.round().ring().size());for(int seat:d.round().ring())out.writeInt(seat);
        out.writeInt(d.round().cursor());out.writeBoolean(d.round().played());out.writeLong(d.revision());
        var p=d.pending();out.writeBoolean(p!=null);if(p!=null){out.writeLong(p.id());out.writeUTF(p.type().name());out.writeInt(p.asker());out.writeInt(p.subject());
          out.writeLong(p.deadline());out.writeInt(p.choices().size());for(long id:p.choices())out.writeLong(id);out.writeBoolean(p.hidden());out.writeUTF(p.skill());out.writeLong(p.revision());}
        out.writeBoolean(d.payment()!=null);if(d.payment()!=null){var b=CardUseCostCodec.encode(d.payment());out.writeInt(b.length);out.write(b);}
        out.writeLong(d.acceptedInput());out.writeInt(d.hpBeforeRecovery());out.writeInt(d.shaBefore());out.writeBoolean(d.wineBefore());
      }
      out.flush();if(bytes.size()>MAX_BYTES)throw new IllegalArgumentException("HP-loss child too large");return bytes.toByteArray();
    }catch(IOException e){throw new IllegalArgumentException("cannot encode HP loss",e);}
  }
  public static HpLossContinuation.Snapshot decode(byte[] bytes){
    if(bytes==null||bytes.length<4||bytes.length>MAX_BYTES)throw new IllegalArgumentException("invalid HP-loss payload size");
    try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){
      if(in.readInt()!=1)throw new IllegalArgumentException("unsupported HP-loss program");
      int actor=in.readInt(),amount=in.readInt(),draw=in.readInt();var step=HpLossContinuation.Step.valueOf(in.readUTF());int hp=in.readInt();DyingContinuation.Snapshot dying=null;
      if(bool(in)){int victim=in.readInt(),source=in.readInt();var ds=DyingContinuation.Step.valueOf(in.readUTF());var ring=new ArrayList<Integer>();
        int n=count(in,8);for(int i=0;i<n;i++)ring.add(in.readInt());var round=new RescueRound(ring,in.readInt(),bool(in));long revision=in.readLong();
        TrickContinuation.Pending pending=null;if(bool(in)){long id=in.readLong();var type=PromptType.valueOf(in.readUTF());int asker=in.readInt(),subject=in.readInt();long deadline=in.readLong();
          var choices=new ArrayList<Long>();n=count(in,160);for(int i=0;i<n;i++)choices.add(in.readLong());pending=new TrickContinuation.Pending(id,type,asker,subject,deadline,choices,bool(in),in.readUTF(),in.readLong());}
        CardUseCostContinuation.Snapshot payment=null;if(bool(in)){n=count(in,CardUseCostCodec.MAX_BYTES);byte[] data=in.readNBytes(n);if(data.length!=n)throw new EOFException();payment=CardUseCostCodec.decode(data);}
        dying=new DyingContinuation.Snapshot(victim,source,ds,round,pending,revision,payment,in.readLong(),in.readInt(),in.readInt(),bool(in));
      }
      if(in.available()!=0)throw new IllegalArgumentException("trailing HP-loss data");
      return new HpLossContinuation.Snapshot(actor,amount,draw,step,hp,dying);
    }catch(IOException|RuntimeException e){throw new IllegalArgumentException("invalid HP-loss program: "+e.getMessage(),e);}
  }
  private static int count(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<0||n>max)throw new IllegalArgumentException("HP-loss length outside bound");return n;}
  private static boolean bool(DataInputStream in)throws IOException{int n=in.readUnsignedByte();if(n>1)throw new IllegalArgumentException("invalid HP-loss boolean");return n==1;}
}
