package dev.sgs.core.persistence;

import dev.sgs.core.*;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.*;
import dev.sgs.core.game.GameState;
import java.util.*;

/** Complete rule state plus an explicit pending trick parent. No serialized callbacks or stack. */
public record TrickCheckpoint(int version,StateCheckpoint state,long promptSequence,int responseTimeoutSeconds,
    Map<String,Map<String,Long>> skillMemory,List<String> bindings,long skillSequence,List<GameState.PublicSkillActivation> activations,
    TrickContinuation.Snapshot continuation,long capturedAtMillis){
  public static final int VERSION=12;
  public static final String LEGACY_RULES_VERSION="historical-standard-2011-simple-trick-v1";
  public static final String FAMILY_V2_RULES_VERSION="historical-standard-2011-trick-family-v2";
  public static final String YIJI_V3_RULES_VERSION="historical-standard-2011-trick-yiji-v3";
  public static final String SIMAYI_V4_RULES_VERSION="historical-standard-2011-trick-simayi-v4";
  public static final String GANGLIE_V5_RULES_VERSION="historical-standard-2011-trick-ganglie-v5";
  public static final String MOVEMENT_V6_RULES_VERSION="historical-standard-2011-trick-movement-v6";
  public static final String LISTENER_V7_RULES_VERSION="historical-standard-2011-trick-listener-program-v7";
  public static final String TARGET_V8_RULES_VERSION="historical-standard-2011-trick-target-program-v8";
  public static final String REVEAL_V9_RULES_VERSION="historical-standard-2011-reveal-cost-program-v9";
  public static final String STRIKE_V10_RULES_VERSION="historical-standard-2011-strike-child-program-v10";
  public static final String MOVEMENT_V11_RULES_VERSION="historical-standard-2011-movement-listener-program-v11";
  public static final String RULES_VERSION="historical-standard-2011-trick-rescue-cost-v12";
  public static String rulesVersion(int version){return switch(version){case 1->LEGACY_RULES_VERSION;case 2->FAMILY_V2_RULES_VERSION;case 3->YIJI_V3_RULES_VERSION;case 4->SIMAYI_V4_RULES_VERSION;case 5->GANGLIE_V5_RULES_VERSION;case 6->MOVEMENT_V6_RULES_VERSION;case 7->LISTENER_V7_RULES_VERSION;case 8->TARGET_V8_RULES_VERSION;case 9->REVEAL_V9_RULES_VERSION;case 10->STRIKE_V10_RULES_VERSION;case 11->MOVEMENT_V11_RULES_VERSION;case 12->RULES_VERSION;default->throw new IllegalArgumentException("unsupported trick version");};}
  /** The one yielded input can belong to the parent, a response provider, or nested rescue. */
  public long pendingDeadlineMillis(){
    var c=continuation;
    var strike=StrikeFrames.pending(c);if(strike!=null)return strike.deadlineMillis();
    if(c.window()!=null)return c.window().deadline();
    if(c.family()!=null&&c.family().revealCost()!=null&&c.family().revealCost().pending()!=null)return c.family().revealCost().pending().deadline();
    if(c.pending()!=null)return c.pending().deadline();
    if(c.family()!=null && c.family().response()!=null && c.family().response().pending()!=null)return c.family().response().pending().deadlineMillis();
    if(c.family()!=null && c.family().damage()!=null && c.family().damage().pending()!=null)return c.family().damage().pending().deadlineMillis();
    if(c.family()!=null&&c.family().ganglie()!=null&&c.family().ganglie().pending()!=null)return c.family().ganglie().pending().deadlineMillis();
    throw new IllegalStateException("trick has no pending boundary");
  }
  public TrickCheckpoint{
    Objects.requireNonNull(state);Objects.requireNonNull(continuation);var memory=new TreeMap<String,Map<String,Long>>();
    skillMemory.forEach((key,value)->memory.put(key,Map.copyOf(value)));skillMemory=Collections.unmodifiableMap(memory);bindings=List.copyOf(bindings);activations=List.copyOf(activations);
  }
  public void validate(){
    state.validate();var c=continuation;int n=state.players().size();
    if((version<1 || version>VERSION) || state.turn().status()!=GameStatus.RUNNING || state.turn().phase()!=Phase.PLAY || responseTimeoutSeconds<1 || responseTimeoutSeconds>3600
        || promptSequence<1 || promptSequence==Long.MAX_VALUE || capturedAtMillis<0 || skillSequence<0 || skillSequence==Long.MAX_VALUE || skillMemory.size()>SkillMemoryLayout.MAX_RECORDS || bindings.size()>256 || activations.size()>32
        || c.version()!=version || c.revision()<1 || c.revision()==Long.MAX_VALUE || c.cardId()<1 || c.cardId()==Long.MAX_VALUE || c.source()!=state.turn().currentSeat()
        || (c.effectiveType()==null || !SgsGameEngine.resumableTrickType(c.effectiveType())) || c.step()==null || c.depth()<0 || c.depth()>160 || c.counters().size()>160)
      throw new IllegalArgumentException("invalid trick checkpoint header");
    seat(c.source(),n);seat(c.target(),n);
    if(c.effectiveType()==CardType.WUZHONG && c.target()!=c.source() || Set.of(CardType.GUOHE,CardType.SHUNSHOU,CardType.JUEDOU,CardType.NANMAN,CardType.WANJIAN).contains(c.effectiveType())&&c.source()==c.target())throw new IllegalArgumentException("invalid trick target");
    var family=c.family();
    if(family!=null){
      var program=family.program();
      if(version<7&&program!=null||version>=7&&(family.damage()!=null)!=(program!=null))throw new IllegalArgumentException("missing or unexpected shared damage program");
      if(program!=null){
        DamageProgramContinuation.validate(program);
        if(program.awaitRescueCosts()!=(version>=12))throw new IllegalArgumentException("trick rescue capability version differs");
        var retained=new HashSet<Long>();retained.add(c.cardId());c.counters().forEach(v->retained.add(v.cardId()));retained.addAll(family.harvest());RescueCostFrames.validate(state,bindings,program,retained,promptSequence);
        if(!program.ordered()||!Objects.equals(program.damage(),family.damage())||!Objects.equals(program.distribution(),family.yiji())||!Objects.equals(program.reaction(),family.ganglie())||program.taken()!=family.feedbackTaken()
            ||!program.parents().stream().map(p->new TrickContinuation.DamageParentSnapshot(p.damage(),p.reaction())).toList().equals(family.parents()))throw new IllegalArgumentException("shared damage projection differs");
        boolean ownsRequest=c.pending()!=null&&(program.rescueCost()!=null||c.step()==TrickContinuation.Step.DAMAGE_SKILL&&Set.of(PromptType.SKILL_INVOKE,PromptType.YIJI_GIVE).contains(c.pending().type())&&c.pending().skill().equals("yiji")||c.step()==TrickContinuation.Step.DAMAGE_FANKUI&&c.pending().type()==PromptType.FANKUI_TAKE);
        if((program.pending()!=null||ownsRequest)&&!Objects.equals(program.pending(),c.pending()))throw new IllegalArgumentException("shared damage request projection differs");
      }
    }
    if(version==1){if(family!=null || !Set.of(CardType.WUZHONG,CardType.GUOHE,CardType.SHUNSHOU).contains(c.effectiveType()))throw new IllegalArgumentException("invalid legacy trick");}
    else validateFamily(c,n);
    if(c.effectiveType()==CardType.TIESUO && (version<8 || family==null || family.targets().size()>2 || family.response()!=null || family.damage()!=null || !Set.of(TrickContinuation.Step.PRIMARY_COMMIT,TrickContinuation.Step.COUNTER,TrickContinuation.Step.COUNTER_COMMIT).contains(c.step()) && c.movements().isEmpty()))throw new IllegalArgumentException("invalid chain target program");
    int dying=family!=null&&family.damage()!=null&&!family.damage().damageFrames().isEmpty()?family.damage().damageFrames().getFirst().targetSeat():-1;
    int allowedDying=StrikeFrames.dying(c,dying);
    if(state.players().stream().anyMatch(p->p.alive()&&p.hp()<1&&p.seat()!=allowedDying))throw new IllegalArgumentException("unexpected dying inside trick");
    skillMemory.forEach((key,fields)->SkillMemoryLayout.validate(key,fields,n,false));
    if(new HashSet<>(bindings).size()!=bindings.size())throw new IllegalArgumentException("duplicate trick bindings");bindings.forEach(s->StateCheckpoint.text(s,256));
    long last=0;for(var a:activations){if(a.sequence()<=last || a.sequence()>skillSequence)throw new IllegalArgumentException("invalid trick activation history");seat(a.seat(),n);StateCheckpoint.text(a.skillId(),128);last=a.sequence();}
    uniqueSeats(c.scopePassed(),n);var costs=new HashSet<Long>();costs.add(c.cardId());for(var cost:c.counters()){seat(cost.seat(),n);if(cost.cardId()<1 || !costs.add(cost.cardId()))throw new IllegalArgumentException("duplicate trick physical cost");}
    if(c.movements().size()>512)throw new IllegalArgumentException("too many trick movements");
    long previousMove=0;var allCards=state.zones().stream().flatMap(z->z.cards().stream()).map(StateCheckpoint.Card::id).collect(java.util.stream.Collectors.toSet());
    for(var m:c.movements()){if(m.sequence()<=previousMove || m.sequence()>state.movementSequence() || m.cards().isEmpty() || m.cards().size()>4096 || new HashSet<>(m.cards()).size()!=m.cards().size() || !allCards.containsAll(m.cards()) || m.actor() < -1 || m.actor()>=n)throw new IllegalArgumentException("invalid trick movement");previousMove=m.sequence();}
    RevealCostFrames.validate(this);
    StrikeFrames.validate(this);
    int boundaries=(StrikeFrames.pending(c)==null?0:1)+(family!=null&&family.revealCost()!=null&&family.revealCost().pending()!=null?1:0)+(family!=null&&family.ganglie()!=null&&family.ganglie().pending()!=null?1:0)+(c.window()==null?0:1)+(c.pending()==null?0:1)+(family!=null&&family.response()!=null&&family.response().pending()!=null?1:0)+(family!=null&&family.damage()!=null&&family.damage().pending()!=null?1:0);
    if(boundaries!=1)throw new IllegalArgumentException("trick must have exactly one pending boundary");
    if(c.window()!=null){var w=c.window();
      if(c.step()!=TrickContinuation.Step.COUNTER || c.counters().size()!=c.depth() || w.id()<1 || w.id()>promptSequence || w.subject()!=c.target() || w.depth()!=c.depth() || w.tokens().isEmpty() || w.tokens().size()>n || w.pending().isEmpty())throw new IllegalArgumentException("invalid trick counter window");
      time(w.deadline());uniqueSeats(w.pending(),n);uniqueSeats(w.passed(),n);if(!Collections.disjoint(w.pending(),w.passed()))throw new IllegalArgumentException("passed actor is pending");
      var seats=new HashSet<Integer>();long previous=w.id();for(var token:w.tokens()){seat(token.seat(),n);if(!seats.add(token.seat()) || token.id()!=previous+1 || token.id()>promptSequence)throw new IllegalArgumentException("invalid trick private token");previous=token.id();}
      if(previous!=promptSequence || !seats.containsAll(w.pending()) || !seats.containsAll(w.passed()) || !Collections.disjoint(seats,c.scopePassed()))throw new IllegalArgumentException("invalid trick invitation progress");
    }else if(c.pending()!=null){var p=c.pending();seat(p.asker(),n);seat(p.subject(),n);time(p.deadline());
      if(p.id()!=promptSequence || p.revision()!=c.revision() || p.choices().size()>160 || new HashSet<>(p.choices()).size()!=p.choices().size())throw new IllegalArgumentException("invalid trick request");
      if(!p.skill().isEmpty())StateCheckpoint.text(p.skill(),128);
      if(family!=null&&family.program()!=null&&family.program().rescueCost()!=null){
        if(c.step()!=TrickContinuation.Step.DAMAGE||!Objects.equals(p,family.program().pending()))throw new IllegalArgumentException("rescue request projection differs");
      }else if(p.type()==PromptType.FANKUI_TAKE){
        var d=family==null?null:family.damage();
        if(version<4 || d==null || c.step()!=TrickContinuation.Step.DAMAGE_FANKUI || p.asker()!=d.damageFrames().getFirst().targetSeat() || p.subject()!=d.sourceSeat() || !p.skill().equals("fankui"))throw new IllegalArgumentException("invalid fankui request");
      }else if(p.skill().equals("yiji")){
        var y=family==null?null:family.yiji();
        if(y==null || c.step()!=TrickContinuation.Step.DAMAGE_SKILL || p.asker()!=y.owner() || p.subject()!=y.owner() || p.hidden()
            || p.type()!=(y.step()==YiJiContinuation.Step.INVOKE?PromptType.SKILL_INVOKE:PromptType.YIJI_GIVE) || !p.choices().equals(y.retained()))throw new IllegalArgumentException("invalid yiji request");
      }else if(p.type()==PromptType.SKILL_INVOKE && (family!=null&&family.movementProgram()!=null || !p.skill().equals("jizhi"))){
        if(c.movements().isEmpty() || p.asker()!=p.subject() || !p.choices().isEmpty() || p.hidden()
            ||version<11&&(!Set.of("lianying","xiaoji").contains(p.skill())||p.asker()!=c.movements().getFirst().from().seat())
            ||version>=11&&(family==null||family.movementProgram()==null))throw new IllegalArgumentException("invalid movement hook request");
      }else if(p.type()==PromptType.SKILL_INVOKE){
        if((c.step()!=TrickContinuation.Step.PRIMARY_COMMIT && c.step()!=TrickContinuation.Step.COUNTER_COMMIT) || !p.skill().equals("jizhi") || !p.choices().isEmpty() || p.hidden() || p.asker()!=p.subject() || c.counters().size()!=c.depth())throw new IllegalArgumentException("invalid trick hook request");
        if(c.step()==TrickContinuation.Step.PRIMARY_COMMIT && (c.depth()!=0 || !c.counters().isEmpty()) || c.step()==TrickContinuation.Step.COUNTER_COMMIT && c.counters().isEmpty())throw new IllegalArgumentException("invalid trick commit progress");
        int actor=c.step()==TrickContinuation.Step.PRIMARY_COMMIT?c.source():c.counters().isEmpty()?-1:c.counters().getLast().seat();
        if(actor!=p.asker())throw new IllegalArgumentException("trick hook actor mismatch");
      }else if(p.type()==PromptType.WUGU_PICK){
        if(family==null || (c.effectiveType()!=CardType.WUGU&&c.effectiveType()!=CardType.ZHULU) || c.step()!=TrickContinuation.Step.CHOICE || p.asker()!=c.target() || p.subject()!=c.source() || !p.choices().equals(family.harvest()) || p.choices().isEmpty() || p.hidden() || !p.skill().equals(c.effectiveType()==CardType.ZHULU?"zhulu":"") || !c.counters().isEmpty() || c.depth()%2!=0)throw new IllegalArgumentException("invalid harvest choice");
      }else if(p.type()==PromptType.CARD_ZONE_SELECT){
        if(c.step()!=TrickContinuation.Step.CHOICE || !c.counters().isEmpty() || c.depth()%2!=0 || !Set.of(CardType.GUOHE,CardType.SHUNSHOU).contains(c.effectiveType()) || p.asker()!=c.source() || p.subject()!=c.target()
            || !p.skill().equals(c.effectiveType()==CardType.GUOHE?"guohe":"shunshou"))throw new IllegalArgumentException("invalid trick zone request");
      }else throw new IllegalArgumentException("unsupported trick request kind");
    }
  }
  private void validateFamily(TrickContinuation.Snapshot c,int n){
    var f=c.family();if(f==null || f.targets().isEmpty() || f.cursor()<0 || f.cursor()>=f.targets().size() || f.targets().get(f.cursor())!=c.target() || f.harvest().size()>8 || new HashSet<>(f.harvest()).size()!=f.harvest().size() || f.harvest().stream().anyMatch(id->id<=0))throw new IllegalArgumentException("invalid trick target cursor");
    boolean movementInput=version>=11?f.movementProgram()!=null:c.pending()!=null&&Set.of("lianying","xiaoji").contains(c.pending().skill());
    if(version<11&&f.movementProgram()!=null||version>=11&&f.movementPrepared())throw new IllegalArgumentException("movement program version mismatch");
    if(f.movementProgram()!=null){
      var program=f.movementProgram();var p=c.pending();
      if(c.movements().isEmpty()||p==null||p.type()!=PromptType.SKILL_INVOKE||program.awaiting()==null||program.done()
          ||p.asker()!=program.awaiting().owner()||p.subject()!=p.asker()||!p.skill().equals(program.awaiting().skill()))
        throw new IllegalArgumentException("movement listener request mismatch");
      var m=c.movements().getFirst();var expected=new dev.sgs.core.engine.skill.MovementListenerProgram.Event(m.sequence(),m.from(),m.to(),m.cards(),m.reason(),m.actor(),m.publicCards(),state.turn().turnNumber(),state.turn().currentSeat());
      if(!program.event().equals(expected))throw new IllegalArgumentException("movement listener event mismatch");
    }
    if(version<6&&(f.movementPrepared()||f.feedbackTaken()||f.penaltyLogPending()||c.step()==TrickContinuation.Step.DAMAGE_FANKUI_FINISH))throw new IllegalArgumentException("v6 fields in legacy frame");
    if(f.penaltyLogPending()&&(!movementInput||c.step()!=TrickContinuation.Step.TARGET_NEXT))throw new IllegalArgumentException("invalid delayed penalty log");
    if(f.movementPrepared()&&!movementInput)throw new IllegalArgumentException("prepared movement has no prompt");
    if(f.feedbackTaken()!=(c.step()==TrickContinuation.Step.DAMAGE_FANKUI_FINISH)||f.feedbackTaken()&&(!movementInput||f.damage()==null))throw new IllegalArgumentException("invalid feedback completion");
    if(version<5&&(f.ganglie()!=null||!f.parents().isEmpty()||f.damage()!=null&&f.damage().cardless()||c.step()==TrickContinuation.Step.DAMAGE_GANGLIE))throw new IllegalArgumentException("v5 fields in legacy frame");
    if((c.step()==TrickContinuation.Step.DAMAGE_GANGLIE)!=(f.ganglie()!=null)||f.ganglie()!=null&&f.damage()==null||f.parents().size()>64||!f.parents().isEmpty()&&f.damage()==null)throw new IllegalArgumentException("invalid ganglie parents");
    if(version<3 && (f.yiji()!=null || f.response()!=null&&f.response().judgmentCard()!=0))throw new IllegalArgumentException("new fields in legacy trick frame");
    if(version<4 && (c.step()==TrickContinuation.Step.DAMAGE_FANKUI || f.response()!=null&&f.response().judgment()!=null))throw new IllegalArgumentException("v4 fields in legacy frame");
    if((c.step()==TrickContinuation.Step.DAMAGE_SKILL)!=(f.yiji()!=null))throw new IllegalArgumentException("missing deferred damage hook");
    if(f.yiji()!=null){var y=f.yiji();
      if(version<3 || f.damage()==null || f.response()!=null || c.pending()==null || y.owner()!=f.damage().damageFrames().getFirst().targetSeat() || y.amount()<1 || y.amount()!=f.damage().damageFrames().getFirst().amount() || y.point()<0 || y.point()>=y.amount() || !Set.of(YiJiContinuation.Step.INVOKE,YiJiContinuation.Step.GIVE).contains(y.step()) || y.retained().size()>2 || new HashSet<>(y.retained()).size()!=y.retained().size() || y.retained().stream().anyMatch(id->id<1) || (y.step()==YiJiContinuation.Step.INVOKE?!y.retained().isEmpty():y.retained().isEmpty()))throw new IllegalArgumentException("invalid yiji program");
    }
    uniqueSeats(f.targets(),n);int previous=-1;for(int target:f.targets()){int position=Math.floorMod(target-c.source(),n);if(position<=previous)throw new IllegalArgumentException("invalid trick target order");previous=position;}
    if(Set.of(CardType.JIEJIA,CardType.GUOHE,CardType.SHUNSHOU,CardType.WUZHONG,CardType.JUEDOU,CardType.HUOGONG,CardType.JIEDAO).contains(c.effectiveType()) && (f.targets().size()!=1 || f.cursor()!=0))throw new IllegalArgumentException("single trick has multiple targets");
    if(c.effectiveType()!=CardType.WUGU && c.effectiveType()!=CardType.ZHULU && (f.revealed()||!f.harvest().isEmpty()) || !f.revealed()&&!f.harvest().isEmpty())throw new IllegalArgumentException("unexpected harvest pool");
    if(c.effectiveType()==CardType.JUEDOU){
      if((f.duelResponder()<0)!=(f.duelOther()<0))throw new IllegalArgumentException("partial duel actors");
      if(f.duelResponder()>=0){seat(f.duelResponder(),n);seat(f.duelOther(),n);if(f.duelResponder()==f.duelOther() || !Set.of(c.source(),c.target()).equals(Set.of(f.duelResponder(),f.duelOther())))throw new IllegalArgumentException("invalid duel actors");}
      if(f.duelRequired()<0||f.duelRequired()>8||f.duelPaid()<0||(f.duelRequired()==0?f.duelPaid()!=0:f.duelPaid()>=f.duelRequired()))throw new IllegalArgumentException("invalid duel payment cursor");
    }else if(f.duelResponder()!=-1||f.duelOther()!=-1||f.duelRequired()!=0||f.duelPaid()!=0)throw new IllegalArgumentException("unexpected duel cursor");
    if(f.response()!=null){
      var r=f.response();boolean completedMovement=version>=6&&movementInput&&r.step()==TrickResponseContinuation.Step.DONE&&r.pending()==null;
      if(c.step()!=TrickContinuation.Step.RESPONSE || !completedMovement&&(r.pending()==null||r.success()||r.supplied()) || r.revision()<1 || f.damage()!=null)throw new IllegalArgumentException("invalid response program");
      seat(r.actor(),n);seat(r.subject(),n);uniqueSeats(r.providers(),n);if(r.cursor()<0||r.cursor()>r.providers().size())throw new IllegalArgumentException("invalid provider cursor");
      PromptType expected=c.effectiveType()==CardType.JUEDOU?PromptType.DUEL_SHA:c.effectiveType()==CardType.NANMAN?PromptType.RESPOND_SHA_NANMAN:c.effectiveType()==CardType.WANJIAN?PromptType.DODGE_SHA:null;
      if(r.type()!=expected || r.actor()!=(c.effectiveType()==CardType.JUEDOU?f.duelResponder():c.target()) || r.subject()!=(c.effectiveType()==CardType.JUEDOU?f.duelOther():c.source()))throw new IllegalArgumentException("response actors mismatch");
      if(!completedMovement)checkPrompt(r.pending(),n);if(!completedMovement&&r.pending().stateRevision()!=r.revision())throw new IllegalArgumentException("response revision mismatch");
      if(r.own()!=null){checkPrompt(r.own(),n);if(r.own().askerSeat()!=r.actor()||r.own().subjectSeat()!=r.subject()||r.own().type()!=r.type())throw new IllegalArgumentException("invalid preallocated response");}
      boolean newJudgment=Set.of(TrickResponseContinuation.Step.OWN_JUDGMENT,TrickResponseContinuation.Step.PROVIDER_JUDGMENT).contains(r.step());
      if(newJudgment!=(r.judgment()!=null))throw new IllegalArgumentException("missing judgment program");
      if(r.judgment()!=null){var j=r.judgment();
        seat(j.owner(),n);uniqueSeats(j.modifiers(),n);
        if(j.cause()<1 || j.revealed()<1 || j.effective()!=r.judgmentCard() || j.cursor()<0 || j.cursor()>j.modifiers().size() || !Objects.equals(j.pending(),r.pending()) || !Set.of(JudgmentContinuation.Step.MODIFY,JudgmentContinuation.Step.TIANDU).contains(j.step()) || j.step()==JudgmentContinuation.Step.MODIFY && (j.cursor()>=j.modifiers().size() || r.pending().askerSeat()!=j.modifiers().get(j.cursor()) || r.pending().type()!=PromptType.GUI_CAI_REPLACE || !r.pending().skillId().equals("guicai")) || j.step()==JudgmentContinuation.Step.TIANDU && (j.cursor()!=j.modifiers().size() || r.pending().askerSeat()!=j.owner() || r.pending().type()!=PromptType.SKILL_INVOKE || !r.pending().skillId().equals("tiandu")))throw new IllegalArgumentException("invalid judgment progress");
      }
      boolean tiandu=newJudgment || Set.of(TrickResponseContinuation.Step.OWN_TIANDU,TrickResponseContinuation.Step.PROVIDER_TIANDU).contains(r.step());
      if(tiandu!=(r.judgmentCard()>0) || r.judgmentCard()<0)throw new IllegalArgumentException("invalid pending judgment entity");
    }
    if(f.damage()!=null){
      if(c.effectiveType()==CardType.HUOGONG||f.program()!=null&&f.program().rescueCost()!=null){DamageProgramFrames.validate(this,c.effectiveType()==CardType.HUOGONG?DamageNature.FIRE:DamageNature.NORMAL);return;}
      var d=f.damage();boolean legacyRescue=DamageProgramFrames.legacyRescueMovement(this);boolean fankui=Set.of(TrickContinuation.Step.DAMAGE_FANKUI,TrickContinuation.Step.DAMAGE_FANKUI_FINISH).contains(c.step());boolean gap=version>=7&&movementInput&&f.program()!=null&&f.program().step()==DamageProgramContinuation.Step.DAMAGE&&d.damageFrames().size()==1&&d.damageFrames().getFirst().step()==AttackContinuation.DamageStep.AFTER_DEFERRED;boolean yielded=f.yiji()!=null||fankui||f.ganglie()!=null||gap||legacyRescue;
      if(c.step()!=(f.ganglie()!=null?TrickContinuation.Step.DAMAGE_GANGLIE:fankui?c.step():f.yiji()!=null?TrickContinuation.Step.DAMAGE_SKILL:TrickContinuation.Step.DAMAGE) || d.version()!=(d.cardless()?4:3) || d.step()!=AttackContinuation.AttackStep.DAMAGE || (d.pending()==null)!=yielded || d.cardId()!=c.cardId() || d.effectiveType()!=c.effectiveType() || d.baseDamage()!=1 || d.damageFrames().size()!=1 || !d.providers().isEmpty()||d.providerCursor()!=0||d.weaponCost()!=0||!d.parents().isEmpty()||!d.extraCosts().isEmpty()||!d.parentSizes().isEmpty()||!d.targets().equals(List.of(d.targetSeat()))||d.targetCursor()!=0)throw new IllegalArgumentException("invalid nested damage");
      seat(d.sourceSeat(),n);seat(d.targetSeat(),n);int expectedSource=c.effectiveType()==CardType.JUEDOU?f.duelOther():c.source(),expectedTarget=c.effectiveType()==CardType.JUEDOU?f.duelResponder():c.target();boolean cardless=false;
      for(var parent:f.parents().reversed()){
        var pd=parent.damage();var g=parent.ganglie();
        if(pd==null||g==null||pd.sourceSeat()!=expectedSource||pd.targetSeat()!=expectedTarget||pd.cardless()!=cardless||pd.version()!=(cardless?4:3)||pd.step()!=AttackContinuation.AttackStep.DAMAGE||pd.cardId()!=c.cardId()||pd.effectiveType()!=c.effectiveType()||pd.baseDamage()!=1||pd.pending()!=null||pd.damageFrames().size()!=1||!pd.providers().isEmpty()||pd.providerCursor()!=0||pd.weaponCost()!=0||!pd.parents().isEmpty()||!pd.extraCosts().isEmpty()||!pd.parentSizes().isEmpty()||!pd.targets().equals(List.of(pd.targetSeat()))||pd.targetCursor()!=0)throw new IllegalArgumentException("invalid suspended damage parent");
        var pf=pd.damageFrames().getFirst();if(pf.targetSeat()!=expectedTarget||pf.step()!=AttackContinuation.DamageStep.AFTER_DEFERRED||pf.amount()<1||pf.amount()>1000000||pf.nature()!=DamageNature.NORMAL||pf.chainDamage())throw new IllegalArgumentException("invalid suspended damage state");
        validateGanglie(g,pd,n,true);expectedSource=g.owner();expectedTarget=g.source();cardless=true;
      }
      if(d.sourceSeat()!=expectedSource||d.targetSeat()!=expectedTarget||d.cardless()!=cardless)throw new IllegalArgumentException("damage actors mismatch");
      if(f.ganglie()!=null)validateGanglie(f.ganglie(),d,n,false);
      var damage=d.damageFrames().getFirst();if(damage.targetSeat()!=d.targetSeat()||damage.amount()<1||damage.amount()>1000000||damage.chainDamage()||damage.nature()!=DamageNature.NORMAL||damage.rescueCursor()<0||damage.rescueCursor()>damage.rescueRing().size() || yielded&&!legacyRescue&&damage.step()!=AttackContinuation.DamageStep.AFTER_DEFERRED)throw new IllegalArgumentException("invalid nested damage state");uniqueSeats(damage.rescueRing(),n);
      if(!yielded){checkPrompt(d.pending(),n);if(d.pending().id()!=promptSequence||d.pending().stateRevision()!=d.revision())throw new IllegalArgumentException("damage request mismatch");}
    }
  }
  void validateGanglie(GangLieContinuation.Snapshot g,AttackContinuation.Snapshot d,int n,boolean suspended){
    seat(g.owner(),n);seat(g.source(),n);
    if(g.owner()!=d.damageFrames().getFirst().targetSeat()||g.source()!=d.sourceSeat()||g.owner()==g.source()||g.revision()<1||g.revision()==Long.MAX_VALUE||!state.players().get(g.owner()).hero().skillIds().contains("ganglie")||g.costs().size()>(g.step()==GangLieContinuation.Step.DONE?2:1)||new HashSet<>(g.costs()).size()!=g.costs().size())throw new IllegalArgumentException("invalid ganglie owner");
    if(suspended){if(g.step()!=GangLieContinuation.Step.WAIT_DAMAGE||!g.accepted()||g.pending()!=null||g.judgment()!=null||!g.costs().isEmpty())throw new IllegalArgumentException("invalid suspended ganglie");return;}
    if(g.step()==GangLieContinuation.Step.DONE){
      if(version<6||g.pending()!=null||g.judgment()!=null||!g.accepted()||g.costs().size()!=2||continuation.pending()==null||!continuation.pending().skill().equals("lianying")||continuation.pending().asker()!=g.source())throw new IllegalArgumentException("invalid completed ganglie movement");return;
    }
    var p=g.pending();if(p==null)throw new IllegalArgumentException("missing ganglie boundary");checkPrompt(p,n);
    if(p.id()!=promptSequence||p.stateRevision()!=g.revision()||!p.allowedTypes().isEmpty())throw new IllegalArgumentException("ganglie request mismatch");
    switch(g.step()){
      case INVOKE->{if(g.accepted()||!g.costs().isEmpty()||g.judgment()!=null||p.type()!=PromptType.SKILL_INVOKE||p.askerSeat()!=g.owner()||p.subjectSeat()!=g.source()||!p.skillId().equals("ganglie"))throw new IllegalArgumentException("invalid ganglie invoke");}
      case COST->{if(!g.accepted()||g.judgment()!=null||p.type()!=PromptType.GANGLIE_DISCARD||p.askerSeat()!=g.source()||p.subjectSeat()!=g.owner()||!p.skillId().equals("ganglie"))throw new IllegalArgumentException("invalid ganglie cost");}
      case JUDGE->{var j=g.judgment();if(!g.accepted()||!g.costs().isEmpty()||j==null||j.owner()!=g.owner()||j.cause()!=0||j.revealed()<1||j.effective()<1||!Objects.equals(p,j.pending())||j.step()!=JudgmentContinuation.Step.MODIFY||j.cursor()<0||j.cursor()>=j.modifiers().size()||p.type()!=PromptType.GUI_CAI_REPLACE||p.askerSeat()!=j.modifiers().get(j.cursor())||p.subjectSeat()!=g.owner()||!p.skillId().equals("guicai"))throw new IllegalArgumentException("invalid ganglie judgment");uniqueSeats(j.modifiers(),n);}
      default->throw new IllegalArgumentException("ganglie is not pending");
    }
  }
  void checkPrompt(AttackContinuation.PendingSnapshot p,int n){seat(p.askerSeat(),n);seat(p.subjectSeat(),n);time(p.deadlineMillis());if(!p.skillId().isEmpty())StateCheckpoint.text(p.skillId(),128);if(p.id()<1||p.id()>promptSequence||p.type()==null||p.stateRevision()<0||p.allowedTypes().size()>8)throw new IllegalArgumentException("invalid nested request");}
  private void time(long deadline){if(deadline<0 || deadline-capturedAtMillis>responseTimeoutSeconds*1000L)throw new IllegalArgumentException("invalid trick remaining time");}
  private static void seat(int seat,int n){if(seat<0 || seat>=n)throw new IllegalArgumentException("invalid trick seat");}
  private static void uniqueSeats(List<Integer> seats,int n){if(seats.size()>n || new HashSet<>(seats).size()!=seats.size())throw new IllegalArgumentException("invalid trick seat set");seats.forEach(s->seat(s,n));}
}
