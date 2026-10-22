package dev.sgs.core.persistence;

import dev.sgs.core.engine.*;
import dev.sgs.core.engine.AttackSupplyContinuation.*;
import dev.sgs.core.card.*;
import java.io.*;
import java.util.*;
import java.security.*;

/** Standalone bounded supply frame. Parent roots must explicitly opt in to this codec. */
public final class AttackSupplyCodec {
  private static final int MAGIC=0x53475350,VERSION=1,MAX_BYTES=8192;
  private AttackSupplyCodec(){}
  public static byte[] encode(Snapshot s){
    AttackSupplyContinuation.validate(s);
    try(var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes)){
      out.writeInt(MAGIC);out.writeInt(VERSION);out.writeInt(s.user());out.writeInt(s.target());out.writeUTF(s.ownType().name());out.writeBoolean(s.offerProviders());out.writeBoolean(s.allowOwn());out.writeUTF(s.step().name());out.writeInt(s.providers().size());for(int seat:s.providers())out.writeInt(seat);out.writeInt(s.cursor());ContinuationFrameIO.pending(out,s.pending());out.writeLong(s.revision());
      var r=s.receipt();out.writeBoolean(r!=null);if(r!=null){out.writeInt(r.supplier());out.writeUTF(r.type().name());out.writeUTF(r.conversion().name());out.writeLong(r.weapon());out.writeLong(r.sequence());out.writeInt(r.costs().size());for(var c:r.costs()){out.writeLong(c.id());out.writeUTF(c.from().kind().name());out.writeInt(c.from().seat());}}
      out.flush();byte[] data=bytes.toByteArray();if(data.length+32>MAX_BYTES)throw new IllegalArgumentException("supply frame too large");bytes.write(digest(data));return bytes.toByteArray();
    }catch(IOException e){throw new IllegalArgumentException("cannot encode supply",e);}
  }
  public static Snapshot decode(byte[] bytes){
    if(bytes==null||bytes.length<48||bytes.length>MAX_BYTES)throw new IllegalArgumentException("invalid supply size");
    byte[] data=Arrays.copyOf(bytes,bytes.length-32);if(!MessageDigest.isEqual(digest(data),Arrays.copyOfRange(bytes,bytes.length-32,bytes.length)))throw new IllegalArgumentException("supply checksum mismatch");
    try(var in=new DataInputStream(new ByteArrayInputStream(data))){
      if(in.readInt()!=MAGIC||in.readInt()!=VERSION)throw new IllegalArgumentException("unsupported supply frame");int user=in.readInt(),target=in.readInt();var type=PromptType.valueOf(in.readUTF());boolean offer=bool(in),own=bool(in);var step=Step.valueOf(in.readUTF());var providers=new ArrayList<Integer>();int size=count(in,7);for(int i=0;i<size;i++)providers.add(in.readInt());int cursor=in.readInt();var pending=ContinuationFrameIO.pending(in);long revision=in.readLong();Receipt receipt=null;
      if(bool(in)){int supplier=in.readInt();var effective=CardType.valueOf(in.readUTF());var conversion=Conversion.valueOf(in.readUTF());long weapon=in.readLong(),sequence=in.readLong();var costs=new ArrayList<Cost>();size=count(in,2);for(int i=0;i<size;i++)costs.add(new Cost(in.readLong(),new CardZone(CardZone.Kind.valueOf(in.readUTF()),in.readInt())));receipt=new Receipt(supplier,effective,costs,conversion,weapon,sequence);}
      if(in.available()!=0)throw new IllegalArgumentException("trailing supply bytes");var result=new Snapshot(user,target,type,offer,own,step,providers,cursor,pending,revision,receipt);AttackSupplyContinuation.validate(result);return result;
    }catch(IOException|RuntimeException e){throw new IllegalArgumentException("invalid supply frame: "+e.getMessage(),e);}
  }
  private static int count(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<0||n>max)throw new IllegalArgumentException("invalid supply length");return n;}
  private static boolean bool(DataInputStream in)throws IOException{int n=in.readUnsignedByte();if(n>1)throw new IllegalArgumentException("invalid supply boolean");return n==1;}
  private static byte[] digest(byte[] bytes){try{return MessageDigest.getInstance("SHA-256").digest(bytes);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
