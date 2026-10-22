package org.sgs.packs.yjcm2011;

import dev.sgs.core.*;
import dev.sgs.core.addon.*;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.*;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.engine.skill.equipment.EquipmentDamageRules;
import dev.sgs.core.hero.HeroDefinition;
import dev.sgs.core.player.SgsPlayer;
import java.util.*;
import java.util.function.Consumer;

/** Independent pack: QSanguosha-v2 yjcm.cpp revision, not nostalgic/mobile/reworked mixtures. */
public final class Yjcm2011Pack implements HeroPack {
  static String id(String key){return "yjcm2011:"+key;}
  public Optional<BotSkillAction> botAction(dev.sgs.core.game.GameState state,SgsPlayer actor){
    if(!actor.hero().id().equals(id("wuguotai")))return Optional.empty();
    for(var first:state.alivePlayersFromCurrent())for(var second:state.alivePlayersFromCurrent())
      if(first!=second&&(!first.equipment().all().isEmpty()||!second.equipment().all().isEmpty())
          &&Math.abs(first.equipment().all().size()-second.equipment().all().size())<=actor.maxHp()-actor.hp())
        return Optional.of(new BotSkillAction(id("ganlu"),first.seat(),List.of()));
    return Optional.empty();
  }
  public Optional<Boolean> botInvoke(dev.sgs.core.game.GameState state,PromptRequest prompt){
    if(prompt.skillId().equals(id("buyi")))return Optional.of(prompt.subject()==prompt.asker()||state.areTeammates(prompt.asker(),prompt.subject()));
    if(prompt.skillId().equals(id("xuanfeng")))return Optional.of(state.alivePlayersFromCurrent().stream().anyMatch(x->x!=prompt.asker()&&!state.areTeammates(prompt.asker(),x)&&(x.handCount()>0||!x.equipment().all().isEmpty())));
    if(prompt.skillId().equals(id("pojun")))return Optional.of(prompt.subject()!=null&&!state.areTeammates(prompt.asker(),prompt.subject()));
    return Optional.empty();
  }
  public Optional<SkillChoice> botChoice(dev.sgs.core.game.GameState state,PromptRequest prompt){
    if(prompt.type()==PromptType.EXPANSION_OPTION)return Optional.of(new SkillChoice(0,0));
    if(prompt.type()==PromptType.CARD_ZONE_SELECT)return prompt.hiddenHandAvailable()?Optional.of(new SkillChoice(HandSelection.positionToken(0),-1))
        :prompt.choices().stream().findFirst().map(x->new SkillChoice(x.instanceId(),-1));
    if(prompt.type()!=PromptType.EXPANSION_CHOICE)return Optional.empty();
    var targets=prompt.allowedTargetSeats().stream().map(i->state.players().get(i)).toList();
    boolean aid=prompt.skillId().equals(id("jujian"));
    var target=targets.stream().filter(x->aid?state.areTeammates(prompt.asker(),x):!state.areTeammates(prompt.asker(),x)).findFirst().orElse(targets.isEmpty()?null:targets.getFirst());
    var card=prompt.choices().stream().findFirst().orElse(null);
    return Optional.of(new SkillChoice(card==null?0:card.instanceId(),target==null?-1:target.seat()));
  }
  public void register(HeroPackRegistrar r){
    r.skill(view("xuanfeng","旋风","失去装备区里的牌后，或弃牌阶段弃置至少两张牌后，可依次弃置其他角色共两张牌。",SkillPresentation.Kind.TRIGGERED,SkillPresentation.Target.NONE,0,0),
        (p,e)->{var m=new Memory(p,e,"xuanfeng",Map.of("turn",-1L,"discarded",0L));return hooks(m,
            hook(m,Timing.CARD_MOVE_AFTER,c->{var move=c.movement();if(move==null||move.from().seat()!=p.seat())return;
              if(e.gameState().currentPlayer()==p&&e.gameState().phase()==Phase.DISCARD&&move.reason()==CardMove.Reason.DISCARD){
                if(m.get("turn")!=e.gameState().turnNumber()){m.set("turn",e.gameState().turnNumber());m.set("discarded",0);}
                m.set("discarded",m.get("discarded")+move.cards().size());
              }
              if(move.from().kind()==CardZone.Kind.EQUIPMENT)xuanfeng(m,c);
            }),
            hook(m,Timing.PHASE_DISCARD_END,c->{if(c.actor()==p&&m.get("turn")==e.gameState().turnNumber()&&m.get("discarded")>=2){m.set("discarded",0);xuanfeng(m,c);}}));});
    r.skill(view("wuyan","无言","锁定技，你防止自己造成或受到的锦囊牌伤害；不取消锦囊的其他效果。",SkillPresentation.Kind.PASSIVE,SkillPresentation.Target.NONE,0,0),
        (p,e)->{var m=new Memory(p,e,"wuyan",Map.of());return hooks(m,
            hook(m,Timing.DAMAGE_CAUSE_BEFORE,c->{if(c.source()==p&&trick(c.card())){c.setDamage(0);c.confirmPublicActivation();}}),
            hook(m,Timing.DAMAGE_TAKE_BEFORE,c->{if(c.target()==p&&trick(c.card())){c.setDamage(0);c.confirmPublicActivation();}}));});
    r.skill(view("jujian","举荐","结束阶段，可弃一张非基本牌，令另一角色选择摸两张牌、回复1点体力或复原。",SkillPresentation.Kind.TRIGGERED,SkillPresentation.Target.NONE,0,0),
        (p,e)->{var m=new Memory(p,e,"jujian",Map.of());return hooks(m,hook(m,Timing.PHASE_TURN_END,c->{
          if(c.actor()!=p)return;var costs=owned(m,p).stream().filter(x->x.type().category()!=CardCategory.BASIC).toList();
          var targets=others(m);if(costs.isEmpty()||targets.isEmpty())return;
          var answer=e.requestSkillChoice(p,p,PromptType.EXPANSION_CHOICE,id("jujian"),costs,targets.stream().map(SgsPlayer::seat).toList(),false);
          if(answer.isEmpty())return;var card=costs.stream().filter(x->x.instanceId()==answer.get().cardId()).findFirst().orElseThrow();
          var target=e.gameState().players().get(answer.get().targetSeat());e.discardCards(p,List.of(card));if(!target.alive())return;
          var actions=new ArrayList<String>();actions.add("摸两张牌");if(target.hp()<target.maxHp())actions.add("回复1点体力");
          if(e.faceDown(target)||e.gameState().chained(target.seat()))actions.add("复原");
          int choice=option(m,target,"jujian",actions,0);switch(actions.get(choice)){
            case "回复1点体力"->e.heal(target,1);
            case "复原"->{e.gameState().setChained(target.seat(),false);if(e.faceDown(target))e.turnOver(target);}
            default->e.drawCards(target,2);
          }c.confirmPublicActivation();
        }));});
    r.skill(view("ganlu","甘露","出牌阶段限一次，交换两名角色的全部装备，两者装备数之差不得超过你已损失的体力。",SkillPresentation.Kind.ACTIVE,SkillPresentation.Target.ANY,0,0),
        (p,e)->SkillBinding.active(new Ganlu(p,e)));
    r.skill(view("buyi","补益","有人进入濒死时，可展示其一张手牌；若不是基本牌，弃置该牌并令其回复1点体力。",SkillPresentation.Kind.TRIGGERED,SkillPresentation.Target.NONE,0,0),
        (p,e)->{var m=new Memory(p,e,"buyi",Map.of());return hooks(m,hook(m,Timing.DYING,c->{
          var victim=c.target();if(victim==null||!victim.alive()||victim.hp()>0||victim.handCount()==0||!e.requestSkillInvoke(p,id("buyi"),victim))return;
          Optional<SkillChoice> selected=e.requestDyingSkillChoice(p,victim,victim==p?PromptType.EXPANSION_CHOICE:PromptType.CARD_ZONE_SELECT,
              id("buyi"),victim==p?victim.handView():List.of(),victim!=p);
          CardInstance shown=null;
          if(victim==p){if(selected.isPresent())shown=victim.handView().stream().filter(x->x.instanceId()==selected.get().cardId()).findFirst().orElse(null);if(shown==null)shown=victim.handView().getFirst();}
          else if(selected.isPresent()&&selected.get().cardId()<=-2){int i=HandSelection.positionIndex(selected.get().cardId(),victim.handCount());if(i>=0)shown=victim.handView().get(i);}
          else shown=victim.handView().get(e.gameState().random().nextInt(victim.handCount()));
          if(shown==null)return;e.gameState().log("buyi reveals seat {} card {}",victim.seat(),shown.cardId());
          if(shown.type().category()!=CardCategory.BASIC){e.discardCards(victim,List.of(shown));if(victim.alive())e.heal(victim,1);}
          c.confirmPublicActivation();
        }));});
    r.skill(view("pojun","破军","以杀造成非连环、非转移伤害后，可令存活目标摸至多五张牌（等于其当前体力），然后翻面。",SkillPresentation.Kind.TRIGGERED,SkillPresentation.Target.NONE,0,0),
        (p,e)->{var m=new Memory(p,e,"pojun",Map.of());return hooks(m,hook(m,Timing.DAMAGE_CAUSE_AFTER,c->{
          var target=c.target();if(c.source()!=p||!EquipmentDamageRules.attack(c.card())||c.chainDamage()||c.transferredDamage()
              ||target==null||!target.alive()||!e.requestSkillInvoke(p,id("pojun"),target))return;
          e.drawCards(target,Math.max(0,Math.min(5,target.hp())));if(target.alive())e.turnOver(target);c.confirmPublicActivation();
        }));});
    hero(r,"lingtong","凌统",Kingdom.WU,4,true,"xuanfeng");
    hero(r,"xushu","徐庶",Kingdom.SHU,3,true,"wuyan","jujian");
    hero(r,"wuguotai","吴国太",Kingdom.WU,3,false,"ganlu","buyi");
    hero(r,"xusheng","徐盛",Kingdom.WU,4,true,"pojun");
  }
  static SkillPresentation view(String key,String name,String text,SkillPresentation.Kind kind,SkillPresentation.Target target,int min,int max){return new SkillPresentation(id(key),name,text,kind,target,min,max,false);}
  static void hero(HeroPackRegistrar r,String key,String name,Kingdom kingdom,int hp,boolean male,String... skills){
    r.hero(new HeroDefinition(id(key),kingdom,hp,Arrays.stream(skills).map(Yjcm2011Pack::id).toList(),male),name,"一将成名2011·QSanguosha-v2修订规则",id("textures/heroes/"+key+".png"));r.idleCheckpoint(id(key));
  }
  static boolean trick(GameCard card){return card!=null&&(card.type().category()==CardCategory.TRICK||card.type().category()==CardCategory.DELAYED_TRICK);}
  static List<SgsPlayer> others(Memory m){return m.e.gameState().alivePlayersFromCurrent().stream().filter(x->x!=m.p).toList();}
  static List<CardInstance> owned(Memory m,SgsPlayer p){var result=new ArrayList<>(p.handView());result.addAll(p.equipment().all());return result;}
  static int option(Memory m,SgsPlayer actor,String key,List<String> labels,int fallback){
    return m.e.requestSkillChoice(actor,actor,PromptType.EXPANSION_OPTION,id(key)+"|"+String.join("|",labels),List.of(),
        java.util.stream.IntStream.range(0,labels.size()).boxed().toList(),true).map(SkillChoice::targetSeat).orElse(fallback);
  }
  static void xuanfeng(Memory m,SkillContext c){
    var targets=others(m).stream().filter(x->!owned(m,x).isEmpty()).toList();
    if(targets.isEmpty()||!m.e.requestSkillInvoke(m.p,m.id(),m.p))return;
    for(int n=0;n<2&&m.p.alive();n++){
      targets=others(m).stream().filter(x->!owned(m,x).isEmpty()).toList();if(targets.isEmpty())break;
      var choice=m.e.requestSkillChoice(m.p,m.p,PromptType.EXPANSION_CHOICE,m.id(),List.of(),targets.stream().map(SgsPlayer::seat).toList(),true);
      var target=choice.isPresent()?m.e.gameState().players().get(choice.get().targetSeat()):targets.getFirst();
      var selected=m.e.requestSkillChoice(m.p,target,PromptType.CARD_ZONE_SELECT,m.id(),target.equipment().all(),List.of(),target.handCount()>0);
      CardInstance card=null;
      if(selected.isPresent()&&selected.get().cardId()>0)card=target.equipment().all().stream().filter(x->x.instanceId()==selected.get().cardId()).findFirst().orElse(null);
      else if(selected.isPresent()&&selected.get().cardId()<=-2){int i=HandSelection.positionIndex(selected.get().cardId(),target.handCount());if(i>=0)card=target.handView().get(i);}
      else if(target.handCount()>0)card=target.handView().get(m.e.gameState().random().nextInt(target.handCount()));
      else if(!target.equipment().all().isEmpty())card=target.equipment().all().getFirst();
      if(card!=null)m.e.discardCards(target,List.of(card));
    }c.confirmPublicActivation();
  }
  static class Memory implements PersistentSkillMemory {
    final SgsPlayer p;final SkillEngine e;final String key;final Map<String,Long> values=new TreeMap<>();
    Memory(SgsPlayer p,SkillEngine e,String key,Map<String,Long> initial){this.p=p;this.e=e;this.key=key;values.put("schema",1L);values.putAll(initial);}
    String id(){return Yjcm2011Pack.id(key);}long get(String name){return values.get(name);}void set(String name,long value){values.put(name,value);}
    public Map<String,Long> saveMemory(){return Map.copyOf(values);}
    public void validateMemory(Map<String,Long> v,int turn){
      if(!v.keySet().equals(values.keySet())||v.get("schema")!=1L)throw new IllegalArgumentException("unsupported "+id()+" memory schema");
      for(var entry:v.entrySet())if(entry.getValue()< -1||entry.getValue()>(entry.getKey().equals("discarded")?160:Math.max(1,turn)))throw new IllegalArgumentException("invalid "+id()+" memory value");
    }
    public void restoreMemory(Map<String,Long> v,int turn){validateMemory(v,turn);values.clear();values.putAll(v);}
  }
  static class Hook implements SkillListener,PersistentSkillMemory {
    final Memory m;final Timing timing;final Consumer<SkillContext> body;
    Hook(Memory m,Timing timing,Consumer<SkillContext> body){this.m=m;this.timing=timing;this.body=body;}
    public String id(){return m.id();}public SgsPlayer owner(){return m.p;}public Timing timing(){return timing;}
    public int priority(){return m.key.equals("wuyan")?Priority.STATE_DETECTION:Priority.ACTIVE_CHOICE;}
    public boolean applies(SkillContext c){return m.p.alive();}public void execute(SkillContext c){body.accept(c);}
    public PersistentSkillMemory memoryOwner(){return m;}public Map<String,Long> saveMemory(){return m.saveMemory();}
    public void validateMemory(Map<String,Long> v,int t){m.validateMemory(v,t);}public void restoreMemory(Map<String,Long> v,int t){m.restoreMemory(v,t);}
  }
  static Hook hook(Memory m,Timing t,Consumer<SkillContext> body){return new Hook(m,t,body);}
  static SkillBinding hooks(Memory m,Hook... hooks){return new SkillBinding(List.of(),List.of(hooks),List.of());}
  static final class Ganlu extends Memory implements ActiveSkill {
    Ganlu(SgsPlayer p,SkillEngine e){super(p,e,"ganlu",Map.of("used",-1L));}
    public String id(){return super.id();}public SgsPlayer owner(){return p;}
    public String unavailableReason(SkillEngine ignored){return get("used")==e.gameState().turnNumber()?"SKILL_USED_THIS_TURN":"";}
    public SkillResult activate(SkillEngine api,SgsPlayer actor,SgsPlayer first,List<CardInstance> cards){
      if(actor!=p||!p.alive()||!unavailableReason(api).isEmpty()||!cards.isEmpty()||first==null||!first.alive()||!api.gameState().players().contains(first))return SkillResult.reject("invalid_ganlu");
      int budget=p.maxHp()-p.hp();var targets=api.gameState().alivePlayersFromCurrent().stream().filter(x->x!=first&&Math.abs(x.equipment().all().size()-first.equipment().all().size())<=budget).toList();
      if(targets.isEmpty())return SkillResult.reject("equipment_difference");
      var answer=api.requestSkillChoice(p,p,PromptType.EXPANSION_CHOICE,id(),List.of(),targets.stream().map(SgsPlayer::seat).toList(),true);
      if(answer.isEmpty())return SkillResult.reject("selection_cancelled");var second=api.gameState().players().get(answer.get().targetSeat());
      set("used",api.gameState().turnNumber());var a=List.copyOf(first.equipment().all());var b=List.copyOf(second.equipment().all());
      api.gameState().cards().moveBatch(List.of(new CardMovement.Transfer(a,CardZone.equipment(first.seat()),CardZone.equipment(second.seat()),CardMove.Reason.SKILL_GIVE,p.seat(),true),
          new CardMovement.Transfer(b,CardZone.equipment(second.seat()),CardZone.equipment(first.seat()),CardMove.Reason.SKILL_GIVE,p.seat(),true)));
      return SkillResult.ok();
    }
  }
}
