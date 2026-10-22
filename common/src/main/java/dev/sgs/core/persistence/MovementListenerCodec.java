package dev.sgs.core.persistence;

import dev.sgs.core.card.*;
import dev.sgs.core.engine.skill.*;
import java.io.*;
import java.util.*;

/** Bounded child payload; the containing checkpoint provides its checksum and version. */
public final class MovementListenerCodec {
  public static final int MAX_BYTES=131072;
  private MovementListenerCodec(){}
  public static byte[] encode(MovementListenerProgram.Snapshot s){
    try(var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes)){
      out.writeInt(1);var e=s.event();out.writeLong(e.sequence());zone(out,e.from());zone(out,e.to());
      out.writeInt(e.cards().size());for(long id:e.cards())out.writeLong(id);
      out.writeUTF(e.reason().name());out.writeInt(e.actor());out.writeBoolean(e.publicCards());out.writeInt(e.turn());out.writeInt(e.currentSeat());
      byte[] plan=ListenerProgramCodec.encode(s.listeners());out.writeInt(plan.length);out.write(plan);
      out.writeBoolean(s.awaiting()!=null);if(s.awaiting()!=null){out.writeInt(s.awaiting().owner());out.writeUTF(s.awaiting().skill());}
      out.flush();if(bytes.size()>MAX_BYTES)throw new IllegalArgumentException("movement listener payload too large");return bytes.toByteArray();
    }catch(IOException error){throw new IllegalArgumentException("cannot encode movement listeners",error);}
  }
  public static MovementListenerProgram.Snapshot decode(byte[] bytes){
    if(bytes==null||bytes.length<8||bytes.length>MAX_BYTES)throw new IllegalArgumentException("invalid movement listener payload size");
    try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){
      if(in.readInt()!=1)throw new IllegalArgumentException("unsupported movement listener version");
      long sequence=in.readLong();var from=zone(in);var to=zone(in);int size=count(in,160);var cards=new ArrayList<Long>();for(int i=0;i<size;i++)cards.add(in.readLong());
      var event=new MovementListenerProgram.Event(sequence,from,to,cards,CardMove.Reason.valueOf(in.readUTF()),in.readInt(),bool(in),in.readInt(),in.readInt());
      int n=count(in,MAX_BYTES);byte[] plan=in.readNBytes(n);if(plan.length!=n)throw new EOFException();
      var listeners=ListenerProgramCodec.decode(plan);var awaiting=bool(in)?new AwaitableMovementEffect.Request(in.readInt(),in.readUTF()):null;
      if(in.available()!=0)throw new IllegalArgumentException("trailing movement listener bytes");return new MovementListenerProgram.Snapshot(event,listeners,awaiting);
    }catch(IOException|RuntimeException error){throw new IllegalArgumentException("invalid movement listeners",error);}
  }
  private static void zone(DataOutputStream out,CardZone zone)throws IOException{out.writeUTF(zone.kind().name());out.writeInt(zone.seat());}
  private static CardZone zone(DataInputStream in)throws IOException{return new CardZone(CardZone.Kind.valueOf(in.readUTF()),in.readInt());}
  private static int count(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<0||n>max)throw new IllegalArgumentException("invalid movement listener count");return n;}
  private static boolean bool(DataInputStream in)throws IOException{int n=in.readUnsignedByte();if(n>1)throw new IllegalArgumentException("invalid movement listener boolean");return n==1;}
}
