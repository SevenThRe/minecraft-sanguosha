package dev.sgs.core.persistence;

import java.io.*;
import java.util.Random;

/** Local Java 21 Random state only. No general-purpose Java object deserialization API. */
final class RandomCheckpoint {
  private RandomCheckpoint(){}
  static byte[] capture(Random random){
    if(random.getClass()!=Random.class)throw new IllegalArgumentException("unsupported RNG subclass");
    try(var bytes=new ByteArrayOutputStream();var out=new ObjectOutputStream(bytes)){
      out.writeObject(random);out.flush();return bytes.toByteArray();
    }catch(IOException e){throw new IllegalArgumentException("cannot capture RNG",e);}
  }
  static Random restore(byte[] bytes){
    if(bytes.length==0 || bytes.length>4096)throw new IllegalArgumentException("invalid RNG payload length");
    try(var input=new ObjectInputStream(new ByteArrayInputStream(bytes)){
      @Override protected Class<?> resolveClass(ObjectStreamClass descriptor)throws IOException,ClassNotFoundException{
        if(!descriptor.getName().equals("java.util.Random"))throw new InvalidClassException("RNG class not allowed");
        return Random.class;
      }
      @Override protected Class<?> resolveProxyClass(String[] interfaces)throws IOException{
        throw new InvalidClassException("proxy not allowed");
      }
    }){
      input.setObjectInputFilter(info->{
        if(info.depth()>2 || info.references()>4 || info.streamBytes()>4096 || info.arrayLength()>=0)
          return ObjectInputFilter.Status.REJECTED;
        return info.serialClass()==null?ObjectInputFilter.Status.UNDECIDED:
          info.serialClass()==Random.class?ObjectInputFilter.Status.ALLOWED:ObjectInputFilter.Status.REJECTED;
      });
      Object value=input.readObject();
      if(value==null || value.getClass()!=Random.class || input.read()!=-1)throw new InvalidObjectException("not one exact Random state");
      return (Random)value;
    }catch(IOException|ClassNotFoundException e){throw new IllegalArgumentException("invalid RNG checkpoint",e);}
  }
}
