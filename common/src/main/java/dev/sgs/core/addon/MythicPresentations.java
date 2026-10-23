package dev.sgs.core.addon;
import java.util.*;
import static dev.sgs.core.addon.SkillPresentation.Kind.*;
import static dev.sgs.core.addon.SkillPresentation.Target.*;

/** One catalog shared by server selection, collection, tooltips, and active-skill controls. */
public final class MythicPresentations {
  private static SkillPresentation s(String id,String name,String description,SkillPresentation.Kind kind,SkillPresentation.Target target,int min,int max,boolean lord,boolean equipment){
    return new SkillPresentation(id,name,description,kind,target,min,max,lord,equipment);
  }
  public static final List<SkillPresentation> ALL=List.of(
      s("huangtian_give","黄天","出牌阶段限一次，交给张角主公一张闪或闪电。",ACTIVE,OTHER,1,1,false,false),
      s("zhiba_pindian","制霸","出牌阶段限一次，向孙策主公拼点；你没赢时，主公可获得双方拼点牌。",ACTIVE,OTHER,0,0,false,false),
      s("shensu","神速","可跳过判定及摸牌阶段，或弃一张装备牌跳过出牌阶段，视为使用一张无距离限制的杀。",TRIGGERED,NONE,0,0,false,false),
      s("jushou","据守","结束阶段可摸三张牌，然后翻面。",TRIGGERED,NONE,0,0,false,false),
      s("tianxiang","天香","受到伤害时，可弃一张红桃手牌将伤害转移给其他角色，其存活后摸已损失体力值张牌。",TRIGGERED,NONE,0,0,false,false),
      s("hongyan","红颜","锁定技，你的黑桃牌均视为红桃牌。",PASSIVE,NONE,0,0,false,false),
      s("buqu","不屈","体力扣至0以下时，可亮出体力缺口数量的牌置于武将牌上；点数各不相同则不死亡。回复体力后移去多余牌。",TRIGGERED,NONE,0,0,false,false),
      s("leiji","雷击","使用或打出闪后，可令一名角色判定，黑桃则对其造成2点雷电伤害。",TRIGGERED,NONE,0,0,false,false),
      s("guidao","鬼道","判定生效前，可用一张黑色手牌或装备替换判定牌并获得原判定牌。",TRIGGERED,NONE,0,0,false,true),
      s("huangtian","黄天","主公技，其他群角色的出牌阶段限一次，可交给你一张闪或闪电。",TRIGGERED,NONE,0,0,true,false),
      s("guhuo","蛊惑","以一张手牌声明基本牌或非延时锦囊。其他角色可质疑；真牌令质疑者失去体力，假牌令其摸牌。有质疑时仅红桃真牌生效。",ACTIVE,NONE,1,1,false,false),
      s("quhu","驱虎","出牌阶段限一次，与体力比你多的角色拼点；赢则其对攻击范围内你选择的角色造成1点伤害，否则其对你造成1点伤害。",ACTIVE,OTHER,0,0,false,false),
      s("jieming","节命","每受到1点伤害后，可令一名角色将手牌补至其体力上限，最多五张。",TRIGGERED,NONE,0,0,false,false),
      s("bazhen","八阵","未装备防具时，视为装备八卦阵。",PASSIVE,NONE,0,0,false,false),
      s("huoji","火计","可将一张红色手牌当火攻使用。",TOGGLE,OTHER,1,1,false,false),
      s("kanpo","看破","可将一张黑色手牌当无懈可击使用。",TOGGLE,ANY,1,1,false,false),
      s("lianhuan","连环","可将一张梅花手牌当铁索连环使用或重铸。",TOGGLE,ANY,1,1,false,false),
      s("niepan","涅槃","限定技，濒死时可弃置所有牌，复原武将牌，摸三张牌并将体力回复至三点（不超过上限）。",TRIGGERED,NONE,0,0,false,false),
      s("tianyi","天义","出牌阶段限一次，与其他角色拼点；赢则本回合杀次数及目标数各加一且无距离限制，没赢则不能使用杀。",ACTIVE,OTHER,0,0,false,false),
      s("luanji","乱击","将两张相同花色的手牌当万箭齐发使用。",ACTIVE,NONE,2,2,false,false),
      s("xueyi","血裔","主公技，手牌上限增加其他存活群角色数的两倍。",PASSIVE,NONE,0,0,true,false),
      s("shuangxiong","双雄","摸牌阶段可改为判定并获得判定牌；本回合可将异色手牌当决斗使用。",TOGGLE,OTHER,1,1,false,false),
      s("mengjin","猛进","你的杀被闪抵消后，可弃置目标一张手牌或装备牌。",TRIGGERED,NONE,0,0,false,false),
      s("xingshang","行殇","其他角色死亡时，可获得其全部手牌及装备牌。",TRIGGERED,NONE,0,0,false,false),
      s("fangzhu","放逐","受到伤害后，可令一名其他角色摸你已损失体力值张牌，然后翻面。",TRIGGERED,NONE,0,0,false,false),
      s("songwei","颂威","主公技，其他魏角色的黑色判定生效后，其可令你摸一张牌。",TRIGGERED,NONE,0,0,true,false),
      s("huoshou","祸首","南蛮入侵对你无效，其他角色使用南蛮入侵时，你成为伤害来源。",PASSIVE,NONE,0,0,false,false),
      s("zaiqi","再起","摸牌阶段可改为亮出已损失体力值张牌；每张红桃回复1点体力，获得其余牌。",TRIGGERED,NONE,0,0,false,false),
      s("juxiang","巨象","南蛮入侵对你无效；其他角色使用的南蛮入侵结算完后，你获得其实体牌。",PASSIVE,NONE,0,0,false,false),
      s("lieren","烈刃","以杀造成伤害后，可与受伤角色拼点；赢则获得其一张手牌或装备牌。",TRIGGERED,NONE,0,0,false,false),
      s("yinghun","英魂","准备阶段，若已受伤，可选其他角色：令其摸X弃一，或摸一弃X，X为你已损失体力值。",TRIGGERED,NONE,0,0,false,false),
      s("haoshi","好施","摸牌阶段可额外摸两张；若随后手牌超过五张，交出一半手牌（向下取整）给手牌最少的其他角色。",TRIGGERED,NONE,0,0,false,false),
      s("dimeng","缔盟","出牌阶段限一次，选择两名其他角色，弃置其手牌数差值张牌，然后交换其全部手牌。",ACTIVE,OTHER,0,160,false,true),
      s("jiuchi","酒池","可将一张黑桃手牌当酒使用。",TOGGLE,ANY,1,1,false,false),
      s("roulin","肉林","锁定技，你对女性使用杀或女性对你使用杀时，须连续两张闪抵消。",PASSIVE,NONE,0,0,false,false),
      s("benghuai","崩坏","结束阶段，若你的体力不是全场最少，失去1点体力或体力上限。",TRIGGERED,NONE,0,0,false,false),
      s("baonue","暴虐","主公技，其他群角色造成伤害后，其可判定，黑桃则令你回复1点体力。",TRIGGERED,NONE,0,0,true,false),
      s("wansha","完杀","锁定技，你的回合内，除你与濒死角色外，其他角色不能使用桃。",PASSIVE,NONE,0,0,false,false),
      s("luanwu","乱武","限定技，所有其他角色依次向距离最近的一名角色使用杀，否则失去1点体力。",LIMITED,NONE,0,0,false,false),
      s("weimu","帷幕","锁定技，不能成为黑色锦囊的目标。",PASSIVE,NONE,0,0,false,false),
      s("tuntian","屯田","回合外失去手牌或装备后，可判定；非红桃判定牌置为田。计算与其他角色距离减去田数。",TRIGGERED,NONE,0,0,false,false),
      s("zaoxian","凿险","觉醒技，准备阶段若有至少三张田，减1点体力上限并获得急袭。",PASSIVE,NONE,0,0,false,false),
      s("jixi","急袭","将一张田当顺手牵羊使用。发动后选择消耗的田。",ACTIVE,OTHER,0,0,false,false),
      s("qiaobian","巧变","判定、摸牌、出牌、弃牌阶段前可弃一张手牌跳过该阶段；摸牌阶段改为获得至多两名角色的手牌，出牌阶段可移动场上一张牌。",TRIGGERED,NONE,0,0,false,false),
      s("tiaoxin","挑衅","出牌阶段限一次，令攻击范围内含你的其他角色对你使用杀，否则弃置其一张牌。",ACTIVE,OTHER,0,0,false,false),
      s("zhiji","志继","觉醒技，准备阶段若没有手牌，选择回复1点体力或摸两张牌，减1点体力上限并获得观星。",PASSIVE,NONE,0,0,false,false),
      s("xiangle","享乐","锁定技，成为杀的目标后，使用者须弃置一张基本牌，否则此杀对你无效。",PASSIVE,NONE,0,0,false,false),
      s("fangquan","放权","可跳过出牌阶段；回合结束时可弃一张手牌令一名其他角色执行额外回合。",TRIGGERED,NONE,0,0,false,false),
      s("ruoyu","若愚","主公觉醒技，准备阶段若体力为全场最少，增加1点体力上限，回复1点体力并获得激将。",PASSIVE,NONE,0,0,true,false),
      s("jiang","激昂","使用或成为红色杀或决斗的目标后，可摸一张牌。",TRIGGERED,NONE,0,0,false,false),
      s("hunzi","魂姿","觉醒技，准备阶段若体力为1，减1点体力上限并获得英姿、英魂。",PASSIVE,NONE,0,0,false,false),
      s("zhiba","制霸","主公技，其他吴角色出牌阶段限一次，可向你拼点；没赢时你可获得双方拼点牌。魂姿觉醒后可拒绝拼点。",TRIGGERED,NONE,0,0,true,false),
      s("zhijian","直谏","将一张手牌中的装备牌置于其他角色的空装备栏，然后摸一张牌。",ACTIVE,OTHER,1,1,false,false),
      s("guzheng","固政","其他角色弃牌阶段结束时，可令其获得一张本阶段弃置的手牌，然后你获得其余仍在弃牌堆的弃牌。",TRIGGERED,NONE,0,0,false,false),
      s("beige","悲歌","角色受到杀的伤害后，可弃一张牌令其判定：红桃回复1，方块摸二，梅花伤害来源弃二，黑桃伤害来源翻面。",TRIGGERED,NONE,0,0,false,true),
      s("duanchang","断肠","锁定技，你死亡时，令杀死你的角色失去其武将技能。",PASSIVE,NONE,0,0,false,false),
      s("huashen","化身","游戏开始时随机获得两张未登场武将牌；准备或结束阶段可选择化身及其非主公、非限定、非觉醒技能，获得其势力与性别。",TRIGGERED,NONE,0,0,false,false),
      s("xinsheng","新生","每受到1点伤害后，可随机获得一张新的化身。",TRIGGERED,NONE,0,0,false,false)
  );
  private MythicPresentations(){}
}
