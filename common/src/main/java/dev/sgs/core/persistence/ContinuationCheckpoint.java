package dev.sgs.core.persistence;

import dev.sgs.core.GameStatus;
import dev.sgs.core.card.CardType;
import dev.sgs.core.engine.AttackContinuation;
import dev.sgs.core.engine.PromptType;
import dev.sgs.core.game.GameState;
import java.util.*;

/** Authoritative attack program and complete rule state, independent of idle-boundary saves. */
public record ContinuationCheckpoint(int version,StateCheckpoint state,long promptSequence,
    int responseTimeoutSeconds,Map<String,Map<String,Long>> skillMemory,List<String> bindings,
    long skillSequence,List<GameState.PublicSkillActivation> activations,
    AttackContinuation.Snapshot continuation,long capturedAtMillis) {
  public static final int VERSION=21;
  public static final String CHILDREN_V14_RULES_VERSION="historical-standard-2011-attack-child-programs-v14";
  public static final String USE_COST_V15_RULES_VERSION="historical-standard-2011-attack-use-cost-v15";
  public static final String RESPONSE_COST_V16_RULES_VERSION="historical-standard-2011-attack-response-cost-v16";
  public static final String DISCARD_COST_V17_RULES_VERSION="historical-standard-2011-attack-discard-cost-v17";
  public static final String WEAPON_DISCARD_V18_RULES_VERSION="historical-standard-2011-attack-weapon-discards-v18";
  public static final String QINGLONG_COST_V19_RULES_VERSION="historical-standard-2011-attack-qinglong-cost-v19";
  public static final String RESCUE_COST_V20_RULES_VERSION="historical-standard-2011-attack-rescue-cost-v20";
  public static final String RULES_VERSION="historical-standard-2011-attack-target-redirect-v21";
  public ContinuationCheckpoint {
    Objects.requireNonNull(state);Objects.requireNonNull(skillMemory);Objects.requireNonNull(continuation);
    var copy=new TreeMap<String,Map<String,Long>>();
    skillMemory.forEach((key,value)->copy.put(key,Map.copyOf(value)));
    skillMemory=Collections.unmodifiableMap(copy);bindings=List.copyOf(bindings);activations=List.copyOf(activations);
  }

  /** Structural validation only. Engine restore separately validates skill and program semantics. */
  public void validate(){
    state.validate(); // PROCESSING and living negative HP are intentional here.
    if((version<3 || version>VERSION) || state.turn().status()!=GameStatus.RUNNING || promptSequence<0 || promptSequence==Long.MAX_VALUE
        || responseTimeoutSeconds<1 || responseTimeoutSeconds>3600 || skillSequence<0 || skillSequence==Long.MAX_VALUE
        || capturedAtMillis<0 || skillMemory.size()>SkillMemoryLayout.MAX_RECORDS || bindings.size()>256 || activations.size()>32)
      throw new IllegalArgumentException("invalid continuation checkpoint header");
    int seats=state.players().size();
    skillMemory.forEach((key,values)->{
      StateCheckpoint.text(key,128);
      SkillMemoryLayout.validate(key,values,seats,false);
    });
    var uniqueBindings=new HashSet<String>();
    for(var binding:bindings){StateCheckpoint.text(binding,256);if(!uniqueBindings.add(binding))throw new IllegalArgumentException("duplicate binding");}
    long previous=0;
    for(var activation:activations){
      if(activation==null || activation.sequence()<=previous || activation.sequence()>skillSequence)
        throw new IllegalArgumentException("invalid public skill sequence");
      seat(activation.seat(),seats);StateCheckpoint.text(activation.skillId(),128);previous=activation.sequence();
    }
    var frame=continuation;
    TargetRedirectFrames.validate(this);
    DiscardCostFrames.validate(this);
    AttackUseCostFrames.validate(this);
    AttackChildFrames.validate(this);
    if(version>=13 && frame.step()==AttackContinuation.AttackStep.TARGET_RULES && frame.ruleQuery()==null)throw new IllegalArgumentException("target rule child missing");
    if(frame.ruleQuery()!=null){
      if(version<13 || frame.step()!=AttackContinuation.AttackStep.TARGET_RULES || !frame.damageFrames().isEmpty())throw new IllegalArgumentException("rule child requires v13 target rules");
      dev.sgs.core.engine.IntRuleContinuation.validate(frame.ruleQuery(),frame.pending(),frame.targetSeat(),seats);
    }
    if(frame.version()!=version || frame.cardless() || frame.revision()<0 || frame.revision()==Long.MAX_VALUE || frame.cardId()<=0 || frame.cardId()==Long.MAX_VALUE
        || frame.baseDamage()<1 || frame.baseDamage()>1_000_000 || frame.step()==null || frame.damageFrames().size()>8
        || frame.effectiveType()==null || !Set.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA).contains(frame.effectiveType()))
      throw new IllegalArgumentException("invalid attack frame");
    if(version<11 && (frame.dodgeProhibited() || frame.step()==AttackContinuation.AttackStep.TARGET_RULES))throw new IllegalArgumentException("Tieji flags require v11");
    if(frame.dodgesRemaining()<0 || frame.dodgesRemaining()>2 || version<12 && frame.dodgesRemaining()!=1)throw new IllegalArgumentException("invalid dodge count version or range");
    if(version==3 && (Set.of(AttackContinuation.AttackStep.QILIN_INVOKE,AttackContinuation.AttackStep.QILIN_CHOOSE,AttackContinuation.AttackStep.DAMAGE_START).contains(frame.step())
        || frame.pending()!=null && frame.pending().type()==PromptType.CARD_ZONE_SELECT))throw new IllegalArgumentException("weapon steps require v4");
    if(version<5 && (Set.of(AttackContinuation.AttackStep.CIXIONG_INVOKE,AttackContinuation.AttackStep.CIXIONG_CHOOSE).contains(frame.step())
        || frame.pending()!=null && frame.pending().type()==PromptType.WEAPON_DISCARD))throw new IllegalArgumentException("cixiong steps require v5");
    if(version<6 && Set.of(AttackContinuation.AttackStep.HANBING_INVOKE,AttackContinuation.AttackStep.HANBING_FIRST,AttackContinuation.AttackStep.HANBING_SECOND).contains(frame.step()))throw new IllegalArgumentException("hanbing steps require v6");
    if(frame.weaponCost()<0 || (frame.step()==AttackContinuation.AttackStep.GUANSHI_SECOND)!=(frame.weaponCost()>0))throw new IllegalArgumentException("invalid tentative guanshi cost");
    if(version<7 && (frame.weaponCost()!=0 || Set.of(AttackContinuation.AttackStep.GUANSHI_INVOKE,AttackContinuation.AttackStep.GUANSHI_FIRST,AttackContinuation.AttackStep.GUANSHI_SECOND).contains(frame.step())))throw new IllegalArgumentException("guanshi steps require v7");
    if(frame.parents().size()>160 || new HashSet<>(frame.parents()).size()!=frame.parents().size() || frame.parents().contains(frame.cardId())
        || frame.parents().stream().anyMatch(id->id==null || id<=0 || state.zones().stream().flatMap(z->z.cards().stream()).noneMatch(c->c.id()==id)))throw new IllegalArgumentException("invalid qinglong parent costs");
    if(version<8 && (!frame.parents().isEmpty() || frame.step()==AttackContinuation.AttackStep.QINGLONG || frame.pending()!=null && frame.pending().type()==PromptType.QINGLONG_SHA))throw new IllegalArgumentException("qinglong requires v8");
    boolean qinglongSupply=Set.of(AttackContinuation.AttackStep.QINGLONG_LORD,AttackContinuation.AttackStep.QINGLONG_PROVIDER,AttackContinuation.AttackStep.QINGLONG_PROVIDER_SHA).contains(frame.step()) || frame.step()==AttackContinuation.AttackStep.QINGLONG_COST && frame.useCost()!=null && frame.useCost().program().reason()==dev.sgs.core.card.CardMove.Reason.RESPONSE;
    if(version<9 && (qinglongSupply || !frame.extraCosts().isEmpty() || frame.parentSizes().stream().anyMatch(n->n!=1) || frame.pending()!=null && frame.pending().type()==PromptType.JIJIANG_SHA))throw new IllegalArgumentException("Qinglong supply requires v9");
    var costs=new HashSet<Long>();costs.add(frame.cardId());costs.addAll(frame.parents());
    if(frame.extraCosts().size()>1 || !frame.extraCosts().isEmpty() && frame.effectiveType()!=CardType.SHA || frame.extraCosts().stream().anyMatch(id->id==null || id<=0 || !costs.add(id) || state.zones().stream().flatMap(z->z.cards().stream()).noneMatch(c->c.id()==id)) || frame.parentSizes().size()>160 || frame.parentSizes().stream().anyMatch(n->n==null || n<1 || n>2) || frame.parentSizes().stream().mapToInt(Integer::intValue).sum()!=frame.parents().size())throw new IllegalArgumentException("invalid grouped Qinglong costs");
    if(frame.targets().isEmpty() || frame.targets().size()>3 || frame.targetCursor()<0 || frame.targetCursor()>=frame.targets().size()
        || (frame.targets().get(frame.targetCursor())!=frame.targetSeat()&&(frame.redirect()==null||frame.redirect().step()!=dev.sgs.core.engine.TargetRedirectContinuation.Step.DONE||!frame.redirect().activated()||frame.redirect().chosenTarget()!=frame.targetSeat())) || new HashSet<>(frame.targets()).size()!=frame.targets().size()
        || frame.targets().contains(frame.sourceSeat()) || version<10 && (frame.targets().size()!=1 || frame.targetCursor()!=0))throw new IllegalArgumentException("invalid attack target cursor");
    int previousTarget=0;for(int target:frame.targets()){seat(target,seats);int order=Math.floorMod(target-frame.sourceSeat(),seats);if(order<=previousTarget)throw new IllegalArgumentException("attack targets out of action order");previousTarget=order;}
    seat(frame.sourceSeat(),seats);seat(frame.targetSeat(),seats);
    if(state.zones().stream().flatMap(z->z.cards().stream()).noneMatch(c->c.id()==frame.cardId()))
      throw new IllegalArgumentException("unknown attack physical card");
    for(var damage:frame.damageFrames()){
      if(damage==null || damage.amount()<0 || damage.amount()>1_000_000 || damage.nature()==null || damage.step()==null
          || damage.rescueRing().size()>8 || damage.rescueCursor()<0 || damage.rescueCursor()>damage.rescueRing().size())
        throw new IllegalArgumentException("invalid damage program frame");
      seat(damage.targetSeat(),seats);var seen=new HashSet<Integer>();
      for(int rescuer:damage.rescueRing()){seat(rescuer,seats);if(!seen.add(rescuer))throw new IllegalArgumentException("duplicate rescue seat");}
    }
    if(frame.providers().size()>seats || frame.providerCursor()<0 || frame.providerCursor()>frame.providers().size())throw new IllegalArgumentException("invalid provider cursor");
    var uniqueProviders=new HashSet<Integer>();
    for(int provider:frame.providers()){seat(provider,seats);if(!uniqueProviders.add(provider) || provider==(qinglongSupply?frame.sourceSeat():frame.targetSeat()))throw new IllegalArgumentException("invalid provider ring");}
    var pending=frame.pending();
    if(pending!=null){
      if(pending.id()<=0 || pending.id()>promptSequence || pending.stateRevision()!=frame.revision() || pending.deadlineMillis()<0
          || pending.allowedTypes().size()>8 || pending.type()==null || !(Set.of(PromptType.LIULI_TARGET,PromptType.DODGE_SHA,PromptType.SKILL_INVOKE,PromptType.RESCUE_TAO,PromptType.HUJIA_SHAN,PromptType.CARD_ZONE_SELECT,PromptType.WEAPON_DISCARD,PromptType.WEAPON_COST_FIRST,PromptType.WEAPON_COST_SECOND,PromptType.QINGLONG_SHA,PromptType.JIJIANG_SHA).contains(pending.type()) || (frame.ruleQuery()!=null||frame.responseProgram()!=null||frame.damageProgram()!=null)&&pending.type()==PromptType.GUI_CAI_REPLACE||frame.damageProgram()!=null&&Set.of(PromptType.YIJI_GIVE,PromptType.FANKUI_TAKE,PromptType.GANGLIE_DISCARD).contains(pending.type())))
        throw new IllegalArgumentException("invalid pending attack request");
      seat(pending.askerSeat(),seats);seat(pending.subjectSeat(),seats);
      if(!pending.skillId().isEmpty())StateCheckpoint.text(pending.skillId(),128);
      if(new HashSet<>(pending.allowedTypes()).size()!=pending.allowedTypes().size() || pending.allowedTypes().stream().anyMatch(Objects::isNull))
        throw new IllegalArgumentException("invalid pending allowed card types");
    }
  }
  private static void seat(int seat,int seats){if(seat<0 || seat>=seats)throw new IllegalArgumentException("invalid continuation seat");}
}
