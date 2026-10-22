package dev.sgs.core.addon;

import dev.sgs.core.hero.*;
import java.util.*;
import static dev.sgs.core.addon.SkillPresentation.Kind.*;
import static dev.sgs.core.addon.SkillPresentation.Target.*;

/** Immutable server-selected catalog; built-in IDs are never replaceable by an extension. */
public final class HeroCatalog {
  private final Map<String,HeroDefinition> heroes;
  private final Map<String,HeroPresentation> presentations;
  private final Map<String,SkillPresentation> skills;
  private final Map<String,SkillFactory> factories;
  private final List<PackManifest> packs;
  private final Set<String> idleCheckpointHeroes;
  private final Set<String> programmedCheckpointHeroes;
  private final Map<String,HeroPack> providers;
  private final Map<String,String> packFingerprints;
  private HeroCatalog(Builder b){
    programmedCheckpointHeroes=Set.copyOf(b.programmedCheckpointHeroes);
    heroes=Collections.unmodifiableMap(new LinkedHashMap<>(b.heroes));skills=Map.copyOf(b.skills);factories=Map.copyOf(b.factories);packs=List.copyOf(b.packs);idleCheckpointHeroes=Set.copyOf(b.idleCheckpointHeroes);providers=Map.copyOf(b.providers);packFingerprints=Map.copyOf(b.packFingerprints);
    var p=new LinkedHashMap<String,HeroPresentation>();
    for(var h:heroes.values()){
      var display=b.display.get(h.id());
      var list=h.skillIds().stream().map(id->{var skill=skills.get(id);if(skill==null)throw new IllegalArgumentException("unknown skill "+id+" for "+h.id());return skill;}).toList();
      p.put(h.id(),new HeroPresentation(h.id(),display[0],display[1],display[2],list));
    }
    presentations=Map.copyOf(p);
  }
  public List<HeroDefinition> heroes(){return List.copyOf(heroes.values());}
  public Optional<HeroDefinition> hero(String id){return Optional.ofNullable(heroes.get(id));}
  public Optional<SkillPresentation> skill(String id){return Optional.ofNullable(skills.get(id));}
  public Optional<SkillFactory> factory(String id){return Optional.ofNullable(factories.get(id));}
  public List<PackManifest> packs(){return packs;}
  public boolean supportsIdleCheckpoint(String heroId){return idleCheckpointHeroes.contains(heroId);}
  public boolean supportsProgrammedCheckpoint(String heroId){return programmedCheckpointHeroes.contains(heroId);}
  public Map<String,Long> packIdentity(String heroId){
    var namespace=heroId.split(":",2)[0];var manifest=packs.stream().filter(p->p.id().equals(namespace)).findFirst().orElseThrow();
    try{
      var data=(manifest.toString()+"/"+packFingerprints.getOrDefault(namespace,"")).getBytes(java.nio.charset.StandardCharsets.UTF_8);
      var bytes=java.nio.ByteBuffer.wrap(java.security.MessageDigest.getInstance("SHA-256").digest(data));
      return Map.of("schema",1L,"p0",bytes.getLong(),"p1",bytes.getLong(),"p2",bytes.getLong(),"p3",bytes.getLong());
    }catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}
  }
  private HeroPack promptProvider(dev.sgs.core.engine.PromptRequest prompt){var id=prompt.skillId();int colon=id.indexOf(':');return colon<1?null:providers.get(id.substring(0,colon));}
  public Optional<Boolean> botInvoke(dev.sgs.core.game.GameState state,dev.sgs.core.engine.PromptRequest prompt){var provider=promptProvider(prompt);return provider==null?Optional.empty():Objects.requireNonNull(provider.botInvoke(state,prompt));}
  public Optional<dev.sgs.core.engine.SkillChoice> botChoice(dev.sgs.core.game.GameState state,dev.sgs.core.engine.PromptRequest prompt){var provider=promptProvider(prompt);return provider==null?Optional.empty():Objects.requireNonNull(provider.botChoice(state,prompt));}
  public Optional<BotSkillAction> botAction(dev.sgs.core.game.GameState state,dev.sgs.core.player.SgsPlayer actor){
    if(actor.hero()==null||!actor.hero().id().contains(":"))return Optional.empty();
    var namespace=actor.hero().id().split(":",2)[0];var provider=providers.get(namespace);if(provider==null)return Optional.empty();
    var action=Objects.requireNonNull(provider.botAction(state,actor));
    action.ifPresent(a->{namespaced(namespace,a.skillId());if(!actor.hero().skillIds().contains(a.skillId()))throw new IllegalArgumentException("AI proposal uses an unowned skill");});return action;
  }
  public List<HeroPresentation> present(Collection<String> ids){return ids.stream().distinct().map(presentations::get).filter(Objects::nonNull).toList();}
  public static HeroCatalog builtins(){return builder().build();}
  public static Builder builder(){return new Builder();}
  static void text(String value,int max){if(value==null || value.isBlank() || value.length()>max)throw new IllegalArgumentException("invalid text length");}
  static void namespaced(String namespace,String id){if(id==null || !id.matches(java.util.regex.Pattern.quote(namespace)+":[a-z][a-z0-9_]{0,63}"))throw new IllegalArgumentException("ID must belong to "+namespace+": "+id);}
  public static final class Builder {
    private final Map<String,HeroDefinition> heroes=new LinkedHashMap<>();
    private final Map<String,String[]> display=new LinkedHashMap<>();
    private final Map<String,SkillPresentation> skills=new LinkedHashMap<>();
    private final Map<String,SkillFactory> factories=new LinkedHashMap<>();
    private final List<PackManifest> packs=new ArrayList<>();
    private final Set<String> idleCheckpointHeroes=new HashSet<>();
    private final Set<String> programmedCheckpointHeroes=new HashSet<>();
    private final Map<String,HeroPack> providers=new HashMap<>();
    private final Map<String,String> packFingerprints=new HashMap<>();
    private Builder(){
      builtin("rende","仁德","将手牌交给其他角色，每回合累计两张后回复1点体力。",ACTIVE,OTHER,1,160,false);
      builtin("jijiang","激将","主公技，请求其他蜀角色提供杀；主动使用时选择攻击目标。",ACTIVE,OTHER,0,0,true);
      builtin("paoxiao","咆哮","锁定技，使用杀无次数限制。",PASSIVE,NONE,0,0,false);
      builtin("longdan","龙胆","杀当闪、闪当杀使用或打出。",TOGGLE,ANY,1,1,false);
      builtin("jianxiong","奸雄","受到伤害存活后，可取得造成伤害且仍在处理区的实体牌。",TRIGGERED,NONE,0,0,false);
      builtin("hujia","护驾","主公技，需要闪时请求其他魏角色提供闪。",TRIGGERED,NONE,0,0,true);
      builtin("kurou","苦肉","失去1点体力，濒死结算后存活则摸两张牌。",ACTIVE,NONE,0,0,false);
      builtin("wusheng","武圣","将红色手牌或装备当普通杀使用或打出。",TOGGLE,ANY,1,1,false);
      builtin("jizhi","集智","使用非延时锦囊时，可以摸一张牌；重铸不属于使用。",TRIGGERED,NONE,0,0,false);
      builtin("qicai","奇才","锁定技，使用锦囊牌无距离限制。",PASSIVE,NONE,0,0,false);
      builtin("zhiheng","制衡","出牌阶段每回合限一次，弃置任意数量手牌或装备，摸等量牌。",ACTIVE,NONE,1,160,false,true);
      builtin("jiuyuan","救援","主公技，其他吴角色于你濒死时对你用桃，你额外回复1点体力；自己用桃不增加。",PASSIVE,NONE,0,0,true);
      builtin("keji","克己","若本回合出牌阶段未使用或打出杀，可跳过弃牌阶段。",TRIGGERED,NONE,0,0,false);
      builtin("qixi","奇袭","出牌阶段，将一张黑色手牌或装备当过河拆桥使用。",TOGGLE,OTHER,1,1,false,true);
      builtin("jijiu","急救","自己的回合外，将一张红色手牌或装备当桃使用；可救援自己。",TOGGLE,ANY,1,1,false,true);
      builtin("qingnang","青囊","出牌阶段每回合限一次，弃一张手牌，使任一受伤角色回复1点体力。",ACTIVE,ANY,1,1,false);
      builtin("yingzi","英姿","摸牌阶段，可额外摸一张牌。",TRIGGERED,NONE,0,0,false);
      builtin("fanjian","反间","出牌阶段每回合限一次：其他角色猜花色，随机展示你的一张手牌；猜错受到你造成的1点伤害，伤害及濒死结算后存活才获得此牌。",ACTIVE,OTHER,0,0,false);
      builtin("qianxun","谦逊","锁定技，不能成为顺手牵羊和乐不思蜀的目标。",PASSIVE,NONE,0,0,false);
      builtin("lianying","连营","失去最后的手牌后，可以摸一张牌。",TRIGGERED,NONE,0,0,false);
      builtin("fankui","反馈","每次受到伤害存活后，可获得伤害来源一张牌：抽取一张背面手牌或选择装备；不能取判定区。",TRIGGERED,NONE,0,0,false);
      builtin("guicai","鬼才","判定牌揭示后、生效前，可用一张手牌替换判定牌；同次判定限一次。",TRIGGERED,NONE,0,0,false);
      builtin("tiandu","天妒","自己的判定牌生效后，可获得最终判定牌；不改变已确定的判定结果。",TRIGGERED,NONE,0,0,false);
      builtin("yiji","遗计","每受到1点伤害存活后，可观看牌堆顶两张牌并逐张交给任意存活角色，也可留给自己。",TRIGGERED,NONE,0,0,false);
      builtin("luoyi","裸衣","摸牌阶段可少摸一张牌；本回合由你以杀或决斗造成的伤害增加1点。",TRIGGERED,NONE,0,0,false);
      builtin("jieyin","结姻","出牌阶段限一次，弃置两张手牌并选择一名受伤的其他男性角色，你与其各回复1点体力。",ACTIVE,OTHER,2,2,false);
      builtin("xiaoji","枭姬","一次失去装备区里的牌后，你可以摸两张牌；同一次失去多张也只发动一次。",TRIGGERED,NONE,0,0,false);
      builtin("tuxi","突袭","摸牌阶段可放弃摸牌，依次选择至多两名不同其他角色，各随机获得其一张手牌。",TRIGGERED,NONE,0,0,false);
      builtin("guanxing","观星","准备阶段可观看牌堆顶至多五张牌（不超过存活角色数），依选择顺序分别放到牌堆顶或底；未选择的牌按原序放回顶。",TRIGGERED,NONE,0,0,false);
      builtin("kongcheng","空城","锁定技，没有手牌时不能成为杀或决斗的目标；已确定目标后失去最后手牌不取消该牌。",PASSIVE,NONE,0,0,false);
      builtin("qingguo","倾国","可以将一张黑色手牌当闪使用或打出；装备区牌不适用。",TOGGLE,ANY,1,1,false);
      builtin("luoshen","洛神","准备阶段可反复判定，黑色则取得最终判定牌，红色或自行停止时结束。",TRIGGERED,NONE,0,0,false);
      builtin("ganglie","刚烈","每次受到伤害存活后可判定，非红桃时伤害来源弃置两张手牌或受到你造成的1点伤害。",TRIGGERED,NONE,0,0,false);
      builtin("lijian","离间","出牌阶段限一次，弃一张手牌或装备并选择两名其他男性，令第二名对第一名使用不可无懈的决斗（第一名先打杀）。",ACTIVE,OTHER,1,1,false,true);
      builtin("biyue","闭月","结束阶段可以摸一张牌；在弃牌阶段之后结算。",TRIGGERED,NONE,0,0,false);
      builtin("mashu","马术","锁定技，计算与其他角色的距离时减少1，最低为1。",PASSIVE,NONE,0,0,false);
      builtin("tieji","铁骑","使用杀指定目标后可判定，红色则此杀不能被闪避；不忽略被动防具。",TRIGGERED,NONE,0,0,false);
      builtin("guose","国色","出牌阶段可将一张方块手牌或装备当乐不思蜀使用。",TOGGLE,OTHER,1,1,false,true);
      builtin("liuli","流离","成为杀目标时，可弃一张手牌或装备，将杀转移给弃费后自己攻击范围内另一角色，不能转给杀的使用者。",TRIGGERED,NONE,0,0,false);
      builtin("wushuang","无双","锁定技，你的杀须连续两张闪抵消；与你决斗的另一方每轮须连续打出两张杀。",PASSIVE,NONE,0,0,false);
      builtin("liegong","烈弓","出牌阶段使用杀指定目标后，若其手牌数不小于你的体力值或不大于你的攻击范围，可令此杀不能被闪响应。",TRIGGERED,NONE,0,0,false);
      builtin("kuanggu","狂骨","锁定技，对距离1以内的角色每造成1点伤害后，回复1点体力；距离在伤害发生前确定。",PASSIVE,NONE,0,0,false);
      builtin("duanliang","断粮","出牌阶段，可将一张黑色基本牌或装备牌当兵粮寸断使用；你使用兵粮寸断的距离上限为2。",TOGGLE,OTHER,1,1,false,true);
      builtin("qiangxi","强袭","出牌阶段限一次，失去1点体力或弃置一张武器，对攻击范围内一名其他角色造成1点伤害；弃置已装备武器时按弃置后的攻击范围检查。",ACTIVE,OTHER,0,1,false,true);
      builtin("yizhong","毅重","锁定技，未装备防具时，黑色杀对你无效；青釭剑不能无视此武将技能。",PASSIVE,NONE,0,0,false);
      builtin("yaowu","耀武","锁定技，受到红色杀的伤害时，伤害来源选择回复1点体力或摸一张牌；满体力时摸牌。",PASSIVE,NONE,0,0,false);
      builtin("yicong","义从","锁定技，体力大于2时，计算与其他角色的距离减1；体力不大于2时，其他角色计算与你的距离加1。",PASSIVE,NONE,0,0,false);
      Map<String,String> names=new LinkedHashMap<>(Map.of("liubei","刘备","zhangfei","张飞","zhaoyun","赵云","caocao","曹操","guanyu","关羽","huanggai","黄盖","huangyueying","黄月英"));
      names.put("sunquan","孙权");names.put("lumeng","吕蒙");
      names.put("zhouyu","周瑜");names.put("luxun","陆逊");
      names.put("ganning","甘宁");names.put("huatuo","华佗");
      names.put("simayi","司马懿");names.put("guojia","郭嘉");
      names.put("xuchu","许褚");names.put("sunshangxiang","孙尚香");
      names.put("zhangliao","张辽");
      names.put("zhugeliang","诸葛亮");
      names.put("zhenji","甄姬");names.put("xiahoudun","夏侯惇");names.put("diaochan","貂蝉");
      names.put("machao","马超");names.put("daqiao","大乔");names.put("lvbu","吕布");
      names.put("huangzhong","黄忠");names.put("weiyan","魏延");names.put("xuhuang","徐晃");names.put("dianwei","典韦");
      names.put("yujin","于禁");names.put("huaxiong","华雄");names.put("gongsunzan","公孙瓒");
      MythicPresentations.ALL.forEach(s->skills.put(s.id(),s));
      dev.sgs.core.hero.MythicHeroes.ADDED.forEach(e->names.put(e.hero().id(),e.name()));
      for(var h:StandardHeroes.CATALOG){heroes.put(h.id(),h);display.put(h.id(),new String[]{names.getOrDefault(h.id(),h.id()),java.util.Set.of("huangzhong","weiyan","xuhuang","dianwei","yujin","huaxiong","gongsunzan").contains(h.id())?"经典扩展武将":"标准武将",""});}
      for(var h:StandardHeroes.CATALOG){var pack=dev.sgs.core.hero.MythicHeroes.pack(h.id());if(!pack.isEmpty())display.get(h.id())[1]="神话再临·"+pack;}
    }
    private void builtin(String id,String name,String desc,SkillPresentation.Kind kind,SkillPresentation.Target target,int min,int max,boolean lord){skills.put(id,new SkillPresentation(id,name,desc,kind,target,min,max,lord));}
    private void builtin(String id,String name,String desc,SkillPresentation.Kind kind,SkillPresentation.Target target,int min,int max,boolean lord,boolean equipment){skills.put(id,new SkillPresentation(id,name,desc,kind,target,min,max,lord,equipment));}
    public Builder add(PackManifest manifest,HeroPack provider){
      return add(manifest,provider,"");
    }
    public Builder add(PackManifest manifest,HeroPack provider,String sha256){
      if(sha256==null||!sha256.isEmpty()&&!sha256.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("invalid pack fingerprint");
      Objects.requireNonNull(manifest);Objects.requireNonNull(provider);
      if(packs.stream().anyMatch(p->p.id().equals(manifest.id())))throw new IllegalArgumentException("duplicate pack "+manifest.id());
      var stagedHeroes=new LinkedHashMap<String,HeroDefinition>();var stagedDisplay=new LinkedHashMap<String,String[]>();
      var stagedSkills=new LinkedHashMap<String,SkillPresentation>();var stagedFactories=new LinkedHashMap<String,SkillFactory>();
      var stagedCheckpoints=new HashSet<String>();var stagedPrograms=new HashSet<String>();
      provider.register(new HeroPackRegistrar(){
        public void idleCheckpoint(String heroId){namespaced(manifest.id(),heroId);stagedCheckpoints.add(heroId);}
        public void programmedCheckpoint(String heroId){namespaced(manifest.id(),heroId);stagedPrograms.add(heroId);stagedCheckpoints.add(heroId);}
        public void skill(SkillPresentation presentation,SkillFactory factory){
          namespaced(manifest.id(),presentation.id());Objects.requireNonNull(factory);
          if(skills.containsKey(presentation.id()) || stagedSkills.putIfAbsent(presentation.id(),presentation)!=null)throw new IllegalArgumentException("duplicate skill "+presentation.id());
          stagedFactories.put(presentation.id(),factory);
        }
        public void hero(HeroDefinition h,String name,String description,String portrait){
          namespaced(manifest.id(),h.id());text(name,32);text(description,512);
          if(h.maxHp()>20 || h.skillIds().size()>8 || h.skillIds().stream().distinct().count()!=h.skillIds().size())throw new IllegalArgumentException("invalid hero attributes");
          new HeroPresentation(h.id(),name,description,portrait,List.of());
          if(!portrait.isEmpty() && (!portrait.startsWith(manifest.id()+":") || !portrait.endsWith(".png") || portrait.contains("..")))throw new IllegalArgumentException("portrait must be own namespace PNG resource");
          if(heroes.containsKey(h.id()) || stagedHeroes.putIfAbsent(h.id(),h)!=null)throw new IllegalArgumentException("duplicate hero "+h.id());
          stagedDisplay.put(h.id(),new String[]{name,description,portrait});
        }
      });
      if(stagedHeroes.isEmpty() || stagedHeroes.size()>64 || stagedSkills.size()>128)throw new IllegalArgumentException("invalid pack size");
      if(!stagedHeroes.keySet().containsAll(stagedCheckpoints))throw new IllegalArgumentException("checkpoint contract requires an own registered hero");
      for(var id:stagedPrograms)if(stagedHeroes.get(id).skillIds().stream().anyMatch(s->!stagedFactories.containsKey(s)))throw new IllegalArgumentException("programmed hero requires own external skill factories");
      for(var h:stagedHeroes.values())for(var skill:h.skillIds())if(!skills.containsKey(skill) && !stagedSkills.containsKey(skill))throw new IllegalArgumentException("unknown skill "+skill);
      programmedCheckpointHeroes.addAll(stagedPrograms);
      heroes.putAll(stagedHeroes);display.putAll(stagedDisplay);skills.putAll(stagedSkills);factories.putAll(stagedFactories);idleCheckpointHeroes.addAll(stagedCheckpoints);providers.put(manifest.id(),provider);packFingerprints.put(manifest.id(),sha256);packs.add(manifest);return this;
    }
    public HeroCatalog build(){return new HeroCatalog(this);}
  }
}
