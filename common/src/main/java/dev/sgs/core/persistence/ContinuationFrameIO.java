package dev.sgs.core.persistence;
import dev.sgs.core.card.CardType;
import dev.sgs.core.engine.*;
import dev.sgs.core.engine.AttackContinuation.*;
import java.io.*;
import java.util.*;
/** Shared bounded frame fields for nested damage, independent of the attack root protocol. */
final class ContinuationFrameIO {
  static void intRule(DataOutputStream out,IntRuleContinuation.Snapshot s)throws IOException{
    out.writeUTF(s.rule().name());out.writeInt(s.cursor());out.writeInt(s.value());out.writeUTF(s.step().name());
    out.writeBoolean(s.effect()!=null);if(s.effect()!=null){var e=s.effect();out.writeInt(e.owner());out.writeUTF(e.skill());out.writeUTF(e.effect().name());out.writeInt(e.value());}
    out.writeBoolean(s.judgment()!=null);if(s.judgment()!=null)judgment(out,s.judgment());pending(out,s.pending());out.writeLong(s.revision());
  }
  static IntRuleContinuation.Snapshot intRule(DataInputStream in)throws IOException{
    var rule=dev.sgs.core.engine.skill.RuleId.valueOf(in.readUTF());int cursor=in.readInt(),value=in.readInt();var step=IntRuleContinuation.Step.valueOf(in.readUTF());
    var effect=bool(in)?new dev.sgs.core.engine.skill.AwaitableIntModifier.Request(in.readInt(),in.readUTF(),dev.sgs.core.engine.skill.AwaitableIntModifier.Effect.valueOf(in.readUTF()),in.readInt()):null;
    var judgment=bool(in)?judgment(in):null;var pending=pending(in);return new IntRuleContinuation.Snapshot(rule,cursor,value,step,effect,judgment,pending,in.readLong());
  }
  static void pending(DataOutputStream out,PendingSnapshot p)throws IOException{
    out.writeBoolean(p!=null);if(p==null)return;
    out.writeLong(p.id());out.writeUTF(p.type().name());out.writeInt(p.askerSeat());out.writeInt(p.subjectSeat());out.writeInt(p.allowedTypes().size());for(var t:p.allowedTypes())out.writeUTF(t.name());out.writeLong(p.deadlineMillis());out.writeLong(p.stateRevision());out.writeUTF(p.skillId());
  }
  static PendingSnapshot pending(DataInputStream in)throws IOException{
    if(!bool(in))return null;long id=in.readLong();var type=PromptType.valueOf(in.readUTF());int asker=in.readInt(),subject=in.readInt();var allowed=new ArrayList<CardType>();int n=count(in,8);for(int i=0;i<n;i++)allowed.add(CardType.valueOf(in.readUTF()));return new PendingSnapshot(id,type,asker,subject,allowed,in.readLong(),in.readLong(),in.readUTF());
  }
  static void damage(DataOutputStream out,AttackContinuation.Snapshot c)throws IOException{
    out.writeInt(c.version());out.writeInt(c.sourceSeat());out.writeInt(c.targetSeat());out.writeLong(c.cardId());out.writeUTF(c.effectiveType()==null?"":c.effectiveType().name());out.writeInt(c.baseDamage());out.writeUTF(c.step().name());out.writeLong(c.revision());out.writeInt(c.damageFrames().size());
    for(var f:c.damageFrames()){out.writeInt(f.targetSeat());out.writeInt(f.amount());out.writeUTF(f.nature().name());out.writeBoolean(f.chainDamage());out.writeBoolean(f.wasChained());out.writeUTF(f.step().name());seats(out,f.rescueRing());out.writeInt(f.rescueCursor());out.writeBoolean(f.playedThisRound());}
    pending(out,c.pending());seats(out,c.providers());out.writeInt(c.providerCursor());if(c.version()>=4)out.writeBoolean(c.cardless());
  }
  static AttackContinuation.Snapshot damage(DataInputStream in)throws IOException{return damage(in,false);}
  private static AttackContinuation.Snapshot damage(DataInputStream in,boolean attack)throws IOException{
    int version=in.readInt(),source=in.readInt(),target=in.readInt();long card=in.readLong();String typeName=in.readUTF();var type=typeName.isEmpty()?null:CardType.valueOf(typeName);int base=in.readInt();var step=AttackStep.valueOf(in.readUTF());long revision=in.readLong();var frames=new ArrayList<DamageSnapshot>();int n=count(in,8);
    if(type==null&&version!=5)throw new IllegalArgumentException("null damage cause requires cardless root format 5");
    for(int i=0;i<n;i++)frames.add(new DamageSnapshot(in.readInt(),in.readInt(),DamageNature.valueOf(in.readUTF()),bool(in),bool(in),DamageStep.valueOf(in.readUTF()),seats(in),in.readInt(),bool(in)));
    var pending=pending(in);var providers=seats(in);int cursor=in.readInt();if(version!=3&&version!=4&&version!=5&&!(attack&&(version==14||version==21)))throw new IllegalArgumentException("unsupported damage frame");return new AttackContinuation.Snapshot(version,source,target,card,type,base,step,frames,pending,revision,providers,cursor,version>=4&&bool(in));
  }
  static void attack(DataOutputStream out,AttackContinuation.Snapshot s)throws IOException{
    if(s.version()!=14&&s.version()!=21)throw new IllegalArgumentException("nested attack requires v14 or v21");damage(out,s);
    out.writeLong(s.weaponCost());attackLongs(out,s.parents());attackLongs(out,s.extraCosts());seats(out,s.parentSizes());seats(out,s.targets());out.writeInt(s.targetCursor());out.writeBoolean(s.dodgeProhibited());out.writeInt(s.dodgesRemaining());
    out.writeBoolean(s.ruleQuery()!=null);if(s.ruleQuery()!=null)intRule(out,s.ruleQuery());
    out.writeBoolean(s.responseProgram()!=null);if(s.responseProgram()!=null)response(out,s.responseProgram(),s.version()>=21?8:7);
    out.writeBoolean(s.damageProgram()!=null);if(s.damageProgram()!=null){byte[] b=DamageProgramCodec.encode(s.damageProgram());out.writeInt(b.length);out.write(b);}
    if(s.version()>=21){
      out.writeBoolean(s.useCost()!=null);if(s.useCost()!=null){out.writeInt(s.useCost().shaBefore());out.writeBoolean(s.useCost().wineBefore());byte[] b=CardUseCostCodec.encode(s.useCost().program());out.writeInt(b.length);out.write(b);}
      out.writeBoolean(s.discardCost()!=null);if(s.discardCost()!=null){byte[] b=DiscardCostCodec.encode(s.discardCost());out.writeInt(b.length);out.write(b);}
      out.writeBoolean(s.redirect()!=null);if(s.redirect()!=null){byte[] b=TargetRedirectCodec.encode(s.redirect());out.writeInt(b.length);out.write(b);}
    }
  }
  static AttackContinuation.Snapshot attack(DataInputStream in)throws IOException{
    var s=damage(in,true);if((s.version()!=14&&s.version()!=21)||s.cardless())throw new IllegalArgumentException("invalid nested attack version");
    long weapon=in.readLong();var parents=attackLongs(in,160);var extras=attackLongs(in,1);int size=count(in,160);var groups=new ArrayList<Integer>();for(int i=0;i<size;i++)groups.add(in.readInt());var targets=seats(in);int cursor=in.readInt();boolean prohibited=bool(in);int remaining=in.readInt();
    var rule=bool(in)?intRule(in):null;var response=bool(in)?response(in,s.version()>=21?8:7):null;DamageProgramContinuation.Snapshot program=null;
    if(bool(in)){int n=count(in,DamageProgramCodec.MAX_BYTES);byte[] b=in.readNBytes(n);if(b.length!=n)throw new EOFException();program=DamageProgramCodec.decode(b);}
    AttackContinuation.UseCostSnapshot use=null;DiscardCostContinuation.Snapshot discard=null;TargetRedirectContinuation.Snapshot redirect=null;
    if(s.version()>=21){
      if(bool(in)){int sha=in.readInt();boolean wine=bool(in);int n=count(in,CardUseCostCodec.MAX_BYTES);byte[] b=in.readNBytes(n);if(b.length!=n)throw new EOFException();use=new AttackContinuation.UseCostSnapshot(CardUseCostCodec.decode(b),sha,wine);}
      if(bool(in)){int n=count(in,DiscardCostCodec.MAX_BYTES);byte[] b=in.readNBytes(n);if(b.length!=n)throw new EOFException();discard=DiscardCostCodec.decode(b);}
      if(bool(in)){int n=count(in,TargetRedirectCodec.MAX_BYTES);byte[] b=in.readNBytes(n);if(b.length!=n)throw new EOFException();redirect=TargetRedirectCodec.decode(b);}
    }
    return new AttackContinuation.Snapshot(s.version(),s.sourceSeat(),s.targetSeat(),s.cardId(),s.effectiveType(),s.baseDamage(),s.step(),s.damageFrames(),s.pending(),s.revision(),s.providers(),s.providerCursor(),false,weapon,parents,extras,groups,targets,cursor,prohibited,remaining,rule,response,program,use,discard,redirect);
  }
  private static void attackLongs(DataOutputStream out,List<Long> values)throws IOException{out.writeInt(values.size());for(long v:values)out.writeLong(v);}
  private static List<Long> attackLongs(DataInputStream in,int max)throws IOException{int n=count(in,max);var values=new ArrayList<Long>();for(int i=0;i<n;i++)values.add(in.readLong());return values;}
  static void response(DataOutputStream out,TrickResponseContinuation.Snapshot r,int format)throws IOException{
    if(format<8&&(r.cost()!=null||r.ownCardPaid()))throw new IllegalArgumentException("response cost in legacy frame");
    out.writeInt(r.actor());out.writeInt(r.subject());out.writeUTF(r.type().name());out.writeUTF(r.step().name());seats(out,r.providers());out.writeInt(r.cursor());pending(out,r.own());pending(out,r.pending());out.writeLong(r.revision());out.writeBoolean(r.success());out.writeBoolean(r.supplied());if(format>=3)out.writeLong(r.judgmentCard());if(format>=4){out.writeBoolean(r.judgment()!=null);if(r.judgment()!=null)judgment(out,r.judgment());}
    if(format>=8){out.writeBoolean(r.cost()!=null);if(r.cost()!=null){byte[] b=CardUseCostCodec.encode(r.cost());out.writeInt(b.length);out.write(b);}out.writeBoolean(r.ownCardPaid());}
  }
  static TrickResponseContinuation.Snapshot response(DataInputStream in,int format)throws IOException{
    int actor=in.readInt(),subject=in.readInt();var type=PromptType.valueOf(in.readUTF());var step=TrickResponseContinuation.Step.valueOf(in.readUTF());var providers=seats(in);int cursor=in.readInt();var own=pending(in);var pending=pending(in);long revision=in.readLong();boolean success=bool(in),supplied=bool(in);
    long card=format>=3?in.readLong():0;var judgment=format>=4&&bool(in)?judgment(in):null;
    CardUseCostContinuation.Snapshot cost=null;boolean paid=false;if(format>=8){if(bool(in)){int n=count(in,CardUseCostCodec.MAX_BYTES);byte[] b=in.readNBytes(n);if(b.length!=n)throw new EOFException();cost=CardUseCostCodec.decode(b);}paid=bool(in);}
    return new TrickResponseContinuation.Snapshot(actor,subject,type,step,providers,cursor,own,pending,revision,success,supplied,card,judgment,cost,paid);
  }
  static void judgment(DataOutputStream out,JudgmentContinuation.Snapshot j)throws IOException{out.writeInt(j.owner());out.writeLong(j.cause());out.writeLong(j.revealed());out.writeLong(j.effective());seats(out,j.modifiers());out.writeInt(j.cursor());out.writeUTF(j.step().name());pending(out,j.pending());}
  static JudgmentContinuation.Snapshot judgment(DataInputStream in)throws IOException{return new JudgmentContinuation.Snapshot(in.readInt(),in.readLong(),in.readLong(),in.readLong(),seats(in),in.readInt(),JudgmentContinuation.Step.valueOf(in.readUTF()),pending(in));}
  static void ganglie(DataOutputStream out,GangLieContinuation.Snapshot g)throws IOException{out.writeInt(g.owner());out.writeInt(g.source());out.writeUTF(g.step().name());out.writeBoolean(g.accepted());out.writeInt(g.costs().size());for(long id:g.costs())out.writeLong(id);out.writeBoolean(g.judgment()!=null);if(g.judgment()!=null)judgment(out,g.judgment());pending(out,g.pending());out.writeLong(g.revision());}
  static GangLieContinuation.Snapshot ganglie(DataInputStream in)throws IOException{int owner=in.readInt(),source=in.readInt();var step=GangLieContinuation.Step.valueOf(in.readUTF());boolean accepted=bool(in);var costs=new ArrayList<Long>();int n=count(in,2);for(int i=0;i<n;i++)costs.add(in.readLong());var judgment=bool(in)?judgment(in):null;var pending=pending(in);return new GangLieContinuation.Snapshot(owner,source,step,accepted,costs,judgment,pending,in.readLong());}
  static void yiji(DataOutputStream out,YiJiContinuation.Snapshot y)throws IOException{out.writeInt(y.owner());out.writeInt(y.amount());out.writeInt(y.point());out.writeUTF(y.step().name());out.writeInt(y.retained().size());for(long id:y.retained())out.writeLong(id);}
  static YiJiContinuation.Snapshot yiji(DataInputStream in)throws IOException{int owner=in.readInt(),amount=in.readInt(),point=in.readInt();var step=YiJiContinuation.Step.valueOf(in.readUTF());var retained=new ArrayList<Long>();int n=count(in,2);for(int i=0;i<n;i++)retained.add(in.readLong());return new YiJiContinuation.Snapshot(owner,amount,point,step,retained);}
  static void seats(DataOutputStream out,List<Integer> values)throws IOException{out.writeInt(values.size());for(int v:values)out.writeInt(v);}
  static List<Integer> seats(DataInputStream in)throws IOException{int n=count(in,8);var values=new ArrayList<Integer>();for(int i=0;i<n;i++)values.add(in.readInt());return values;}
  static int count(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<0||n>max)throw new IllegalArgumentException("invalid frame length");return n;}
  static boolean bool(DataInputStream in)throws IOException{int n=in.readUnsignedByte();if(n>1)throw new IllegalArgumentException("invalid frame boolean");return n==1;}
}
