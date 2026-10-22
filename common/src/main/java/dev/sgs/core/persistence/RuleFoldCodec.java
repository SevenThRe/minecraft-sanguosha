package dev.sgs.core.persistence;

import dev.sgs.core.engine.PromptType;
import dev.sgs.core.engine.skill.*;
import java.io.*;
import java.util.*;

/** Explicit bounded child format; parent owns checksum and rules version. */
public final class RuleFoldCodec {
    private RuleFoldCodec(){}
    public static byte[] encode(RuleFoldProgram.Snapshot p){
        try(var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes)){
            out.writeInt(1);out.writeUTF(p.rule().name());out.writeInt(p.actor());out.writeInt(p.turn());
            out.writeInt(p.registry().size());for(var b:p.registry()){out.writeUTF(b.id());out.writeInt(b.owner());out.writeInt(b.priority());out.writeLong(b.sequence());}
            out.writeInt(p.cursor());out.writeInt(p.value());out.writeBoolean(p.progress()!=null);
            if(p.progress()!=null){var s=p.progress();out.writeInt(s.value());out.writeInt(s.selected().size());for(int seat:s.selected())out.writeInt(seat);out.writeBoolean(s.took());out.writeBoolean(s.complete());}
            out.writeBoolean(p.awaiting()!=null);if(p.awaiting()!=null){var a=p.awaiting();out.writeUTF(a.type().name());out.writeInt(a.targets().size());for(int seat:a.targets())out.writeInt(seat);out.writeBoolean(a.hidden());}
            out.flush();if(bytes.size()>65536)throw new IllegalArgumentException("rule fold too large");return bytes.toByteArray();
        }catch(IOException e){throw new IllegalArgumentException("cannot encode rule fold",e);}
    }
    public static RuleFoldProgram.Snapshot decode(byte[] bytes){
        if(bytes==null||bytes.length<24||bytes.length>65536)throw new IllegalArgumentException("invalid rule fold size");
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){
            if(in.readInt()!=1)throw new IllegalArgumentException("unsupported rule fold");
            var rule=RuleId.valueOf(in.readUTF());int actor=in.readInt(),turn=in.readInt();var bindings=new ArrayList<ListenerProgram.Binding>();
            for(int i=0,n=count(in,256);i<n;i++)bindings.add(new ListenerProgram.Binding(in.readUTF(),in.readInt(),in.readInt(),in.readLong()));
            int cursor=in.readInt(),value=in.readInt();AwaitableRuleEffect.Progress progress=null;
            if(bool(in)){int current=in.readInt();var selected=new ArrayList<Integer>();for(int i=0,n=count(in,8);i<n;i++)selected.add(in.readInt());progress=new AwaitableRuleEffect.Progress(current,selected,bool(in),bool(in));}
            AwaitableRuleEffect.Request request=null;if(bool(in)){var type=PromptType.valueOf(in.readUTF());var targets=new ArrayList<Integer>();for(int i=0,n=count(in,8);i<n;i++)targets.add(in.readInt());request=new AwaitableRuleEffect.Request(type,targets,bool(in));}
            if(in.available()!=0)throw new IllegalArgumentException("trailing rule fold data");return new RuleFoldProgram.Snapshot(rule,actor,turn,bindings,cursor,value,progress,request);
        }catch(IOException|RuntimeException e){throw new IllegalArgumentException("invalid rule fold",e);}
    }
    private static int count(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<0||n>max)throw new IllegalArgumentException("invalid rule count");return n;}
    private static boolean bool(DataInputStream in)throws IOException{int b=in.readUnsignedByte();if(b>1)throw new IllegalArgumentException("invalid rule boolean");return b==1;}
}
