package dev.sgs.core.engine.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.sgs.core.engine.skill.equipment.ZhugeNuShaLimit;
import dev.sgs.core.engine.skill.skills.LongDanSkill;
import dev.sgs.core.engine.skill.skills.PaoXiaoSkill;
import dev.sgs.core.engine.skill.skills.RenDeSkill;
import dev.sgs.core.engine.skill.skills.JianXiongSkill;
import dev.sgs.core.engine.skill.skills.HuJiaSkill;
import dev.sgs.core.player.SgsPlayer;

/**
 * Binds the skill ids carried by {@link dev.sgs.core.hero.HeroDefinition} to concrete instances and
 * registers the passive hooks / rule modifiers on the bus. Active skills are retained for explicit
 * launch and are never triggered by timings.
 *
 * <p>This is the single place that maps ids to code; Task 8's JSON loader will reuse the same id
 * space. Unknown ids are returned to the caller for warning logs instead of crashing the match.
 */
public final class SkillRegistry {

  private final SkillEventBus bus;
  private final Map<String, ActiveSkill> activeSkills = new LinkedHashMap<>();
  private final Map<String,PersistentSkillMemory> persistentSkills=new LinkedHashMap<>();
  private final java.util.Set<String> untrackedExternalSkills=new java.util.HashSet<>();
  private final java.util.Set<String> programmedExternalSkills=new java.util.HashSet<>();
  private final Map<Integer,HuJiaSkill> hujia = new LinkedHashMap<>();
  private final Map<Integer,dev.sgs.core.engine.skill.skills.JiJiangSkill> jijiang = new LinkedHashMap<>();
  private final SkillEngine engine;
  private final dev.sgs.core.engine.skill.skills.MythicRules mythic;
  public dev.sgs.core.engine.skill.skills.MythicRules mythic(){return mythic;}
  private boolean builtInsRegistered;
  private final dev.sgs.core.addon.HeroCatalog catalog;

  public SkillRegistry(SkillEventBus bus,SkillEngine engine) {
    this.bus = bus;
    this.engine = engine;
    this.catalog=dev.sgs.core.addon.AddonRuntime.current();
    this.mythic=new dev.sgs.core.engine.skill.skills.MythicRules(engine,bus,(player,id)->{if(!bindOne(player,id))throw new IllegalArgumentException("unknown acquired skill "+id);engine.versionedSkillLayoutChanged();});
    bus.setSkillEnabled(mythic::enabled);
  }

  /** Registers engine-global built-in effects (equipment effects shared by every match). */
  public void registerBuiltIns() {
    if(builtInsRegistered)return;
    builtInsRegistered=true;
    bus.registerRule(new ZhugeNuShaLimit());
    bus.register(new dev.sgs.core.engine.skill.equipment.EquipmentDamageRules(Timing.DAMAGE_CAUSE_BEFORE));
    bus.register(new dev.sgs.core.engine.skill.equipment.EquipmentDamageRules(Timing.DAMAGE_TAKE_BEFORE));
    bus.register(new dev.sgs.core.engine.skill.equipment.SilverLionLeave(engine));
  }

  /**
   * Creates the hooks for one seat according to its hero's skill ids and registers them. Returns
   * the skill ids that have no code binding (bad datapack / future content), so the caller can log
   * them.
   */
  public List<String> bindHero(SgsPlayer player) {
    bus.unregisterOwner(player);
    engine.gameState().clearPublicStatuses(player.seat());
    hujia.remove(player.seat());
    jijiang.remove(player.seat());
    activeSkills.keySet().removeIf(key->key.startsWith(player.seat()+":"));
    persistentSkills.keySet().removeIf(key->key.startsWith(player.seat()+":"));
    untrackedExternalSkills.removeIf(key->key.startsWith(player.seat()+":"));
    programmedExternalSkills.removeIf(key->key.startsWith(player.seat()+":"));
    List<String> unknown = new ArrayList<>();
    if(mythic.active())persistentSkills.put("0:mythic",mythic);
    if (player.hero() == null) {
      return unknown;
    }
    if(mythic.active())for(var attached:List.of("huangtian_give","zhiba_pindian"))registerActive(player,mythic.active(player,attached));
    for (String skillId : player.hero().skillIds()) {
      if (!bindOne(player, skillId)) {
        unknown.add(skillId);
      }
    }
    if(catalog.supportsIdleCheckpoint(player.hero().id())){
      var identity=catalog.packIdentity(player.hero().id());
      String namespace=player.hero().id().split(":",2)[0];
      persistentSkills.put(player.seat()+":"+namespace+":__pack",new PersistentSkillMemory(){
        public Map<String,Long> saveMemory(){return identity;}
        public void validateMemory(Map<String,Long> values,int turn){if(!identity.equals(values))throw new IllegalArgumentException("external pack version/hash differs: "+namespace);}
        public void restoreMemory(Map<String,Long> values,int turn){validateMemory(values,turn);}
      });
    }
    return unknown;
  }

  /**
   * @return whether the id was recognized and bound
   */
  private boolean bindOne(SgsPlayer player, String skillId) {
    switch (skillId) {
      case PaoXiaoSkill.ID -> bus.registerRule(new PaoXiaoSkill(player));
      case LongDanSkill.ID -> bus.register(new LongDanSkill(player));
      case RenDeSkill.ID -> registerActive(player, new RenDeSkill(player));
      case JianXiongSkill.ID -> bus.register(new JianXiongSkill(player,engine));
      case HuJiaSkill.ID -> hujia.put(player.seat(),new HuJiaSkill(player,engine));
      case "jijiang" -> {
        var skill=new dev.sgs.core.engine.skill.skills.JiJiangSkill(player,engine);
        jijiang.put(player.seat(),skill);registerActive(player,skill);
      }
      case "kurou" -> registerActive(player,new dev.sgs.core.engine.skill.skills.KuRouSkill(player));
      case "wusheng" -> bus.register(new dev.sgs.core.engine.skill.skills.WuShengSkill(player));
      case "jizhi" -> bus.register(new dev.sgs.core.engine.skill.skills.JiZhiSkill(player,engine));
      case "qicai" -> bus.registerRule(new dev.sgs.core.engine.skill.skills.QiCaiSkill(player));
      case "zhiheng" -> registerActive(player,new dev.sgs.core.engine.skill.skills.ZhiHengSkill(player));
      case "jiuyuan" -> bus.registerRule(new dev.sgs.core.engine.skill.skills.JiuYuanSkill(player));
      case "keji" -> {
        var skill=new dev.sgs.core.engine.skill.skills.KeJiSkill(player,engine);
        persistentSkills.put(activeKey(player,"keji"),skill);
        bus.register(skill);skill.usageHooks().forEach(bus::register);
      }
      case "qixi" -> bus.register(new dev.sgs.core.engine.skill.skills.QiXiSkill(player));
      case "jijiu" -> bus.register(new dev.sgs.core.engine.skill.skills.JiJiuSkill(player));
      case "yingzi" -> bus.registerRule(new dev.sgs.core.engine.skill.skills.YingZiSkill(player,engine));
      case "fanjian" -> registerActive(player,new dev.sgs.core.engine.skill.skills.FanJianSkill(player));
      case "qianxun" -> bus.registerRule(new dev.sgs.core.engine.skill.skills.QianXunSkill(player));
      case "lianying" -> bus.register(new dev.sgs.core.engine.skill.skills.LianYingSkill(player,engine));
      case "qingnang" -> registerActive(player,new dev.sgs.core.engine.skill.skills.QingNangSkill(player));
      case "fankui" -> bus.register(new dev.sgs.core.engine.skill.skills.FanKuiSkill(player,engine));
      case "guicai" -> bus.register(new dev.sgs.core.engine.skill.skills.GuiCaiSkill(player,engine));
      case "tiandu" -> bus.register(new dev.sgs.core.engine.skill.skills.TianDuSkill(player,engine));
      case "yiji" -> bus.register(new dev.sgs.core.engine.skill.skills.YiJiSkill(player,engine));
      case "luoyi" -> {
        var skill=new dev.sgs.core.engine.skill.skills.LuoYiSkill(player,engine);
        bus.registerRule(skill);bus.register(skill.damageHook());persistentSkills.put(activeKey(player,"luoyi"),skill);
      }
      case "xiaoji" -> bus.register(new dev.sgs.core.engine.skill.skills.XiaoJiSkill(player,engine));
      case "jieyin" -> registerActive(player,new dev.sgs.core.engine.skill.skills.JieYinSkill(player));
      case "qingguo" -> bus.register(new dev.sgs.core.engine.skill.skills.QingGuoSkill(player));
      case "tuxi" -> bus.registerRule(new dev.sgs.core.engine.skill.skills.TuXiSkill(player,engine));
      case "luoshen" -> bus.register(new dev.sgs.core.engine.skill.skills.LuoShenSkill(player,engine));
      case "ganglie" -> bus.register(new dev.sgs.core.engine.skill.skills.GangLieSkill(player,engine));
      case "lijian" -> registerActive(player,new dev.sgs.core.engine.skill.skills.LiJianSkill(player));
      case "biyue" -> bus.register(new dev.sgs.core.engine.skill.skills.BiYueSkill(player,engine));
      case "kongcheng" -> bus.registerRule(new dev.sgs.core.engine.skill.skills.KongChengSkill(player));
      case "guose" -> bus.register(new dev.sgs.core.engine.skill.skills.GuoSeSkill(player));
      case "guanxing" -> bus.register(new dev.sgs.core.engine.skill.skills.GuanXingSkill(player,engine));
      case "wushuang" -> {
        bus.registerRule(new dev.sgs.core.engine.skill.skills.WuShuangSkill(player,RuleId.SHA_DODGE_COUNT));
        bus.registerRule(new dev.sgs.core.engine.skill.skills.WuShuangSkill(player,RuleId.DUEL_SHA_COUNT));
      }
      case "tieji" -> bus.registerRule(new dev.sgs.core.engine.skill.skills.TieJiSkill(player,engine));
      case "mashu" -> bus.registerRule(new dev.sgs.core.engine.skill.skills.MaShuSkill(player));
      case "liuli" -> bus.register(new dev.sgs.core.engine.skill.skills.LiuLiSkill(player,engine));
      case "liegong" -> bus.registerRule(new dev.sgs.core.engine.skill.skills.LieGongSkill(player,engine));
      case "kuanggu" -> bus.register(new dev.sgs.core.engine.skill.skills.KuangGuSkill(player,engine));
      case "duanliang" -> {var skill=new dev.sgs.core.engine.skill.skills.DuanLiangSkill(player);bus.register(skill);bus.registerRule(skill.distanceRule());}
      case "qiangxi" -> registerActive(player,new dev.sgs.core.engine.skill.skills.QiangXiSkill(player));
      case "yizhong" -> bus.registerRule(new dev.sgs.core.engine.skill.skills.YiZhongSkill(player));
      case "yaowu" -> bus.register(new dev.sgs.core.engine.skill.skills.YaoWuSkill(player,engine));
      case "yicong" -> {
        bus.registerRule(new dev.sgs.core.engine.skill.skills.YiCongSkill(player));
        engine.gameState().registerPublicStatus(player.seat(),"yicong:defense","距离+1","义从：体力不大于2，其他角色与你的距离+1",()->player.alive() && player.hp()<=2);
        engine.gameState().registerPublicStatus(player.seat(),"yicong:offense","义从","义从：体力大于2，你与其他角色的距离-1",()->player.alive() && player.hp()>2);
      }
      default -> {
        if(mythic.bind(player,skillId)){
          if(dev.sgs.core.engine.skill.skills.MythicRules.ACTIVE.contains(skillId))registerActive(player,mythic.active(player,skillId));
          return true;
        }
        var factory=catalog.factory(skillId).orElse(null);
        if(factory==null)return false;
        var binding=java.util.Objects.requireNonNull(factory.create(player,engine));binding.validate(skillId,player);
        if(binding.active().size()==1 && binding.active().getFirst() instanceof PlannedActiveSkill
            && binding.active().getFirst() instanceof PersistentSkillMemory
            && binding.listeners().isEmpty() && binding.modifiers().isEmpty())programmedExternalSkills.add(activeKey(player,skillId));
        java.util.stream.Stream.of(binding.active().stream(),binding.listeners().stream(),binding.modifiers().stream())
            .flatMap(s->s).filter(s->!(s instanceof PersistentSkillMemory)).forEach(s->untrackedExternalSkills.add(activeKey(player,skillId)));
        binding.active().forEach(skill->registerActive(player,skill));
        // A binding may expose one shared state object through several hook roles.
        // Distinct mutable state objects under one skill key would silently overwrite each other.
        binding.listeners().forEach(skill->registerMemory(player,skillId,skill));
        binding.modifiers().forEach(skill->registerMemory(player,skillId,skill));
        binding.listeners().forEach(bus::register);binding.modifiers().forEach(bus::registerRule);
      }
    }
    return true;
  }

  private void registerActive(SgsPlayer player, ActiveSkill skill) {
    activeSkills.put(activeKey(player, skill.id()), skill);
    if(skill instanceof PersistentSkillMemory memory)persistentSkills.put(activeKey(player,skill.id()),java.util.Objects.requireNonNull(memory.memoryOwner()));
  }

  public boolean supportsExternalIdleCheckpoint(SgsPlayer player){
    var h=player.hero();
    return catalog.supportsIdleCheckpoint(h.id()) && catalog.hero(h.id()).filter(c->c.kingdom()==h.kingdom()
        && c.maxHp()==h.maxHp() && c.male()==h.male() && c.skillIds().equals(h.skillIds())).isPresent()
        && h.skillIds().stream().noneMatch(id->untrackedExternalSkills.contains(activeKey(player,id)));
  }

  /** Inspect actual bound roles, never trust the catalog declaration alone. */
  public boolean supportsExternalProgrammedCheckpoint(SgsPlayer player){
    return player.hero()!=null && catalog.supportsProgrammedCheckpoint(player.hero().id())
        && supportsExternalIdleCheckpoint(player)
        && player.hero().skillIds().stream().allMatch(id->programmedExternalSkills.contains(activeKey(player,id)));
  }

  private void registerMemory(SgsPlayer player,String id,Object skill){
    if(skill instanceof PersistentSkillMemory memory){
      memory=java.util.Objects.requireNonNull(memory.memoryOwner());
      var previous=persistentSkills.putIfAbsent(activeKey(player,id),memory);
      if(previous!=null && previous!=memory)throw new IllegalArgumentException("multiple state owners for skill "+id);
    }
  }

  /** All fields are required, including unused (-1) counters. Missing memory is never a reset. */
  public Map<String,Map<String,Long>> saveMemory(){
    if(mythic.hasSharedFaceState())persistentSkills.put("0:mythic",mythic);
    var result=new LinkedHashMap<String,Map<String,Long>>();
    persistentSkills.forEach((key,skill)->result.put(key,Map.copyOf(skill.saveMemory())));
    return Map.copyOf(result);
  }
  public void restoreMemory(Map<String,Map<String,Long>> values,int turnNumber){
    if(values!=null&&values.containsKey("0:mythic")){
      if(!mythic.active()&&values.get("0:mythic").keySet().stream().anyMatch(key->!key.matches("[0-7]:(faceDown|initialized)")))
        throw new IllegalArgumentException("non-Mythic match has unsupported shared memory");
      persistentSkills.put("0:mythic",mythic);mythic.prepareRestoration(values.get("0:mythic"),turnNumber);
    }
    if(values==null || !values.keySet().equals(persistentSkills.keySet()))throw new IllegalArgumentException("skill memory layout mismatch");
    // Validate the whole set first: a malformed later entry cannot partially reset earlier skills.
    persistentSkills.forEach((key,skill)->skill.validateMemory(values.get(key),turnNumber));
    persistentSkills.forEach((key,skill)->skill.restoreMemory(values.get(key),turnNumber));
  }

  public Optional<ActiveSkill> activeSkill(SgsPlayer player, String skillId) {
    return Optional.ofNullable(activeSkills.get(activeKey(player, skillId)));
  }
  /** Only queried on the owning game thread. Never exports persistent skill memory. */
  public Map<String,String> activeAvailability(SgsPlayer player){
    var result=new LinkedHashMap<String,String>();var state=engine.gameState();
    if(player==null || player.hero()==null)return Map.of();
    for(String id:mythic.effectiveSkills(player)){
      var skill=activeSkills.get(activeKey(player,id));if(skill==null)continue;
      String reason=!player.alive()?"PLAYER_DEAD":state.status()!=dev.sgs.core.GameStatus.RUNNING?"GAME_NOT_RUNNING"
          :state.currentPlayer()!=player?"NOT_YOUR_TURN":state.phase()!=dev.sgs.core.Phase.PLAY?"WRONG_PHASE"
          :id.equals("jijiang") && player.identity()!=dev.sgs.core.Identity.LORD?"LORD_ONLY":skill.unavailableReason(engine);
      result.put(id,reason==null?"":reason);
    }
    return Map.copyOf(result);
  }
  public boolean provideHujia(SgsPlayer player){var skill=hujia.get(player.seat());return mythic.has(player,"hujia") && skill!=null && skill.provide();}
  public java.util.Optional<dev.sgs.core.card.GameCard> provideJijiang(SgsPlayer player,boolean offer){
    var skill=jijiang.get(player.seat());return skill==null||!mythic.has(player,"jijiang")?java.util.Optional.empty():skill.provide(offer);
  }

  private static String activeKey(SgsPlayer player, String skillId) {
    return player.seat() + ":" + skillId;
  }
}
