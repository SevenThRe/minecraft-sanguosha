package dev.sgs.core.persistence;

import dev.sgs.core.card.CardType;
import dev.sgs.core.engine.AttackContinuation;
import dev.sgs.core.engine.TargetRedirectContinuation;
import dev.sgs.core.engine.AttackContinuation.*;
import dev.sgs.core.engine.DamageNature;
import dev.sgs.core.engine.PromptType;
import dev.sgs.core.game.GameState.PublicSkillActivation;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Bounded explicit binary format. SHA256 detects corruption; it does not authenticate a save. */
public final class ContinuationCheckpointCodec {
  private static final int MAGIC=0x53474341;
  public static final int MAX_BYTES=StateCheckpointCodec.MAX_BYTES+1_048_576;
  private ContinuationCheckpointCodec(){}

  public static byte[] encode(ContinuationCheckpoint saved){
    Objects.requireNonNull(saved);saved.validate();
    try(var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes)){
      out.writeInt(MAGIC);out.writeInt(saved.version());text(out,rules(saved.version()));
      byte[] state=StateCheckpointCodec.encode(saved.state());out.writeInt(state.length);out.write(state);
      out.writeLong(saved.promptSequence());out.writeInt(saved.responseTimeoutSeconds());out.writeLong(saved.skillSequence());
      out.writeInt(saved.activations().size());
      for(var event:saved.activations()){out.writeLong(event.sequence());out.writeInt(event.seat());text(out,event.skillId());}
      out.writeInt(saved.skillMemory().size());
      for(var skill:new TreeMap<>(saved.skillMemory()).entrySet()){
        text(out,skill.getKey());out.writeInt(skill.getValue().size());
        for(var field:new TreeMap<>(skill.getValue()).entrySet()){text(out,field.getKey());out.writeLong(field.getValue());}
      }
      out.writeInt(saved.bindings().size());for(var binding:saved.bindings())text(out,binding);
      var frame=saved.continuation();
      out.writeInt(frame.version());out.writeInt(frame.sourceSeat());out.writeInt(frame.targetSeat());out.writeLong(frame.cardId());
      text(out,frame.effectiveType().name());out.writeInt(frame.baseDamage());text(out,frame.step().name());out.writeLong(frame.revision());
      out.writeInt(frame.damageFrames().size());
      for(var damage:frame.damageFrames()){
        out.writeInt(damage.targetSeat());out.writeInt(damage.amount());text(out,damage.nature().name());out.writeBoolean(damage.chainDamage());
        out.writeBoolean(damage.wasChained());text(out,damage.step().name());out.writeInt(damage.rescueRing().size());
        for(int seat:damage.rescueRing())out.writeInt(seat);
        out.writeInt(damage.rescueCursor());out.writeBoolean(damage.playedThisRound());
      }
      var pending=frame.pending();out.writeBoolean(pending!=null);
      if(pending!=null){
        out.writeLong(pending.id());text(out,pending.type().name());out.writeInt(pending.askerSeat());out.writeInt(pending.subjectSeat());
        out.writeInt(pending.allowedTypes().size());for(var type:pending.allowedTypes())text(out,type.name());
        out.writeLong(pending.deadlineMillis());out.writeLong(pending.stateRevision());text(out,pending.skillId());
      }
      out.writeInt(frame.providers().size());for(int provider:frame.providers())out.writeInt(provider);
      out.writeInt(frame.providerCursor());if(saved.version()>=7)out.writeLong(frame.weaponCost());
      if(saved.version()>=8){out.writeInt(frame.parents().size());for(long id:frame.parents())out.writeLong(id);}
      if(saved.version()>=9){out.writeInt(frame.extraCosts().size());for(long id:frame.extraCosts())out.writeLong(id);out.writeInt(frame.parentSizes().size());for(int n:frame.parentSizes())out.writeInt(n);}
      if(saved.version()>=10){out.writeInt(frame.targets().size());for(int target:frame.targets())out.writeInt(target);out.writeInt(frame.targetCursor());}
      if(saved.version()>=11)out.writeBoolean(frame.dodgeProhibited());
      if(saved.version()>=12)out.writeInt(frame.dodgesRemaining());
      if(saved.version()>=13){out.writeBoolean(frame.ruleQuery()!=null);if(frame.ruleQuery()!=null)ContinuationFrameIO.intRule(out,frame.ruleQuery());}
      if(saved.version()>=14){out.writeBoolean(frame.responseProgram()!=null);if(frame.responseProgram()!=null)ContinuationFrameIO.response(out,frame.responseProgram(),saved.version()>=16?8:7);out.writeBoolean(frame.damageProgram()!=null);if(frame.damageProgram()!=null){byte[] program=DamageProgramCodec.encode(frame.damageProgram());out.writeInt(program.length);out.write(program);}}
      if(saved.version()>=15){var use=frame.useCost();out.writeBoolean(use!=null);if(use!=null){out.writeInt(use.shaBefore());out.writeBoolean(use.wineBefore());byte[] b=CardUseCostCodec.encode(use.program());out.writeInt(b.length);out.write(b);}}
      if(saved.version()>=17){out.writeBoolean(frame.discardCost()!=null);if(frame.discardCost()!=null){byte[] b=DiscardCostCodec.encode(frame.discardCost());out.writeInt(b.length);out.write(b);}}
      if(saved.version()>=21){out.writeBoolean(frame.redirect()!=null);if(frame.redirect()!=null){byte[] b=TargetRedirectCodec.encode(frame.redirect());out.writeInt(b.length);out.write(b);}}
      out.writeLong(saved.capturedAtMillis());out.flush();
      byte[] data=bytes.toByteArray();if(data.length+32>MAX_BYTES)throw new IllegalArgumentException("continuation checkpoint too large");
      bytes.write(digest(data));return bytes.toByteArray();
    }catch(IOException e){throw new IllegalArgumentException("cannot encode continuation checkpoint",e);}
  }

  public static ContinuationCheckpoint decode(byte[] encoded){
    if(encoded==null || encoded.length<48 || encoded.length>MAX_BYTES)throw new IllegalArgumentException("invalid continuation length");
    byte[] data=Arrays.copyOf(encoded,encoded.length-32);
    if(!MessageDigest.isEqual(digest(data),Arrays.copyOfRange(encoded,encoded.length-32,encoded.length)))
      throw new IllegalArgumentException("continuation checksum mismatch");
    try(var in=new DataInputStream(new ByteArrayInputStream(data))){
      if(in.readInt()!=MAGIC)throw new IllegalArgumentException("invalid continuation magic");
      int version=in.readInt();if(version<3 || version>ContinuationCheckpoint.VERSION)throw new IllegalArgumentException("unsupported continuation version");
      if(!text(in,128).equals(rules(version)))throw new IllegalArgumentException("continuation rules version mismatch");
      var state=StateCheckpointCodec.decode(exact(in,count(in,StateCheckpointCodec.MAX_BYTES)));
      long watermark=in.readLong();int timeout=in.readInt();long skillSequence=in.readLong();
      var events=new ArrayList<PublicSkillActivation>();int size=count(in,32);
      for(int i=0;i<size;i++)events.add(new PublicSkillActivation(in.readLong(),in.readInt(),text(in,128)));
      var memory=new TreeMap<String,Map<String,Long>>();size=count(in,SkillMemoryLayout.MAX_RECORDS);
      for(int i=0;i<size;i++){
        String key=text(in,128);var fields=new TreeMap<String,Long>();int count=count(in,8);
        for(int j=0;j<count;j++)if(fields.put(text(in,64),in.readLong())!=null)throw new IllegalArgumentException("duplicate skill memory field");
        if(memory.put(key,fields)!=null)throw new IllegalArgumentException("duplicate skill memory key");
      }
      var bindings=new ArrayList<String>();size=count(in,256);for(int i=0;i<size;i++)bindings.add(text(in,256));
      int frameVersion=in.readInt(),source=in.readInt(),target=in.readInt();long card=in.readLong();
      var effective=enumeration(in,CardType.class);int base=in.readInt();var step=enumeration(in,AttackStep.class);long revision=in.readLong();
      var damageFrames=new ArrayList<DamageSnapshot>();size=count(in,8);
      for(int i=0;i<size;i++){
        int seat=in.readInt(),amount=in.readInt();var nature=enumeration(in,DamageNature.class);boolean chain=bool(in),chained=bool(in);
        var damageStep=enumeration(in,DamageStep.class);var ring=new ArrayList<Integer>();int count=count(in,8);
        for(int j=0;j<count;j++)ring.add(in.readInt());
        damageFrames.add(new DamageSnapshot(seat,amount,nature,chain,chained,damageStep,ring,in.readInt(),bool(in)));
      }
      PendingSnapshot pending=null;
      if(bool(in)){
        long id=in.readLong();var type=enumeration(in,PromptType.class);int asker=in.readInt(),subject=in.readInt();
        var allowed=new ArrayList<CardType>();size=count(in,8);for(int i=0;i<size;i++)allowed.add(enumeration(in,CardType.class));
        pending=new PendingSnapshot(id,type,asker,subject,allowed,in.readLong(),in.readLong(),text(in,128));
      }
      var providers=new ArrayList<Integer>();size=count(in,8);for(int i=0;i<size;i++)providers.add(in.readInt());
      int providerCursor=in.readInt();long weaponCost=version>=7?in.readLong():0;
      var parents=new ArrayList<Long>();if(version>=8){size=count(in,160);for(int i=0;i<size;i++)parents.add(in.readLong());}
      var extras=new ArrayList<Long>();var groups=new ArrayList<Integer>();if(version>=9){size=count(in,1);for(int i=0;i<size;i++)extras.add(in.readLong());size=count(in,160);for(int i=0;i<size;i++)groups.add(in.readInt());}else groups.addAll(Collections.nCopies(parents.size(),1));
      var targets=new ArrayList<Integer>();int targetCursor=0;if(version>=10){size=count(in,3);for(int i=0;i<size;i++)targets.add(in.readInt());targetCursor=in.readInt();}else targets.add(target);
      boolean prohibited=version>=11 && bool(in);
      int dodges=version>=12?in.readInt():1;
      var ruleQuery=version>=13 && bool(in)?ContinuationFrameIO.intRule(in):null;
      var responseProgram=version>=14&&bool(in)?ContinuationFrameIO.response(in,version>=16?8:7):null;
      var damageProgram=version>=14&&bool(in)?DamageProgramCodec.decode(exact(in,count(in,DamageProgramCodec.MAX_BYTES))):null;
      AttackContinuation.UseCostSnapshot useCost=null;if(version>=15&&bool(in)){int sha=in.readInt();boolean wine=bool(in);useCost=new AttackContinuation.UseCostSnapshot(CardUseCostCodec.decode(exact(in,count(in,CardUseCostCodec.MAX_BYTES))),sha,wine);}
      var discardCost=version>=17&&bool(in)?DiscardCostCodec.decode(exact(in,count(in,DiscardCostCodec.MAX_BYTES))):null;
      TargetRedirectContinuation.Snapshot redirect=null;if(version>=21&&bool(in)){int n=count(in,TargetRedirectCodec.MAX_BYTES);redirect=TargetRedirectCodec.decode(exact(in,n));}
      long captured=in.readLong();if(in.available()!=0)throw new IllegalArgumentException("trailing continuation bytes");
      var continuation=new AttackContinuation.Snapshot(frameVersion,source,target,card,effective,base,step,damageFrames,pending,revision,providers,providerCursor,false,weaponCost,parents,extras,groups,targets,targetCursor,prohibited,dodges,ruleQuery,responseProgram,damageProgram,useCost,discardCost,redirect);
      var saved=new ContinuationCheckpoint(version,state,watermark,timeout,memory,bindings,skillSequence,events,continuation,captured);
      saved.validate();return saved;
    }catch(IOException|RuntimeException e){throw new IllegalArgumentException("invalid continuation checkpoint: "+e.getMessage(),e);}
  }

  private static String rules(int version){return version==3?"historical-standard-2011-attack-v3":version==4?"historical-standard-2011-attack-weapons-v4":version==5?"historical-standard-2011-attack-cixiong-v5":version==6?"historical-standard-2011-attack-hanbing-v6":version==7?"historical-standard-2011-attack-guanshi-v7":version==8?"historical-standard-2011-attack-qinglong-v8":version==9?"historical-standard-2011-attack-qinglong-jijiang-v9":version==10?"historical-standard-2011-attack-fangtian-v10":version==11?"historical-standard-2011-attack-tieji-v11":version==12?"historical-standard-2011-attack-wushuang-v12":version==13?"historical-standard-2011-attack-rule-program-v13":version==14?ContinuationCheckpoint.CHILDREN_V14_RULES_VERSION:version==15?ContinuationCheckpoint.USE_COST_V15_RULES_VERSION:version==16?ContinuationCheckpoint.RESPONSE_COST_V16_RULES_VERSION:version==17?ContinuationCheckpoint.DISCARD_COST_V17_RULES_VERSION:version==18?ContinuationCheckpoint.WEAPON_DISCARD_V18_RULES_VERSION:version==19?ContinuationCheckpoint.QINGLONG_COST_V19_RULES_VERSION:version==20?ContinuationCheckpoint.RESCUE_COST_V20_RULES_VERSION:ContinuationCheckpoint.RULES_VERSION;}
  private static boolean bool(DataInputStream in)throws IOException{int value=in.readUnsignedByte();if(value>1)throw new IllegalArgumentException("invalid boolean");return value==1;}
  private static int count(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<0 || n>max)throw new IllegalArgumentException("invalid collection length");return n;}
  private static byte[] exact(DataInputStream in,int length)throws IOException{byte[] bytes=in.readNBytes(length);if(bytes.length!=length)throw new EOFException("truncated continuation field");return bytes;}
  private static void text(DataOutputStream out,String value)throws IOException{
    var buffer=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
    byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);out.writeInt(bytes.length);out.write(bytes);
  }
  private static String text(DataInputStream in,int max)throws IOException{
    var value=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(exact(in,count(in,max*4)))).toString();
    if(value.length()>max)throw new IllegalArgumentException("text too long");return value;
  }
  private static <T extends Enum<T>> T enumeration(DataInputStream in,Class<T> type)throws IOException{return Enum.valueOf(type,text(in,64));}
  private static byte[] digest(byte[] data){try{return MessageDigest.getInstance("SHA-256").digest(data);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
