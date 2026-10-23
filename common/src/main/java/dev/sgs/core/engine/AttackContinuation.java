package dev.sgs.core.engine;

import dev.sgs.core.card.*;
import java.util.*;

/** Explicit attack/rescue program data. No callbacks, player objects or blocked Java stack. */
public final class AttackContinuation {
  public enum AttackStep { WEAPON, ARMOR, HUJIA_INVOKE, HUJIA_PROVIDER, HUJIA_BAGUA, HUJIA_SHAN, DODGE, BAGUA, DAMAGE, CLEANUP, DONE, OWN_BAGUA, QILIN_INVOKE, QILIN_CHOOSE, DAMAGE_START, CIXIONG_INVOKE, CIXIONG_CHOOSE, HANBING_INVOKE, HANBING_FIRST, HANBING_SECOND, GUANSHI_INVOKE, GUANSHI_FIRST, GUANSHI_SECOND, QINGLONG, QINGLONG_LORD, QINGLONG_PROVIDER, QINGLONG_PROVIDER_SHA, TARGET_RULES, RESPONSE_CHILD, USE_COST, GUANSHI_PAYMENT, CIXIONG_DISCARD, HANBING_FIRST_DISCARD, HANBING_SECOND_DISCARD, QILIN_DISCARD, QINGLONG_COST, TARGET_REDIRECT }
  public record UseCostSnapshot(CardUseCostContinuation.Snapshot program,int shaBefore,boolean wineBefore){
    public UseCostSnapshot{Objects.requireNonNull(program);if(shaBefore<0)throw new IllegalArgumentException("invalid pre-use Sha count");}
  }
  public enum DamageStep { BEFORE, DYING_START, RESCUE, AFTER, AFTER_CHOICE, AFTER_APPLIED, AFTER_DEFERRED }
  public record PendingSnapshot(long id,PromptType type,int askerSeat,int subjectSeat,
      List<CardType> allowedTypes,long deadlineMillis,long stateRevision,String skillId){
    public PendingSnapshot{allowedTypes=List.copyOf(allowedTypes);Objects.requireNonNull(skillId);}
  }
  public record DamageSnapshot(int targetSeat,int amount,DamageNature nature,boolean chainDamage,
      boolean wasChained,DamageStep step,List<Integer> rescueRing,int rescueCursor,boolean playedThisRound){
    public DamageSnapshot{rescueRing=List.copyOf(rescueRing);}
  }
  /** Damage frames are top first; the attack is their parent and owns physical cleanup. */
  public record Snapshot(int version,int sourceSeat,int targetSeat,long cardId,CardType effectiveType,
      int baseDamage,AttackStep step,List<DamageSnapshot> damageFrames,PendingSnapshot pending,long revision,
      List<Integer> providers,int providerCursor,boolean cardless,long weaponCost,List<Long> parents,List<Long> extraCosts,List<Integer> parentSizes,List<Integer> targets,int targetCursor,boolean dodgeProhibited,int dodgesRemaining,IntRuleContinuation.Snapshot ruleQuery,TrickResponseContinuation.Snapshot responseProgram,DamageProgramContinuation.Snapshot damageProgram,UseCostSnapshot useCost,DiscardCostContinuation.Snapshot discardCost,TargetRedirectContinuation.Snapshot redirect){
    public Snapshot(int version,int sourceSeat,int targetSeat,long cardId,CardType effectiveType,
      int baseDamage,AttackStep step,List<DamageSnapshot> damageFrames,PendingSnapshot pending,long revision,
      List<Integer> providers,int providerCursor,boolean cardless,long weaponCost,List<Long> parents,List<Long> extraCosts,List<Integer> parentSizes,List<Integer> targets,int targetCursor,boolean dodgeProhibited,int dodgesRemaining,IntRuleContinuation.Snapshot ruleQuery,TrickResponseContinuation.Snapshot responseProgram,DamageProgramContinuation.Snapshot damageProgram,UseCostSnapshot useCost,DiscardCostContinuation.Snapshot discardCost){this(version,sourceSeat,targetSeat,cardId,effectiveType,baseDamage,step,damageFrames,pending,revision,providers,providerCursor,cardless,weaponCost,parents,extraCosts,parentSizes,targets,targetCursor,dodgeProhibited,dodgesRemaining,ruleQuery,responseProgram,damageProgram,useCost,discardCost,null);}
    public Snapshot(int version,int source,int target,long card,CardType type,int damage,AttackStep step,List<DamageSnapshot> damages,PendingSnapshot pending,long revision,List<Integer> providers,int cursor,boolean cardless,long weaponCost,List<Long> parents,List<Long> extras,List<Integer> sizes,List<Integer> targets,int targetCursor,boolean prohibited,int remaining,IntRuleContinuation.Snapshot rule,TrickResponseContinuation.Snapshot response,DamageProgramContinuation.Snapshot program,UseCostSnapshot cost){this(version,source,target,card,type,damage,step,damages,pending,revision,providers,cursor,cardless,weaponCost,parents,extras,sizes,targets,targetCursor,prohibited,remaining,rule,response,program,cost,null);}
    public Snapshot(int version,int source,int target,long card,CardType type,int damage,AttackStep step,List<DamageSnapshot> damages,PendingSnapshot pending,long revision,List<Integer> providers,int cursor,boolean cardless,long weaponCost,List<Long> parents,List<Long> extras,List<Integer> sizes,List<Integer> targets,int targetCursor,boolean prohibited,int remaining,IntRuleContinuation.Snapshot rule,TrickResponseContinuation.Snapshot response,DamageProgramContinuation.Snapshot program){this(version,source,target,card,type,damage,step,damages,pending,revision,providers,cursor,cardless,weaponCost,parents,extras,sizes,targets,targetCursor,prohibited,remaining,rule,response,program,null);}
    public Snapshot(int version,int source,int target,long card,CardType type,int damage,AttackStep step,List<DamageSnapshot> damages,PendingSnapshot pending,long revision,List<Integer> providers,int cursor,boolean cardless,long weaponCost,List<Long> parents,List<Long> extras,List<Integer> sizes,List<Integer> targets,int targetCursor,boolean prohibited,int remaining,IntRuleContinuation.Snapshot rule){this(version,source,target,card,type,damage,step,damages,pending,revision,providers,cursor,cardless,weaponCost,parents,extras,sizes,targets,targetCursor,prohibited,remaining,rule,null,null);}
    public Snapshot{damageFrames=List.copyOf(damageFrames);providers=List.copyOf(providers);parents=List.copyOf(parents);extraCosts=List.copyOf(extraCosts);parentSizes=List.copyOf(parentSizes);targets=List.copyOf(targets);}
    public Snapshot(int version,int source,int target,long card,CardType type,int damage,AttackStep step,List<DamageSnapshot> damages,PendingSnapshot pending,long revision,List<Integer> providers,int cursor,boolean cardless,long weaponCost,List<Long> parents,List<Long> extras,List<Integer> sizes,List<Integer> targets,int targetCursor,boolean prohibited,int remaining){this(version,source,target,card,type,damage,step,damages,pending,revision,providers,cursor,cardless,weaponCost,parents,extras,sizes,targets,targetCursor,prohibited,remaining,null);}
    public Snapshot(int version,int source,int target,long card,CardType type,int damage,AttackStep step,List<DamageSnapshot> damages,PendingSnapshot pending,long revision,List<Integer> providers,int cursor,boolean cardless,long weaponCost,List<Long> parents,List<Long> extras,List<Integer> sizes,List<Integer> targets,int targetCursor,boolean prohibited){this(version,source,target,card,type,damage,step,damages,pending,revision,providers,cursor,cardless,weaponCost,parents,extras,sizes,targets,targetCursor,prohibited,1);}
    public Snapshot(int version,int source,int target,long card,CardType type,int damage,AttackStep step,List<DamageSnapshot> damages,PendingSnapshot pending,long revision,List<Integer> providers,int cursor,boolean cardless,long weaponCost,List<Long> parents,List<Long> extras,List<Integer> sizes,List<Integer> targets,int targetCursor){this(version,source,target,card,type,damage,step,damages,pending,revision,providers,cursor,cardless,weaponCost,parents,extras,sizes,targets,targetCursor,false);}
    public Snapshot(int version,int source,int target,long card,CardType type,int damage,AttackStep step,List<DamageSnapshot> damages,PendingSnapshot pending,long revision,List<Integer> providers,int cursor,boolean cardless,long weaponCost,List<Long> parents,List<Long> extras,List<Integer> sizes){this(version,source,target,card,type,damage,step,damages,pending,revision,providers,cursor,cardless,weaponCost,parents,extras,sizes,List.of(target),0);}
    public Snapshot(int version,int source,int target,long card,CardType type,int damage,AttackStep step,List<DamageSnapshot> damages,PendingSnapshot pending,long revision,List<Integer> providers,int cursor,boolean cardless,long weaponCost,List<Long> parents){this(version,source,target,card,type,damage,step,damages,pending,revision,providers,cursor,cardless,weaponCost,parents,List.of(),Collections.nCopies(parents.size(),1));}
    public Snapshot(int version,int source,int target,long card,CardType type,int damage,AttackStep step,List<DamageSnapshot> damages,PendingSnapshot pending,long revision,List<Integer> providers,int cursor,boolean cardless,long weaponCost){this(version,source,target,card,type,damage,step,damages,pending,revision,providers,cursor,cardless,weaponCost,List.of());}
    public Snapshot(int version,int source,int target,long card,CardType type,int damage,AttackStep step,List<DamageSnapshot> damages,PendingSnapshot pending,long revision,List<Integer> providers,int cursor,boolean cardless){this(version,source,target,card,type,damage,step,damages,pending,revision,providers,cursor,cardless,0);}
    public Snapshot(int version,int source,int target,long card,CardType type,int damage,AttackStep step,
        List<DamageSnapshot> damages,PendingSnapshot pending,long revision){
      this(version,source,target,card,type,damage,step,damages,pending,revision,List.of(),0);
    }
    public Snapshot(int version,int source,int target,long card,CardType type,int damage,AttackStep step,List<DamageSnapshot> damages,PendingSnapshot pending,long revision,List<Integer> providers,int cursor){this(version,source,target,card,type,damage,step,damages,pending,revision,providers,cursor,false);}
  }
  private final int sourceSeat;
  private int targetSeat,targetCursor;
  private List<Integer> targets;
  private boolean ownsCleanup=true;
  private boolean orderedDamageListeners;
  void useOrderedDamageListeners(){if(ownsCleanup)throw new IllegalStateException("ordered listeners require a damage leaf");orderedDamageListeners=true;}
  private boolean cardless;
  private long cardId;
  private final List<Long> parents=new ArrayList<>(),extraCosts=new ArrayList<>();
  private final List<Integer> parentSizes=new ArrayList<>();
  private CardType effectiveType;
  private int baseDamage;
  private AttackStep step=AttackStep.TARGET_REDIRECT;
  private TargetRedirectContinuation redirect;
  private boolean dodgeProhibited;
  private ResponseSequence responses=new ResponseSequence(1);
  private TrickResponseContinuation response;
  private IntRuleContinuation ruleQuery;
  private DamageProgramContinuation damageProgram;
  private CardUseCostContinuation useCost;
  private DiscardCostContinuation discardCost;
  private int shaBefore;private boolean wineBefore;
  private int formatVersion=14;
  private final Deque<DamageFrame> damages=new ArrayDeque<>();
  private PendingSnapshot pending;
  private long revision;
  private List<Integer> providers=List.of();
  private int providerCursor;private long weaponCost;
  private static final class DamageFrame {
    int target,amount,cursor;DamageNature nature;boolean chain,wasChained,played;
    DamageStep step=DamageStep.BEFORE;List<Integer> ring=List.of();
    DamageFrame(int target,int amount,DamageNature nature,boolean chain){this.target=target;this.amount=amount;this.nature=nature;this.chain=chain;}
    DamageSnapshot snapshot(){return new DamageSnapshot(target,amount,nature,chain,wasChained,step,ring,cursor,played);}
    RescueRound rescue(){return new RescueRound(ring,cursor,played);}
    void rescue(RescueRound round){ring=round.ring();cursor=round.cursor();played=round.played();}
  }
  AttackContinuation(int source,int target,long card,CardType type,int damage){sourceSeat=source;targetSeat=target;cardId=card;effectiveType=type;baseDamage=damage;targets=List.of(target);}
  AttackContinuation(int source,List<Integer> targets,long card,CardType type,int damage){this(source,targets.getFirst(),card,type,damage);this.targets=List.copyOf(targets);}
  static AttackContinuation ordinaryUse(SgsGameEngine e,int source,List<Integer> targets,GameCard card){
    var result=committed(source,targets.getFirst(),card,1);result.targets=List.copyOf(targets);result.formatVersion=21;result.step=AttackStep.USE_COST;
    result.useCost=CardUseCostContinuation.accepted(e,source,card,CardMove.Reason.USE);result.shaBefore=e.state().shaPlayedThisTurn();result.wineBefore=e.state().jiuBoostPending();return result;
  }
  /** An already paid strike; actor and physical cost owners need not be the current-turn seat. */
  static AttackContinuation committed(int source,int target,GameCard card,int damage){
    var costs=card.sourceCards();
    if(costs.isEmpty()||costs.size()>2||costs.stream().map(CardInstance::instanceId).distinct().count()!=costs.size())
      throw new IllegalArgumentException("invalid committed attack costs");
    var result=new AttackContinuation(source,target,costs.getFirst().instanceId(),card.type(),damage);
    for(int i=1;i<costs.size();i++)result.extraCosts.add(costs.get(i).instanceId());
    return result;
  }
  AttackContinuation(Snapshot saved){this(saved,true);}
  private AttackContinuation(Snapshot saved,boolean root){
    ownsCleanup=root;
    sourceSeat=saved.sourceSeat();targetSeat=saved.targetSeat();targets=saved.targets();targetCursor=saved.targetCursor();cardId=saved.cardId();effectiveType=saved.effectiveType();baseDamage=saved.baseDamage();
    dodgeProhibited=saved.dodgeProhibited();responses=new ResponseSequence(saved.dodgesRemaining());step=saved.step();pending=saved.pending();revision=saved.revision();providers=saved.providers();providerCursor=saved.providerCursor();
    cardless=saved.cardless();weaponCost=saved.weaponCost();parents.addAll(saved.parents());extraCosts.addAll(saved.extraCosts());parentSizes.addAll(saved.parentSizes());
    if(saved.responseProgram()!=null)response=new TrickResponseContinuation(saved.responseProgram(),0,0,true,saved.version()>=16);else restoreResponseProjection();
    formatVersion=saved.version()>=12?saved.version():13;
    if(saved.version()<13 && saved.step()==AttackStep.TARGET_RULES)formatVersion=12;
    if(saved.ruleQuery()!=null)ruleQuery=new IntRuleContinuation(saved.ruleQuery(),0,0);
    if(saved.useCost()!=null){useCost=new CardUseCostContinuation(saved.useCost().program(),0,0);shaBefore=saved.useCost().shaBefore();wineBefore=saved.useCost().wineBefore();}
    if(saved.discardCost()!=null)discardCost=new DiscardCostContinuation(saved.discardCost(),0,0);
    if(saved.redirect()!=null)redirect=new TargetRedirectContinuation(saved.redirect(),0,0);
    for(var data:saved.damageFrames()){
      var frame=new DamageFrame(data.targetSeat(),data.amount(),data.nature(),data.chainDamage());
      frame.wasChained=data.wasChained();frame.step=data.step();frame.ring=data.rescueRing();frame.cursor=data.rescueCursor();frame.played=data.playedThisRound();damages.addLast(frame);
    }
    if(root&&saved.damageProgram()!=null){damageProgram=new DamageProgramContinuation(saved.damageProgram(),0,0);damages.clear();}
    else if(root&&step==AttackStep.DAMAGE&&!saved.damageFrames().isEmpty()){
      var leaf=new Snapshot(3,sourceSeat,targetSeat,cardId,effectiveType,baseDamage,AttackStep.DAMAGE,saved.damageFrames(),pending,revision,List.of(),0);
      damageProgram=new DamageProgramContinuation(new DamageProgramContinuation.Snapshot(DamageProgramContinuation.Step.DAMAGE,leaf,null,null,null,List.of(),null,false),0,0);damages.clear();
    }
  }
  static AttackContinuation damage(int source,int target,long card,CardType type,int amount){return damage(source,target,card,type,amount,DamageNature.NORMAL);}
  static AttackContinuation damage(int source,int target,long card,CardType type,int amount,DamageNature nature){
    var result=new AttackContinuation(source,target,card,type,amount);result.ownsCleanup=false;result.step=AttackStep.DAMAGE;
    result.damages.push(new DamageFrame(target,amount,nature,false));return result;
  }
  static AttackContinuation restoreDamage(Snapshot saved,long captured,long now){var result=new AttackContinuation(saved,false);result.restoreDeadline(captured,now);return result;}
  static AttackContinuation skillDamage(int source,int target,long rootCard,CardType rootType){var result=damage(source,target,rootCard,rootType,1);result.cardless=true;return result;}
  public Snapshot snapshot(){return new Snapshot(cardless?(cardId==0&&effectiveType==null?5:4):ownsCleanup?formatVersion:3,sourceSeat,targetSeat,cardId,effectiveType,baseDamage,step,damageProgram==null?damages.stream().map(DamageFrame::snapshot).toList():damageProgram.snapshot().damage().damageFrames(),pending,revision,providers,providerCursor,cardless,weaponCost,parents,extraCosts,parentSizes,targets,targetCursor,dodgeProhibited,responses.remaining(),ruleQuery==null?null:ruleQuery.snapshot(),ownsCleanup&&formatVersion>=14&&response!=null?response.snapshot():null,ownsCleanup&&formatVersion>=14&&damageProgram!=null?damageProgram.snapshot():null,useCost==null?null:new UseCostSnapshot(useCost.snapshot(),shaBefore,wineBefore),discardCost==null?null:discardCost.snapshot(),redirect==null?null:redirect.snapshot());}
  public Optional<PendingSnapshot> pending(){return Optional.ofNullable(pending);}
  public boolean done(){return step==AttackStep.DONE;}
  Optional<DamageSnapshot> deferredAfterDamage(){return !damages.isEmpty()&&damages.peek().step==DamageStep.AFTER_DEFERRED?Optional.of(damages.peek().snapshot()):Optional.empty();}
  void completeDeferredAfterDamage(){if(deferredAfterDamage().isEmpty())throw new IllegalStateException("no deferred damage hook");damages.peek().step=DamageStep.AFTER_APPLIED;}
  GameCard card(SgsGameEngine engine){
    if(cardless)return null;
    if(!ownsCleanup){var parent=engine.continuationParentCard(this);if(parent!=null)return parent;}
    if(!ownsCleanup&&cardId==0&&effectiveType==CardType.JUEDOU)return new VirtualCard(CardType.JUEDOU,List.of());
    var physical=engine.state().cards().zones().stream().flatMap(z->engine.state().cards().cards(z).stream()).filter(c->c.instanceId()==cardId).findFirst().orElseThrow(()->new IllegalStateException("attack source missing from canonical zones"));
    var costs=new ArrayList<CardInstance>();costs.add(physical);for(long id:extraCosts)costs.add(engine.state().cards().zones().stream().flatMap(z->engine.state().cards().cards(z).stream()).filter(c->c.instanceId()==id).findFirst().orElseThrow());
    return costs.size()==1 && physical.type()==effectiveType?physical:new VirtualCard(effectiveType,costs);
  }
  PromptRequest request(SgsGameEngine engine){
    if(step==AttackStep.TARGET_REDIRECT&&redirect!=null)return redirect.request(engine);
    if(ruleQuery!=null)return ruleQuery.request(engine);
    if(damageProgram!=null)return damageProgram.prompt(engine).orElseThrow();
    if(response!=null)return response.prompt(engine).orElseThrow();
    if(pending==null)throw new IllegalStateException("no pending decision");
    var seats=engine.state().players();var subject=seats.get(pending.subjectSeat());
    boolean hanbing=pending.type()==PromptType.CARD_ZONE_SELECT && pending.skillId().equals("hanbing");
    var choices=pending.type()==PromptType.CARD_ZONE_SELECT?(hanbing?subject.equipment().all():engine.continuationQilinHorses(seats.get(sourceSeat),seats.get(targetSeat))):pending.type()==PromptType.WEAPON_DISCARD?seats.get(targetSeat).handView():Set.of(PromptType.WEAPON_COST_FIRST,PromptType.WEAPON_COST_SECOND).contains(pending.type())?engine.continuationGuanshiCosts(seats.get(sourceSeat)).stream().filter(c->c.instanceId()!=weaponCost).toList():List.<CardInstance>of();
    return new PromptRequest(pending.id(),pending.type(),seats.get(pending.askerSeat()),subject,pending.allowedTypes(),choices,pending.deadlineMillis(),pending.skillId(),List.of(),hanbing&&subject.handCount()>0);
  }
  private void await(SgsGameEngine engine,PromptType type,int asker,int subject,List<CardType> allowed,String skill){
    revision++;pending=new PendingSnapshot(engine.nextPromptId(),type,asker,subject,allowed,engine.responseDeadlineMillis(),revision,skill);
  }
  void restoreDeadline(long capturedAt,long now){
    if(redirect!=null)redirect=new TargetRedirectContinuation(redirect.snapshot(),capturedAt,now);
    if(discardCost!=null)discardCost=new DiscardCostContinuation(discardCost.snapshot(),capturedAt,now);
    if(useCost!=null)useCost=new CardUseCostContinuation(useCost.snapshot(),capturedAt,now);
    if(damageProgram!=null)damageProgram=new DamageProgramContinuation(damageProgram.snapshot(),capturedAt,now);
    if(ruleQuery!=null)ruleQuery=new IntRuleContinuation(ruleQuery.snapshot(),capturedAt,now);
    if(response!=null)response=new TrickResponseContinuation(response.snapshot(),capturedAt,now,true,formatVersion>=16);
    if(pending!=null){
      pending=new PendingSnapshot(pending.id(),pending.type(),pending.askerSeat(),pending.subjectSeat(),pending.allowedTypes(),DecisionBoundary.restoredDeadline(pending.deadlineMillis(),capturedAt,now),pending.stateRevision(),pending.skillId());}
  }
  /** Runs only atomic rules until the next explicit decision. No responder is called here. */
  void advance(SgsGameEngine engine){
    if(pending!=null){if(engine.continuationNow()>pending.deadlineMillis())answer(engine,Optional.empty(),false);else return;}
    var state=engine.state();var source=state.players().get(sourceSeat);
    int budget=10000;
    while(pending==null && !done()){
      if(ownsCleanup&&engine.trickMovementsPending())return;
      if(--budget<0)throw new IllegalStateException("attack continuation did not yield");
      revision++;
      var target=state.players().get(targetSeat);
      switch(step){
        case GUANSHI_PAYMENT,CIXIONG_DISCARD,HANBING_FIRST_DISCARD,HANBING_SECOND_DISCARD,QILIN_DISCARD->{
          discardCost.advance(engine);
          if(discardCost.done()){
            var paid=discardCost.snapshot();discardCost=null;
            switch(step){
              case GUANSHI_PAYMENT->{engine.continuationGuanshiFinish(source);step=AttackStep.BAGUA;}
              case CIXIONG_DISCARD->{engine.continuationCixiongDiscarded(source,target);step=AttackStep.ARMOR;}
              case HANBING_FIRST_DISCARD->{engine.continuationHanbingDiscarded(source,target,discardedCard(engine,paid));step=AttackStep.HANBING_SECOND;}
              case HANBING_SECOND_DISCARD->{engine.continuationHanbingDiscarded(source,target,discardedCard(engine,paid));engine.continuationHanbingFinish(source,target);step=AttackStep.CLEANUP;}
              case QILIN_DISCARD->{engine.continuationQilinDiscarded(source,target,discardedCard(engine,paid));step=AttackStep.DAMAGE_START;}
              default->throw new IllegalStateException("unknown discard return");
            }
          }
          else{var p=discardCost.pending();if(p==null)throw new IllegalStateException("discard payment failed to yield");revision++;pending=new PendingSnapshot(p.id(),p.type(),p.asker(),p.subject(),List.of(),p.deadline(),revision,p.skill());}
        }
        case USE_COST,QINGLONG_COST->{
          useCost.advance(engine);
          if(useCost.done()){
            var used=useCost.card(engine);
            if(step==AttackStep.QINGLONG_COST){
              var paid=useCost.snapshot();engine.continuationCompleteQinglong(source,target,state.players().get(paid.actor()),used,paid.reason()==CardMove.Reason.RESPONSE);
              useCost=null;followQinglong(used);break;
            }
            engine.recordTargetedUse(source,used,targets.stream().map(i->state.players().get(i)).toList());
            state.registerShaPlayed();engine.continuationCardUseCommitted(source,used);baseDamage=state.consumeJiuBoost()?2:1;
            if(targets.size()>1){state.publishSkillActivation(sourceSeat,"fangtian");state.log("seat {} fangtian targets {}",sourceSeat,targets);}
            useCost=null;step=AttackStep.TARGET_REDIRECT;
          }else{var p=useCost.pending();if(p==null)throw new IllegalStateException("cost failed to yield");revision++;pending=new PendingSnapshot(p.id(),p.type(),p.asker(),p.subject(),List.of(),p.deadline(),revision,p.skill());}
        }
        case TARGET_REDIRECT->{
          if(redirect==null){redirect=engine.continuationTargetRedirect(sourceSeat,targetSeat,card(engine),targets);if(redirect!=null)formatVersion=21;}
          if(redirect==null){step=AttackStep.TARGET_RULES;break;}
          settleRedirect(engine);
        }
        case TARGET_RULES->{
          state.journal().add(state.journal().seat(sourceSeat)+"对"+state.journal().seat(targetSeat)+"结算"+dev.sgs.core.game.MatchJournal.type(effectiveType));
          dodgeProhibited=false;
          if(!target.alive() || state.status()!=dev.sgs.core.GameStatus.RUNNING)step=AttackStep.CLEANUP;
          else{formatVersion=Math.max(13,formatVersion);ruleQuery=new IntRuleContinuation(dev.sgs.core.engine.skill.RuleId.SHA_DODGE_COUNT,1);settleRule(engine);}
        }
        case WEAPON->{
          if(!target.alive() || state.status()!=dev.sgs.core.GameStatus.RUNNING){step=AttackStep.CLEANUP;break;}
          var weapon=source.equipment().weapon();
          if(effectiveType==CardType.SHA && !card(engine).isVirtual() && weapon!=null && weapon.type()==CardType.ZHUQUE)
            await(engine,PromptType.SKILL_INVOKE,sourceSeat,targetSeat,List.of(),"zhuque");
          else if(engine.continuationCixiongAvailable(source,target)){
            step=AttackStep.CIXIONG_INVOKE;await(engine,PromptType.SKILL_INVOKE,sourceSeat,targetSeat,List.of(),"cixiong");
          }else step=AttackStep.ARMOR;
        }
        case ARMOR->{
          if(engine.continuationArmorBlocks(source,target,card(engine)))step=AttackStep.CLEANUP;
          else if(dodgeProhibited)step=AttackStep.BAGUA;
          else startDodge(engine);
        }
        case QINGLONG_PROVIDER->{
          if(!engine.continuationQinglongAvailable(source,target)){step=AttackStep.CLEANUP;break;}
          if(providerCursor>=providers.size()){ownQinglong(engine);break;}
          int helper=providers.get(providerCursor);if(!state.players().get(helper).alive()){providerCursor++;break;}
          step=AttackStep.QINGLONG_PROVIDER_SHA;await(engine,PromptType.JIJIANG_SHA,helper,sourceSeat,List.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA),"jijiang");
        }
        case HUJIA_PROVIDER,HUJIA_INVOKE,HUJIA_BAGUA,HUJIA_SHAN,OWN_BAGUA,RESPONSE_CHILD->throw new IllegalStateException("response projection without a child program");
        case DODGE,QILIN_INVOKE,QILIN_CHOOSE,CIXIONG_INVOKE,CIXIONG_CHOOSE,HANBING_INVOKE,GUANSHI_INVOKE,GUANSHI_FIRST,GUANSHI_SECOND,QINGLONG,QINGLONG_LORD,QINGLONG_PROVIDER_SHA->throw new IllegalStateException("attack choice stage without pending decision");
        case BAGUA->{
          if(ownsCleanup && engine.continuationHanbingAvailable(source,target)){
            step=AttackStep.HANBING_INVOKE;await(engine,PromptType.SKILL_INVOKE,sourceSeat,targetSeat,List.of(),"hanbing");
          }else if(ownsCleanup && !engine.continuationQilinHorses(source,target).isEmpty()){
            step=AttackStep.QILIN_INVOKE;await(engine,PromptType.SKILL_INVOKE,sourceSeat,targetSeat,List.of(),"qilin_gong");
          }else step=AttackStep.DAMAGE_START;
        }
        case HANBING_FIRST,HANBING_SECOND->{
          if(!target.alive() || engine.continuationHanbingCards(target).isEmpty()){engine.continuationHanbingFinish(source,target);step=AttackStep.CLEANUP;}
          else await(engine,PromptType.CARD_ZONE_SELECT,sourceSeat,targetSeat,List.of(),"hanbing");
        }
        case DAMAGE_START->{
          {
            String element=effectiveType==CardType.HUOSHA?"火":effectiveType==CardType.LEISHA?"雷":"";
            state.log("seat {} was hit by {}sha from seat {} for {}",targetSeat,element,sourceSeat,baseDamage);
            step=AttackStep.DAMAGE;
            if(ownsCleanup){var leaf=damage(sourceSeat,targetSeat,cardId,effectiveType,baseDamage);leaf.damages.peek().nature=DamageNature.of(card(engine));damageProgram=new DamageProgramContinuation(leaf,formatVersion>=14,formatVersion>=20);}
            else damages.push(new DamageFrame(targetSeat,baseDamage,DamageNature.of(card(engine)),false));
          }
        }
        case DAMAGE->{if(damageProgram!=null)settleDamage(engine);else if(damages.isEmpty())step=ownsCleanup?AttackStep.CLEANUP:AttackStep.DONE;else{advanceDamage(engine,damages.peek());if(deferredAfterDamage().isPresent())return;}}
        case CLEANUP->{
          if(targetCursor+1<targets.size() && state.status()==dev.sgs.core.GameStatus.RUNNING){
            targetSeat=targets.get(++targetCursor);providers=List.of();providerCursor=0;weaponCost=0;dodgeProhibited=false;redirect=null;step=AttackStep.TARGET_REDIRECT;continue;
          }
          state.cards().finish(card(engine),sourceSeat);int offset=parents.size();for(int i=parentSizes.size()-1;i>=0;i--){int size=parentSizes.get(i);offset-=size;engine.continuationFinishQinglongParent(sourceSeat,parents.subList(offset,offset+size));}parents.clear();parentSizes.clear();step=AttackStep.DONE;}
        case DONE->{}
      }
    }
  }
  private void dodged(SgsGameEngine engine){
    responses=responses.committed();
    if(!responses.complete()){startDodge(engine);return;}
    if(engine.continuationGuanshiAvailable(engine.state().players().get(sourceSeat))){step=AttackStep.GUANSHI_INVOKE;await(engine,PromptType.SKILL_INVOKE,sourceSeat,targetSeat,List.of(),"guanshi");}
    else if(engine.continuationQinglongAvailable(engine.state().players().get(sourceSeat),engine.state().players().get(targetSeat))){providers=engine.trickLordProviders(engine.state().players().get(sourceSeat),true);providerCursor=0;if(providers.isEmpty())ownQinglong(engine);else{step=AttackStep.QINGLONG_LORD;await(engine,PromptType.SKILL_INVOKE,sourceSeat,sourceSeat,List.of(),"jijiang");}}
    else step=AttackStep.CLEANUP;
  }
  private void startDodge(SgsGameEngine engine){
    response=TrickResponseContinuation.forAttack(targetSeat,sourceSeat,formatVersion>=16);settleResponse(engine);
  }
  private static TrickResponseContinuation.Step responseStep(AttackStep step){return switch(step){
    case HUJIA_INVOKE->TrickResponseContinuation.Step.LORD;
    case HUJIA_PROVIDER->TrickResponseContinuation.Step.PROVIDER;
    case HUJIA_BAGUA->TrickResponseContinuation.Step.PROVIDER_BAGUA;
    case HUJIA_SHAN->TrickResponseContinuation.Step.PROVIDER_CARD;
    case OWN_BAGUA->TrickResponseContinuation.Step.OWN_BAGUA;
    case DODGE->TrickResponseContinuation.Step.OWN_CARD;
    default->null;
  };}
  private void restoreResponseProjection(){
    var phase=responseStep(step);if(phase==null)return;
    var child=new TrickResponseContinuation.Snapshot(targetSeat,sourceSeat,PromptType.DODGE_SHA,phase,providers,providerCursor,
        phase==TrickResponseContinuation.Step.OWN_CARD?pending:null,pending,revision,false,false);
    response=new TrickResponseContinuation(child,0,0,true);
  }
  private void settleResponse(SgsGameEngine engine){
    response.advance(engine);
    if(response.done()){
      if(formatVersion>=16&&response.ownCardPaid())engine.state().log("seat {} dodged the {} from seat {}",targetSeat,effectiveType.id(),sourceSeat);
      boolean success=response.success();response=null;
      if(success)dodged(engine);else step=AttackStep.BAGUA;
      return;
    }
    var child=response.snapshot();
    step=formatVersion>=14?AttackStep.RESPONSE_CHILD:switch(child.step()){
      case LORD->AttackStep.HUJIA_INVOKE;case PROVIDER->AttackStep.HUJIA_PROVIDER;
      case PROVIDER_BAGUA->AttackStep.HUJIA_BAGUA;case PROVIDER_CARD->AttackStep.HUJIA_SHAN;
      case OWN_BAGUA->AttackStep.OWN_BAGUA;case OWN_CARD->AttackStep.DODGE;
      default->throw new IllegalStateException("nested response step needs a versioned attack-frame codec: "+child.step());
    };
    providers=child.providers();providerCursor=child.cursor();var choice=child.pending();
    if(choice==null)throw new IllegalStateException("response child failed to yield");
    revision++;pending=new PendingSnapshot(choice.id(),choice.type(),choice.askerSeat(),choice.subjectSeat(),choice.allowedTypes(),choice.deadlineMillis(),revision,choice.skillId());
  }
  private void ownQinglong(SgsGameEngine engine){providers=List.of();providerCursor=0;step=AttackStep.QINGLONG;await(engine,PromptType.QINGLONG_SHA,sourceSeat,targetSeat,List.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA),"qinglong");}
  private void settleDamage(SgsGameEngine engine){
    damageProgram.advanceOne(engine,revision,false);
    if(damageProgram.done()){damageProgram=null;step=AttackStep.CLEANUP;return;}
    if(formatVersion<14&&damageProgram.step()!=DamageProgramContinuation.Step.DAMAGE)throw new IllegalStateException("after-damage body requires a versioned attack child-frame codec");
    var child=damageProgram.prompt(engine).orElse(null);
    if(child!=null){revision++;pending=new PendingSnapshot(child.id(),child.type(),child.asker().seat(),child.subject().seat(),child.allowedTypes(),child.deadlineMillis(),revision,child.skillId());}
  }
  private void settleRedirect(SgsGameEngine engine){
    redirect.advance(engine);
    if(redirect.done()){targetSeat=redirect.target();step=AttackStep.TARGET_RULES;return;}
    var child=redirect.pending();if(child==null)throw new IllegalStateException("redirect failed to yield");
    revision++;pending=new PendingSnapshot(child.id(),child.type(),child.askerSeat(),child.subjectSeat(),child.allowedTypes(),child.deadlineMillis(),revision,child.skillId());
  }
  void validateRedirect(SgsGameEngine engine){if(redirect!=null)redirect.validate(engine,card(engine));}
  private void settleRule(SgsGameEngine engine){
    ruleQuery.advance(engine,dev.sgs.core.engine.skill.SkillContext.cardTarget(engine.state(),engine.state().players().get(sourceSeat),engine.state().players().get(targetSeat),card(engine)));
    if(ruleQuery.done()){int count=Math.max(0,ruleQuery.value());responses=new ResponseSequence(count);dodgeProhibited=count==0;ruleQuery=null;step=AttackStep.WEAPON;return;}
    var child=ruleQuery.pending();if(child==null)throw new IllegalStateException("rule child failed to yield");
    revision++;pending=new PendingSnapshot(child.id(),child.type(),child.askerSeat(),child.subjectSeat(),child.allowedTypes(),child.deadlineMillis(),revision,child.skillId());
  }
  void validateUseCost(SgsGameEngine engine){
    if(useCost==null||!Set.of(AttackStep.USE_COST,AttackStep.QINGLONG_COST).contains(step)||engine.state().shaPlayedThisTurn()!=shaBefore||engine.state().jiuBoostPending()!=wineBefore)
      throw new IllegalArgumentException("attack use counters changed during payment");
    if(step==AttackStep.QINGLONG_COST){
      var source=engine.state().players().get(sourceSeat);var paid=useCost.snapshot();boolean provided=paid.reason()==CardMove.Reason.RESPONSE;
      if(source.equipment().weapon()==null||source.equipment().weapon().type()!=CardType.QINGLONG||!source.alive()||!engine.state().players().get(targetSeat).alive()
          ||provided&&(!providers.equals(engine.trickLordProviders(source,true))||providerCursor>=providers.size()||providers.get(providerCursor)!=paid.actor())
          ||!provided&&(paid.actor()!=sourceSeat||paid.cards().size()!=1||!providers.isEmpty()||providerCursor!=0))throw new IllegalArgumentException("Qinglong payment ownership differs");
    }
    useCost.validate(engine);
  }
  private void startQinglongCost(SgsGameEngine engine,int supplier,GameCard sha,boolean provided){
    var state=engine.state();engine.validateQinglongPayment(state.players().get(sourceSeat),state.players().get(targetSeat),state.players().get(supplier),sha,provided);
    useCost=CardUseCostContinuation.accepted(engine,supplier,sha,provided?CardMove.Reason.RESPONSE:CardMove.Reason.USE);shaBefore=state.shaPlayedThisTurn();wineBefore=state.jiuBoostPending();step=AttackStep.QINGLONG_COST;
  }
  void validateDiscardCost(SgsGameEngine engine){
    var source=engine.state().players().get(sourceSeat);var weapon=source.equipment().weapon();
    CardType expected=switch(step){case GUANSHI_PAYMENT->CardType.GUANSHI;case CIXIONG_DISCARD->CardType.CIXIONG;case HANBING_FIRST_DISCARD,HANBING_SECOND_DISCARD->CardType.HANBING;case QILIN_DISCARD->CardType.QILIN_GONG;default->null;};
    boolean own=step==AttackStep.GUANSHI_PAYMENT;
    if(discardCost==null||expected==null||weapon==null||weapon.type()!=expected
        ||discardCost.snapshot().actor()!=(step==AttackStep.CIXIONG_DISCARD?targetSeat:sourceSeat)||discardCost.snapshot().owner()!=(own?sourceSeat:targetSeat)
        ||discardCost.snapshot().cards().size()!=(own?2:1)||discardCost.snapshot().cards().contains(weapon.instanceId()))throw new IllegalArgumentException("discard has no original weapon or owner");
    discardCost.validate(engine);
  }
  private CardInstance discardedCard(SgsGameEngine e,DiscardCostContinuation.Snapshot paid){return e.state().cards().cards(CardZone.DISCARD).stream().filter(c->c.instanceId()==paid.cards().getFirst()).findFirst().orElseThrow();}
  private void discardTarget(CardInstance card,int actor,AttackStep returnPhase){discardCost=new DiscardCostContinuation(actor,targetSeat,List.of(card));step=returnPhase;}
  boolean childChoice(){return pending!=null&&Set.of(PromptType.GUI_CAI_REPLACE,PromptType.YIJI_GIVE,PromptType.FANKUI_TAKE,PromptType.GANGLIE_DISCARD,PromptType.LIULI_TARGET).contains(pending.type());}
  boolean chooseChild(SgsGameEngine engine,Optional<SkillChoice> choice){
    if(!childChoice())return false;
    if(step==AttackStep.TARGET_REDIRECT&&redirect!=null){if(!redirect.choose(engine,card(engine),choice))return false;pending=null;revision++;settleRedirect(engine);return true;}
    if(ruleQuery!=null)return chooseRule(engine,choice);
    if(response!=null){if(!response.choose(engine,choice))return false;pending=null;revision++;settleResponse(engine);return true;}
    if(damageProgram!=null){if(!damageProgram.choose(engine,choice))return false;pending=null;revision++;settleDamage(engine);return true;}
    return false;
  }
  boolean chooseRule(SgsGameEngine engine,Optional<SkillChoice> choice){
    if(ruleQuery==null || !ruleQuery.choose(engine,choice))return false;pending=null;revision++;settleRule(engine);return true;
  }
  private void followQinglong(GameCard card){parentSizes.add(1+extraCosts.size());parents.add(cardId);parents.addAll(extraCosts);extraCosts.clear();cardId=card.sourceCards().getFirst().instanceId();card.sourceCards().stream().skip(1).map(CardInstance::instanceId).forEach(extraCosts::add);effectiveType=card.type();baseDamage=1;providers=List.of();providerCursor=0;dodgeProhibited=false;if(redirect!=null&&!redirect.snapshot().activated())redirect=null;step=AttackStep.TARGET_REDIRECT;}
  void chooseWeapon(SgsGameEngine engine,Optional<SkillChoice> choice){
    if(pending!=null && (step==AttackStep.GUANSHI_FIRST || step==AttackStep.GUANSHI_SECOND)){
      boolean first=step==AttackStep.GUANSHI_FIRST;
      if(choice.isPresent() && (choice.get().targetSeat()!=-1 || engine.continuationGuanshiSelect(engine.state().players().get(sourceSeat),first?0:weaponCost,choice.get().cardId()).isEmpty()))return;
      pending=null;revision++;
      if(choice.isEmpty()){weaponCost=0;step=AttackStep.CLEANUP;return;}
      if(first){weaponCost=choice.get().cardId();step=AttackStep.GUANSHI_SECOND;await(engine,PromptType.WEAPON_COST_SECOND,sourceSeat,targetSeat,List.of(),"guanshi");}
      else{
        if(formatVersion>=17){var costs=engine.continuationGuanshiPayment(engine.state().players().get(sourceSeat),weaponCost,choice.get().cardId());weaponCost=0;
          if(costs.isEmpty())step=AttackStep.CLEANUP;else{discardCost=new DiscardCostContinuation(sourceSeat,costs.get());step=AttackStep.GUANSHI_PAYMENT;}}
        else{boolean paid=engine.continuationGuanshiPay(engine.state().players().get(sourceSeat),weaponCost,choice.get().cardId());weaponCost=0;step=paid?AttackStep.BAGUA:AttackStep.CLEANUP;}
      }
      return;
    }
    if(pending!=null && (step==AttackStep.HANBING_FIRST || step==AttackStep.HANBING_SECOND)){
      var seats=engine.state().players();boolean first=step==AttackStep.HANBING_FIRST;pending=null;revision++;
      if(formatVersion>=18){
        var selected=engine.continuationHanbingSelection(seats.get(targetSeat),choice);
        if(selected!=null){discardTarget(selected,sourceSeat,first?AttackStep.HANBING_FIRST_DISCARD:AttackStep.HANBING_SECOND_DISCARD);return;}
      }else engine.continuationHanbingDiscard(seats.get(sourceSeat),seats.get(targetSeat),choice);
      if(first)step=AttackStep.HANBING_SECOND;
      else{engine.continuationHanbingFinish(seats.get(sourceSeat),seats.get(targetSeat));step=AttackStep.CLEANUP;}
      return;
    }
    if(pending!=null && step==AttackStep.CIXIONG_CHOOSE){
      var seats=engine.state().players();pending=null;revision++;
      if(formatVersion>=18){var selected=choice.flatMap(c->seats.get(targetSeat).handView().stream().filter(h->h.instanceId()==c.cardId()).findFirst());
        if(selected.isPresent()){discardTarget(selected.get(),targetSeat,AttackStep.CIXIONG_DISCARD);return;}}
      engine.continuationCixiongFinish(seats.get(sourceSeat),seats.get(targetSeat),choice);step=AttackStep.ARMOR;return;
    }
    chooseQilin(engine,choice);
  }
  void chooseQilin(SgsGameEngine engine,Optional<SkillChoice> choice){
    if(pending==null || step!=AttackStep.QILIN_CHOOSE)throw new IllegalStateException("no qilin choice");
    var seats=engine.state().players();var source=seats.get(sourceSeat);var target=seats.get(targetSeat);
    var horses=engine.continuationQilinHorses(source,target);
    long selected=choice.map(SkillChoice::cardId).orElse(horses.isEmpty()?-1L:horses.getFirst().instanceId());
    pending=null;revision++;
    if(selected>0){
      if(formatVersion>=18){var card=horses.stream().filter(c->c.instanceId()==selected).findFirst().orElseThrow();discardTarget(card,sourceSeat,AttackStep.QILIN_DISCARD);return;}
      engine.continuationQilinDiscard(source,target,selected);
    }
    step=AttackStep.DAMAGE_START;
  }
  PendingSnapshot suspendRescuePayment(){
    if(pending==null||pending.type()!=PromptType.RESCUE_TAO||damages.isEmpty()||damages.peek().step!=DamageStep.RESCUE)throw new IllegalStateException("no rescue input to accept");
    var accepted=pending;pending=null;revision++;return accepted;
  }
  void completeRescuePayment(){
    if(pending!=null||damages.isEmpty()||damages.peek().step!=DamageStep.RESCUE)throw new IllegalStateException("no accepted rescue to complete");
    var frame=damages.peek();frame.rescue(frame.rescue().completed(true));
  }
  private void advanceDamage(SgsGameEngine engine,DamageFrame frame){
    var state=engine.state();var victim=state.players().get(frame.target);var source=state.players().get(sourceSeat);
    switch(frame.step){
      case BEFORE->{
        if(state.status()!=dev.sgs.core.GameStatus.RUNNING || !victim.alive() || (frame.chain && !state.chained(frame.target))){damages.pop();return;}
        var applied=engine.continuationApplyDamage(victim,frame.amount,source,card(engine),frame.nature,frame.chain);
        if(applied.amount()==0){damages.pop();return;}
        frame.amount=applied.amount();frame.nature=applied.nature();frame.wasChained=applied.wasChained();
        frame.step=victim.hp()<1?DamageStep.DYING_START:DamageStep.AFTER;
      }
      case DYING_START->{
        engine.continuationStartDying(victim,source);frame.rescue(RescueRound.start(state.alivePlayersFromCurrent().stream().map(p->p.seat()).toList()));
        frame.step=DamageStep.RESCUE;
      }
      case RESCUE->{
        if(victim.hp()>=1 || !victim.alive()){engine.continuationCompleteDying(victim,source);frame.step=DamageStep.AFTER;return;}
        if(frame.rescue().exhausted()){
          if(!frame.rescue().played()){engine.continuationCompleteDying(victim,source);frame.step=DamageStep.AFTER;return;}
          frame.rescue(frame.rescue().next(state.alivePlayersFromCurrent().stream().map(p->p.seat()).toList()));return;
        }
        int asker=frame.rescue().asker();
        if(!state.players().get(asker).alive()){frame.rescue(frame.rescue().completed(false));return;}
        await(engine,PromptType.RESCUE_TAO,asker,frame.target,asker==frame.target?List.of(CardType.TAO,CardType.JIU):List.of(CardType.TAO),"");
      }
      case AFTER->{
        if(!ownsCleanup && orderedDamageListeners && engine.continuationBeginOrderedDamage(victim,source,frame.amount,card(engine),frame.nature,frame.chain)){
          frame.step=DamageStep.AFTER_DEFERRED;
        }else if(!ownsCleanup && !orderedDamageListeners && engine.continuationBeginDeferredDamage(victim,source,frame.amount,card(engine),frame.nature,frame.chain)){
          frame.step=DamageStep.AFTER_DEFERRED;
        }else if(engine.continuationJianxiongApplies(victim,source,frame.amount,card(engine),frame.nature,frame.chain)){
          frame.step=DamageStep.AFTER_CHOICE;await(engine,PromptType.SKILL_INVOKE,frame.target,sourceSeat,List.of(),"jianxiong");
        }else{engine.continuationAfterDamage(victim,source,frame.amount,card(engine),frame.nature,frame.chain);frame.step=DamageStep.AFTER_APPLIED;}
      }
      case AFTER_CHOICE,AFTER_DEFERRED->throw new IllegalStateException("after-damage hook without pending decision");
      case AFTER_APPLIED->{
        damages.pop();
        if(frame.nature.elemental() && frame.wasChained && state.status()==dev.sgs.core.GameStatus.RUNNING){
          engine.continuationReleaseChain(frame.target);
          if(!frame.chain){var ring=state.alivePlayersFromCurrent();for(int i=ring.size()-1;i>=0;i--)
            damages.push(new DamageFrame(ring.get(i).seat(),frame.amount,frame.nature,true));}
        }
      }
    }
  }
  /** Called only after the engine has validated identity, revision, deadline and canonical costs. */
  void answer(SgsGameEngine engine,Optional<PlayDecision> decision,boolean invoked){
    if(step==AttackStep.TARGET_REDIRECT&&redirect!=null&&redirect.snapshot().step()==TargetRedirectContinuation.Step.PAYMENT){
      if(!redirect.invoke(engine,invoked))throw new IllegalStateException("redirect rejected payment invocation");pending=null;revision++;settleRedirect(engine);return;
    }
    if(pending==null)throw new IllegalStateException("missing pending decision");
    if(discardCost!=null){if(pending.type()!=PromptType.SKILL_INVOKE||!discardCost.invoke(engine,invoked))throw new IllegalStateException("discard payment rejected invocation");pending=null;revision++;return;}
    if(useCost!=null){if(pending.type()!=PromptType.SKILL_INVOKE||!useCost.invoke(engine,invoked))throw new IllegalStateException("cost rejected invocation");pending=null;revision++;return;}
    if(childChoice()){if(!chooseChild(engine,Optional.empty()))throw new IllegalStateException("child rejected empty choice");return;}
    if(damageProgram!=null){boolean accepted=pending.type()==PromptType.SKILL_INVOKE?damageProgram.invoke(engine,invoked):damageProgram.respond(engine,decision);if(!accepted)throw new IllegalStateException("damage child rejected canonical input");pending=null;revision++;settleDamage(engine);return;}
    if(ruleQuery!=null){if(pending.type()==PromptType.GUI_CAI_REPLACE){chooseRule(engine,Optional.empty());return;}if(!ruleQuery.invoke(engine,invoked))throw new IllegalStateException("invalid rule invocation");pending=null;revision++;settleRule(engine);return;}
    if(pending.type()==PromptType.CARD_ZONE_SELECT || pending.type()==PromptType.WEAPON_DISCARD || pending.type()==PromptType.WEAPON_COST_FIRST || pending.type()==PromptType.WEAPON_COST_SECOND){chooseWeapon(engine,Optional.empty());return;}
    var request=pending;pending=null;revision++;
    var state=engine.state();var actor=state.players().get(request.askerSeat());
    if(response!=null){
      boolean accepted=request.type()==PromptType.SKILL_INVOKE?response.invoke(engine,invoked):response.answer(engine,decision);
      if(!accepted)throw new IllegalStateException("shared response rejected canonical attack input");
      if(formatVersion<16&&request.type()==PromptType.DODGE_SHA && decision.isPresent())state.log("seat {} dodged the {} from seat {}",targetSeat,effectiveType.id(),sourceSeat);
      settleResponse(engine);return;
    }
    switch(request.type()){
      case SKILL_INVOKE->{
        switch(request.skillId()){
          // Compatibility only: pre-v13 saves did not contain a rule-query child.
          case "tieji"->{if(formatVersion>12)throw new IllegalStateException("new rule decision lost its child");dodgeProhibited=engine.continuationTiejiResult(actor,state.players().get(targetSeat),card(engine),invoked);step=AttackStep.WEAPON;}
          case "zhuque"->{if(invoked){effectiveType=CardType.HUOSHA;state.publishSkillActivation(sourceSeat,"zhuque");state.log("seat {} zhuque converts normal sha to fire",sourceSeat);}step=AttackStep.ARMOR;}
          case "jijiang"->{if(step!=AttackStep.QINGLONG_LORD)throw new IllegalStateException("invalid Qinglong lord invocation");if(invoked)step=AttackStep.QINGLONG_PROVIDER;else ownQinglong(engine);}
          case "guanshi"->{
            if(invoked){step=AttackStep.GUANSHI_FIRST;await(engine,PromptType.WEAPON_COST_FIRST,sourceSeat,targetSeat,List.of(),"guanshi");}
            else step=AttackStep.CLEANUP;
          }
          case "hanbing"->{step=invoked?AttackStep.HANBING_FIRST:AttackStep.DAMAGE_START;}
          case "cixiong"->{
            if(invoked){
              if(state.players().get(targetSeat).handCount()>0){step=AttackStep.CIXIONG_CHOOSE;await(engine,PromptType.WEAPON_DISCARD,targetSeat,sourceSeat,List.of(),"cixiong");}
              else{engine.continuationCixiongFinish(state.players().get(sourceSeat),state.players().get(targetSeat),Optional.empty());step=AttackStep.ARMOR;}
            }else step=AttackStep.ARMOR;
          }
          case "qilin_gong"->{
            var source=state.players().get(sourceSeat);var target=state.players().get(targetSeat);
            var horses=engine.continuationQilinHorses(source,target);
            if(invoked && horses.size()>1){step=AttackStep.QILIN_CHOOSE;await(engine,PromptType.CARD_ZONE_SELECT,sourceSeat,targetSeat,List.of(),"qilin_gong");}
            else{if(invoked && !horses.isEmpty()){
              if(formatVersion>=18){discardTarget(horses.getFirst(),sourceSeat,AttackStep.QILIN_DISCARD);break;}
              engine.continuationQilinDiscard(source,target,horses.getFirst().instanceId());}step=AttackStep.DAMAGE_START;}
          }
          case "jianxiong"->{
            var frame=damages.peek();
            engine.continuationAfterDamageWithInvoke(state.players().get(frame.target),state.players().get(sourceSeat),frame.amount,card(engine),frame.nature,frame.chain,invoked);
            frame.step=DamageStep.AFTER_APPLIED;
          }
          default->throw new IllegalStateException("unsupported deferred skill "+request.skillId());
        }
      }
      case JIJIANG_SHA->{
        if(step!=AttackStep.QINGLONG_PROVIDER_SHA)throw new IllegalStateException("invalid Qinglong provider response");
        if(decision.isPresent()){var follow=decision.get().card();if(formatVersion>=19)startQinglongCost(engine,actor.seat(),follow,true);else{engine.continuationCommitProvidedQinglong(state.players().get(sourceSeat),state.players().get(targetSeat),actor,follow);followQinglong(follow);}}
        else{providerCursor++;step=AttackStep.QINGLONG_PROVIDER;}
      }
      case QINGLONG_SHA->{
        if(decision.isEmpty()){step=AttackStep.CLEANUP;break;}
        var follow=decision.get().card();
        if(formatVersion>=19){startQinglongCost(engine,actor.seat(),follow,false);break;}
        engine.continuationCommitQinglong(actor,state.players().get(targetSeat),follow);
        followQinglong(follow);
      }
      case RESCUE_TAO->{
        var frame=damages.peek();
        if(decision.isPresent())engine.continuationRescue(actor,state.players().get(frame.target),decision.get());
        frame.rescue(frame.rescue().completed(decision.isPresent()));
      }
      default->throw new IllegalStateException("unsupported continuation prompt");
    }
  }
}
