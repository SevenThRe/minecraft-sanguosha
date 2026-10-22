package dev.sgs.core.hero;

import java.util.List;

import dev.sgs.core.Kingdom;
import dev.sgs.core.engine.skill.skills.LongDanSkill;
import dev.sgs.core.engine.skill.skills.PaoXiaoSkill;
import dev.sgs.core.engine.skill.skills.RenDeSkill;

/** Classic standard roster plus complete ordinary Wind/Fire/Forest/Mountain packs; the separate ALL list preserves fixed development fixtures. */
public final class StandardHeroes {

  public static final HeroDefinition LIUBEI =
      new HeroDefinition("liubei", Kingdom.SHU, 4, List.of(RenDeSkill.ID,"jijiang"));

  public static final HeroDefinition ZHANGFEI =
      new HeroDefinition("zhangfei", Kingdom.SHU, 4, List.of(PaoXiaoSkill.ID));

  public static final HeroDefinition ZHAOYUN =
      new HeroDefinition("zhaoyun", Kingdom.SHU, 4, List.of(LongDanSkill.ID));

  public static final HeroDefinition CAOCAO = new HeroDefinition("caocao",Kingdom.WEI,4,
      List.of(dev.sgs.core.engine.skill.skills.JianXiongSkill.ID,dev.sgs.core.engine.skill.skills.HuJiaSkill.ID));

  public static final List<HeroDefinition> ALL = List.of(LIUBEI, ZHANGFEI, ZHAOYUN, CAOCAO);
  public static final HeroDefinition GUANYU=new HeroDefinition("guanyu",Kingdom.SHU,4,List.of("wusheng"));
  public static final HeroDefinition HUANGGAI=new HeroDefinition("huanggai",Kingdom.WU,4,List.of("kurou"));
  public static final HeroDefinition HUANGYUEYING=new HeroDefinition("huangyueying",Kingdom.SHU,3,List.of("jizhi","qicai"));
  public static final HeroDefinition GANNING=new HeroDefinition("ganning",Kingdom.WU,4,List.of("qixi"));
  public static final HeroDefinition HUATUO=new HeroDefinition("huatuo",Kingdom.QUN,3,List.of("jijiu","qingnang"));
  public static final HeroDefinition SUNQUAN=new HeroDefinition("sunquan",Kingdom.WU,4,List.of("zhiheng","jiuyuan"));
  public static final HeroDefinition LUMENG=new HeroDefinition("lumeng",Kingdom.WU,4,List.of("keji"));
  public static final HeroDefinition ZHOUYU=new HeroDefinition("zhouyu",Kingdom.WU,3,List.of("yingzi","fanjian"));
  public static final HeroDefinition LUXUN=new HeroDefinition("luxun",Kingdom.WU,3,List.of("qianxun","lianying"));
  public static final HeroDefinition SIMAYI=new HeroDefinition("simayi",Kingdom.WEI,3,List.of("fankui","guicai"));
  public static final HeroDefinition GUOJIA=new HeroDefinition("guojia",Kingdom.WEI,3,List.of("tiandu","yiji"));
  public static final HeroDefinition XUCHU=new HeroDefinition("xuchu",Kingdom.WEI,4,List.of("luoyi"));
  public static final HeroDefinition SUNSHANGXIANG=new HeroDefinition("sunshangxiang",Kingdom.WU,3,List.of("jieyin","xiaoji"));
  public static final HeroDefinition ZHANGLIAO=new HeroDefinition("zhangliao",Kingdom.WEI,4,List.of("tuxi"));
  public static final HeroDefinition ZHUGELIANG=new HeroDefinition("zhugeliang",Kingdom.SHU,3,List.of("guanxing","kongcheng"));
  public static final HeroDefinition ZHENJI=new HeroDefinition("zhenji",Kingdom.WEI,3,List.of("qingguo","luoshen"));
  public static final HeroDefinition XIAHOUDUN=new HeroDefinition("xiahoudun",Kingdom.WEI,4,List.of("ganglie"));
  public static final HeroDefinition DIAOCHAN=new HeroDefinition("diaochan",Kingdom.QUN,3,List.of("lijian","biyue"));
  public static final HeroDefinition MACHAO=new HeroDefinition("machao",Kingdom.SHU,4,List.of("mashu","tieji"));
  public static final HeroDefinition DAQIAO=new HeroDefinition("daqiao",Kingdom.WU,3,List.of("guose","liuli"));
  public static final HeroDefinition LVBU=new HeroDefinition("lvbu",Kingdom.QUN,4,List.of("wushuang"));
  public static final HeroDefinition HUANGZHONG=new HeroDefinition("huangzhong",Kingdom.SHU,4,List.of("liegong"));
  public static final HeroDefinition WEIYAN=new HeroDefinition("weiyan",Kingdom.SHU,4,List.of("kuanggu"));
  public static final HeroDefinition XUHUANG=new HeroDefinition("xuhuang",Kingdom.WEI,4,List.of("duanliang"));
  public static final HeroDefinition DIANWEI=new HeroDefinition("dianwei",Kingdom.WEI,4,List.of("qiangxi"));
  public static final HeroDefinition YUJIN=new HeroDefinition("yujin",Kingdom.WEI,4,List.of("yizhong"));
  public static final HeroDefinition HUAXIONG=new HeroDefinition("huaxiong",Kingdom.QUN,6,List.of("yaowu"));
  public static final HeroDefinition GONGSUNZAN=new HeroDefinition("gongsunzan",Kingdom.QUN,4,List.of("yicong"));
  /** Audited expansion catalog; the fixed four-seat development fixtures retain ALL's ordering. */
  public static final List<HeroDefinition> CATALOG=java.util.stream.Stream.concat(
      List.of(LIUBEI,ZHANGFEI,ZHAOYUN,CAOCAO,GUANYU,HUANGGAI,HUANGYUEYING,GANNING,HUATUO,SUNQUAN,LUMENG,ZHOUYU,LUXUN,SIMAYI,GUOJIA,XUCHU,SUNSHANGXIANG,ZHANGLIAO,ZHUGELIANG,ZHENJI,XIAHOUDUN,DIAOCHAN,MACHAO,DAQIAO,LVBU,HUANGZHONG,WEIYAN,XUHUANG,DIANWEI,YUJIN,HUAXIONG,GONGSUNZAN).stream(),
      MythicHeroes.ADDED.stream().map(MythicHeroes.Entry::hero)).toList();

  private StandardHeroes() {}
}
