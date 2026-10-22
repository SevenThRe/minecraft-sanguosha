package dev.sgs.core.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** World-local journal. Commit a validated complete file atomically; never expose a partial write. */
public final class MatchCheckpointStore {
  private final Path directory;
  public MatchCheckpointStore(Path directory)throws IOException{
    this.directory=directory.toAbsolutePath().normalize();Files.createDirectories(this.directory);
  }
  private Path file(long id){if(id<0)throw new IllegalArgumentException("invalid room id");return directory.resolve("match-"+id+".sgs");}
  public synchronized void save(MatchCheckpoint saved)throws IOException{
    byte[] bytes=MatchCheckpointCodec.encode(saved);
    MatchCheckpointCodec.decode(bytes); // Validate before replacing a previously valid journal.
    Path destination=file(saved.roomId()),temp=Files.createTempFile(directory,"match-"+saved.roomId()+"-",".new");
    try{
      try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){
        var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
      }
      Files.move(temp,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }finally{Files.deleteIfExists(temp);}
  }
  public synchronized Optional<MatchCheckpoint> load(long id)throws IOException{
    var path=file(id);if(!Files.exists(path))return Optional.empty();
    if(Files.size(path)>4_100_000)throw new IOException("match checkpoint exceeds size bound");
    try{
      var saved=MatchCheckpointCodec.decode(Files.readAllBytes(path));
      if(saved.roomId()!=id)throw new IllegalArgumentException("journal filename does not match match id");
      return Optional.of(saved);
    }catch(IllegalArgumentException error){throw new IOException("invalid saved match "+id,error);}
  }
  public synchronized List<Long> ids()throws IOException{
    try(var paths=Files.list(directory)){
      return paths.map(p->p.getFileName().toString()).filter(n->n.matches("match-[0-9]{1,19}\\.sgs"))
          .map(n->n.substring(6,n.length()-4)).map(Long::parseLong).sorted().toList();
    }catch(NumberFormatException error){throw new IOException("invalid saved match filename",error);}
  }
  public synchronized void remove(long id)throws IOException{Files.deleteIfExists(file(id));}
}
