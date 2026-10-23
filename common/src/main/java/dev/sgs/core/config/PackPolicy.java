package dev.sgs.core.config;

import dev.sgs.core.addon.HeroCatalog;
import dev.sgs.core.hero.HeroDefinition;
import dev.sgs.core.hero.MythicHeroes;
import dev.sgs.core.match.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;

/** Immutable new-match admission policy. Never unloads code used by existing matches. */
public record PackPolicy(Map<String,Boolean> enabled) {
  public static final String FILE_NAME="packs.properties";
  public static final String TEMPLATE="""
      # 三国杀内容包配置（UTF-8），修改后执行 /sgs reload，无需重启。
      # 只影响之后开局的牌堆/选将池；进行中对局和恢复存档保留原规则。
      cards.standard.enabled=true
      cards.ex.enabled=true
      cards.junzheng.enabled=true
      # 完整逐鹿牌堆尚未实现，暂不能设为 true；重载失败保留旧配置。
      cards.zhulu.enabled=false
      heroes.standard.enabled=true
      heroes.wind.enabled=true
      heroes.fire.enabled=true
      heroes.forest.enabled=true
      heroes.mountain.enabled=true
      # 内置兼容武将：于禁、华雄、公孙瓒。
      heroes.legacy.enabled=true
      # 已安装 SDK 包按 manifest.id 控制；省略时启用，例如：
      # heroes.addon.yjcm2011.enabled=false
      # 开关可热更新；安装/更新/移除可执行 JAR 本身仍需重启。
      """;
  private static final Set<String> BUILTINS=Set.of("cards.standard","cards.ex","cards.junzheng","cards.zhulu",
      "heroes.standard","heroes.wind","heroes.fire","heroes.forest","heroes.mountain","heroes.legacy");
  public PackPolicy { enabled=Map.copyOf(enabled); }
  public static PackPolicy defaults(){return new PackPolicy(Map.of());}
  public boolean allows(String id){return enabled.getOrDefault(id,!id.equals("cards.zhulu"));}
  public static PackPolicy load(Path directory)throws IOException {
    Files.createDirectories(directory);var file=directory.resolve(FILE_NAME);
    if(!Files.exists(file))try{Files.writeString(file,TEMPLATE,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(FileAlreadyExistsException ignored){}
    if(Files.size(file)>32768)throw new IllegalArgumentException("卡包配置超过 32 KiB");
    var p=new Properties();try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)){p.load(reader);}
    var values=new HashMap<String,Boolean>();
    for(String key:p.stringPropertyNames()) {
      if(!key.endsWith(".enabled"))throw new IllegalArgumentException("未知卡包配置项: "+key);
      String id=key.substring(0,key.length()-8);
      if(!BUILTINS.contains(id)&&!id.matches("heroes\\.addon\\.[a-z][a-z0-9_]{1,31}"))throw new IllegalArgumentException("未知卡包配置项: "+key);
      String value=p.getProperty(key).strip();if(!value.equals("true")&&!value.equals("false"))throw new IllegalArgumentException(key+" 必须为 true 或 false");
      values.put(id,Boolean.parseBoolean(value));
    }
    var result=new PackPolicy(values);
    if(result.allows("cards.zhulu"))throw new IllegalArgumentException("逐鹿天下完整卡包尚未实现，cards.zhulu.enabled 必须为 false");
    if(result.contentPool().size()==0)throw new IllegalArgumentException("至少启用一个游戏牌包");
    return result;
  }
  public ContentPool contentPool(){return ContentPool.selected(allows("cards.standard"),allows("cards.ex"),allows("cards.junzheng"));}
  public static String heroPack(HeroDefinition hero) {
    String id=hero.id();if(id.contains(":"))return "heroes.addon."+id.substring(0,id.indexOf(':'));
    return switch(MythicHeroes.pack(id)) {
      case "风"->"heroes.wind";case "火"->"heroes.fire";case "林"->"heroes.forest";case "山"->"heroes.mountain";
      default->Set.of("yujin","huaxiong","gongsunzan").contains(id)?"heroes.legacy":"heroes.standard";
    };
  }
  public void validateCatalog(HeroCatalog catalog) {
    for(var entry:enabled.entrySet())if(entry.getKey().startsWith("heroes.addon.") && entry.getValue()) {
      String id=entry.getKey().substring("heroes.addon.".length());
      if(catalog.packs().stream().noneMatch(p->p.id().equals(id)))throw new IllegalArgumentException("未安装的武将包: "+id);
    }
    if(catalog.heroes().stream().filter(h->allows(heroPack(h))).count()<3)throw new IllegalArgumentException("启用的武将不足 3 名");
  }
  public MatchConfig apply(MatchConfig base) {
    return new MatchConfig(base.responseTimeoutSeconds(),base.turnTimeoutSeconds(),base.selectionTimeoutSeconds(),base.botStepDelayMillis(),
        base.heroPool().stream().filter(h->allows(heroPack(h))).toList(),base.roomSettings(),contentPool(),base.heroBansEnabled(),base.openingHandEnabled());
  }
}
