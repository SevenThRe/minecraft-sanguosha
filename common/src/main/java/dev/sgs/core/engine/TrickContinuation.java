package dev.sgs.core.engine;

import dev.sgs.core.card.*;
import dev.sgs.core.player.SgsPlayer;
import dev.sgs.core.engine.skill.MovementListenerProgram;
import dev.sgs.core.engine.skill.SkillContext;
import java.util.*;

/** Data-owned parent for simple trick use, JiZhi hooks, shared counterspells and private choices. */
public final class TrickContinuation {
  public enum Step { PRIMARY_COMMIT, COUNTER, COUNTER_COMMIT, EFFECT, CHOICE, CLEANUP, DONE, TARGET_START, TARGET_NEXT, RESPONSE, DAMAGE, DAMAGE_SKILL, DAMAGE_FANKUI, DAMAGE_GANGLIE, DAMAGE_FANKUI_FINISH, REVEAL_COST, STRIKE }
  public record DamageParentSnapshot(AttackContinuation.Snapshot damage,GangLieContinuation.Snapshot ganglie) {}
  public record Cost(int seat,long cardId) {}
  public record Token(long id,int seat) {}
  public record Movement(long sequence,CardZone from,CardZone to,List<Long> cards,CardMove.Reason reason,int actor,boolean publicCards){public Movement{cards=List.copyOf(cards);}}
  public record Window(long id,int subject,int depth,long deadline,List<Token> tokens,List<Integer> pending,List<Integer> passed){
    public Window{tokens=List.copyOf(tokens);pending=List.copyOf(pending);passed=List.copyOf(passed);}
  }
  public record Pending(long id,PromptType type,int asker,int subject,long deadline,List<Long> choices,boolean hidden,String skill,long revision){
    public Pending{choices=List.copyOf(choices);Objects.requireNonNull(skill);}
  }
  public record Family(List<Integer> targets,int cursor,boolean revealed,List<Long> harvest,int duelResponder,int duelOther,int duelRequired,int duelPaid,
      TrickResponseContinuation.Snapshot response,AttackContinuation.Snapshot damage,YiJiContinuation.Snapshot yiji,GangLieContinuation.Snapshot ganglie,List<DamageParentSnapshot> parents,boolean movementPrepared,boolean feedbackTaken,boolean penaltyLogPending,DamageProgramContinuation.Snapshot program,RevealCostContinuation.Snapshot revealCost,StrikeContinuation.Snapshot strike,MovementListenerProgram.Snapshot movementProgram){

    public Family(List<Integer> targets,int cursor,boolean revealed,List<Long> harvest,int responder,int other,int required,int paid,TrickResponseContinuation.Snapshot response,AttackContinuation.Snapshot damage,YiJiContinuation.Snapshot yiji,GangLieContinuation.Snapshot ganglie,List<DamageParentSnapshot> parents,boolean prepared,boolean taken,boolean penalty,DamageProgramContinuation.Snapshot program,RevealCostContinuation.Snapshot reveal,StrikeContinuation.Snapshot strike){this(targets,cursor,revealed,harvest,responder,other,required,paid,response,damage,yiji,ganglie,parents,prepared,taken,penalty,program,reveal,strike,null);}
    public Family(List<Integer> targets,int cursor,boolean revealed,List<Long> harvest,int responder,int other,int required,int paid,TrickResponseContinuation.Snapshot response,AttackContinuation.Snapshot damage,YiJiContinuation.Snapshot yiji,GangLieContinuation.Snapshot ganglie,List<DamageParentSnapshot> parents,boolean prepared,boolean taken,boolean penalty,DamageProgramContinuation.Snapshot program,RevealCostContinuation.Snapshot reveal){this(targets,cursor,revealed,harvest,responder,other,required,paid,response,damage,yiji,ganglie,parents,prepared,taken,penalty,program,reveal,null);}
    public Family(List<Integer> targets,int cursor,boolean revealed,List<Long> harvest,int responder,int other,int required,int paid,TrickResponseContinuation.Snapshot response,AttackContinuation.Snapshot damage,YiJiContinuation.Snapshot yiji,GangLieContinuation.Snapshot ganglie,List<DamageParentSnapshot> parents,boolean prepared,boolean taken,boolean penalty,DamageProgramContinuation.Snapshot program){this(targets,cursor,revealed,harvest,responder,other,required,paid,response,damage,yiji,ganglie,parents,prepared,taken,penalty,program,null);}
    public Family(List<Integer> targets,int cursor,boolean revealed,List<Long> harvest,int responder,int other,int required,int paid,TrickResponseContinuation.Snapshot response,AttackContinuation.Snapshot damage,YiJiContinuation.Snapshot yiji,GangLieContinuation.Snapshot ganglie,List<DamageParentSnapshot> parents,boolean prepared,boolean taken,boolean penalty){this(targets,cursor,revealed,harvest,responder,other,required,paid,response,damage,yiji,ganglie,parents,prepared,taken,penalty,null);}
    public Family{targets=List.copyOf(targets);harvest=List.copyOf(harvest);parents=List.copyOf(parents);}
    public Family(List<Integer> targets,int cursor,boolean revealed,List<Long> harvest,int responder,int other,int required,int paid,TrickResponseContinuation.Snapshot response,AttackContinuation.Snapshot damage,YiJiContinuation.Snapshot yiji){this(targets,cursor,revealed,harvest,responder,other,required,paid,response,damage,yiji,null,List.of());}
    public Family(List<Integer> targets,int cursor,boolean revealed,List<Long> harvest,int responder,int other,int required,int paid,TrickResponseContinuation.Snapshot response,AttackContinuation.Snapshot damage,YiJiContinuation.Snapshot yiji,GangLieContinuation.Snapshot ganglie,List<DamageParentSnapshot> parents){this(targets,cursor,revealed,harvest,responder,other,required,paid,response,damage,yiji,ganglie,parents,false,false,false);}
    public Family(List<Integer> targets,int cursor,boolean revealed,List<Long> harvest,int responder,int other,int required,int paid,TrickResponseContinuation.Snapshot response,AttackContinuation.Snapshot damage){this(targets,cursor,revealed,harvest,responder,other,required,paid,response,damage,null);}
  }
  public record Snapshot(int version,int source,int target,long cardId,CardType effectiveType,Step step,int depth,
      List<Cost> counters,List<Integer> scopePassed,List<Movement> movements,Window window,Pending pending,long revision,Family family){
    public Snapshot{counters=List.copyOf(counters);scopePassed=List.copyOf(scopePassed);movements=List.copyOf(movements);}
  }
  private final int source;private int target;private final long cardId;private final CardType type;
  private int formatVersion=12;
  private Step step=Step.PRIMARY_COMMIT;private int depth;private long revision;
  private final List<Cost> counters=new ArrayList<>();private final Set<Integer> passed=new TreeSet<>();
  private CounterspellWindow window;private Pending pending;
  private final Deque<Movement> movements=new ArrayDeque<>();
  private List<Integer> targets;private int cursor;private boolean revealed;private final List<Long> harvest=new ArrayList<>();
  private DuelExchange duel=DuelExchange.inactive();
  private TrickResponseContinuation response;private DamageProgramContinuation damageProgram;
  private boolean movementPrepared,penaltyLogPending;
  private MovementListenerProgram movementProgram;
  private RevealCostContinuation revealCost;
  private StrikeContinuation strike;
  void attachStrike(int user,int target){if(type!=CardType.JIEDAO||strike!=null)throw new IllegalStateException("unexpected strike child");strike=new StrikeContinuation(user,target);}
  boolean movementsPending(){return !movements.isEmpty();}
  boolean strikeInput(){return pending==null&&strike!=null&&step==Step.STRIKE;}
  StrikeContinuation strike(){return strike;}
  void deferLordPenaltyLog(){penaltyLogPending=true;}
  TrickContinuation(SgsGameEngine e,int source,int target,long cardId,CardType type){this(e,source,target,cardId,type,null);}
  TrickContinuation(SgsGameEngine e,int source,int target,long cardId,CardType type,List<Integer> selected){this.source=source;this.target=target;this.cardId=cardId;this.type=type;
    targets=(type==CardType.ZHULU || type==CardType.TAOYUAN || type==CardType.WUGU)?e.state().alivePlayersFromSeat(source).stream().map(SgsPlayer::seat).toList():
        (type==CardType.NANMAN || type==CardType.WANJIAN)?e.state().alivePlayersAfter(source).stream().map(SgsPlayer::seat).toList():List.of(target);
    if(selected!=null)targets=List.copyOf(selected);
    if(type==CardType.ZHULU)targets=targets.stream().filter(seat->e.cardTargetAllowed(player(e,source),player(e,seat),card(e))).toList();
    if(!targets.isEmpty())this.target=targets.getFirst();
  }
  TrickContinuation(SgsGameEngine engine,Snapshot saved,long captured,long now){
    formatVersion=Math.max(6,saved.version());source=saved.source();target=saved.target();cardId=saved.cardId();type=saved.effectiveType();step=saved.step();depth=saved.depth();revision=saved.revision();
    var family=saved.family();targets=family==null?List.of(target):family.targets();cursor=family==null?0:family.cursor();
    if(family!=null){revealed=family.revealed();harvest.addAll(family.harvest());duel=new DuelExchange(family.duelResponder(),family.duelOther(),family.duelRequired(),family.duelPaid());
      if(family.response()!=null)response=new TrickResponseContinuation(family.response(),captured,now);
      if(family.damage()!=null)damageProgram=family.program()!=null?new DamageProgramContinuation(family.program(),captured,now):restoreLegacyDamage(saved,captured,now);
      if(family.strike()!=null)strike=new StrikeContinuation(family.strike(),captured,now);
      if(family.revealCost()!=null)revealCost=new RevealCostContinuation(family.revealCost(),captured,now);
      movementPrepared=family.movementPrepared();penaltyLogPending=family.penaltyLogPending();
      if(family.movementProgram()!=null)movementProgram=new MovementListenerProgram(family.movementProgram());
    }
    counters.addAll(saved.counters());passed.addAll(saved.scopePassed());movements.addAll(saved.movements());pending=saved.pending();
    if(pending!=null)pending=new Pending(pending.id(),pending.type(),pending.asker(),pending.subject(),shift(pending.deadline(),captured,now),pending.choices(),pending.hidden(),pending.skill(),pending.revision());
    if(saved.window()!=null){var w=saved.window();long deadline=shift(w.deadline(),captured,now);
      var requests=w.tokens().stream().map(t->new PromptRequest(t.id(),PromptType.WUXIE_COUNTER,player(engine,t.seat()),player(engine,w.subject()),List.of(CardType.WUXIE),deadline)).toList();
      window=new CounterspellWindow(w.id(),w.subject(),w.depth(),deadline,requests,engine::continuationNow,engine::trickValidateResponse).withEffect(type);
      window.restoreProgress(Set.copyOf(w.pending()),Set.copyOf(w.passed()));
    }
  }
  private static long shift(long deadline,long captured,long now){return DecisionBoundary.restoredDeadline(deadline,captured,now);}
  /** v2-v6 wire names are compatibility projections, not a second after-damage executor. */
  private static DamageProgramContinuation restoreLegacyDamage(Snapshot saved,long captured,long now){
    var f=saved.family();var phase=switch(saved.step()){
      case DAMAGE_SKILL->DamageProgramContinuation.Step.DISTRIBUTE;case DAMAGE_FANKUI->DamageProgramContinuation.Step.TAKE;
      case DAMAGE_FANKUI_FINISH->DamageProgramContinuation.Step.TAKE_FINISH;case DAMAGE_GANGLIE->DamageProgramContinuation.Step.REACTION;default->DamageProgramContinuation.Step.DAMAGE;
    };
    dev.sgs.core.engine.skill.AwaitableDamageEffect.Request effect=switch(phase){
      case DISTRIBUTE->legacyEffect(f.yiji().owner(),"yiji",dev.sgs.core.engine.skill.AwaitableDamageEffect.Kind.DISTRIBUTE_NEW_CARDS);
      case TAKE,TAKE_FINISH->legacyEffect(f.damage().targetSeat(),"fankui",dev.sgs.core.engine.skill.AwaitableDamageEffect.Kind.TAKE_SOURCE_CARD);
      case REACTION->legacyEffect(f.ganglie().owner(),"ganglie",dev.sgs.core.engine.skill.AwaitableDamageEffect.Kind.JUDGMENT_OR_RETALIATION);default->null;
    };
    var p=saved.pending();if(p!=null && !(phase==DamageProgramContinuation.Step.DISTRIBUTE&&p.skill().equals("yiji") || phase==DamageProgramContinuation.Step.TAKE&&p.type()==PromptType.FANKUI_TAKE))p=null;
    var parents=f.parents().stream().map(v->new DamageProgramContinuation.ReturnSnapshot(v.damage(),v.ganglie(),legacyEffect(v.ganglie().owner(),"ganglie",dev.sgs.core.engine.skill.AwaitableDamageEffect.Kind.JUDGMENT_OR_RETALIATION))).toList();
    return new DamageProgramContinuation(new DamageProgramContinuation.Snapshot(phase,f.damage(),f.yiji(),f.ganglie(),effect,parents,p,f.feedbackTaken()),captured,now);
  }
  private static dev.sgs.core.engine.skill.AwaitableDamageEffect.Request legacyEffect(int owner,String skill,dev.sgs.core.engine.skill.AwaitableDamageEffect.Kind kind){return new dev.sgs.core.engine.skill.AwaitableDamageEffect.Request(owner,skill,kind);}
  private void projectDamage(){
    if(damageProgram.done()){damageProgram=null;step=Step.TARGET_NEXT;return;}
    step=switch(damageProgram.step()){case DAMAGE,RESCUE_COST->Step.DAMAGE;case DISTRIBUTE->Step.DAMAGE_SKILL;case TAKE->Step.DAMAGE_FANKUI;case TAKE_FINISH->Step.DAMAGE_FANKUI_FINISH;case REACTION->Step.DAMAGE_GANGLIE;case DONE->throw new IllegalStateException();};pending=damageProgram.ownPending();
  }
  private static SgsPlayer player(SgsGameEngine e,int seat){return e.state().players().get(seat);}
  private CardInstance physical(SgsGameEngine e,long id){return e.state().cards().zones().stream().flatMap(z->e.state().cards().cards(z).stream()).filter(c->c.instanceId()==id).findFirst().orElseThrow();}
  GameCard card(SgsGameEngine e){var c=physical(e,cardId);return c.type()==type?c:new VirtualCard(type,List.of(c));}
  public Snapshot snapshot(){
    var body=damageProgram==null?null:damageProgram.snapshot();
    Window data=null;if(window!=null)data=new Window(window.windowId(),window.subjectSeat(),window.chainDepth(),window.deadlineMillis(),
        window.requests().stream().map(p->new Token(p.id(),p.asker().seat())).toList(),window.pendingRequests().stream().map(p->p.asker().seat()).toList(),window.scopePassSeats());
    return new Snapshot(formatVersion,source,target,cardId,type,step,depth,counters,List.copyOf(passed),List.copyOf(movements),data,pending,revision,new Family(targets,cursor,revealed,harvest,duel.responder(),duel.other(),duel.required(),duel.paid(),response==null?null:response.snapshot(),body==null?null:body.damage(),body==null?null:body.distribution(),body==null?null:body.reaction(),body==null?List.of():body.parents().stream().map(p->new DamageParentSnapshot(p.damage(),p.reaction())).toList(),movementPrepared,body!=null&&body.taken(),penaltyLogPending,formatVersion>=7?body:null,revealCost==null?null:revealCost.snapshot(),strike==null?null:strike.snapshot(),movementProgram==null?null:movementProgram.snapshot()));
  }
  public boolean done(){return step==Step.DONE && movements.isEmpty();}
  void enqueue(CardMove move){movements.addLast(new Movement(move.sequence(),move.from(),move.to(),move.cards().stream().map(CardInstance::instanceId).toList(),move.reason(),move.actorSeat(),move.publicCards()));}
  private CardMove movement(SgsGameEngine e,Movement m){return new CardMove(m.sequence(),m.from(),m.to(),m.cards().stream().map(id->physical(e,id)).toList(),m.reason(),m.actor(),m.publicCards());}
  public long revision(){return revision;}
  public Optional<CounterspellWindow> window(){return Optional.ofNullable(window);}
  public Optional<PromptRequest> prompt(SgsGameEngine e){
    if(pending==null){if(strikeInput()&&!movements.isEmpty())return Optional.empty();if(strikeInput())return strike.prompt(e);if(revealCost!=null&&revealCost.prompt(e).isPresent())return revealCost.prompt(e);if(response!=null)return response.prompt(e);return damageProgram==null?Optional.empty():damageProgram.prompt(e);}
    var choices=pending.choices().stream().map(id->physical(e,id)).toList();
    var recipients=pending.type()==PromptType.YIJI_GIVE?e.state().players().stream().filter(SgsPlayer::alive).map(SgsPlayer::seat).toList():List.<Integer>of();
    return Optional.of(new PromptRequest(pending.id(),pending.type(),player(e,pending.asker()),player(e,pending.subject()),List.of(),choices,pending.deadline(),pending.skill(),recipients,pending.hidden()));
  }
  private boolean jizhi(SgsGameEngine e,int seat){return player(e,seat).alive() && player(e,seat).hero().skillIds().contains("jizhi");}
  private void awaitInvoke(SgsGameEngine e,int actor){revision++;pending=new Pending(e.nextPromptId(),PromptType.SKILL_INVOKE,actor,actor,e.responseDeadlineMillis(),List.of(),false,"jizhi",revision);}
  private void afterCounterCommit(SgsGameEngine e,int actor){e.state().log("seat {} negated a trick (chain depth {})",actor,depth);step=Step.COUNTER;}
  void advance(SgsGameEngine e){
    if(window!=null || pending!=null || done())return;
    if(movements.isEmpty() && (strikeInput()&&strike.prompt(e).isPresent() || revealCost!=null&&revealCost.prompt(e).isPresent() || response!=null&&response.prompt(e).isPresent() || damageProgram!=null&&damageProgram.prompt(e).isPresent()))return;
    for(int budget=0;budget<10000;budget++){
      revision++;
      if(!movements.isEmpty()){
        if(formatVersion>=11){
          var move=movement(e,movements.getFirst());var context=SkillContext.movement(e.state(),move);
          if(movementProgram==null)movementProgram=e.skillBus().beginMovement(context);
          var request=movementProgram.advance(e.skillBus(),context);
          if(request.isPresent()){
            var r=request.get();revision++;pending=new Pending(e.nextPromptId(),PromptType.SKILL_INVOKE,r.owner(),r.owner(),e.responseDeadlineMillis(),List.of(),false,r.skill(),revision);return;
          }
          if(!movementProgram.done())throw new IllegalStateException("movement program did not yield or finish");
          movements.removeFirst();movementProgram=null;e.movementEventFinished(move);continue;
        }
        var move=movement(e,movements.getFirst());String skill=e.trickMovementInvoke(move);
        if(!skill.isEmpty()){if(!movementPrepared){e.trickPrepareMovement(move,skill);movementPrepared=true;}int actor=move.from().seat();revision++;pending=new Pending(e.nextPromptId(),PromptType.SKILL_INVOKE,actor,actor,e.responseDeadlineMillis(),List.of(),false,skill,revision);return;}
        movements.removeFirst();e.trickDispatchMovement(move);continue;
      }
      if(penaltyLogPending){e.state().log("lord accidentally killed a loyalist and discarded the whole hand");penaltyLogPending=false;}
      switch(step){
        case PRIMARY_COMMIT->{if(jizhi(e,source)){awaitInvoke(e,source);return;}e.trickCommit(player(e,source),card(e));step=Step.TARGET_START;}
        case COUNTER_COMMIT->{var cost=counters.getLast();if(jizhi(e,cost.seat())){awaitInvoke(e,cost.seat());return;}e.trickCommit(player(e,cost.seat()),physical(e,cost.cardId()));afterCounterCommit(e,cost.seat());}
        case TARGET_START->{
          if(e.state().status()!=dev.sgs.core.GameStatus.RUNNING || cursor>=targets.size() || (type==CardType.WUGU||type==CardType.ZHULU)&&revealed&&harvest.isEmpty()){step=Step.CLEANUP;continue;}
          if(type==CardType.ZHULU&&!revealed){revealed=true;harvest.addAll(ZhuluRules.reveal(e,source,targets.size()).stream().map(CardInstance::instanceId).toList());continue;}
          if(type==CardType.WUGU&&!revealed){revealed=true;harvest.addAll(e.state().cards().drawTo(CardZone.PROCESSING,e.state().alivePlayers().size(),CardMove.Reason.REVEAL,source,true).stream().map(CardInstance::instanceId).toList());e.state().beginDraft(cardId,source,"五谷丰登",harvest.stream().map(id->physical(e,id)).toList());continue;}
          target=targets.get(cursor);if(!player(e,target).alive() || !e.cardTargetAllowed(player(e,source),player(e,target),card(e))){cursor++;continue;}
          if((type==CardType.NANMAN||type==CardType.WANJIAN) && player(e,target).equipment().armor()!=null && player(e,target).equipment().armor().type()==CardType.TENGJIA){e.state().log("seat {} tengjia nullifies aoe",target);cursor++;continue;}
          e.state().journal().add(e.state().journal().seat(source)+"对"+e.state().journal().seat(target)+"结算"+dev.sgs.core.game.MatchJournal.type(type));
          depth=0;step=Step.COUNTER;
        }
        case TARGET_NEXT->{cursor++;step=Step.TARGET_START;}
        case COUNTER->{
          int subject=type==CardType.WUZHONG?source:target;long id=e.nextPromptId(),deadline=e.responseDeadlineMillis();
          // Keep the persisted token ring stable; the owning session prunes ineligible seats before publishing.
          var requests=e.state().alivePlayersFromSeat(subject).stream().filter(p->!passed.contains(p.seat())).map(p->new PromptRequest(e.nextPromptId(),PromptType.WUXIE_COUNTER,p,player(e,subject),List.of(CardType.WUXIE),deadline)).toList();
          if(requests.isEmpty()){finishCounters(e);continue;}
          window=new CounterspellWindow(id,subject,depth,deadline,requests,e::continuationNow,e::trickValidateResponse).withEffect(type);return;
        }
        case EFFECT->{
          if(type==CardType.JIEDAO){step=Step.STRIKE;continue;}

          if(type==CardType.JIEJIA){ZhuluRules.disarm(e,source,target);step=Step.TARGET_NEXT;}
          else if(type==CardType.WUZHONG){var drawn=e.drawCards(player(e,source),2);e.state().log("seat {} drew {} cards from wuzhong",source,drawn.size());step=Step.TARGET_NEXT;}
          else if(type==CardType.TIESUO){e.state().setChained(target,!e.state().chained(target));e.state().log("seat {} chain state {}",target,e.state().chained(target));step=Step.TARGET_NEXT;}
          else if(type==CardType.HUOGONG){revealCost=new RevealCostContinuation(source,target);step=Step.REVEAL_COST;}
          else if(type==CardType.TAOYUAN){var who=player(e,target);if(who.hp()<who.maxHp()){who.setHp(who.hp()+1);e.state().log("seat {} healed 1 from taoyuan",target);}step=Step.TARGET_NEXT;}
          else if(type==CardType.WUGU||type==CardType.ZHULU){e.state().draftPicker(target);revision++;pending=new Pending(e.nextPromptId(),PromptType.WUGU_PICK,target,source,e.responseDeadlineMillis(),harvest,false,type==CardType.ZHULU?"zhulu":"",revision);step=Step.CHOICE;return;}
          else if(type==CardType.NANMAN||type==CardType.WANJIAN){response=new TrickResponseContinuation(target,source,type==CardType.NANMAN?PromptType.RESPOND_SHA_NANMAN:PromptType.DODGE_SHA);step=Step.RESPONSE;}
          else if(type==CardType.JUEDOU){
            if(!duel.started())duel=DuelExchange.start(source,target);
            if(!player(e,duel.responder()).alive()||!player(e,duel.other()).alive()){step=Step.TARGET_NEXT;continue;}
            if(duel.needsRequirement())duel=duel.requiring(e.trickDuelRequired(player(e,duel.other()),player(e,duel.responder()),card(e)));
            response=new TrickResponseContinuation(duel.responder(),duel.other(),PromptType.DUEL_SHA);step=Step.RESPONSE;
          }
          else{var victim=player(e,target);var visible=new ArrayList<CardInstance>(victim.equipment().all());visible.addAll(victim.delayZone().cards());
            revision++;pending=new Pending(e.nextPromptId(),PromptType.CARD_ZONE_SELECT,source,target,e.responseDeadlineMillis(),visible.stream().map(CardInstance::instanceId).toList(),victim.handCount()>0,type==CardType.GUOHE?"guohe":"shunshou",revision);step=Step.CHOICE;return;}
        }
        case STRIKE->{
          strike.advance(e);if(!movements.isEmpty())continue;
          if(strike.done()){if(strike.declined())e.trickBorrowWeapon(source,target);step=Step.TARGET_NEXT;}else return;
        }
        case REVEAL_COST->{
          revealCost.advance(e,revision);if(revealCost.prompt(e).isPresent())return;
          if(revealCost.committed()){damageProgram=new DamageProgramContinuation(AttackContinuation.damage(source,target,cardId,type,1,DamageNature.FIRE),true,formatVersion>=12);step=Step.DAMAGE;}
          else if(revealCost.declined())step=Step.TARGET_NEXT;
        }
        case RESPONSE->{
          response.advance(e);if(!response.done())return;
          boolean success=response.success(),provided=response.supplied();response=null;
          if(type==CardType.JUEDOU){
            if(success){duel=duel.committed();step=Step.EFFECT;}
            else{e.state().log("seat {} lost the duel against seat {}",duel.responder(),duel.other());damageProgram=new DamageProgramContinuation(AttackContinuation.damage(duel.other(),duel.responder(),cardId,type,1),formatVersion>=7,formatVersion>=12);step=Step.DAMAGE;}
          }else if(success){if(type!=CardType.NANMAN||!provided)e.state().log("seat {} answered the aoe from seat {}",target,source);step=Step.TARGET_NEXT;}
          else{damageProgram=new DamageProgramContinuation(AttackContinuation.damage(source,target,cardId,type,1),formatVersion>=7,formatVersion>=12);step=Step.DAMAGE;}
        }
        case DAMAGE,DAMAGE_SKILL,DAMAGE_FANKUI,DAMAGE_FANKUI_FINISH,DAMAGE_GANGLIE->{
          damageProgram.advanceOne(e,revision,()->!movements.isEmpty());projectDamage();
          if(pending!=null || movements.isEmpty()&&damageProgram!=null&&damageProgram.prompt(e).isPresent())return;
        }
        case CHOICE->throw new IllegalStateException("choice stage without a request");
        case CLEANUP->{
          if(revealCost!=null&&revealCost.snapshot().shown()>0)e.state().completeCardReveal("huogong",target,source,revealCost.snapshot().shown());
          if(!harvest.isEmpty()){var remaining=harvest.stream().map(id->physical(e,id)).filter(c->CardZone.PROCESSING.equals(e.state().cards().locate(c.instanceId()))).toList();e.state().cards().move(remaining,CardZone.PROCESSING,CardZone.DISCARD,CardMove.Reason.FINISH,source);harvest.clear();}
          if(type==CardType.WUGU){e.state().finishDraft();}e.state().cards().finish(card(e),source);e.trickPlayedLog(source,card(e));step=Step.DONE;continue;}
        case DONE->{return;}
      }
    }
    throw new IllegalStateException("trick program did not yield");
  }
  void counterResult(SgsGameEngine e,long id,CounterspellResult result){
    if(window==null || window.windowId()!=id || !window.finish().equals(result))throw new IllegalArgumentException("foreign trick counterspell result");
    for(var actor:result.scopePasses())passed.add(e.state().players().stream().filter(p->p.id().equals(actor)).findFirst().orElseThrow().seat());
    window=null;revision++;
    if(result.winner().isEmpty()){finishCounters(e);return;}
    var winner=result.winner().get();var actor=e.state().players().stream().filter(p->p.id().equals(winner.actor())).findFirst().orElseThrow();
    var cost=winner.decision().card();if(cost.sourceCards().size()!=1 || cost.type()!=CardType.WUXIE)throw new IllegalArgumentException("invalid trick counter cost");
    counters.add(new Cost(actor.seat(),cost.sourceCards().getFirst().instanceId()));depth++;e.trickBeginOwned(actor,cost);step=Step.COUNTER_COMMIT;
  }
  private void finishCounters(SgsGameEngine e){for(int i=counters.size()-1;i>=0;i--){var c=counters.get(i);e.state().cards().finish(physical(e,c.cardId()),c.seat());}counters.clear();step=depth%2==1?Step.TARGET_NEXT:Step.EFFECT;}
  boolean invoke(SgsGameEngine e,UUID actor,long id,long expected,boolean accepted){
    var request=prompt(e).orElse(null);if(request==null || request.type()!=PromptType.SKILL_INVOKE || !DecisionBoundary.of(request,revision).matches(actor,id,expected))return false;
    if(strikeInput()){if(!strike.invoke(e,accepted&&!DecisionBoundary.of(request,revision).expired(e.continuationNow())))return false;revision++;return true;}
    if(pending==null){boolean answer=accepted&&!DecisionBoundary.of(request,revision).expired(e.continuationNow());revision++;
      if(response!=null)return response.invoke(e,answer);return damageProgram!=null&&damageProgram.invoke(e,answer);
    }
    var who=player(e,pending.asker());boolean answer=accepted && e.continuationNow()<=pending.deadline();
    if(damageProgram!=null&&damageProgram.ownPending()!=null){if(!damageProgram.invoke(e,answer))return false;pending=null;revision++;projectDamage();return true;}
    if(movementProgram!=null){e.completeMovementInvoke(movementProgram,movement(e,movements.getFirst()),answer);pending=null;revision++;return true;}
    if(!pending.skill().equals("jizhi")){
      var move=movement(e,movements.removeFirst());e.trickMovementWithInvoke(move,who,pending.skill(),answer,movementPrepared);movementPrepared=false;pending=null;revision++;return true;
    }
    var used=step==Step.PRIMARY_COMMIT?card(e):physical(e,counters.getLast().cardId());
    e.trickCommitWithInvoke(who,used,answer);pending=null;revision++;
    if(step==Step.PRIMARY_COMMIT)step=Step.TARGET_START;else afterCounterCommit(e,who.seat());return true;
  }
  boolean respond(SgsGameEngine e,UUID actor,long id,long expected,Optional<PlayDecision> answer){
    var request=prompt(e).orElse(null);if(request==null || request.type()==PromptType.SKILL_INVOKE || request.type()==PromptType.CARD_ZONE_SELECT || !DecisionBoundary.of(request,revision).matches(actor,id,expected))return false;
    if(strikeInput()){if(!strike.respond(e,answer))return false;revision++;return true;}
    if(pending!=null&&pending.type()==PromptType.WUGU_PICK){
      var picked=answer.isEmpty()?physical(e,harvest.getFirst()):answer.get().card().sourceCards().getFirst();if(!harvest.remove(picked.instanceId()))return false;
      if(type==CardType.ZHULU)ZhuluRules.equip(e,target,picked);
      else {e.state().cards().move(List.of(picked),CardZone.PROCESSING,CardZone.hand(target),CardMove.Reason.EFFECT_GAIN,target);e.state().log("seat {} picked {} from wugu",target,picked.cardId());}e.state().claimDraft(picked.instanceId(),target);pending=null;revision++;step=Step.TARGET_NEXT;return true;
    }
    if(revealCost!=null&&revealCost.prompt(e).isPresent()){if(!revealCost.answer(e,answer))return false;revision++;return true;}
    revision++;if(response!=null)return response.answer(e,answer);return damageProgram!=null&&damageProgram.respond(e,answer);
  }
  boolean choose(SgsGameEngine e,UUID actor,long id,long expected,Optional<SkillChoice> answer){
    var request=prompt(e).orElse(null);
    if(request==null||!DecisionBoundary.of(request,revision).matches(actor,id,expected))return false;
    if(strikeInput()){if(!strike.choose(e,answer))return false;revision++;return true;}
    if(damageProgram!=null && (pending==null || damageProgram.ownPending()!=null)
        && Set.of(PromptType.GUI_CAI_REPLACE,PromptType.GANGLIE_DISCARD,PromptType.YIJI_GIVE,PromptType.FANKUI_TAKE).contains(request.type())){
      if(!damageProgram.choose(e,answer))return false;pending=null;revision++;projectDamage();return true;
    }
    if(pending==null && response!=null && request.type()==PromptType.GUI_CAI_REPLACE){if(!response.choose(e,answer))return false;revision++;return true;}
    if(pending==null || pending.type()!=PromptType.CARD_ZONE_SELECT || !player(e,source).id().equals(actor) || pending.id()!=id || revision!=expected)return false;
    var victim=player(e,target);CardInstance taken=null;
    if(e.continuationNow()>pending.deadline())answer=Optional.empty();
    if(answer.isPresent()){
      long selected=answer.get().cardId();if(answer.get().targetSeat()!=-1)return false;
      if(selected==0 && pending.hidden() && victim.handCount()>0)taken=victim.handView().get(e.state().random().nextInt(victim.handCount()));
      else if(HandSelection.allowsPosition(pending.type(),pending.hidden(),victim.handCount(),selected)){int index=HandSelection.positionIndex(selected,victim.handCount());taken=victim.handView().get(index);}
      else if(selected>0 && pending.choices().contains(selected)){
        var zone=e.state().cards().locate(selected);if(!CardZone.equipment(target).equals(zone) && !CardZone.judgment(target).equals(zone))return false;taken=physical(e,selected);
      }else return false;
    }else{var options=new ArrayList<CardInstance>(victim.handView());options.addAll(victim.equipment().all());options.addAll(victim.delayZone().cards());if(!options.isEmpty())taken=options.get(e.state().random().nextInt(options.size()));}
    if(taken!=null){e.state().cards().move(List.of(taken),e.state().cards().locate(taken.instanceId()),type==CardType.GUOHE?CardZone.DISCARD:CardZone.hand(source),type==CardType.GUOHE?CardMove.Reason.DISCARD:CardMove.Reason.EFFECT_GAIN,source);
      if(type==CardType.GUOHE)e.state().log("seat {} dismantled {} from seat {}",source,taken.cardId(),target);else e.state().log("seat {} snatched a card from seat {}",source,target);}
    pending=null;revision++;step=Step.TARGET_NEXT;return true;
  }
}
