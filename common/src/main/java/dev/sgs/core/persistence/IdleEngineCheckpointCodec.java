package dev.sgs.core.persistence;

import dev.sgs.core.game.GameState.PublicSkillActivation;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

/** Bounded explicit data format, checksum and rules version; never ObjectInputStream. */
public final class IdleEngineCheckpointCodec {
  private IdleEngineCheckpointCodec(){}
  private static final int MAGIC=0x53474549,MAX_BYTES=2_000_000;
  public static byte[] encode(IdleEngineCheckpoint saved){
    saved.validate();
    // Reject semantically incomplete layouts before writing a file that cannot bootstrap.
    dev.sgs.core.engine.SgsGameEngine.restoreIdle(saved,saved.state().contentVersion(),(s,p)->Optional.empty(),()->0);
    try(var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes)){
      out.writeInt(MAGIC);out.writeInt(saved.version());text(out,IdleEngineCheckpoint.RULES_VERSION);
      var state=StateCheckpointCodec.encode(saved.state());out.writeInt(state.length);out.write(state);
      out.writeLong(saved.promptSequence());out.writeInt(saved.responseTimeoutSeconds());out.writeLong(saved.skillSequence());
      out.writeInt(saved.activations().size());
      for(var event:saved.activations()){out.writeLong(event.sequence());out.writeInt(event.seat());text(out,event.skillId());}
      out.writeInt(saved.skillMemory().size());
      for(var skill:new TreeMap<>(saved.skillMemory()).entrySet()){
        text(out,skill.getKey());out.writeInt(skill.getValue().size());
        for(var field:new TreeMap<>(skill.getValue()).entrySet()){text(out,field.getKey());out.writeLong(field.getValue());}
      }
      out.writeInt(saved.bindings().size());for(var binding:saved.bindings())text(out,binding);
      out.flush();var data=bytes.toByteArray();if(data.length+32>MAX_BYTES)throw new IllegalArgumentException("engine checkpoint too large");
      bytes.write(digest(data));return bytes.toByteArray();
    }catch(IOException e){throw new IllegalArgumentException("cannot encode engine checkpoint",e);}
  }
  public static IdleEngineCheckpoint decode(byte[] encoded){
    if(encoded==null || encoded.length<48 || encoded.length>MAX_BYTES)throw new IllegalArgumentException("invalid engine checkpoint length");
    byte[] data=Arrays.copyOf(encoded,encoded.length-32);
    if(!MessageDigest.isEqual(digest(data),Arrays.copyOfRange(encoded,encoded.length-32,encoded.length)))throw new IllegalArgumentException("engine checksum mismatch");
    try(var in=new DataInputStream(new ByteArrayInputStream(data))){
      if(in.readInt()!=MAGIC || in.readInt()!=IdleEngineCheckpoint.VERSION)throw new IllegalArgumentException("unsupported engine format");
      if(!text(in,128).equals(IdleEngineCheckpoint.RULES_VERSION))throw new IllegalArgumentException("rules version mismatch");
      int size=count(in,MAX_BYTES);byte[] stateBytes=in.readNBytes(size);if(stateBytes.length!=size)throw new EOFException();
      var state=StateCheckpointCodec.decode(stateBytes);long prompt=in.readLong();int timeout=in.readInt();long sequence=in.readLong();
      var events=new ArrayList<PublicSkillActivation>();size=count(in,32);
      for(int i=0;i<size;i++)events.add(new PublicSkillActivation(in.readLong(),in.readInt(),text(in,128)));
      var skills=new TreeMap<String,Map<String,Long>>();size=count(in,80);
      for(int i=0;i<size;i++){
        String key=text(in,128);int fields=count(in,key.equals("0:mythic")?4096:8);var values=new TreeMap<String,Long>();
        for(int j=0;j<fields;j++)if(values.put(text(in,64),in.readLong())!=null)throw new IllegalArgumentException("duplicate memory field");
        if(skills.put(key,values)!=null)throw new IllegalArgumentException("duplicate skill entry");
      }
      var bindings=new ArrayList<String>();size=count(in,256);for(int i=0;i<size;i++)bindings.add(text(in,256));
      if(in.available()!=0)throw new IllegalArgumentException("trailing engine bytes");
      var saved=new IdleEngineCheckpoint(1,state,prompt,timeout,skills,bindings,sequence,events);saved.validate();return saved;
    }catch(IOException|RuntimeException e){throw new IllegalArgumentException("invalid engine checkpoint: "+e.getMessage(),e);}
  }
  private static int count(DataInputStream in,int max)throws IOException{
    int n=in.readInt();if(n<0 || n>max)throw new IllegalArgumentException("invalid length");return n;
  }
  private static void text(DataOutputStream out,String value)throws IOException{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
  private static String text(DataInputStream in,int max)throws IOException{
    int size=count(in,max*4);byte[] bytes=in.readNBytes(size);if(bytes.length!=size)throw new EOFException();
    String value=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    if(value.length()>max)throw new IllegalArgumentException("text too long");return value;
  }
  private static byte[] digest(byte[] data){try{return MessageDigest.getInstance("SHA-256").digest(data);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
