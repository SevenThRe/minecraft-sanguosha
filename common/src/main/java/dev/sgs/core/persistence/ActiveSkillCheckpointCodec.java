package dev.sgs.core.persistence;

import dev.sgs.core.card.CardType;
import dev.sgs.core.engine.*;
import dev.sgs.core.engine.skill.ActiveSkillPlan;
import dev.sgs.core.game.GameState.PublicSkillActivation;
import java.io.*;
import java.security.*;
import java.util.*;

/** Bounded data frames with the existing response/discard/damage child codecs. */
public final class ActiveSkillCheckpointCodec {
  private static final int MAGIC=0x5347534b;
  public static final int MAX_BYTES=StateCheckpointCodec.MAX_BYTES+1_000_000;
  private ActiveSkillCheckpointCodec(){}
  public static boolean isPayload(byte[] data){return data!=null&&data.length>=8&&java.nio.ByteBuffer.wrap(data).getInt()==MAGIC;}
  public static byte[] encode(ActiveSkillCheckpoint s){
    s.validate();try(var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes)){
      out.writeInt(MAGIC);out.writeInt(s.version());out.writeUTF(rules(s.version()));
      blob(out,StateCheckpointCodec.encode(s.state()));out.writeLong(s.promptSequence());out.writeInt(s.responseTimeoutSeconds());
      out.writeLong(s.skillSequence());out.writeInt(s.activations().size());
      for(var a:s.activations()){out.writeLong(a.sequence());out.writeInt(a.seat());out.writeUTF(a.skillId());}
      out.writeInt(s.skillMemory().size());for(var entry:new TreeMap<>(s.skillMemory()).entrySet()){
        out.writeUTF(entry.getKey());out.writeInt(entry.getValue().size());
        for(var field:new TreeMap<>(entry.getValue()).entrySet()){out.writeUTF(field.getKey());out.writeLong(field.getValue());}
      }
      out.writeInt(s.bindings().size());for(var binding:s.bindings())out.writeUTF(binding);
      var c=s.continuation();var p=c.plan();out.writeInt(p.actor());out.writeInt(p.turn());out.writeUTF(p.skillId());
      out.writeInt(p.primaryTarget());out.writeUTF(p.selection()==null?"":p.selection().name());out.writeUTF(p.effect().name());
      out.writeInt(p.candidates().size());for(int seat:p.candidates())out.writeInt(seat);
      out.writeInt(p.costs().size());for(long id:p.costs())out.writeLong(id);
      if(s.version()>=2){out.writeInt(p.hpLoss());out.writeInt(p.drawCount());}
      out.writeUTF(c.step().name());out.writeInt(c.selectedSource());out.writeLong(c.revision());
      pending(out,c.selection());out.writeBoolean(c.payment()!=null);if(c.payment()!=null)blob(out,DiscardCostCodec.encode(c.payment()));
      var duel=c.duel();out.writeInt(duel.responder());out.writeInt(duel.other());out.writeInt(duel.required());out.writeInt(duel.paid());
      out.writeBoolean(c.response()!=null);if(c.response()!=null)ContinuationFrameIO.response(out,c.response(),8);
      out.writeBoolean(c.damage()!=null);if(c.damage()!=null)blob(out,DamageProgramCodec.encode(c.damage()));
      DiscardCostCodec.writeQueue(out,c.movements());out.writeBoolean(c.accepted());out.writeBoolean(c.published());
      out.writeBoolean(c.penaltyLogPending());out.writeBoolean(c.outcomePending());
      if(s.version()>=2){out.writeBoolean(c.hpLoss()!=null);if(c.hpLoss()!=null)blob(out,HpLossContinuationCodec.encode(c.hpLoss()));}
      if(s.version()>=3){var active=c.providedAttack();out.writeBoolean(active!=null);if(active!=null){
        out.writeInt(active.shaBefore());out.writeBoolean(active.wineBefore());var strike=active.strike();out.writeUTF(strike.step().name());
        blob(out,AttackSupplyCodec.encode(strike.supply()));out.writeBoolean(strike.attack()!=null);if(strike.attack()!=null)ContinuationFrameIO.attack(out,strike.attack());
      }}
      if(s.version()>=4){var g=c.revealedGift();out.writeBoolean(g!=null);if(g!=null){
        out.writeInt(g.actor());out.writeInt(g.target());out.writeUTF(g.skill());out.writeUTF(g.step().name());out.writeUTF(g.guess()==null?"":g.guess().name());out.writeLong(g.selected());
        out.writeInt(g.hand().size());for(long id:g.hand())out.writeLong(id);pending(out,g.pending());out.writeBoolean(g.damage()!=null);if(g.damage()!=null)blob(out,DamageProgramCodec.encode(g.damage()));
      }}
      out.writeLong(s.capturedAtMillis());out.flush();
      byte[] body=bytes.toByteArray();if(body.length+32>MAX_BYTES)throw new IllegalArgumentException("active checkpoint too large");
      out.write(digest(body));return bytes.toByteArray();
    }catch(IOException e){throw new IllegalArgumentException("cannot encode active checkpoint",e);}
  }
  public static ActiveSkillCheckpoint decode(byte[] data){
    if(data==null||data.length<44||data.length>MAX_BYTES)throw new IllegalArgumentException("invalid active checkpoint size");
    int size=data.length-32;byte[] body=Arrays.copyOf(data,size);
    if(!MessageDigest.isEqual(digest(body),Arrays.copyOfRange(data,size,data.length)))throw new IllegalArgumentException("active checksum differs");
    try(var in=new DataInputStream(new ByteArrayInputStream(body))){
      if(in.readInt()!=MAGIC)throw new IllegalArgumentException("unsupported active checkpoint");
      int version=in.readInt();if(version<1||version>ActiveSkillCheckpoint.VERSION
          ||!in.readUTF().equals(rules(version)))throw new IllegalArgumentException("unsupported active checkpoint");
      var state=StateCheckpointCodec.decode(blob(in,StateCheckpointCodec.MAX_BYTES));long sequence=in.readLong();int timeout=in.readInt();
      long skillSequence=in.readLong();var activations=new ArrayList<PublicSkillActivation>();
      int n=count(in,32);for(int i=0;i<n;i++)activations.add(new PublicSkillActivation(in.readLong(),in.readInt(),in.readUTF()));
      var memory=new TreeMap<String,Map<String,Long>>();n=count(in,SkillMemoryLayout.MAX_RECORDS);
      for(int i=0;i<n;i++){
        String key=in.readUTF();int fields=count(in,8);var values=new TreeMap<String,Long>();
        for(int j=0;j<fields;j++)if(values.put(in.readUTF(),in.readLong())!=null)throw new IllegalArgumentException("duplicate active field");
        if(memory.put(key,values)!=null)throw new IllegalArgumentException("duplicate active memory");
      }
      var bindings=new ArrayList<String>();n=count(in,256);for(int i=0;i<n;i++)bindings.add(in.readUTF());
      int actor=in.readInt(),turn=in.readInt();String skill=in.readUTF();int target=in.readInt();String selectionName=in.readUTF();var selection=selectionName.isEmpty()?null:PromptType.valueOf(selectionName);
      var effect=ActiveSkillPlan.Effect.valueOf(in.readUTF());var candidates=new ArrayList<Integer>();n=count(in,6);for(int i=0;i<n;i++)candidates.add(in.readInt());
      var costs=new ArrayList<Long>();n=count(in,160);for(int i=0;i<n;i++)costs.add(in.readLong());
      int hpLoss=version>=2?in.readInt():0,drawCount=version>=2?in.readInt():0;
      var plan=new ActiveSkillPlan(actor,turn,skill,target,selection,candidates,costs,effect,hpLoss,drawCount);
      var step=ActiveSkillContinuation.Step.valueOf(in.readUTF());int source=in.readInt();long revision=in.readLong();
      var pending=pending(in);var payment=bool(in)?DiscardCostCodec.decode(blob(in,DiscardCostCodec.MAX_BYTES)):null;
      var duel=new DuelExchange(in.readInt(),in.readInt(),in.readInt(),in.readInt());
      var response=bool(in)?ContinuationFrameIO.response(in,8):null;
      var damage=bool(in)?DamageProgramCodec.decode(blob(in,DamageProgramCodec.MAX_BYTES)):null;
      var movements=DiscardCostCodec.readQueue(in);boolean accepted=bool(in),published=bool(in),penalty=bool(in),outcome=bool(in);
      var hpProgram=version>=2&&bool(in)?HpLossContinuationCodec.decode(blob(in,HpLossContinuationCodec.MAX_BYTES)):null;
      ActiveAttackContinuation.Snapshot active=null;
      if(version>=3&&bool(in)){
        int sha=in.readInt();boolean wine=bool(in);var phase=StrikeContinuation.Step.valueOf(in.readUTF());
        var supply=AttackSupplyCodec.decode(blob(in,8192));var attack=bool(in)?ContinuationFrameIO.attack(in):null;
        active=new ActiveAttackContinuation.Snapshot(new StrikeContinuation.Snapshot(phase,supply,attack),sha,wine);
      }
      RevealedGiftContinuation.Snapshot gift=null;
      if(version>=4&&bool(in)){
        int giver=in.readInt(),recipient=in.readInt();String giftSkill=in.readUTF();var phase=RevealedGiftContinuation.Step.valueOf(in.readUTF());String guessName=in.readUTF();
        var guess=guessName.isEmpty()?null:dev.sgs.core.card.Suit.valueOf(guessName);long selected=in.readLong();var hand=new ArrayList<Long>();n=count(in,160);for(int i=0;i<n;i++)hand.add(in.readLong());
        var giftPending=pending(in);var giftDamage=bool(in)?DamageProgramCodec.decode(blob(in,DamageProgramCodec.MAX_BYTES)):null;
        gift=new RevealedGiftContinuation.Snapshot(giver,recipient,giftSkill,phase,guess,selected,hand,giftPending,giftDamage);
      }
      long captured=in.readLong();
      if(in.available()!=0)throw new IllegalArgumentException("trailing active data");
      var c=new ActiveSkillContinuation.Snapshot(plan,step,source,revision,pending,payment,duel,response,damage,movements,accepted,published,penalty,outcome,hpProgram,active,gift);
      var result=new ActiveSkillCheckpoint(version,state,sequence,timeout,memory,bindings,skillSequence,activations,c,captured);result.validate();return result;
    }catch(IOException|RuntimeException e){throw new IllegalArgumentException("invalid active checkpoint: "+e.getMessage(),e);}
  }
  private static void pending(DataOutputStream out,TrickContinuation.Pending p)throws IOException{
    out.writeBoolean(p!=null);if(p==null)return;out.writeLong(p.id());out.writeUTF(p.type().name());out.writeInt(p.asker());out.writeInt(p.subject());
    out.writeLong(p.deadline());out.writeInt(p.choices().size());for(long id:p.choices())out.writeLong(id);out.writeBoolean(p.hidden());out.writeUTF(p.skill());out.writeLong(p.revision());
  }
  private static TrickContinuation.Pending pending(DataInputStream in)throws IOException{
    if(!bool(in))return null;long id=in.readLong();var type=PromptType.valueOf(in.readUTF());int asker=in.readInt(),subject=in.readInt();long deadline=in.readLong();
    var choices=new ArrayList<Long>();int n=count(in,160);for(int i=0;i<n;i++)choices.add(in.readLong());
    return new TrickContinuation.Pending(id,type,asker,subject,deadline,choices,bool(in),in.readUTF(),in.readLong());
  }
  private static void blob(DataOutputStream out,byte[] data)throws IOException{out.writeInt(data.length);out.write(data);}
  private static String rules(int version){return switch(version){case 1->ActiveSkillCheckpoint.LEGACY_RULES_VERSION;case 2->ActiveSkillCheckpoint.HP_LOSS_RULES_VERSION;case 3->ActiveSkillCheckpoint.SUPPLIED_RULES_VERSION;case 4->ActiveSkillCheckpoint.RULES_VERSION;default->throw new IllegalArgumentException("unsupported active version");};}
  private static byte[] blob(DataInputStream in,int max)throws IOException{int n=count(in,max);byte[] b=in.readNBytes(n);if(b.length!=n)throw new EOFException();return b;}
  private static int count(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<0||n>max)throw new IllegalArgumentException("active length outside bound");return n;}
  private static boolean bool(DataInputStream in)throws IOException{int b=in.readUnsignedByte();if(b>1)throw new IllegalArgumentException("invalid active boolean");return b==1;}
  private static byte[] digest(byte[] b){try{return MessageDigest.getInstance("SHA-256").digest(b);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
