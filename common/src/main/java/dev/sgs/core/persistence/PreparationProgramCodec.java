package dev.sgs.core.persistence;

import dev.sgs.core.engine.skill.*;
import java.io.*;
import java.util.*;

/** Private preparation child; the turn parent authenticates the complete payload with its checksum. */
final class PreparationProgramCodec {
    private PreparationProgramCodec(){}
    static byte[] encode(PreparationProgram.Snapshot p){
        try(var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes)){
            out.writeInt(1);out.writeInt(p.actor());out.writeInt(p.turn());out.writeUTF(p.step().name());var plan=ListenerProgramCodec.encode(p.listeners());out.writeInt(plan.length);out.write(plan);
            out.writeBoolean(p.order()!=null);if(p.order()!=null)for(var list:List.of(p.order().original(),p.order().pending(),p.order().top(),p.order().bottom())){out.writeInt(list.size());for(long id:list)out.writeLong(id);}
            out.flush();if(bytes.size()>262144)throw new IllegalArgumentException("preparation child too large");return bytes.toByteArray();
        }catch(IOException e){throw new IllegalArgumentException("cannot encode preparation",e);}
    }
    static PreparationProgram.Snapshot decode(byte[] bytes){
        if(bytes==null||bytes.length<16||bytes.length>262144)throw new IllegalArgumentException("invalid preparation size");
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){
            if(in.readInt()!=1)throw new IllegalArgumentException("preparation format differs");int actor=in.readInt(),turn=in.readInt();var step=PreparationProgram.Step.valueOf(in.readUTF());
            int size=count(in,262144);var plan=in.readNBytes(size);if(plan.length!=size)throw new EOFException();var listeners=ListenerProgramCodec.decode(plan);
            PreparationProgram.Order order=null;int flag=in.readUnsignedByte();if(flag>1)throw new IllegalArgumentException("invalid preparation flag");
            if(flag==1)order=new PreparationProgram.Order(ids(in),ids(in),ids(in),ids(in));
            if(in.available()!=0)throw new IllegalArgumentException("trailing preparation bytes");return new PreparationProgram.Snapshot(actor,turn,listeners,step,order);
        }catch(IOException|RuntimeException e){throw new IllegalArgumentException("invalid preparation child",e);}
    }
    private static List<Long> ids(DataInputStream in)throws IOException{var list=new ArrayList<Long>();for(int i=0,n=count(in,160);i<n;i++)list.add(in.readLong());return list;}
    private static int count(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<0||n>max)throw new IllegalArgumentException("invalid preparation count");return n;}
}
