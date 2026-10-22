package dev.sgs.core.persistence;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Room and live program commit together; corruption never falls back to a newly dealt game. */
public final class RoomMatchArchiveStore {
  private static final int MAGIC=0x53475357,MAX=4_200_000;
  private final Path directory;
  public RoomMatchArchiveStore(Path path)throws IOException{directory=path.toAbsolutePath().normalize();Files.createDirectories(directory);}
  private Path file(long id){if(id<0)throw new IllegalArgumentException("invalid room id");return directory.resolve("room-"+id+".sgs");}
  public synchronized void save(RoomMatchArchive archive)throws IOException{
    var bytes=encode(archive);decode(bytes);
    var temp=Files.createTempFile(directory,"room-"+archive.room().id()+"-",".new");
    try{
      try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){
        var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
      }
      Files.move(temp,file(archive.room().id()),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }finally{Files.deleteIfExists(temp);}
  }
  public synchronized Optional<RoomMatchArchive> load(long id)throws IOException{
    var path=file(id);if(!Files.exists(path))return Optional.empty();if(Files.size(path)>MAX)throw new IOException("archive too large");
    try{var archive=decode(Files.readAllBytes(path));if(archive.room().id()!=id)throw new IllegalArgumentException("archive filename mismatch");return Optional.of(archive);}
    catch(IllegalArgumentException error){throw new IOException("invalid room archive "+id,error);}
  }
  public synchronized List<Long> ids()throws IOException{
    try(var paths=Files.list(directory)){
      return paths.map(p->p.getFileName().toString()).filter(n->n.matches("room-[0-9]{1,19}\\.sgs"))
          .map(n->Long.parseLong(n.substring(5,n.length()-4))).sorted().toList();
    }catch(NumberFormatException error){throw new IOException("invalid archive name",error);}
  }
  public synchronized void remove(long id)throws IOException{Files.deleteIfExists(file(id));}
  /** Retain the previous bytes as evidence, but never offer that stale action for automatic restore. */
  public synchronized void invalidate(long id)throws IOException{
    var previous=file(id);if(Files.exists(previous))Files.move(previous,
        directory.resolve("room-"+id+"-previous-"+UUID.randomUUID()+".sgs"),StandardCopyOption.ATOMIC_MOVE);
  }
  public static byte[] encode(RoomMatchArchive archive){
    try(var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes)){
      out.writeInt(MAGIC);out.writeInt(1);blob(out,RoomCheckpointCodec.encode(archive.room()));
      blob(out,archive.match()==null?new byte[0]:MatchCheckpointCodec.encode(archive.match()));out.flush();
      var data=bytes.toByteArray();if(data.length+32>MAX)throw new IllegalArgumentException("archive too large");bytes.write(digest(data));return bytes.toByteArray();
    }catch(IOException error){throw new IllegalArgumentException("cannot encode archive",error);}
  }
  public static RoomMatchArchive decode(byte[] bytes){
    if(bytes==null || bytes.length<48 || bytes.length>MAX)throw new IllegalArgumentException("invalid archive size");
    var data=Arrays.copyOf(bytes,bytes.length-32);
    if(!MessageDigest.isEqual(digest(data),Arrays.copyOfRange(bytes,bytes.length-32,bytes.length)))throw new IllegalArgumentException("archive checksum mismatch");
    try(var in=new DataInputStream(new ByteArrayInputStream(data))){
      if(in.readInt()!=MAGIC || in.readInt()!=1)throw new IllegalArgumentException("unsupported archive version");
      var room=RoomCheckpointCodec.decode(blob(in));var match=blob(in);
      if(in.available()!=0)throw new IllegalArgumentException("trailing archive bytes");
      return new RoomMatchArchive(room,match.length==0?null:MatchCheckpointCodec.decode(match));
    }catch(IOException error){throw new IllegalArgumentException("invalid room archive",error);}
  }
  private static void blob(DataOutputStream out,byte[] bytes)throws IOException{out.writeInt(bytes.length);out.write(bytes);}
  private static byte[] blob(DataInputStream in)throws IOException{
    int size=in.readInt();if(size<0 || size>4_100_000)throw new IllegalArgumentException("invalid archive component size");
    var data=in.readNBytes(size);if(data.length!=size)throw new EOFException();return data;
  }
  private static byte[] digest(byte[] data){try{return MessageDigest.getInstance("SHA-256").digest(data);}catch(NoSuchAlgorithmException error){throw new IllegalStateException(error);}}
}
