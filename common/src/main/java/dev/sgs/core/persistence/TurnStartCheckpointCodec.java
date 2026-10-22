package dev.sgs.core.persistence;

import dev.sgs.core.Phase;
import dev.sgs.core.engine.*;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.game.GameState.PublicSkillActivation;
import java.io.*;
import java.security.*;
import java.util.*;

/** Bounded explicit phase graph and checksum; nested payment/listener codecs remain unchanged. */
public final class TurnStartCheckpointCodec {
    private static final int MAGIC=0x53475453,MAX_BYTES=4_000_000;
    private TurnStartCheckpointCodec(){}
    public static boolean isPayload(byte[] bytes){return bytes!=null&&bytes.length>=8&&java.nio.ByteBuffer.wrap(bytes).getInt()==MAGIC;}
    public static byte[] encode(TurnStartCheckpoint s){
        s.validate();
        SgsGameEngine.restoreTurnStart(s,s.state().contentVersion(),(state,p)->Optional.empty(),()->s.capturedAtMillis());
        try(var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes)){
            out.writeInt(MAGIC);out.writeInt(s.version());out.writeUTF(TurnStartCheckpoint.rulesVersion(s.version()));
            blob(out,StateCheckpointCodec.encode(s.state()));out.writeLong(s.promptSequence());out.writeInt(s.responseTimeoutSeconds());out.writeLong(s.skillSequence());
            out.writeInt(s.activations().size());for(var a:s.activations()){out.writeLong(a.sequence());out.writeInt(a.seat());out.writeUTF(a.skillId());}
            out.writeInt(s.skillMemory().size());for(var entry:new TreeMap<>(s.skillMemory()).entrySet()){
                out.writeUTF(entry.getKey());out.writeInt(entry.getValue().size());
                for(var field:new TreeMap<>(entry.getValue()).entrySet()){out.writeUTF(field.getKey());out.writeLong(field.getValue());}
            }
            out.writeInt(s.bindings().size());for(var binding:s.bindings())out.writeUTF(binding);
            var c=s.continuation();out.writeInt(c.actor());out.writeInt(c.turn());out.writeUTF(c.step().name());
            blob(out,s.version()==1?RuleFoldCodec.encode(c.fold()):PreparationProgramCodec.encode(c.prepare()));DiscardCostCodec.writeQueue(out,c.movements());
            var p=c.pending();out.writeBoolean(p!=null);if(p!=null){out.writeLong(p.id());out.writeUTF(p.type().name());out.writeInt(p.asker());out.writeInt(p.subject());out.writeLong(p.deadline());out.writeBoolean(p.hidden());if(s.version()>=2){out.writeInt(p.choices().size());for(long id:p.choices())out.writeLong(id);}out.writeUTF(p.skill());out.writeLong(p.revision());}
            out.writeInt(c.drawn());
            out.writeLong(c.revision());out.writeLong(s.capturedAtMillis());out.flush();
            byte[] data=bytes.toByteArray();if(data.length>MAX_BYTES-32)throw new IllegalArgumentException("phase checkpoint too large");
            bytes.write(digest(data));return bytes.toByteArray();
        }catch(IOException error){throw new IllegalArgumentException("cannot encode turn-start",error);}
    }
    public static TurnStartCheckpoint decode(byte[] bytes){
        if(bytes==null||bytes.length<64||bytes.length>MAX_BYTES)throw new IllegalArgumentException("invalid turn-start size");
        byte[] data=Arrays.copyOf(bytes,bytes.length-32);
        if(!MessageDigest.isEqual(digest(data),Arrays.copyOfRange(bytes,bytes.length-32,bytes.length)))throw new IllegalArgumentException("phase checksum differs");
        try(var in=new DataInputStream(new ByteArrayInputStream(data))){
            if(in.readInt()!=MAGIC)throw new IllegalArgumentException("phase magic differs");int version=in.readInt();
            if((version!=1&&version!=2)||!in.readUTF().equals(TurnStartCheckpoint.rulesVersion(version)))throw new IllegalArgumentException("phase format differs");
            var state=StateCheckpointCodec.decode(blob(in));long prompt=in.readLong();int timeout=in.readInt();long sequence=in.readLong();
            var activations=new ArrayList<PublicSkillActivation>();for(int i=0,n=count(in,32);i<n;i++)activations.add(new PublicSkillActivation(in.readLong(),in.readInt(),in.readUTF()));
            var memory=new TreeMap<String,Map<String,Long>>();for(int i=0,n=count(in,80);i<n;i++){
                String key=in.readUTF();var fields=new TreeMap<String,Long>();for(int j=0,size=count(in,8);j<size;j++){
                    if(fields.put(in.readUTF(),in.readLong())!=null)throw new IllegalArgumentException("duplicate phase memory field");
                }
                if(memory.put(key,fields)!=null)throw new IllegalArgumentException("duplicate phase memory");
            }
            var bindings=new ArrayList<String>();for(int i=0,n=count(in,256);i<n;i++)bindings.add(in.readUTF());
            int actor=in.readInt(),turn=in.readInt();var step=TurnStartContinuation.Step.valueOf(in.readUTF());
            byte[] program=blob(in);var fold=version==1?RuleFoldCodec.decode(program):null;var prep=version==2?PreparationProgramCodec.decode(program):null;var queue=DiscardCostCodec.readQueue(in);
            TrickContinuation.Pending pending=null;if(bool(in)){
                long id=in.readLong();var type=PromptType.valueOf(in.readUTF());int asker=in.readInt(),subject=in.readInt();long deadline=in.readLong();boolean hidden=bool(in);var choices=new ArrayList<Long>();
                if(version>=2)for(int i=0,n=count(in,160);i<n;i++)choices.add(in.readLong());
                pending=new TrickContinuation.Pending(id,type,asker,subject,deadline,choices,hidden,in.readUTF(),in.readLong());
            }
            int drawn=in.readInt();var c=new TurnStartContinuation.Snapshot(actor,turn,step,fold,queue,pending,in.readLong(),drawn,prep);long captured=in.readLong();
            if(in.available()!=0)throw new IllegalArgumentException("trailing phase bytes");
            var s=new TurnStartCheckpoint(version,state,prompt,timeout,memory,bindings,sequence,activations,c,captured);s.validate();
            SgsGameEngine.restoreTurnStart(s,state.contentVersion(),(st,p)->Optional.empty(),()->captured);return s;
        }catch(IOException|RuntimeException error){throw new IllegalArgumentException("invalid turn-start checkpoint",error);}
    }
    private static void blob(DataOutputStream out,byte[] b)throws IOException{out.writeInt(b.length);out.write(b);}
    private static byte[] blob(DataInputStream in)throws IOException{int n=count(in,MAX_BYTES);byte[] b=in.readNBytes(n);if(b.length!=n)throw new EOFException();return b;}
    private static int count(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<0||n>max)throw new IllegalArgumentException("phase length differs");return n;}
    private static boolean bool(DataInputStream in)throws IOException{int v=in.readUnsignedByte();if(v>1)throw new IllegalArgumentException("phase boolean differs");return v==1;}
    private static byte[] digest(byte[] data){try{return MessageDigest.getInstance("SHA-256").digest(data);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
