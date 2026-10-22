package dev.sgs.core.persistence;

import java.io.*;
import java.security.*;
import java.util.*;

/** Bounded, checksummed envelope. Includes nonce floors and remaining turn time, not network objects. */
public final class MatchCheckpointCodec {
  private static final int MAGIC=0x5347534d,MAX=4_100_000;
  private MatchCheckpointCodec(){}
  public static byte[] encode(MatchCheckpoint saved){
    try(var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes)){
      out.writeInt(MAGIC);out.writeInt(saved.version());out.writeLong(saved.roomId());out.writeUTF(saved.contentId());
      out.writeBoolean(saved.attack());out.writeLong(saved.phaseRemainingMillis());out.writeBoolean(saved.teammateSharing());
      var settings=saved.roomSettings();out.writeUTF(settings.password());out.writeInt(settings.turnSeconds());
      out.writeInt(settings.responseSeconds());out.writeInt(settings.selectionSeconds());out.writeBoolean(settings.allowChat());
      out.writeBoolean(settings.allowSpectators());out.writeBoolean(settings.doubleRenegade());out.writeBoolean(settings.fixedSeats());
      out.writeInt(settings.bannedHeroes().size());for(var hero:new TreeSet<>(settings.bannedHeroes()))out.writeUTF(hero);
      out.writeLong(saved.botStepDelayMillis());
      var payload=saved.engine();out.writeInt(payload.length);out.write(payload);out.writeInt(saved.nonceFloors().size());
      for(var item:new TreeMap<>(saved.nonceFloors()).entrySet()){
        out.writeLong(item.getKey().getMostSignificantBits());out.writeLong(item.getKey().getLeastSignificantBits());out.writeInt(item.getValue());
      }
      if(saved.version()>=2){out.writeInt(saved.userAutoplay().size());for(var id:new TreeSet<>(saved.userAutoplay())){out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}}
      if(saved.version()>=3){
        out.writeInt(saved.seatControls().size());for(var item:new TreeMap<>(saved.seatControls()).entrySet()){
          out.writeLong(item.getKey().getMostSignificantBits());out.writeLong(item.getKey().getLeastSignificantBits());var c=item.getValue();
          out.writeUTF(c.source().name());out.writeLong(c.version());out.writeBoolean(c.awaitingReconnect());out.writeLong(c.offlineRemainingMillis());
        }
      }
      out.flush();var data=bytes.toByteArray();if(data.length+32>MAX)throw new IllegalArgumentException("match checkpoint too large");
      bytes.write(digest(data));return bytes.toByteArray();
    }catch(IOException error){throw new IllegalArgumentException("cannot encode match checkpoint",error);}
  }
  public static MatchCheckpoint decode(byte[] bytes){
    if(bytes==null || bytes.length<64 || bytes.length>MAX)throw new IllegalArgumentException("invalid match checkpoint size");
    var data=Arrays.copyOf(bytes,bytes.length-32);
    if(!MessageDigest.isEqual(digest(data),Arrays.copyOfRange(bytes,bytes.length-32,bytes.length)))throw new IllegalArgumentException("match checksum mismatch");
    try(var in=new DataInputStream(new ByteArrayInputStream(data))){
      if(in.readInt()!=MAGIC)throw new IllegalArgumentException("bad match checkpoint magic");
      int version=in.readInt();if(version<1 || version>MatchCheckpoint.VERSION)throw new IllegalArgumentException("unsupported match checkpoint version");long room=in.readLong();String content=in.readUTF();boolean attack=in.readBoolean();
      long remaining=in.readLong();boolean sharing=in.readBoolean();String password=in.readUTF();
      int turn=in.readInt(),response=in.readInt(),selection=in.readInt();
      boolean chat=in.readBoolean(),spectators=in.readBoolean(),renegade=in.readBoolean(),fixed=in.readBoolean();
      int bannedCount=in.readInt();if(bannedCount<0 || bannedCount>6)throw new IllegalArgumentException("bad ban count");
      var banned=new HashSet<String>();for(int i=0;i<bannedCount;i++)if(!banned.add(in.readUTF()))throw new IllegalArgumentException("duplicate ban");
      var settings=new dev.sgs.core.room.RoomSettings(password,turn,response,selection,chat,spectators,renegade,fixed,banned);
      long botDelay=in.readLong();int size=in.readInt();
      if(size<40 || size>4_000_000)throw new IllegalArgumentException("bad engine payload size");
      var engine=in.readNBytes(size);if(engine.length!=size)throw new EOFException();
      int count=in.readInt();if(count<2 || count>8)throw new IllegalArgumentException("bad nonce count");
      var nonces=new HashMap<UUID,Integer>();
      for(int i=0;i<count;i++)if(nonces.put(new UUID(in.readLong(),in.readLong()),in.readInt())!=null)throw new IllegalArgumentException("duplicate player");
      var autoplay=new HashSet<UUID>();
      if(version>=2){int amount=in.readInt();if(amount<0 || amount>8)throw new IllegalArgumentException("bad autoplay count");
        for(int i=0;i<amount;i++)if(!autoplay.add(new UUID(in.readLong(),in.readLong())))throw new IllegalArgumentException("duplicate autoplay");}
      var controls=new HashMap<UUID,dev.sgs.core.match.SeatControl.Snapshot>();
      if(version>=3){
        int amount=in.readInt();if(amount<1 || amount>8)throw new IllegalArgumentException("bad seat control count");
        for(int i=0;i<amount;i++){
          var id=new UUID(in.readLong(),in.readLong());var source=dev.sgs.core.match.SeatControl.Source.valueOf(in.readUTF());long controlVersion=in.readLong();
          int waiting=in.readUnsignedByte();if(waiting>1)throw new IllegalArgumentException("invalid control presence flag");
          var c=new dev.sgs.core.match.SeatControl.Snapshot(source,controlVersion,waiting==1,in.readLong());
          if(controls.put(id,c)!=null)throw new IllegalArgumentException("duplicate seat control");
        }
      }
      if(in.available()!=0)throw new IllegalArgumentException("trailing match checkpoint bytes");
      return new MatchCheckpoint(version,room,content,attack,engine,remaining,sharing,nonces,settings,botDelay,autoplay,controls);
    }catch(IOException error){throw new IllegalArgumentException("invalid match checkpoint",error);}
  }
  private static byte[] digest(byte[] data){
    try{return MessageDigest.getInstance("SHA-256").digest(data);}catch(NoSuchAlgorithmException error){throw new IllegalStateException(error);}
  }
}
