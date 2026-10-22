package dev.sgs.core.persistence;

import dev.sgs.core.engine.*;
import java.io.*;
import java.util.*;

/** Bounded target-selection/discard child, covered by the enclosing checkpoint checksum. */
public final class TargetRedirectCodec {
  public static final int MAX_BYTES=DiscardCostCodec.MAX_BYTES+8192;
  private TargetRedirectCodec(){}
  public static byte[] encode(TargetRedirectContinuation.Snapshot s){
    try(var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes)){
      out.writeInt(1);out.writeInt(s.source());out.writeInt(s.owner());out.writeUTF(s.skill());
      ContinuationFrameIO.seats(out,s.originalTargets());out.writeInt(s.costs().size());for(long id:s.costs())out.writeLong(id);
      ContinuationFrameIO.seats(out,s.targets());out.writeUTF(s.step().name());out.writeLong(s.chosenCost());out.writeInt(s.chosenTarget());
      out.writeBoolean(s.payment()!=null);if(s.payment()!=null){byte[] b=DiscardCostCodec.encode(s.payment());out.writeInt(b.length);out.write(b);}
      ContinuationFrameIO.pending(out,s.pending());out.writeLong(s.revision());out.writeBoolean(s.activated());out.flush();
      if(bytes.size()>MAX_BYTES)throw new IllegalArgumentException("target redirect too large");return bytes.toByteArray();
    }catch(IOException error){throw new IllegalArgumentException("cannot encode target redirect",error);}
  }
  public static TargetRedirectContinuation.Snapshot decode(byte[] bytes){
    if(bytes==null||bytes.length<16||bytes.length>MAX_BYTES)throw new IllegalArgumentException("invalid target redirect size");
    try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){
      if(in.readInt()!=1)throw new IllegalArgumentException("unsupported target redirect version");
      int source=in.readInt(),owner=in.readInt();String skill=in.readUTF();var group=ContinuationFrameIO.seats(in);
      var costs=new ArrayList<Long>();int count=ContinuationFrameIO.count(in,160);for(int i=0;i<count;i++)costs.add(in.readLong());
      var targets=ContinuationFrameIO.seats(in);var step=TargetRedirectContinuation.Step.valueOf(in.readUTF());long selected=in.readLong();int target=in.readInt();
      DiscardCostContinuation.Snapshot payment=null;if(ContinuationFrameIO.bool(in)){int n=ContinuationFrameIO.count(in,DiscardCostCodec.MAX_BYTES);byte[] b=in.readNBytes(n);if(b.length!=n)throw new EOFException();payment=DiscardCostCodec.decode(b);}
      var pending=ContinuationFrameIO.pending(in);long revision=in.readLong();boolean activated=ContinuationFrameIO.bool(in);
      if(in.available()!=0)throw new IllegalArgumentException("trailing target redirect bytes");
      return new TargetRedirectContinuation.Snapshot(source,owner,skill,group,costs,targets,step,selected,target,payment,pending,revision,activated);
    }catch(IOException|RuntimeException error){throw new IllegalArgumentException("invalid target redirect",error);}
  }
}
