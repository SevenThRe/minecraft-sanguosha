package dev.sgs.core.hero;

import dev.sgs.core.Kingdom;
import java.util.*;

/** Original Wind/Fire/Forest/Mountain ordinary roster. God generals are a separate product. */
public final class MythicHeroes {
  public record Entry(HeroDefinition hero,String name,String pack) {}
  private static Entry h(String id,String name,String pack,Kingdom k,int hp,boolean male,String... skills){
    return new Entry(new HeroDefinition(id,k,hp,List.of(skills),male),name,pack);
  }
  public static final List<Entry> ADDED=List.of(
      h("xiahouyuan","夏侯渊","风",Kingdom.WEI,4,true,"shensu"),
      h("caoren","曹仁","风",Kingdom.WEI,4,true,"jushou"),
      h("xiaoqiao","小乔","风",Kingdom.WU,3,false,"tianxiang","hongyan"),
      h("zhoutai","周泰","风",Kingdom.WU,4,true,"buqu"),
      h("zhangjiao","张角","风",Kingdom.QUN,3,true,"leiji","guidao","huangtian"),
      h("yuji","于吉","风",Kingdom.QUN,3,true,"guhuo"),
      h("xunyu","荀彧","火",Kingdom.WEI,3,true,"quhu","jieming"),
      h("wolong","卧龙诸葛亮","火",Kingdom.SHU,3,true,"bazhen","huoji","kanpo"),
      h("pangtong","庞统","火",Kingdom.SHU,3,true,"lianhuan","niepan"),
      h("taishici","太史慈","火",Kingdom.WU,4,true,"tianyi"),
      h("yuanshao","袁绍","火",Kingdom.QUN,4,true,"luanji","xueyi"),
      h("yanliangwenchou","颜良文丑","火",Kingdom.QUN,4,true,"shuangxiong"),
      h("pangde","庞德","火",Kingdom.QUN,4,true,"mashu","mengjin"),
      h("caopi","曹丕","林",Kingdom.WEI,3,true,"xingshang","fangzhu","songwei"),
      h("menghuo","孟获","林",Kingdom.SHU,4,true,"huoshou","zaiqi"),
      h("zhurong","祝融","林",Kingdom.SHU,4,false,"juxiang","lieren"),
      h("sunjian","孙坚","林",Kingdom.WU,4,true,"yinghun"),
      h("lusu","鲁肃","林",Kingdom.WU,3,true,"haoshi","dimeng"),
      h("dongzhuo","董卓","林",Kingdom.QUN,8,true,"jiuchi","roulin","benghuai","baonue"),
      h("jiaxu","贾诩","林",Kingdom.QUN,3,true,"wansha","luanwu","weimu"),
      h("dengai","邓艾","山",Kingdom.WEI,4,true,"tuntian","zaoxian"),
      h("zhanghe","张郃","山",Kingdom.WEI,4,true,"qiaobian"),
      h("jiangwei","姜维","山",Kingdom.SHU,4,true,"tiaoxin","zhiji"),
      h("liushan","刘禅","山",Kingdom.SHU,3,true,"xiangle","fangquan","ruoyu"),
      h("sunce","孙策","山",Kingdom.WU,4,true,"jiang","hunzi","zhiba"),
      h("zhangzhaozhanghong","张昭张纮","山",Kingdom.WU,3,true,"zhijian","guzheng"),
      h("caiwenji","蔡文姬","山",Kingdom.QUN,3,false,"beige","duanchang"),
      h("zuoci","左慈","山",Kingdom.QUN,3,true,"huashen","xinsheng")
  );
  public static final Set<String> IDS=ADDED.stream().map(e->e.hero().id()).collect(java.util.stream.Collectors.toUnmodifiableSet());
  public static String pack(String id){
    if(Set.of("huangzhong","weiyan").contains(id))return "风";
    if(id.equals("dianwei"))return "火";
    if(id.equals("xuhuang"))return "林";
    return ADDED.stream().filter(e->e.hero().id().equals(id)).map(Entry::pack).findFirst().orElse("");
  }
  private MythicHeroes(){}
}
