package dev.sgs.core.persistence;

import dev.sgs.core.Phase;
import dev.sgs.core.engine.*;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.game.GameState.PublicSkillActivation;
import java.io.*;
import java.security.*;
import java.util.*;

/** Bounded explicit phase graph and checksum; nested payment/listener codecs remain unchanged. */
public final class TurnEndCheckpointCodec {
    private static final int MAGIC=0x53475445,MAX_BYTES=4_000_000;
    private TurnEndCheckpointCodec(){}
    public static boolean isPayload(byte[] bytes){return bytes!=null&&bytes.length>=8&&java.nio.ByteBuffer.wrap(bytes).getInt()==MAGIC;}
    public static byte[] encode(TurnEndCheckpoint s){
        s.validate();
        SgsGameEngine.restoreTurnEnd(s,s.state().contentVersion(),(state,p)->Optional.empty(),()->s.capturedAtMillis());
        try(var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes)){
            out.writeInt(MAGIC);out.writeInt(s.version());out.writeUTF(TurnEndCheckpoint.RULES_VERSION);
            blob(out,StateCheckpointCodec.encode(s.state()));out.writeLong(s.promptSequence());out.writeInt(s.responseTimeoutSeconds());out.writeLong(s.skillSequence());
            out.writeInt(s.activations().size());for(var a:s.activations()){out.writeLong(a.sequence());out.writeInt(a.seat());out.writeUTF(a.skillId());}
            out.writeInt(s.skillMemory().size());for(var entry:new TreeMap<>(s.skillMemory()).entrySet()){
                out.writeUTF(entry.getKey());out.writeInt(entry.getValue().size());
                for(var field:new TreeMap<>(entry.getValue()).entrySet()){out.writeUTF(field.getKey());out.writeLong(field.getValue());}
            }
            out.writeInt(s.bindings().size());for(var binding:s.bindings())out.writeUTF(binding);
            var c=s.continuation();out.writeInt(c.actor());out.writeInt(c.turn());out.writeUTF(c.step().name());out.writeBoolean(c.skipped());out.writeBoolean(c.automaticDiscard());
            out.writeInt(c.discarded().size());for(long id:c.discarded())out.writeLong(id);
            out.writeBoolean(c.payment()!=null);if(c.payment()!=null)blob(out,DiscardCostCodec.encode(c.payment()));
            out.writeBoolean(c.listeners()!=null);if(c.listeners()!=null){var l=c.listeners();var e=l.event();
                out.writeUTF(e.timing().name());out.writeUTF(e.phase().name());out.writeInt(e.actor());out.writeInt(e.turn());
                blob(out,ListenerProgramCodec.encode(l.listeners()));out.writeBoolean(l.skipped());out.writeBoolean(l.awaiting()!=null);
                if(l.awaiting()!=null){out.writeInt(l.awaiting().owner());out.writeUTF(l.awaiting().skill());}
            }
            var p=c.pending();out.writeBoolean(p!=null);if(p!=null){
                out.writeLong(p.id());out.writeInt(p.asker());out.writeInt(p.subject());out.writeLong(p.deadline());out.writeUTF(p.skill());out.writeLong(p.revision());
            }
            out.writeLong(c.revision());out.writeLong(s.capturedAtMillis());out.flush();
            byte[] data=bytes.toByteArray();if(data.length>MAX_BYTES-32)throw new IllegalArgumentException("phase checkpoint too large");
            bytes.write(digest(data));return bytes.toByteArray();
        }catch(IOException error){throw new IllegalArgumentException("cannot encode turn-end",error);}
    }
    public static TurnEndCheckpoint decode(byte[] bytes){
        if(bytes==null||bytes.length<64||bytes.length>MAX_BYTES)throw new IllegalArgumentException("invalid turn-end size");
        byte[] data=Arrays.copyOf(bytes,bytes.length-32);
        if(!MessageDigest.isEqual(digest(data),Arrays.copyOfRange(bytes,bytes.length-32,bytes.length)))throw new IllegalArgumentException("phase checksum differs");
        try(var in=new DataInputStream(new ByteArrayInputStream(data))){
            if(in.readInt()!=MAGIC||in.readInt()!=1||!in.readUTF().equals(TurnEndCheckpoint.RULES_VERSION))throw new IllegalArgumentException("phase format differs");
            var state=StateCheckpointCodec.decode(blob(in));long prompt=in.readLong();int timeout=in.readInt();long sequence=in.readLong();
            var activations=new ArrayList<PublicSkillActivation>();for(int i=0,n=count(in,32);i<n;i++)activations.add(new PublicSkillActivation(in.readLong(),in.readInt(),in.readUTF()));
            var memory=new TreeMap<String,Map<String,Long>>();for(int i=0,n=count(in,80);i<n;i++){
                String key=in.readUTF();var fields=new TreeMap<String,Long>();for(int j=0,size=count(in,8);j<size;j++){
                    if(fields.put(in.readUTF(),in.readLong())!=null)throw new IllegalArgumentException("duplicate phase memory field");
                }
                if(memory.put(key,fields)!=null)throw new IllegalArgumentException("duplicate phase memory");
            }
            var bindings=new ArrayList<String>();for(int i=0,n=count(in,256);i<n;i++)bindings.add(in.readUTF());
            int actor=in.readInt(),turn=in.readInt();var step=TurnEndContinuation.Step.valueOf(in.readUTF());boolean skipped=bool(in),automatic=bool(in);
            var costs=new ArrayList<Long>();for(int i=0,n=count(in,160);i<n;i++)costs.add(in.readLong());
            var payment=bool(in)?DiscardCostCodec.decode(blob(in)):null;
            PhaseListenerProgram.Snapshot listeners=null;if(bool(in)){
                var event=new PhaseListenerProgram.Event(Timing.valueOf(in.readUTF()),Phase.valueOf(in.readUTF()),in.readInt(),in.readInt());
                var plan=ListenerProgramCodec.decode(blob(in));boolean skip=bool(in);
                var request=bool(in)?new AwaitablePhaseEffect.Request(in.readInt(),in.readUTF()):null;
                listeners=new PhaseListenerProgram.Snapshot(event,plan,skip,request);
            }
            TrickContinuation.Pending pending=null;if(bool(in))pending=new TrickContinuation.Pending(in.readLong(),PromptType.SKILL_INVOKE,in.readInt(),in.readInt(),in.readLong(),List.of(),false,in.readUTF(),in.readLong());
            var c=new TurnEndContinuation.Snapshot(actor,turn,step,skipped,automatic,costs,payment,listeners,pending,in.readLong());long captured=in.readLong();
            if(in.available()!=0)throw new IllegalArgumentException("trailing phase bytes");
            var s=new TurnEndCheckpoint(1,state,prompt,timeout,memory,bindings,sequence,activations,c,captured);s.validate();
            SgsGameEngine.restoreTurnEnd(s,state.contentVersion(),(st,p)->Optional.empty(),()->captured);return s;
        }catch(IOException|RuntimeException error){throw new IllegalArgumentException("invalid turn-end checkpoint",error);}
    }
    private static void blob(DataOutputStream out,byte[] b)throws IOException{out.writeInt(b.length);out.write(b);}
    private static byte[] blob(DataInputStream in)throws IOException{int n=count(in,MAX_BYTES);byte[] b=in.readNBytes(n);if(b.length!=n)throw new EOFException();return b;}
    private static int count(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<0||n>max)throw new IllegalArgumentException("phase length differs");return n;}
    private static boolean bool(DataInputStream in)throws IOException{int v=in.readUnsignedByte();if(v>1)throw new IllegalArgumentException("phase boolean differs");return v==1;}
    private static byte[] digest(byte[] data){try{return MessageDigest.getInstance("SHA-256").digest(data);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
