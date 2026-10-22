package dev.sgs.core.persistence;

import dev.sgs.core.room.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

/** Explicit bounded private-world format. SHA256 detects corruption, not unauthorized modification. */
public final class RoomCheckpointCodec {
  private static final int MAGIC=0x5347524d;
  public static final int MAX_BYTES=8192;
  private RoomCheckpointCodec(){}
  public static byte[] encode(RoomCheckpoint saved){
    Objects.requireNonNull(saved);saved.validate();
    try(var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes)){
      out.writeInt(MAGIC);out.writeInt(saved.version());out.writeLong(saved.id());uuid(out,saved.owner());text(out,saved.name());
      text(out,saved.mode().name());text(out,saved.state().name());out.writeBoolean(saved.teammateSharing());
      var s=saved.settings();text(out,s.password());out.writeInt(s.turnSeconds());out.writeInt(s.responseSeconds());out.writeInt(s.selectionSeconds());
      out.writeBoolean(s.allowChat());out.writeBoolean(s.allowSpectators());out.writeBoolean(s.doubleRenegade());out.writeBoolean(s.fixedSeats());
      out.writeInt(s.bannedHeroes().size());for(String id:new TreeSet<>(s.bannedHeroes()))text(out,id);
      out.writeInt(saved.seats().size());for(var seat:saved.seats().entrySet()){out.writeInt(seat.getKey());uuid(out,seat.getValue());}
      out.writeInt(saved.aiSlots().size());for(int slot:saved.aiSlots())out.writeInt(slot);
      out.writeInt(saved.ready().size());for(UUID player:new TreeSet<>(saved.ready()))uuid(out,player);
      out.flush();var data=bytes.toByteArray();if(data.length+32>MAX_BYTES)throw new IllegalArgumentException("room checkpoint too large");
      bytes.write(digest(data));return bytes.toByteArray();
    }catch(IOException error){throw new IllegalArgumentException("cannot encode room checkpoint",error);}
  }
  public static RoomCheckpoint decode(byte[] bytes){
    if(bytes==null || bytes.length<64 || bytes.length>MAX_BYTES)throw new IllegalArgumentException("invalid room checkpoint length");
    var data=Arrays.copyOf(bytes,bytes.length-32);
    if(!MessageDigest.isEqual(digest(data),Arrays.copyOfRange(bytes,bytes.length-32,bytes.length)))throw new IllegalArgumentException("room checkpoint checksum mismatch");
    try(var in=new DataInputStream(new ByteArrayInputStream(data))){
      if(in.readInt()!=MAGIC)throw new IllegalArgumentException("invalid room checkpoint magic");
      int version=in.readInt();if(version!=RoomCheckpoint.VERSION)throw new IllegalArgumentException("unsupported room checkpoint version");
      long id=in.readLong();UUID owner=uuid(in);String name=text(in,32);var mode=RoomMode.valueOf(text(in,32));var state=Room.State.valueOf(text(in,16));
      boolean sharing=bool(in);String password=text(in,32);int turn=in.readInt(),response=in.readInt(),selection=in.readInt();
      boolean chat=bool(in),spectators=bool(in),doubleRenegade=bool(in),fixed=bool(in);
      var bans=new TreeSet<String>();int size=count(in,6);for(int i=0;i<size;i++)if(!bans.add(text(in,128)))throw new IllegalArgumentException("duplicate banned hero");
      var settings=new RoomSettings(password,turn,response,selection,chat,spectators,doubleRenegade,fixed,bans);
      var seats=new TreeMap<Integer,UUID>();size=count(in,8);for(int i=0;i<size;i++)if(seats.put(in.readInt(),uuid(in))!=null)throw new IllegalArgumentException("duplicate occupied seat");
      var ai=new TreeSet<Integer>();size=count(in,8);for(int i=0;i<size;i++)if(!ai.add(in.readInt()))throw new IllegalArgumentException("duplicate AI seat");
      var ready=new HashSet<UUID>();size=count(in,8);for(int i=0;i<size;i++)if(!ready.add(uuid(in)))throw new IllegalArgumentException("duplicate ready member");
      if(in.available()!=0)throw new IllegalArgumentException("trailing room checkpoint bytes");
      return new RoomCheckpoint(version,id,owner,name,mode,settings,seats,ai,ready,sharing,state);
    }catch(IOException|RuntimeException error){throw new IllegalArgumentException("invalid room checkpoint",error);}
  }
  private static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
  private static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
  private static int count(DataInputStream in,int max)throws IOException{int size=in.readInt();if(size<0 || size>max)throw new IllegalArgumentException("invalid room collection size");return size;}
  private static boolean bool(DataInputStream in)throws IOException{int value=in.readUnsignedByte();if(value>1)throw new IllegalArgumentException("invalid room boolean");return value==1;}
  private static void text(DataOutputStream out,String value)throws IOException{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
  private static String text(DataInputStream in,int max)throws IOException{
    int size=count(in,max*4);byte[] bytes=in.readNBytes(size);if(bytes.length!=size)throw new EOFException();
    String value=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    RoomCheckpoint.text(value,max);return value;
  }
  private static byte[] digest(byte[] data){try{return MessageDigest.getInstance("SHA-256").digest(data);}catch(NoSuchAlgorithmException error){throw new IllegalStateException(error);}}
}
