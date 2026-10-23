package dev.sgs.core.engine.skill.skills;

import dev.sgs.core.*;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.*;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.engine.skill.equipment.EquipmentDamageRules;
import dev.sgs.core.game.GameState;
import dev.sgs.core.hero.*;
import dev.sgs.core.player.SgsPlayer;
import java.util.*;
import java.util.function.*;

/** Original ordinary expansion rules. All physical costs pass through CardMovement. */
public final class MythicRules implements PersistentSkillMemory {
  private final SkillEngine api;
  private final SkillEventBus bus;
  private final BiConsumer<SgsPlayer,String> acquire;
  private final Map<String,Long> memory=new TreeMap<>();
  private final Map<Integer,Set<String>> acquired=new HashMap<>();
  private final Map<Integer,Set<String>> bound=new HashMap<>();
  private record AcquiredBinding(int seat,String skill){}
  private final List<AcquiredBinding> boundHistory=new ArrayList<>();
  private final Map<Integer,List<String>> incarnations=new HashMap<>();
  private final Set<Integer> busy=new HashSet<>();
  private final Map<Integer,List<CardInstance>> discarded=new HashMap<>();
  private final Map<Integer,Set<Long>> guzhengReturns=new HashMap<>();
  private final Map<Integer,String> borrowed=new HashMap<>();
  public MythicRules(SkillEngine api,SkillEventBus bus,BiConsumer<SgsPlayer,String> acquire){this.api=api;this.bus=bus;this.acquire=acquire;}
  private GameState g(){return api.gameState();}
  private long get(SgsPlayer p,String key){return memory.getOrDefault(p.seat()+":"+key,0L);}
  private void put(SgsPlayer p,String key,long value){memory.put(p.seat()+":"+key,value);}
  public boolean has(SgsPlayer p,String id){
    if(p!=null&&p.hero()!=null&&get(p,"silenced")==0){
      if(id.equals("huangtian_give"))return kingdom(p)==Kingdom.QUN&&lordWith(p,"huangtian")!=null;
      if(id.equals("zhiba_pindian"))return kingdom(p)==Kingdom.WU&&lordWith(p,"zhiba")!=null;
    }
    return p!=null && p.hero()!=null && get(p,"silenced")==0 && (p.hero().skillIds().contains(id)||acquired.getOrDefault(p.seat(),Set.of()).contains(id));
  }
  public boolean enabled(SgsPlayer p,String id){
    if(p!=null&&bound.getOrDefault(p.seat(),Set.of()).contains(id)&&!has(p,id))return false;
    return p==null || get(p,"silenced")==0 || !(p.hero()!=null && p.hero().skillIds().contains(id)||acquired.getOrDefault(p.seat(),Set.of()).contains(id));
  }
  public List<String> effectiveSkills(SgsPlayer p){
    if(p==null||p.hero()==null)return List.of();
    var result=new LinkedHashSet<>(p.hero().skillIds());var learned=acquired.getOrDefault(p.seat(),Set.of());
    boundHistory.stream().filter(b->b.seat()==p.seat()&&learned.contains(b.skill())).map(AcquiredBinding::skill).forEach(result::add);
    if(has(p,"huangtian_give"))result.add("huangtian_give");if(has(p,"zhiba_pindian"))result.add("zhiba_pindian");
    if(get(p,"silenced")!=0)result.clear();return List.copyOf(result);
  }
  private SgsPlayer lordWith(SgsPlayer actor,String id){return g().players().stream().filter(p->p!=actor&&p.alive()&&p.identity()==Identity.LORD&&get(p,"silenced")==0&&p.hero().skillIds().contains(id)).findFirst().orElse(null);}
  private void gain(SgsPlayer p,String id){if(has(p,id))return;acquired.computeIfAbsent(p.seat(),ignored->new LinkedHashSet<>()).add(id);if(bound.computeIfAbsent(p.seat(),ignored->new HashSet<>()).add(id)){boundHistory.add(new AcquiredBinding(p.seat(),id));acquire.accept(p,id);}g().publishSkillActivation(p.seat(),id);}
  public boolean faceDown(SgsPlayer p){return get(p,"faceDown")!=0;}
  /** SDK skills can flip standard characters even when no built-in Mythic hero is seated. */
  public boolean hasSharedFaceState(){return memory.keySet().stream().anyMatch(key->key.endsWith(":faceDown"));}
  public List<dev.sgs.core.match.dto.MatchViews.StatusView> statuses(SgsPlayer p){
    var result=new ArrayList<dev.sgs.core.match.dto.MatchViews.StatusView>();
    if(faceDown(p))result.add(new dev.sgs.core.match.dto.MatchViews.StatusView("turned_over","翻面","下个回合翻回正面并跳过该回合"));
    if(get(p,"silenced")!=0)result.add(new dev.sgs.core.match.dto.MatchViews.StatusView("duanchang","断肠","武将技能已失去"));
    // Public limited-use marks, derived from authoritative persistent memory (never client guesses).
    // Status label is a stable state token; clients paint the small "限" seal instead of status text.
    for(var id:List.of("luanwu","niepan"))if(p.hero().skillIds().contains(id)||acquired.getOrDefault(p.seat(),Set.of()).contains(id)){
      boolean spent=get(p,"limited_"+id)!=0;
      boolean ready=!spent && p.alive() && has(p,id) && g().status()==GameStatus.RUNNING
          && (id.equals("niepan")?p.hp()<=0:g().currentPlayer()==p && g().phase()==Phase.PLAY);
      var presentation=dev.sgs.core.addon.AddonRuntime.current().skill(id).orElseThrow();
      result.add(new dev.sgs.core.match.dto.MatchViews.StatusView("limited:"+id,spent?"SPENT":ready?"READY":"BLOCKED",
          presentation.description()));
    }
    if(has(p,"tuntian")&&!pile(p).isEmpty())result.add(new dev.sgs.core.match.dto.MatchViews.StatusView("tuntian","田×"+pile(p).size(),"计算与其他角色距离-"+pile(p).size()));
    if(has(p,"buqu")&&!pile(p).isEmpty())result.add(new dev.sgs.core.match.dto.MatchViews.StatusView("buqu","不屈×"+pile(p).size(),"点数："+String.join("、",pile(p).stream().map(c->c.rank().label()).toList())));
    if(has(p,"tianyi")&&g().currentPlayer()==p&&get(p,"tianyiTurn")==g().turnNumber())result.add(new dev.sgs.core.match.dto.MatchViews.StatusView("tianyi",get(p,"tianyiWin")==1?"天义":"禁杀",get(p,"tianyiWin")==1?"杀次数及目标+1，无距离限制":"本回合不能使用杀"));
    for(var id:List.of("zaoxian","zhiji","ruoyu","hunzi"))if(get(p,"awake_"+id)!=0)result.add(new dev.sgs.core.match.dto.MatchViews.StatusView(id,"已觉醒",dev.sgs.core.addon.AddonRuntime.current().skill(id).orElseThrow().name()+"已觉醒"));
    if(handLimit(p)!=Math.max(0,p.hp()))result.add(new dev.sgs.core.match.dto.MatchViews.StatusView("hand_limit","上限"+handLimit(p),"手牌上限："+handLimit(p)));
    return List.copyOf(result);
  }
  public void turnOver(SgsPlayer p){put(p,"faceDown",faceDown(p)?0:1);}
  public boolean active(){return g().players().stream().anyMatch(p->p.hero()!=null && MythicHeroes.IDS.contains(p.hero().id()));}
  public void initializeGame(){
    if(g().turnNumber()!=1||get(g().players().getFirst(),"initialized")!=0)return;
    put(g().players().getFirst(),"initialized",1);
    for(var p:g().players())if(has(p,"huashen")){addIncarnations(p,2);changeIncarnation(p);}
  }
  public int handLimit(SgsPlayer p){
    int n=Math.max(0,p.hp());
    if(has(p,"xueyi") && p.identity()==Identity.LORD)n+=2*(int)g().players().stream().filter(x->x!=p&&x.alive()&&kingdom(x)==Kingdom.QUN).count();
    return n;
  }
  public Kingdom kingdom(SgsPlayer p){
    var ids=incarnations.getOrDefault(p.seat(),List.of());long index=get(p,"incarnation");
    if(index>0 && index<=ids.size())return StandardHeroes.CATALOG.stream().filter(h->h.id().equals(ids.get((int)index-1))).findFirst().orElseThrow().kingdom();
    return p.hero().kingdom();
  }
  public Suit suit(SgsPlayer p,GameCard card){return has(p,"hongyan")&&card.suit()==Suit.SPADE?Suit.HEART:card.suit();}
  private boolean red(SgsPlayer p,GameCard c){var s=suit(p,c);return s==Suit.HEART||s==Suit.DIAMOND;}
  public GameCard effectiveCard(SgsPlayer p,GameCard card){
    if(card==null||!has(p,"hongyan")||card.sourceCards().stream().noneMatch(c->c.suit()==Suit.SPADE))return card;
    return new GameCard(){
      public CardType type(){return card.type();}public Suit suit(){return MythicRules.this.suit(p,card);}
      public Rank rank(){return card.rank();}public boolean isVirtual(){return card.isVirtual();}public boolean canRecast(){return card.canRecast();}
      public List<CardInstance> sourceCards(){return card.sourceCards();}
      public Color color(){boolean allRed=sourceCards().stream().allMatch(c->red(p,c));boolean allBlack=sourceCards().stream().allMatch(c->!red(p,c));return allRed?Color.RED:allBlack?Color.BLACK:Color.NONE;}
    };
  }
  private List<SgsPlayer> living(){return g().alivePlayersFromCurrent();}
  private List<CardInstance> owned(SgsPlayer p){var cards=new ArrayList<>(p.handView());cards.addAll(p.equipment().all());return cards;}
  private List<CardInstance> pile(SgsPlayer p){return g().cards().cards(CardZone.special(p.seat()));}
  private boolean invoke(SgsPlayer p,String id,SgsPlayer subject){return p.alive()&&api.requestSkillInvoke(p,id,subject);}
  private Optional<SkillChoice> choose(SgsPlayer p,String id,List<CardInstance> cards,List<SgsPlayer> targets){
    if(cards.isEmpty()&&targets.isEmpty())return Optional.empty();
    return api.requestSkillChoice(p,p,PromptType.EXPANSION_CHOICE,id,cards,targets.stream().map(SgsPlayer::seat).toList(),cards.isEmpty());
  }
  private SgsPlayer target(SgsPlayer p,String id,List<SgsPlayer> targets){
    var c=choose(p,id,List.of(),targets);return c.map(x->g().players().get(x.targetSeat())).orElse(null);
  }
  private CardInstance card(SgsPlayer p,String id,List<CardInstance> cards,boolean mandatory){
    if(cards.isEmpty())return null;
    var c=choose(p,id,cards,List.of());return c.flatMap(x->cards.stream().filter(v->v.instanceId()==x.cardId()).findFirst()).orElse(mandatory?cards.getFirst():null);
  }
  private int option(SgsPlayer p,String id,int count,int fallback){
    return api.requestSkillChoice(p,p,PromptType.EXPANSION_OPTION,id,List.of(),java.util.stream.IntStream.range(0,count).boxed().toList(),true)
        .map(SkillChoice::targetSeat).orElse(fallback);
  }
  private void move(CardInstance c,CardZone to,CardMove.Reason reason,SgsPlayer actor){
    var from=g().cards().locate(c.instanceId());if(from!=null&&!from.equals(to))g().cards().move(List.of(c),from,to,reason,actor.seat());
  }
  private void take(SgsPlayer actor,SgsPlayer subject,String id){
    var answer=api.requestSkillChoice(actor,subject,PromptType.CARD_ZONE_SELECT,id,subject.equipment().all(),List.of(),subject.handCount()>0);
    answer.ifPresent(c->api.takeFeedbackCard(actor,subject,c));
  }
  private void discard(SgsPlayer chooser,SgsPlayer subject,int count,String id,boolean equipment){
    for(int i=0;i<count && subject.alive();i++){
      var choices=equipment?owned(subject):subject.handView();if(choices.isEmpty())break;
      if(chooser==subject){var cost=card(subject,id,choices,true);api.discardCards(subject,List.of(cost));}
      else {
        var c=api.requestSkillChoice(chooser,subject,PromptType.CARD_ZONE_SELECT,id,subject.equipment().all(),List.of(),subject.handCount()>0);
        CardInstance chosen=null;
        if(c.isPresent()&&c.get().cardId()>0)chosen=subject.equipment().all().stream().filter(x->x.instanceId()==c.get().cardId()).findFirst().orElse(null);
        else if(c.isPresent()&&c.get().cardId()<=-2){
          int index=HandSelection.positionIndex(c.get().cardId(),subject.handCount());
          if(index>=0)chosen=subject.handView().get(index);
        }
        else if(subject.handCount()>0)chosen=subject.handView().get(g().random().nextInt(subject.handCount()));
        else if(!subject.equipment().all().isEmpty())chosen=subject.equipment().all().getFirst();
        if(chosen!=null)api.discardCards(subject,List.of(chosen));
      }
    }
  }
  /** Both choices are committed before either physical card is publicly revealed. */
  private boolean pindian(SgsPlayer from,SgsPlayer to,String id){
    var a=card(from,id,from.handView(),true);var b=card(to,id,to.handView(),true);if(a==null||b==null)return false;
    g().cards().moveBatch(List.of(new CardMovement.Transfer(List.of(a),CardZone.hand(from.seat()),CardZone.PROCESSING,CardMove.Reason.REVEAL,from.seat(),true),
        new CardMovement.Transfer(List.of(b),CardZone.hand(to.seat()),CardZone.PROCESSING,CardMove.Reason.REVEAL,to.seat(),true)));
    boolean won=a.rank().value()>b.rank().value();g().tableEffect("PINDIAN",from.seat(),to.seat(),id,List.of(a,b),won);g().log("pindian {} seat {}:{} seat {}:{}",id,from.seat(),a.rank().value(),to.seat(),b.rank().value());
    g().cards().finish(a,from.seat());g().cards().finish(b,to.seat());return won;
  }
  private void listen(SgsPlayer p,String id,Timing timing,Consumer<SkillContext> body){
    bus.register(new SkillListener(){
      public String id(){return id;}public SgsPlayer owner(){return p;}public Timing timing(){return timing;}
      public int priority(){return Priority.ACTIVE_CHOICE;}
      public boolean applies(SkillContext c){return has(p,id)&&(p.alive()||timing==Timing.DEATH)&&!busy.contains(p.seat())&&(timing!=Timing.CARD_TRANSFORM||transformable(p,id,c));}
      public boolean allowsTransformRecast(SkillContext c){return id.equals("lianhuan")&&applies(c);}
      public void execute(SkillContext c){body.accept(c);}
    });
  }
  private void rule(SgsPlayer p,String id,RuleId rule,Predicate<SkillContext> applies,IntBinaryOperator effect){
    bus.registerRule(new RuleModifier(){
      public String id(){return id;}public SgsPlayer owner(){return p;}public RuleId rule(){return rule;}public int priority(){return Priority.RULE_MODIFIER;}
      public boolean applies(SkillContext c){return p.alive()&&has(p,id)&&applies.test(c);}
      public int modifyInt(SkillContext c,int value){return effect.applyAsInt(value,c.target()==null?-1:c.target().seat());}
    });
  }
  public static final Set<String> ACTIVE=Set.of("quhu","tianyi","luanji","dimeng","luanwu","tiaoxin","zhijian","jixi","guhuo","huangtian_give","zhiba_pindian");
  public ActiveSkill active(SgsPlayer p,String id){return new ActiveSkill(){
    public String id(){return id;}public SgsPlayer owner(){return p;}
    public String unavailableReason(SkillEngine ignored){
      if(!has(p,id))return "SKILL_UNAVAILABLE";
      if(Set.of("quhu","tianyi","dimeng","tiaoxin","huangtian_give","zhiba_pindian").contains(id)&&get(p,"used_"+id)==g().turnNumber())return "SKILL_USED_THIS_TURN";
      if(id.equals("luanwu")&&get(p,"limited_"+id)!=0)return "SKILL_ALREADY_SPENT";
      return "";
    }
    public SkillResult preview(SkillEngine ignored,SgsPlayer actor,SgsPlayer target,List<CardInstance> cards){
      if(actor!=p||!has(p,id)||!unavailableReason(api).isEmpty())return SkillResult.reject("skill_unavailable");
      return previewSkill(p,id,target,cards);
    }
    public SkillResult activate(SkillEngine ignored,SgsPlayer actor,SgsPlayer target,List<CardInstance> cards){
      var check=preview(ignored,actor,target,cards);if(!check.accepted())return check;
      if(actor!=p||!has(p,id)||!unavailableReason(api).isEmpty())return SkillResult.reject("skill_unavailable");
      return activateSkill(p,id,target,cards);
    }
  };}
  private SkillResult previewSkill(SgsPlayer p,String id,SgsPlayer t,List<CardInstance> cards){
    boolean other=t!=null&&t!=p&&t.alive()&&g().players().contains(t);
    switch(id){
      case "huangtian_give"->{
        var lord=lordWith(p,"huangtian");if(lord==null||t!=lord||cards.size()!=1||(cards.getFirst().type()!=CardType.SHAN&&cards.getFirst().type()!=CardType.SHANDIAN))return SkillResult.reject("huangtian_lord_and_card_required");
      }
      case "zhiba_pindian"->{
        var lord=lordWith(p,"zhiba");if(lord==null||t!=lord||p.handCount()==0||lord.handCount()==0||!cards.isEmpty())return SkillResult.reject("zhiba_pindian_required");
      }
      case "quhu"->{
        if(!other||t.hp()<=p.hp()||p.handCount()==0||t.handCount()==0||!cards.isEmpty())return SkillResult.reject("quhu_target");
      }
      case "tianyi"->{
        if(!other||p.handCount()==0||t.handCount()==0||!cards.isEmpty())return SkillResult.reject("pindian_required");
      }
      case "luanji"->{
        if(cards.size()!=2||cards.stream().anyMatch(c->!CardZone.hand(p.seat()).equals(g().cards().locate(c.instanceId())))||suit(p,cards.get(0))!=suit(p,cards.get(1)))return SkillResult.reject("same_suit_two_hand_cards");
      }
      case "luanwu"->{
        if(!cards.isEmpty()||t!=null)return SkillResult.reject("no_cost_or_target");
      }
      case "tiaoxin"->{
        if(!other||!g().inBasicAttackRange(t,p)||!cards.isEmpty())return SkillResult.reject("not_in_targets_range");
      }
      case "zhijian"->{
        if(!other||cards.size()!=1||!cards.getFirst().type().isEquipment()||!CardZone.hand(p.seat()).equals(g().cards().locate(cards.getFirst().instanceId()))||t.equipment().all().stream().anyMatch(c->c.type().category()==cards.getFirst().type().category()))return SkillResult.reject("empty_equipment_slot_required");
      }
      case "jixi"->{
        if(!other||!cards.isEmpty()||pile(p).isEmpty())return SkillResult.reject("field_required");
        if(api instanceof SgsGameEngine e && pile(p).stream().noneMatch(c->e.mythicDeclarationLegal(p,new VirtualCard(CardType.SHUNSHOU,List.of(c)),t,null)))return SkillResult.reject("illegal_target");
      }
      case "dimeng"->{if(!other || living().stream().noneMatch(x->x!=p&&x!=t&&cards.size()==Math.abs(t.handCount()-x.handCount())))return SkillResult.reject("hand_difference_cost");}
      case "guhuo"->{if(cards.size()!=1)return SkillResult.reject("one_hand_card_required");}
      default->{return SkillResult.reject("unknown_skill");}
    }
    return SkillResult.ok();
  }
  private SkillResult activateSkill(SgsPlayer p,String id,SgsPlayer t,List<CardInstance> cards){
    boolean other=t!=null&&t!=p&&t.alive()&&g().players().contains(t);
    switch(id){
      case "huangtian_give"->{
        var lord=lordWith(p,"huangtian");if(lord==null||t!=lord||cards.size()!=1||(cards.getFirst().type()!=CardType.SHAN&&cards.getFirst().type()!=CardType.SHANDIAN))return SkillResult.reject("huangtian_lord_and_card_required");
        put(p,"used_"+id,g().turnNumber());api.transferCard(p,lord,cards.getFirst());
      }
      case "zhiba_pindian"->{
        var lord=lordWith(p,"zhiba");if(lord==null||t!=lord||p.handCount()==0||lord.handCount()==0||!cards.isEmpty())return SkillResult.reject("zhiba_pindian_required");
        put(p,"used_"+id,g().turnNumber());if(get(lord,"awake_hunzi")==0||invoke(lord,"zhiba_accept",p))zhiba(p,lord);
      }
      case "quhu"->{
        if(!other||t.hp()<=p.hp()||p.handCount()==0||t.handCount()==0||!cards.isEmpty())return SkillResult.reject("quhu_target");
        put(p,"used_"+id,g().turnNumber());
        if(pindian(p,t,id)){
          var possible=living().stream().filter(x->x!=t&&g().inBasicAttackRange(t,x)).toList();
          var victim=target(p,id,possible);if(victim==null&&!possible.isEmpty())victim=possible.getFirst();
          if(victim!=null)api.dealSkillDamage(t,victim,1);
        }else api.dealSkillDamage(t,p,1);
      }
      case "tianyi"->{
        if(!other||p.handCount()==0||t.handCount()==0||!cards.isEmpty())return SkillResult.reject("pindian_required");
        put(p,"used_"+id,g().turnNumber());put(p,"tianyiTurn",g().turnNumber());put(p,"tianyiWin",pindian(p,t,id)?1:0);
      }
      case "luanji"->{
        if(cards.size()!=2||cards.stream().anyMatch(c->!CardZone.hand(p.seat()).equals(g().cards().locate(c.instanceId())))||suit(p,cards.get(0))!=suit(p,cards.get(1)))return SkillResult.reject("same_suit_two_hand_cards");
        if(!api.useVirtualCard(p,new VirtualCard(CardType.WANJIAN,cards),null,null))return SkillResult.reject("illegal_card_use");
      }
      case "dimeng"->{
        if(!other)return SkillResult.reject("other_target_required");
        var second=target(p,id,living().stream().filter(x->x!=p&&x!=t&&cards.size()==Math.abs(t.handCount()-x.handCount())).toList());if(second==null)return SkillResult.reject("second_target_required");
        if(cards.size()!=Math.abs(t.handCount()-second.handCount()))return SkillResult.reject("hand_difference_cost");
        put(p,"used_"+id,g().turnNumber());api.discardCards(p,cards);
        if(t.alive()&&second.alive())g().cards().moveBatch(List.of(new CardMovement.Transfer(t.handView(),CardZone.hand(t.seat()),CardZone.hand(second.seat()),CardMove.Reason.SKILL_GIVE,p.seat(),false),new CardMovement.Transfer(second.handView(),CardZone.hand(second.seat()),CardZone.hand(t.seat()),CardMove.Reason.SKILL_GIVE,p.seat(),false)));
      }
      case "luanwu"->{
        if(!cards.isEmpty()||t!=null)return SkillResult.reject("no_cost_or_target");put(p,"limited_"+id,1);
        for(var actor:List.copyOf(g().alivePlayersAfter(p.seat()))){
          if(!actor.alive())continue;
          int min=living().stream().filter(x->x!=actor).mapToInt(x->g().distance(actor,x)).min().orElse(0);
          var victims=living().stream().filter(x->x!=actor&&g().distance(actor,x)==min&&g().inBasicAttackRange(actor,x)).toList();
          var victim=target(actor,id,victims);
          boolean used=victim!=null&&api.requestProvidedAttack(actor,victim).map(attack->useProvided(actor,victim,attack)).orElse(false);
          if(!used&&actor.alive())api.loseHp(actor,1);
        }
      }
      case "tiaoxin"->{
        if(!other||!g().inBasicAttackRange(t,p)||!cards.isEmpty())return SkillResult.reject("not_in_targets_range");put(p,"used_"+id,g().turnNumber());
        boolean used=api.requestProvidedAttack(t,p).map(attack->useProvided(t,p,attack)).orElse(false);
        if(!used&&t.alive()&&!owned(t).isEmpty())discard(p,t,1,id,true);
      }
      case "zhijian"->{
        if(!other||cards.size()!=1||!cards.getFirst().type().isEquipment()||!CardZone.hand(p.seat()).equals(g().cards().locate(cards.getFirst().instanceId()))||t.equipment().all().stream().anyMatch(c->c.type().category()==cards.getFirst().type().category()))return SkillResult.reject("empty_equipment_slot_required");
        move(cards.getFirst(),CardZone.equipment(t.seat()),CardMove.Reason.EQUIP,p);api.drawCards(p,1);
      }
      case "jixi"->{
        if(!other||!cards.isEmpty()||pile(p).isEmpty())return SkillResult.reject("field_required");
        if(api instanceof SgsGameEngine e && pile(p).stream().noneMatch(c->e.mythicDeclarationLegal(p,new VirtualCard(CardType.SHUNSHOU,List.of(c)),t,null)))return SkillResult.reject("illegal_target");
        var eligible=pile(p).stream().filter(c->!(api instanceof SgsGameEngine e)||e.mythicDeclarationLegal(p,new VirtualCard(CardType.SHUNSHOU,List.of(c)),t,null)).toList();
        var cost=card(p,id,eligible,false);if(cost==null)return SkillResult.reject("field_required");
        if(!api.useVirtualCard(p,new VirtualCard(CardType.SHUNSHOU,List.of(cost)),t,null))return SkillResult.reject("illegal_target");
      }
      case "guhuo"->{return guhuo(p,t,cards);}
      default->{return SkillResult.reject("unknown_skill");}
    }
    return SkillResult.ok();
  }
  private boolean useProvided(SgsPlayer p,SgsPlayer target,GameCard attack){
    try{return api.useProvidedSkillAttack(p,target,attack);}finally{g().cards().finish(attack,p.seat());}
  }
  public int attackLimit(SgsPlayer p,int value){
    if(has(p,"tianyi")&&get(p,"tianyiTurn")==g().turnNumber())return get(p,"tianyiWin")==1?(value==Integer.MAX_VALUE?value:value+1):0;
    return value;
  }
  public boolean unlimitedDistance(SgsPlayer p){return has(p,"tianyi")&&get(p,"tianyiTurn")==g().turnNumber()&&get(p,"tianyiWin")==1;}
  public int additionalTargets(SgsPlayer p){return unlimitedDistance(p)?1:0;}
  public boolean bind(SgsPlayer p,String id){
    switch(id){
      case "shensu"->{
        listen(p,id,Timing.PHASE_JUDGMENT_BEFORE,c->{if(c.actor()==p){
          var t=target(p,id,living().stream().filter(x->x!=p).toList());if(t!=null){c.skipPhase();g().setSkipDrawPhase(true);api.useVirtualAttack(p,t,true);c.confirmPublicActivation();}
        }});
        listen(p,id,Timing.PHASE_PLAY_BEFORE,c->{if(c.actor()==p){
          var costs=owned(p).stream().filter(x->x.type().isEquipment()).toList();var cost=card(p,id,costs,false);if(cost==null)return;
          var t=target(p,id,living().stream().filter(x->x!=p).toList());if(t==null)return;api.discardCards(p,List.of(cost));c.skipPhase();api.useVirtualAttack(p,t,true);c.confirmPublicActivation();
        }});
      }
      case "jushou"->listen(p,id,Timing.PHASE_TURN_END,c->{if(c.actor()==p&&invoke(p,id,p)){api.drawCards(p,3);api.turnOver(p);c.confirmPublicActivation();}});
      case "hongyan"->{} // Physical suit stays immutable; effective suit is queried at every settlement boundary.
      case "tianxiang"->listen(p,id,Timing.DAMAGE_TAKE_BEFORE,c->{
        if(c.target()!=p||c.damage()<=0)return;var hearts=p.handView().stream().filter(x->suit(p,x)==Suit.HEART).toList();
        var choice=choose(p,id,hearts,living().stream().filter(x->x!=p).toList());if(hearts.isEmpty()||choice.isEmpty())return;
        var cost=hearts.stream().filter(x->x.instanceId()==choice.get().cardId()).findFirst().orElse(null);if(cost==null)return;
        var t=g().players().get(choice.get().targetSeat());int damage=c.damage();c.setDamage(0);api.discardCards(p,List.of(cost));
        busy.add(p.seat());try{api.transferSkillDamage(c.source(),t,damage,c.card(),c.nature());if(t.alive())api.drawCards(t,t.maxHp()-t.hp());}finally{busy.remove(p.seat());}c.confirmPublicActivation();
      });
      case "buqu"->{
        listen(p,id,Timing.DYING,c->{if(c.target()==p&&p.hp()<1&&invoke(p,id,p)){
          int needed=1-p.hp()-pile(p).size();if(needed>0)g().cards().drawTo(CardZone.special(p.seat()),needed,CardMove.Reason.REVEAL,p.seat(),true);
          if(pile(p).size()==1-p.hp()&&pile(p).stream().map(CardInstance::rank).distinct().count()==pile(p).size()){put(p,"buqu",1);c.confirmPublicActivation();}
        }});
      }
      case "guidao"->listen(p,id,Timing.JUDGE_CARD_MODIFY,c->{
        var choices=owned(p).stream().filter(x->!red(p,x)).toList();if(choices.isEmpty())return;
        var selected=api.requestSkillChoice(p,c.target(),PromptType.GUI_CAI_REPLACE,id,choices,List.of(),false);
        if(selected.isEmpty())return;var replacement=choices.stream().filter(x->x.instanceId()==selected.get().cardId()).findFirst().orElse(null);if(replacement==null)return;
        c.exchangeJudgment(p,replacement);c.confirmPublicActivation();
      });
      case "leiji"->listen(p,id,Timing.CARD_RESPONSE_COMMITTED,c->{if(c.actor()==p&&c.card()!=null&&c.card().type()==CardType.SHAN&&invoke(p,id,p)){
        var t=target(p,id,living());if(t==null)return;var judge=api.judgeForSkill(t,id,false);
        if(judge!=null&&suit(t,judge)==Suit.SPADE)api.dealElementalSkillDamage(p,t,2,DamageNature.THUNDER);c.confirmPublicActivation();
      }});
      case "huangtian"->{}
      case "guhuo"->{}
      case "quhu"->{}
      case "jieming"->listen(p,id,Timing.DAMAGE_TAKE_AFTER,c->{if(c.target()==p)for(int i=0;i<c.damage()&&p.alive();i++){
        if(!invoke(p,id,p))continue;
        var t=target(p,id,living());if(t==null)continue;api.drawCards(t,Math.max(0,Math.min(t.maxHp(),5)-t.handCount()));c.confirmPublicActivation();
      }});
      case "bazhen"->{}
      case "huoji","kanpo","lianhuan","jiuchi"->listen(p,id,Timing.CARD_TRANSFORM,c->{
        if(transformable(p,id,c)){c.confirmTransform(id.equals("lianhuan"));c.confirmPublicActivation();}
      });
      case "niepan"->listen(p,id,Timing.DYING,c->{if(c.target()==p&&get(p,"limited_"+id)==0&&invoke(p,id,p)){
        put(p,"limited_"+id,1);api.discardCards(p,owned(p));
        var delayed=new ArrayList<>(p.delayZone().cards());for(var card:delayed)move(card,CardZone.DISCARD,CardMove.Reason.DISCARD,p);
        g().setChained(p.seat(),false);if(faceDown(p))api.turnOver(p);api.drawCards(p,3);p.setHp(Math.min(3,p.maxHp()));c.confirmPublicActivation();
      }});
      case "tianyi","luanji","xueyi"->{}
      case "shuangxiong"->{
        rule(p,id,RuleId.DRAW_COUNT,c->c.actor()==p,(n,t)->{
          if(!invoke(p,id,p))return n;var card=api.judgeForSkill(p,id,c->true,CardZone.hand(p.seat()));if(card==null)return 0;
          put(p,"shuangxiongTurn",g().turnNumber());put(p,"shuangxiongRed",red(p,card)?1:0);g().publishSkillActivation(p.seat(),id);return 0;
        });
        listen(p,id,Timing.CARD_TRANSFORM,c->{if(transformable(p,id,c)){c.confirmTransform();c.confirmPublicActivation();}});
      }
      case "mengjin"->listen(p,id,Timing.SHA_DODGED,c->{if(c.source()==p&&c.target()!=null&&!owned(c.target()).isEmpty()&&invoke(p,id,c.target())){discard(p,c.target(),1,id,true);c.confirmPublicActivation();}});
      default->{return bindLater(p,id);}
    }
    return true;
  }
  public boolean transformable(SgsPlayer p,String id,SkillContext c){
    if(c.actor()!=p||!has(p,id)||c.card()==null||c.card().isVirtual()||c.card().sourceCards().size()!=1)return false;
    var card=c.card().sourceCards().getFirst();if(!CardZone.hand(p.seat()).equals(g().cards().locate(card.instanceId())))return false;
    return switch(id){
      case "huoji"->c.promptType()==null&&c.declaredAs()==CardType.HUOGONG&&red(p,card);
      case "kanpo"->c.declaredAs()==CardType.WUXIE&&!red(p,card);
      case "lianhuan"->c.promptType()==null&&c.declaredAs()==CardType.TIESUO&&suit(p,card)==Suit.CLUB;
      case "jiuchi"->c.declaredAs()==CardType.JIU&&suit(p,card)==Suit.SPADE;
      case "shuangxiong"->c.promptType()==null&&c.declaredAs()==CardType.JUEDOU&&get(p,"shuangxiongTurn")==g().turnNumber()&&(red(p,card)?1:0)!=get(p,"shuangxiongRed");
      default->false;
    };
  }
  public boolean survivesBuqu(SgsPlayer p){return has(p,"buqu")&&get(p,"buqu")!=0&&pile(p).size()==1-p.hp()&&pile(p).stream().map(CardInstance::rank).distinct().count()==pile(p).size();}
  public void healed(SgsPlayer p){
    if(!has(p,"buqu")||pile(p).isEmpty())return;
    int keep=Math.max(0,1-p.hp());while(pile(p).size()>keep){var c=card(p,"buqu",pile(p),true);move(c,CardZone.DISCARD,CardMove.Reason.DISCARD,p);}
    if(p.hp()>0)put(p,"buqu",0);
  }
  public boolean rescueAllowed(SgsPlayer actor,SgsPlayer victim){
    var turn=g().currentPlayer();return !turn.alive()||!has(turn,"wansha")||actor==turn||actor==victim;
  }
  public boolean xiangle(SgsPlayer attacker,SgsPlayer defender){
    if(!has(defender,"xiangle"))return true;
    var cost=card(attacker,"xiangle",attacker.handView().stream().filter(c->c.type().category()==CardCategory.BASIC).toList(),false);
    if(cost==null)return false;api.discardCards(attacker,List.of(cost));g().publishSkillActivation(defender.seat(),"xiangle");return true;
  }
  private boolean bindLater(SgsPlayer p,String id){
    switch(id){
      case "xingshang"->listen(p,id,Timing.DEATH_BEFORE_CLEANUP,c->{
        if(c.target()!=p&&invoke(p,id,c.target())){
          for(var card:owned(c.target()))move(card,CardZone.hand(p.seat()),CardMove.Reason.EFFECT_GAIN,p);c.confirmPublicActivation();
        }
      });
      case "fangzhu"->listen(p,id,Timing.DAMAGE_TAKE_AFTER,c->{if(c.target()==p&&invoke(p,id,p)){
        var t=target(p,id,living().stream().filter(x->x!=p).toList());if(t!=null){api.drawCards(t,p.maxHp()-p.hp());api.turnOver(t);c.confirmPublicActivation();}
      }});
      case "songwei"->listen(p,id,Timing.JUDGE_CARD_EFFECT,c->{
        var a=c.target();if(a!=p&&p.identity()==Identity.LORD&&kingdom(a)==Kingdom.WEI&&c.judgeCard()!=null&&!red(a,c.judgeCard())&&invoke(a,id,p)){api.drawCards(p,1);c.confirmPublicActivation();}
      });
      case "huoshou"->{}
      case "zaiqi"->rule(p,id,RuleId.DRAW_COUNT,c->c.actor()==p&&p.hp()<p.maxHp(),(n,t)->{
        if(!invoke(p,id,p))return n;
        var cards=g().cards().drawTo(CardZone.PROCESSING,p.maxHp()-p.hp(),CardMove.Reason.REVEAL,p.seat(),true);
        try{for(var card:cards)if(suit(p,card)==Suit.HEART){api.heal(p,1);g().cards().finish(card,p.seat());}else move(card,CardZone.hand(p.seat()),CardMove.Reason.EFFECT_GAIN,p);}
        finally{for(var card:cards)g().cards().finish(card,p.seat());}g().publishSkillActivation(p.seat(),id);return 0;
      });
      case "juxiang"->listen(p,id,Timing.CARD_FINISHED,c->{if(c.actor()!=p&&c.card()!=null&&c.card().type()==CardType.NANMAN&&!c.card().sourceCards().isEmpty()){
        if(api.obtainProcessingCards(p,c.card())>0)c.confirmPublicActivation();
      }});
      case "lieren"->listen(p,id,Timing.DAMAGE_CAUSE_AFTER,c->{
        if(c.source()==p&&c.card()!=null&&EquipmentDamageRules.attack(c.card())&&c.target().alive()&&c.target().handCount()>0&&p.handCount()>0&&invoke(p,id,c.target())){
          if(pindian(p,c.target(),id)&&p.alive()&&c.target().alive())take(p,c.target(),id);c.confirmPublicActivation();
        }
      });
      case "yinghun"->listen(p,id,Timing.PHASE_PREPARE_START,c->yinghun(p,c));
      case "haoshi"->{
        rule(p,id,RuleId.DRAW_COUNT,c->c.actor()==p,(n,t)->{
          if(!invoke(p,id,p))return n;put(p,"haoshiTurn",g().turnNumber());g().publishSkillActivation(p.seat(),id);return n+2;
        });
        listen(p,id,Timing.PHASE_DRAW_END,c->{if(c.actor()==p&&get(p,"haoshiTurn")==g().turnNumber()&&p.handCount()>5){
          int min=living().stream().filter(x->x!=p).mapToInt(SgsPlayer::handCount).min().orElse(0);
          var candidates=living().stream().filter(x->x!=p&&x.handCount()==min).toList();if(candidates.isEmpty())return;
          var t=target(p,id,candidates);if(t==null)t=candidates.getFirst();int count=p.handCount()/2;
          var chosen=new ArrayList<CardInstance>();for(int i=0;i<count;i++){var choices=p.handView().stream().filter(x->!chosen.contains(x)).toList();chosen.add(card(p,id,choices,true));}
          g().cards().move(chosen,CardZone.hand(p.seat()),CardZone.hand(t.seat()),CardMove.Reason.SKILL_GIVE,p.seat());c.confirmPublicActivation();
        }});
      }
      case "dimeng"->{}
      case "roulin"->rule(p,id,RuleId.SHA_DODGE_COUNT,c->c.source()!=null&&c.target()!=null
          &&(c.source()==p&&!male(c.target())||c.target()==p&&!male(c.source())),(n,t)->Math.max(2,n));
      case "benghuai"->listen(p,id,Timing.PHASE_TURN_END,c->{if(c.actor()==p&&living().stream().anyMatch(x->x.hp()<p.hp())){
        if(p.maxHp()>1&&option(p,id,2,0)==1){p.setMaxHp(p.maxHp()-1);if(p.hp()>p.maxHp())p.setHp(p.maxHp());}
        else api.loseHp(p,1);c.confirmPublicActivation();
      }});
      case "baonue"->listen(p,id,Timing.DAMAGE_CAUSE_AFTER,c->{
        var source=c.source();if(source!=null&&source!=p&&source.alive()&&p.identity()==Identity.LORD&&kingdom(source)==Kingdom.QUN&&invoke(source,id,p)){
          var card=api.judgeForSkill(source,id,false);if(card!=null&&suit(source,card)==Suit.SPADE)api.heal(p,1);c.confirmPublicActivation();
        }
      });
      case "wansha","luanwu"->{}
      case "weimu"->rule(p,id,RuleId.CARD_TARGET_ALLOWED,c->c.target()==p&&c.card()!=null&&(c.card().type().category()==CardCategory.TRICK||c.card().type().category()==CardCategory.DELAYED_TRICK)&&c.card().isBlack(),(n,t)->0);
      case "tuntian"->{
        listen(p,id,Timing.CARD_MOVE_AFTER,c->{
          var m=c.movement();if(m==null||m.from().seat()!=p.seat()||g().currentPlayer()==p||(m.from().kind()!=CardZone.Kind.HAND&&m.from().kind()!=CardZone.Kind.EQUIPMENT)||m.cards().isEmpty())return;
          if(!invoke(p,id,p))return;
          busy.add(p.seat());try{
            api.judgeForSkill(p,id,card->suit(p,card)!=Suit.HEART,CardZone.special(p.seat()));
          }finally{busy.remove(p.seat());}c.confirmPublicActivation();
        });
      }
      case "zaoxian"->listen(p,id,Timing.PHASE_PREPARE_START,c->{if(c.actor()==p&&get(p,"awake_"+id)==0&&pile(p).size()>=3){awaken(p,id,-1);gain(p,"jixi");c.confirmPublicActivation();}});
      case "jixi"->{}
      case "qiaobian"->{
        for(var timing:List.of(Timing.PHASE_JUDGMENT_BEFORE,Timing.PHASE_DRAW_BEFORE,Timing.PHASE_PLAY_BEFORE,Timing.PHASE_DISCARD_BEFORE))listen(p,id,timing,c->{
          if(c.actor()!=p||p.handCount()==0||c.phaseSkipped())return;
          var cost=card(p,id,p.handView(),false);if(cost==null)return;api.discardCards(p,List.of(cost));c.skipPhase();
          if(timing==Timing.PHASE_DRAW_BEFORE){
            var seen=new HashSet<SgsPlayer>();for(int i=0;i<2;i++){
              var t=target(p,id,living().stream().filter(x->x!=p&&x.handCount()>0&&!seen.contains(x)).toList());if(t==null)break;
              seen.add(t);var random=t.handView().get(g().random().nextInt(t.handCount()));api.transferCard(t,p,random);
            }
          }else if(timing==Timing.PHASE_PLAY_BEFORE)moveTableCard(p,id);
          c.confirmPublicActivation();
        });
      }
      case "tiaoxin"->{}
      case "zhiji"->listen(p,id,Timing.PHASE_PREPARE_START,c->{if(c.actor()==p&&get(p,"awake_"+id)==0&&p.handCount()==0){
        if(p.hp()<p.maxHp()&&option(p,id,2,1)==0)api.heal(p,1);else api.drawCards(p,2);
        awaken(p,id,-1);gain(p,"guanxing");c.confirmPublicActivation();new GuanXingSkill(p,api).execute(c);
      }});
      case "xiangle"->{}
      case "fangquan"->{
        listen(p,id,Timing.PHASE_PLAY_BEFORE,c->{if(c.actor()==p&&!c.phaseSkipped()&&invoke(p,id,p)){put(p,"fangquanTurn",g().turnNumber());c.skipPhase();c.confirmPublicActivation();}});
        listen(p,id,Timing.PHASE_TURN_END,c->{if(c.actor()==p&&get(p,"fangquanTurn")==g().turnNumber()&&p.handCount()>0){
          var choice=choose(p,id,p.handView(),living().stream().filter(x->x!=p).toList());if(choice.isEmpty())return;
          var cost=p.handView().stream().filter(x->x.instanceId()==choice.get().cardId()).findFirst().orElse(null);if(cost==null)return;
          api.discardCards(p,List.of(cost));put(p,"extraSeat",choice.get().targetSeat()+1);c.confirmPublicActivation();
        }});
      }
      case "ruoyu"->listen(p,id,Timing.PHASE_PREPARE_START,c->{if(c.actor()==p&&p.identity()==Identity.LORD&&get(p,"awake_"+id)==0&&living().stream().noneMatch(x->x.hp()<p.hp())){
        awaken(p,id,1);api.heal(p,1);gain(p,"jijiang");c.confirmPublicActivation();
      }});
      case "jiang"->{
        listen(p,id,Timing.CARD_USE_COMMITTED,c->{if(c.actor()==p&&jiangCard(c.card(),p)&&invoke(p,id,p)){api.drawCards(p,1);c.confirmPublicActivation();}});
        listen(p,id,Timing.CARD_TARGET_CONFIRMED,c->{if(c.target()==p&&c.source()!=p&&jiangCard(c.card(),c.source())&&invoke(p,id,c.source())){api.drawCards(p,1);c.confirmPublicActivation();}});
      }
      case "hunzi"->listen(p,id,Timing.PHASE_PREPARE_START,c->{if(c.actor()==p&&get(p,"awake_"+id)==0&&p.hp()==1){awaken(p,id,-1);gain(p,"yingzi");gain(p,"yinghun");c.confirmPublicActivation();yinghun(p,c);}});
      case "zhiba"->{}
      case "zhijian"->{}
      case "guzheng"->{
        listen(p,id,Timing.PHASE_DISCARD_START,c->{if(c.actor()!=p){discarded.put(p.seat(),new ArrayList<>());guzhengReturns.put(p.seat(),new HashSet<>());}});
        listen(p,id,Timing.CARD_MOVE_AFTER,c->{var m=c.movement();
          if(g().phase()==Phase.DISCARD&&g().currentPlayer()!=p&&m!=null&&m.to().equals(CardZone.DISCARD)&&m.reason()==CardMove.Reason.DISCARD){
            discarded.computeIfAbsent(p.seat(),ignored->new ArrayList<>()).addAll(m.cards());
            if(m.from().equals(CardZone.hand(g().currentSeat())))m.cards().forEach(card->guzhengReturns.computeIfAbsent(p.seat(),ignored->new HashSet<>()).add(card.instanceId()));
          }
        });
        listen(p,id,Timing.PHASE_DISCARD_END,c->{
          var all=discarded.remove(p.seat());if(c.actor()==p||all==null)return;
          var remain=all.stream().filter(x->CardZone.DISCARD.equals(g().cards().locate(x.instanceId()))).toList();
          // Only cards discarded from this turn character's hand may be returned.
          var handIds=guzhengReturns.remove(p.seat());if(handIds==null)return;
          var returns=remain.stream().filter(x->handIds.contains(x.instanceId())).toList();
          var chosen=card(p,id,returns,false);if(chosen==null)return;
          move(chosen,CardZone.hand(c.actor().seat()),CardMove.Reason.EFFECT_GAIN,p);
          for(var card:remain)if(card!=chosen&&CardZone.DISCARD.equals(g().cards().locate(card.instanceId())))move(card,CardZone.hand(p.seat()),CardMove.Reason.EFFECT_GAIN,p);c.confirmPublicActivation();
        });
      }
      case "beige"->listen(p,id,Timing.DAMAGE_TAKE_AFTER,c->{
        if(c.card()==null||!EquipmentDamageRules.attack(c.card())||!c.target().alive())return;
        var cost=card(p,id,owned(p),false);if(cost==null)return;api.discardCards(p,List.of(cost));var judge=api.judgeForSkill(c.target(),id,false);if(judge==null)return;
        switch(suit(c.target(),judge)){
          case HEART->api.heal(c.target(),1);case DIAMOND->api.drawCards(c.target(),2);
          case CLUB->{if(c.source()!=null&&c.source().alive())discard(c.source(),c.source(),2,id,true);}
          case SPADE->{if(c.source()!=null&&c.source().alive())api.turnOver(c.source());}default->{}
        }c.confirmPublicActivation();
      });
      case "duanchang"->listen(p,id,Timing.DEATH,c->{if(c.target()==p&&c.source()!=null){put(c.source(),"silenced",1);c.confirmPublicActivation();}});
      case "huashen"->{
        for(var timing:List.of(Timing.PHASE_PREPARE_START,Timing.PHASE_TURN_END))listen(p,id,timing,c->{
          if(c.actor()!=p)return;
          if(!incarnations.containsKey(p.seat())){addIncarnations(p,2);changeIncarnation(p);}
          else if(invoke(p,id,p))changeIncarnation(p);
        });
      }
      case "xinsheng"->listen(p,id,Timing.DAMAGE_TAKE_AFTER,c->{if(c.target()==p)for(int i=0;i<c.damage();i++)if(invoke(p,id,p)){addIncarnations(p,1);c.confirmPublicActivation();}});
      default->{return false;}
    }
    return true;
  }
  private boolean jiangCard(GameCard card,SgsPlayer source){return card!=null&&(card.type()==CardType.JUEDOU||EquipmentDamageRules.attack(card)&&red(source,card));}
  private void yinghun(SgsPlayer p,SkillContext c){
    if(c.actor()!=p||p.hp()>=p.maxHp())return;
    var t=target(p,"yinghun",living().stream().filter(x->x!=p).toList());if(t==null)return;int x=p.maxHp()-p.hp();
    boolean help=option(p,"yinghun",2,0)==0;api.drawCards(t,help?x:1);discard(t,t,help?1:x,"yinghun",true);c.confirmPublicActivation();
  }
  private void awaken(SgsPlayer p,String id,int maxDelta){
    put(p,"awake_"+id,1);p.setMaxHp(Math.max(1,p.maxHp()+maxDelta));if(p.hp()>p.maxHp())p.setHp(p.maxHp());g().publishSkillActivation(p.seat(),id);
  }
  public boolean male(SgsPlayer p){
    var list=incarnations.getOrDefault(p.seat(),List.of());long index=get(p,"incarnation");
    if(index>0&&index<=list.size())return StandardHeroes.CATALOG.stream().filter(h->h.id().equals(list.get((int)index-1))).findFirst().orElseThrow().male();
    return p.hero().male();
  }
  private void moveTableCard(SgsPlayer p,String id){
    var sources=living().stream().filter(x->!x.equipment().all().isEmpty()||!x.delayZone().cards().isEmpty()).toList();
    var from=target(p,id,sources);if(from==null)return;
    var choices=new ArrayList<>(from.equipment().all());choices.addAll(from.delayZone().cards());var cost=card(p,id,choices,false);if(cost==null)return;
    var zone=g().cards().locate(cost.instanceId());var destinations=living().stream().filter(x->x!=from).filter(x->{
      if(zone.kind()==CardZone.Kind.EQUIPMENT)return x.equipment().all().stream().noneMatch(card->card.type().category()==cost.type().category());
      var type=from.delayZone().effectiveType(cost);return x.delayZone().cards().stream().noneMatch(card->x.delayZone().effectiveType(card)==type)
          && (!(api instanceof SgsGameEngine e)||e.cardTargetAllowed(from,x,new VirtualCard(type,List.of(cost))));
    }).toList();
    var to=target(p,id,destinations);if(to==null)return;
    var destination=zone.kind()==CardZone.Kind.EQUIPMENT?CardZone.equipment(to.seat()):CardZone.judgment(to.seat());
    g().cards().move(List.of(cost),zone,destination,CardMove.Reason.SKILL_GIVE,p.seat());
  }
  private void zhiba(SgsPlayer challenger,SgsPlayer lord){
    var a=card(challenger,"zhiba",challenger.handView(),true);var b=card(lord,"zhiba",lord.handView(),true);if(a==null||b==null)return;
    g().cards().moveBatch(List.of(new CardMovement.Transfer(List.of(a),CardZone.hand(challenger.seat()),CardZone.PROCESSING,CardMove.Reason.REVEAL,challenger.seat(),true),new CardMovement.Transfer(List.of(b),CardZone.hand(lord.seat()),CardZone.PROCESSING,CardMove.Reason.REVEAL,lord.seat(),true)));
    g().tableEffect("PINDIAN",challenger.seat(),lord.seat(),"zhiba",List.of(a,b),a.rank().value()>b.rank().value());
    try{if(a.rank().value()<=b.rank().value()&&invoke(lord,"zhiba_gain",challenger)){
      if(CardZone.PROCESSING.equals(g().cards().locate(a.instanceId())))move(a,CardZone.hand(lord.seat()),CardMove.Reason.EFFECT_GAIN,lord);
      if(CardZone.PROCESSING.equals(g().cards().locate(b.instanceId())))move(b,CardZone.hand(lord.seat()),CardMove.Reason.EFFECT_GAIN,lord);
    }}finally{g().cards().finish(a,lord.seat());g().cards().finish(b,lord.seat());}
  }
  private void addIncarnations(SgsPlayer p,int count){
    var existing=incarnations.computeIfAbsent(p.seat(),ignored->new ArrayList<>());
    var used=g().players().stream().map(x->x.hero().id()).collect(java.util.stream.Collectors.toSet());
    incarnations.values().forEach(used::addAll);
    var pool=new ArrayList<>(StandardHeroes.CATALOG.stream().filter(h->!used.contains(h.id())&&!h.id().equals("zuoci")).map(HeroDefinition::id).toList());
    Collections.shuffle(pool,g().random());existing.addAll(pool.subList(0,Math.min(count,pool.size())));
  }
  private static final Set<String> NOT_BORROWABLE=Set.of("jijiang","hujia","jiuyuan","huangtian","xueyi","songwei","baonue","ruoyu","zhiba","niepan","luanwu","zaoxian","zhiji","hunzi","huashen","xinsheng");
  private void changeIncarnation(SgsPlayer p){
    var ids=incarnations.getOrDefault(p.seat(),List.of());if(ids.isEmpty())return;
    var names=ids.stream().map(id->dev.sgs.core.addon.HeroCatalog.builtins().present(List.of(id)).getFirst().name()).toList();
    int index=labeledOption(p,"huashen",names,0);var hero=StandardHeroes.CATALOG.stream().filter(h->h.id().equals(ids.get(index))).findFirst().orElseThrow();
    var choices=hero.skillIds().stream().filter(id->!NOT_BORROWABLE.contains(id)).toList();if(choices.isEmpty())return;
    int skill=labeledOption(p,"huashen_skill",choices.stream().map(id->dev.sgs.core.addon.HeroCatalog.builtins().skill(id).orElseThrow().name()).toList(),0);
    var old=borrowed.put(p.seat(),choices.get(skill));if(old!=null)acquired.getOrDefault(p.seat(),Set.of()).remove(old);
    put(p,"incarnation",index+1);gain(p,choices.get(skill));g().publishSkillActivation(p.seat(),"huashen");
  }
  private int labeledOption(SgsPlayer p,String id,List<String> labels,int fallback){
    if(labels.isEmpty())return fallback;int page=0;
    for(int tries=0;tries<32;tries++){
      int start=page*7,end=Math.min(labels.size(),start+7);var names=new ArrayList<>(labels.subList(start,end));boolean more=labels.size()>7;
      if(more)names.add("下一页");int picked=option(p,id+"|"+String.join("|",names),names.size(),-1);
      if(picked<0)return fallback;if(more&&picked==names.size()-1){page=(page+1)%((labels.size()+6)/7);continue;}return start+picked;
    }return fallback;
  }
  private static final List<CardType> GUHUO_TYPES=List.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA,CardType.TAO,CardType.JIU,CardType.WUZHONG,CardType.GUOHE,CardType.SHUNSHOU,CardType.JUEDOU,CardType.NANMAN,CardType.WANJIAN,CardType.TAOYUAN,CardType.WUGU,CardType.HUOGONG,CardType.TIESUO,CardType.JIEDAO);
  private SkillResult guhuo(SgsPlayer p,SgsPlayer target,List<CardInstance> costs){
    if(costs.size()!=1||!CardZone.hand(p.seat()).equals(g().cards().locate(costs.getFirst().instanceId())))return SkillResult.reject("one_hand_card_required");
    int selected=labeledOption(p,"guhuo_declare",GUHUO_TYPES.stream().map(t->t.name()).toList(),0);var declared=GUHUO_TYPES.get(selected);
    if(Set.of(CardType.WUZHONG,CardType.NANMAN,CardType.WANJIAN,CardType.TAOYUAN,CardType.WUGU,CardType.TAO,CardType.JIU).contains(declared))target=null;
    SgsPlayer secondary=null;
    boolean needsTarget=!Set.of(CardType.WUZHONG,CardType.NANMAN,CardType.WANJIAN,CardType.TAOYUAN,CardType.WUGU,CardType.TAO,CardType.JIU).contains(declared);
    if(needsTarget && api instanceof SgsGameEngine e){
      var virtual=new VirtualCard(declared,costs);
      var eligible=living().stream().filter(first->declared==CardType.JIEDAO?living().stream().anyMatch(second->e.mythicDeclarationLegal(p,virtual,first,second)):e.mythicDeclarationLegal(p,virtual,first,null)).toList();
      if(target==null)target=target(p,"guhuo_target",eligible);if(target==null || !eligible.contains(target))return SkillResult.reject("target_required");
      if(declared==CardType.JIEDAO || declared==CardType.TIESUO){var first=target;secondary=target(p,"guhuo_second",living().stream().filter(second->second!=first&&e.mythicDeclarationLegal(p,virtual,first,second)).toList());if(declared==CardType.JIEDAO && secondary==null)return SkillResult.reject("second_target_required");}
    }
    if(api instanceof SgsGameEngine e&&!e.mythicDeclarationLegal(p,new VirtualCard(declared,costs),target,secondary))return SkillResult.reject("illegal_declaration_target");
    var cost=costs.getFirst();boolean succeeds=bluff(p,cost,declared);
    try{if(succeeds&&p.alive()&&g().status()==GameStatus.RUNNING)api.useVirtualCard(p,new VirtualCard(declared,List.of(cost)),target,secondary);}
    finally{g().cards().finish(cost,p.seat());}return SkillResult.ok();
  }
  private boolean bluff(SgsPlayer p,CardInstance cost,CardType declared){
    g().log("seat {} declares {} with guhuo",p.seat(),declared.id());
    g().cards().moveBatch(List.of(new CardMovement.Transfer(List.of(cost),CardZone.hand(p.seat()),CardZone.PROCESSING,CardMove.Reason.REVEAL,p.seat(),false)));
    var doubters=new ArrayList<SgsPlayer>();for(var other:g().alivePlayersAfter(p.seat()))if(other.hp()>0&&invoke(other,"guhuo_question",p))doubters.add(other);
    boolean truthful=cost.type()==declared||(declared==CardType.SHA&&EquipmentDamageRules.attack(cost));
    boolean succeeds=doubters.isEmpty()||truthful&&suit(p,cost)==Suit.HEART;
    if(!doubters.isEmpty())g().log("guhuo revealed: {} {} {}",cost.type().id(),cost.suit(),cost.rank());
    for(var other:doubters)if(truthful)api.loseHp(other,1);else api.drawCards(other,1);
    return succeeds;
  }
  public record GuhuoAnswer(boolean attempted,Optional<PlayDecision> answer){}
  public GuhuoAnswer offerGuhuo(PromptRequest prompt){
    var p=prompt.asker();
    if(!has(p,"guhuo")||p.handCount()==0||prompt.allowedTypes().isEmpty()
        ||Set.of(PromptType.WUGU_PICK,PromptType.HUOGONG_REVEAL,PromptType.HUOGONG_PAY).contains(prompt.type()))return new GuhuoAnswer(false,Optional.empty());
    if(!invoke(p,"guhuo_response",prompt.subject()))return new GuhuoAnswer(false,Optional.empty());
    var cost=card(p,"guhuo_response",p.handView(),false);if(cost==null)return new GuhuoAnswer(true,Optional.empty());
    var types=prompt.allowedTypes().stream().filter(t->t.category()==CardCategory.BASIC||t.category()==CardCategory.TRICK).toList();
    if(types.isEmpty())return new GuhuoAnswer(true,Optional.empty());
    int selected=labeledOption(p,"guhuo_declare",types.stream().map(CardType::name).toList(),0);var declared=types.get(selected);
    if(!rescueAllowed(p,prompt.subject())&&prompt.type()==PromptType.RESCUE_TAO)return new GuhuoAnswer(true,Optional.empty());
    boolean succeeded=bluff(p,cost,declared);
    if(!succeeded||!p.alive()||g().status()!=GameStatus.RUNNING){g().cards().finish(cost,p.seat());return new GuhuoAnswer(true,Optional.empty());}
    g().publishSkillActivation(p.seat(),"guhuo");return new GuhuoAnswer(true,Optional.of(PlayDecision.asIs(new VirtualCard(declared,List.of(cost)))));
  }
  public int nextTurnSeat(int normal){
    for(var p:g().players())if(get(p,"returnSeat")>0){int seat=(int)get(p,"returnSeat")-1;put(p,"returnSeat",0);return g().players().get(seat).alive()?seat:g().nextAliveSeat(seat);}
    for(var p:g().players())if(get(p,"extraSeat")>0){int seat=(int)get(p,"extraSeat")-1;put(p,"extraSeat",0);if(g().players().get(seat).alive()){put(p,"returnSeat",normal+1);return seat;}}
    return normal;
  }
  public Map<String,Long> saveMemory(){
    var result=new TreeMap<>(memory);
    var skills=skillDictionary();
    for(int i=0;i<boundHistory.size();i++){var b=boundHistory.get(i);result.put("0:bindSeat_"+i,(long)b.seat()+1);result.put("0:bindSkill_"+i,(long)skills.indexOf(b.skill())+1);}
    acquired.forEach((seat,ids)->ids.forEach(id->result.put(seat+":acq_"+skills.indexOf(id),1L)));
    borrowed.forEach((seat,id)->result.put(seat+":borrowed",(long)skills.indexOf(id)+1));
    incarnations.forEach((seat,ids)->{for(int i=0;i<ids.size();i++){var id=ids.get(i);int index=java.util.stream.IntStream.range(0,StandardHeroes.CATALOG.size()).filter(n->StandardHeroes.CATALOG.get(n).id().equals(id)).findFirst().orElseThrow();result.put(seat+":inc_"+i,(long)index+1);}});
    discarded.forEach((seat,cards)->{result.put(seat+":discardActive",1L);for(int i=0;i<cards.size();i++)result.put(seat+":disc_"+i,cards.get(i).instanceId());});
    guzhengReturns.forEach((seat,ids)->{int i=0;for(long id:new TreeSet<>(ids))result.put(seat+":return_"+i++,id);});
    return Map.copyOf(result);
  }
  private static List<String> skillDictionary(){return java.util.stream.Stream.concat(StandardHeroes.CATALOG.stream().flatMap(h->h.skillIds().stream()),dev.sgs.core.addon.MythicPresentations.ALL.stream().map(dev.sgs.core.addon.SkillPresentation::id)).distinct().sorted().toList();}
  private static final Set<String> SCALARS=Set.of("initialized","silenced","faceDown","incarnation","buqu","tianyiTurn","tianyiWin","shuangxiongTurn","shuangxiongRed","haoshiTurn","fangquanTurn","extraSeat","returnSeat","used_quhu","used_tianyi","used_dimeng","used_tiaoxin","used_huangtian_give","used_zhiba_pindian","limited_luanwu","limited_niepan","awake_zaoxian","awake_zhiji","awake_ruoyu","awake_hunzi");
  private List<Long> indexed(Map<String,Long> values,String prefix){
    var indexed=new TreeMap<Integer,Long>();
    values.forEach((key,value)->{if(key.startsWith(prefix)){var suffix=key.substring(prefix.length());if(!suffix.matches("0|[1-9][0-9]{0,3}"))throw new IllegalArgumentException("invalid expansion index");indexed.put(Integer.parseInt(suffix),value);}});
    var result=new ArrayList<Long>();for(var e:indexed.entrySet()){if(e.getKey()!=result.size())throw new IllegalArgumentException("noncontiguous expansion memory");result.add(e.getValue());}return result;
  }
  public void validateMemory(Map<String,Long> values,int turn){
    if(values==null||values.size()>4096)throw new IllegalArgumentException("invalid expansion memory");
    var dictionary=skillDictionary();var physical=g().cards().zones().stream().flatMap(z->g().cards().cards(z).stream()).map(CardInstance::instanceId).collect(java.util.stream.Collectors.toSet());
    for(var e:values.entrySet()){
      var key=e.getKey();long value=e.getValue()==null?-1:e.getValue();
      if(!key.matches("[0-7]:[a-zA-Z_][a-zA-Z_0-9]{0,47}")||value<0||value>Long.MAX_VALUE-1||key.charAt(0)-'0'>=g().players().size())throw new IllegalArgumentException("invalid expansion field");
      var field=key.substring(2);
      if(SCALARS.contains(field)){
        if((field.endsWith("Turn")||field.startsWith("used_"))&&value>turn)throw new IllegalArgumentException("future expansion turn");
        if((field.startsWith("awake_")||field.startsWith("limited_")||Set.of("initialized","silenced","faceDown","buqu","tianyiWin","shuangxiongRed").contains(field))&&value>1)throw new IllegalArgumentException("invalid expansion flag");
        if((field.equals("extraSeat")||field.equals("returnSeat"))&&value>g().players().size())throw new IllegalArgumentException("invalid extra turn seat");
      }else if(field.equals("borrowed")){if(value<1||value>dictionary.size())throw new IllegalArgumentException("invalid borrowed skill");}
      else if(field.equals("discardActive")){if(value!=1)throw new IllegalArgumentException("invalid discard tracker");}
      else if(field.matches("acq_[0-9]+")){int i=Integer.parseInt(field.substring(4));if(i>=dictionary.size()||value!=1)throw new IllegalArgumentException("invalid acquired skill");}
      else if(field.matches("(disc|return)_[0-9]+")){if(!physical.contains(value))throw new IllegalArgumentException("unknown discard tracker card");}
      else if(field.matches("inc_[0-9]+")){if(value<1||value>StandardHeroes.CATALOG.size())throw new IllegalArgumentException("invalid incarnation hero");}
      else if(field.matches("bind(Seat|Skill)_[0-9]+")){if(key.charAt(0)!='0'||value<1||value>(field.startsWith("bindSeat")?g().players().size():dictionary.size()))throw new IllegalArgumentException("invalid dynamic binding");}
      else throw new IllegalArgumentException("unknown expansion field "+field);
    }
    var seats=indexed(values,"0:bindSeat_");var skills=indexed(values,"0:bindSkill_");if(seats.size()!=skills.size())throw new IllegalArgumentException("dynamic binding pair mismatch");
    var bindings=new HashSet<String>();for(int i=0;i<seats.size();i++)if(!bindings.add((seats.get(i)-1)+":"+dictionary.get(skills.get(i).intValue()-1)))throw new IllegalArgumentException("duplicate acquired binding");
    for(var p:g().players()){
      var inc=indexed(values,p.seat()+":inc_");if(new HashSet<>(inc).size()!=inc.size()||values.getOrDefault(p.seat()+":incarnation",0L)>inc.size())throw new IllegalArgumentException("invalid incarnation selection");
      indexed(values,p.seat()+":disc_");indexed(values,p.seat()+":return_");
      for(int i=0;i<dictionary.size();i++)if(values.containsKey(p.seat()+":acq_"+i)&&!bindings.contains(p.seat()+":"+dictionary.get(i)))throw new IllegalArgumentException("acquired skill lacks binding");
      var selected=values.get(p.seat()+":borrowed");if(selected!=null&&!values.containsKey(p.seat()+":acq_"+(selected-1)))throw new IllegalArgumentException("borrowed skill not acquired");
    }
  }
  private Map<String,Long> preparedRestore;
  /** Bootstrap dynamic bindings in their original global order, before validating registry memory layout. */
  public void prepareRestoration(Map<String,Long> values,int turn){
    validateMemory(values,turn);if(preparedRestore!=null){if(!preparedRestore.equals(values))throw new IllegalArgumentException("expansion restoration changed");return;}
    if(!boundHistory.isEmpty()||!acquired.isEmpty()||!incarnations.isEmpty())throw new IllegalStateException("dynamic restoration requires fresh engine");
    var dictionary=skillDictionary();
    for(var p:g().players()){
      for(int i=0;i<dictionary.size();i++)if(values.containsKey(p.seat()+":acq_"+i))acquired.computeIfAbsent(p.seat(),ignored->new LinkedHashSet<>()).add(dictionary.get(i));
      var inc=indexed(values,p.seat()+":inc_");if(!inc.isEmpty())incarnations.put(p.seat(),new ArrayList<>(inc.stream().map(index->StandardHeroes.CATALOG.get(index.intValue()-1).id()).toList()));
      var current=values.get(p.seat()+":borrowed");if(current!=null)borrowed.put(p.seat(),dictionary.get(current.intValue()-1));
      if(values.containsKey(p.seat()+":discardActive")){
        var physical=g().cards().zones().stream().flatMap(z->g().cards().cards(z).stream()).collect(java.util.stream.Collectors.toMap(CardInstance::instanceId,c->c));
        discarded.put(p.seat(),new ArrayList<>(indexed(values,p.seat()+":disc_").stream().map(physical::get).toList()));guzhengReturns.put(p.seat(),new HashSet<>(indexed(values,p.seat()+":return_")));
      }
    }
    var seats=indexed(values,"0:bindSeat_");var skills=indexed(values,"0:bindSkill_");
    for(int i=0;i<seats.size();i++){int seat=seats.get(i).intValue()-1;var id=dictionary.get(skills.get(i).intValue()-1);bound.computeIfAbsent(seat,ignored->new HashSet<>()).add(id);boundHistory.add(new AcquiredBinding(seat,id));acquire.accept(g().players().get(seat),id);}
    preparedRestore=Map.copyOf(values);
  }
  public void restoreMemory(Map<String,Long> values,int turn){
    prepareRestoration(values,turn);memory.clear();values.forEach((key,value)->{if(SCALARS.contains(key.substring(2)))memory.put(key,value);});
  }
}
