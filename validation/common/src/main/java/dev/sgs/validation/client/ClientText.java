package dev.sgs.validation.client;

import dev.sgs.core.addon.HeroPresentation;
import dev.sgs.core.addon.SkillPresentation;
import java.util.List;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/** Client presentation only. Never mutates network views, rule IDs or user text. */
public final class ClientText {
    private static List<HeroPresentation> sourceHeroes, localizedHeroes;
    private static net.minecraft.locale.Language heroLanguage;
    private ClientText() {}
    public static String tr(String key) { return Component.translatable(key).getString(); }
    public static String named(String key, String fallback) {
        return I18n.exists(key) ? tr(key) : fallback;
    }
    public static String heroName(String id, String fallback) { return named("sgs.hero."+id, fallback); }
    public static String skillName(String id, String fallback) { return named("sgs.skill."+id, fallback); }
    public static String skillDescription(String id, String fallback) { return named("sgs.skill."+id+".description", fallback); }
    public static SkillPresentation skill(SkillPresentation skill) {
        return new SkillPresentation(skill.id(),skillName(skill.id(),skill.name()),
            skillDescription(skill.id(),skill.description()),skill.kind(),skill.target(),
            skill.minimumCards(),skill.maximumCards(),skill.lordOnly(),skill.allowEquipmentCosts());
    }
    public static HeroPresentation hero(HeroPresentation hero) {
        return new HeroPresentation(hero.id(),heroName(hero.id(),hero.name()),category(hero.description()),
            hero.portrait(),hero.skills().stream().map(ClientText::skill).toList());
    }
    public static List<HeroPresentation> heroes(List<HeroPresentation> heroes) {
        var language=net.minecraft.locale.Language.getInstance();
        if(sourceHeroes!=heroes || heroLanguage!=language){
            sourceHeroes=heroes;heroLanguage=language;localizedHeroes=heroes.stream().map(ClientText::hero).toList();
        }
        return localizedHeroes;
    }
    public static dev.sgs.validation.collection.CollectionNetwork.Hero collectionHero(dev.sgs.validation.collection.CollectionNetwork.Hero hero) {
        return new dev.sgs.validation.collection.CollectionNetwork.Hero(hero.id(),heroName(hero.id(),hero.name()),
            hero.owned(),hero.price(),hero.forSale(),hero.kingdom(),hero.maxHp(),hero.portrait(),category(hero.description()),
            hero.skills().stream().map(ClientText::skill).toList(),hero.derivedSkills().stream().map(ClientText::skill).toList());
    }
    public static String defaultCurrency(String value) {
        return switch(value) {
            case "武将招募令" -> tr("sgs.ui.recruitment_token_78131882");
            case "将符" -> tr("sgs.ui.hero_token_22b6b037");
            case "用于招募武将。" -> tr("sgs.ui.used_to_recruit_heroes_b06d6d6e");
            case "用于兑换指定武将。" -> tr("sgs.ui.used_to_redeem_a_specific_hero_f65b5773");
            default -> value;
        };
    }
    public static String heroLabel(String name) {
        return switch(name) {
            case "刘备" -> heroName("liubei",name);
            case "张飞" -> heroName("zhangfei",name);
            case "赵云" -> heroName("zhaoyun",name);
            case "曹操" -> heroName("caocao",name);
            case "关羽" -> heroName("guanyu",name);
            case "黄盖" -> heroName("huanggai",name);
            case "黄月英" -> heroName("huangyueying",name);
            case "孙权" -> heroName("sunquan",name);
            case "吕蒙" -> heroName("lumeng",name);
            case "周瑜" -> heroName("zhouyu",name);
            case "陆逊" -> heroName("luxun",name);
            case "甘宁" -> heroName("ganning",name);
            case "华佗" -> heroName("huatuo",name);
            case "司马懿" -> heroName("simayi",name);
            case "郭嘉" -> heroName("guojia",name);
            case "许褚" -> heroName("xuchu",name);
            case "孙尚香" -> heroName("sunshangxiang",name);
            case "张辽" -> heroName("zhangliao",name);
            case "诸葛亮" -> heroName("zhugeliang",name);
            case "甄姬" -> heroName("zhenji",name);
            case "夏侯惇" -> heroName("xiahoudun",name);
            case "貂蝉" -> heroName("diaochan",name);
            case "马超" -> heroName("machao",name);
            case "大乔" -> heroName("daqiao",name);
            case "吕布" -> heroName("lvbu",name);
            case "黄忠" -> heroName("huangzhong",name);
            case "魏延" -> heroName("weiyan",name);
            case "徐晃" -> heroName("xuhuang",name);
            case "典韦" -> heroName("dianwei",name);
            case "于禁" -> heroName("yujin",name);
            case "华雄" -> heroName("huaxiong",name);
            case "公孙瓒" -> heroName("gongsunzan",name);
            case "夏侯渊" -> heroName("xiahouyuan",name);
            case "曹仁" -> heroName("caoren",name);
            case "小乔" -> heroName("xiaoqiao",name);
            case "周泰" -> heroName("zhoutai",name);
            case "张角" -> heroName("zhangjiao",name);
            case "于吉" -> heroName("yuji",name);
            case "荀彧" -> heroName("xunyu",name);
            case "卧龙诸葛亮" -> heroName("wolong",name);
            case "庞统" -> heroName("pangtong",name);
            case "太史慈" -> heroName("taishici",name);
            case "袁绍" -> heroName("yuanshao",name);
            case "颜良文丑" -> heroName("yanliangwenchou",name);
            case "庞德" -> heroName("pangde",name);
            case "曹丕" -> heroName("caopi",name);
            case "孟获" -> heroName("menghuo",name);
            case "祝融" -> heroName("zhurong",name);
            case "孙坚" -> heroName("sunjian",name);
            case "鲁肃" -> heroName("lusu",name);
            case "董卓" -> heroName("dongzhuo",name);
            case "贾诩" -> heroName("jiaxu",name);
            case "邓艾" -> heroName("dengai",name);
            case "张郃" -> heroName("zhanghe",name);
            case "姜维" -> heroName("jiangwei",name);
            case "刘禅" -> heroName("liushan",name);
            case "孙策" -> heroName("sunce",name);
            case "张昭张纮" -> heroName("zhangzhaozhanghong",name);
            case "蔡文姬" -> heroName("caiwenji",name);
            case "左慈" -> heroName("zuoci",name);
            default -> name;
        };
    }
    public static String skillLabel(String name) {
        return switch(name) {
            case "仁德" -> skillName("rende",name);
            case "激将" -> skillName("jijiang",name);
            case "咆哮" -> skillName("paoxiao",name);
            case "龙胆" -> skillName("longdan",name);
            case "奸雄" -> skillName("jianxiong",name);
            case "护驾" -> skillName("hujia",name);
            case "苦肉" -> skillName("kurou",name);
            case "武圣" -> skillName("wusheng",name);
            case "集智" -> skillName("jizhi",name);
            case "奇才" -> skillName("qicai",name);
            case "制衡" -> skillName("zhiheng",name);
            case "救援" -> skillName("jiuyuan",name);
            case "克己" -> skillName("keji",name);
            case "奇袭" -> skillName("qixi",name);
            case "急救" -> skillName("jijiu",name);
            case "青囊" -> skillName("qingnang",name);
            case "英姿" -> skillName("yingzi",name);
            case "反间" -> skillName("fanjian",name);
            case "谦逊" -> skillName("qianxun",name);
            case "连营" -> skillName("lianying",name);
            case "反馈" -> skillName("fankui",name);
            case "鬼才" -> skillName("guicai",name);
            case "天妒" -> skillName("tiandu",name);
            case "遗计" -> skillName("yiji",name);
            case "裸衣" -> skillName("luoyi",name);
            case "结姻" -> skillName("jieyin",name);
            case "枭姬" -> skillName("xiaoji",name);
            case "突袭" -> skillName("tuxi",name);
            case "观星" -> skillName("guanxing",name);
            case "空城" -> skillName("kongcheng",name);
            case "倾国" -> skillName("qingguo",name);
            case "洛神" -> skillName("luoshen",name);
            case "刚烈" -> skillName("ganglie",name);
            case "离间" -> skillName("lijian",name);
            case "闭月" -> skillName("biyue",name);
            case "马术" -> skillName("mashu",name);
            case "铁骑" -> skillName("tieji",name);
            case "国色" -> skillName("guose",name);
            case "流离" -> skillName("liuli",name);
            case "无双" -> skillName("wushuang",name);
            case "烈弓" -> skillName("liegong",name);
            case "狂骨" -> skillName("kuanggu",name);
            case "断粮" -> skillName("duanliang",name);
            case "强袭" -> skillName("qiangxi",name);
            case "毅重" -> skillName("yizhong",name);
            case "耀武" -> skillName("yaowu",name);
            case "义从" -> skillName("yicong",name);
            case "黄天" -> skillName("huangtian",name);
            case "制霸" -> skillName("zhiba",name);
            case "神速" -> skillName("shensu",name);
            case "据守" -> skillName("jushou",name);
            case "天香" -> skillName("tianxiang",name);
            case "红颜" -> skillName("hongyan",name);
            case "不屈" -> skillName("buqu",name);
            case "雷击" -> skillName("leiji",name);
            case "鬼道" -> skillName("guidao",name);
            case "蛊惑" -> skillName("guhuo",name);
            case "驱虎" -> skillName("quhu",name);
            case "节命" -> skillName("jieming",name);
            case "八阵" -> skillName("bazhen",name);
            case "火计" -> skillName("huoji",name);
            case "看破" -> skillName("kanpo",name);
            case "连环" -> skillName("lianhuan",name);
            case "涅槃" -> skillName("niepan",name);
            case "天义" -> skillName("tianyi",name);
            case "乱击" -> skillName("luanji",name);
            case "血裔" -> skillName("xueyi",name);
            case "双雄" -> skillName("shuangxiong",name);
            case "猛进" -> skillName("mengjin",name);
            case "行殇" -> skillName("xingshang",name);
            case "放逐" -> skillName("fangzhu",name);
            case "颂威" -> skillName("songwei",name);
            case "祸首" -> skillName("huoshou",name);
            case "再起" -> skillName("zaiqi",name);
            case "巨象" -> skillName("juxiang",name);
            case "烈刃" -> skillName("lieren",name);
            case "英魂" -> skillName("yinghun",name);
            case "好施" -> skillName("haoshi",name);
            case "缔盟" -> skillName("dimeng",name);
            case "酒池" -> skillName("jiuchi",name);
            case "肉林" -> skillName("roulin",name);
            case "崩坏" -> skillName("benghuai",name);
            case "暴虐" -> skillName("baonue",name);
            case "完杀" -> skillName("wansha",name);
            case "乱武" -> skillName("luanwu",name);
            case "帷幕" -> skillName("weimu",name);
            case "屯田" -> skillName("tuntian",name);
            case "凿险" -> skillName("zaoxian",name);
            case "急袭" -> skillName("jixi",name);
            case "巧变" -> skillName("qiaobian",name);
            case "挑衅" -> skillName("tiaoxin",name);
            case "志继" -> skillName("zhiji",name);
            case "享乐" -> skillName("xiangle",name);
            case "放权" -> skillName("fangquan",name);
            case "若愚" -> skillName("ruoyu",name);
            case "激昂" -> skillName("jiang",name);
            case "魂姿" -> skillName("hunzi",name);
            case "直谏" -> skillName("zhijian",name);
            case "固政" -> skillName("guzheng",name);
            case "悲歌" -> skillName("beige",name);
            case "断肠" -> skillName("duanchang",name);
            case "化身" -> skillName("huashen",name);
            case "新生" -> skillName("xinsheng",name);
            default -> name;
        };
    }
    public static String serverMessage(String message) {
        return switch(message) {
            case "请求已过期" -> tr("sgs.ui.this_request_has_expired_6770cdbc");
            case "房间信息已更新" -> tr("sgs.ui.room_information_has_changed_refresh_and_retry_48083904");
            case "房间状态已变化，请重试" -> tr("sgs.ui.the_room_state_has_changed_please_retry_2d0f7fc6");
            case "服务器已关闭此房间玩法" -> tr("sgs.ui.this_room_s_mode_is_disabled_on_the_server_a5fe73c9");
            case "服务器已关闭快速匹配" -> tr("sgs.ui.matchmaking_is_disabled_on_the_server_38fcb146");
            case "请等待所有玩家上线" -> tr("sgs.ui.wait_for_all_players_to_come_online_7731a4a1");
            case "玩家离线或已在其他对局中" -> tr("sgs.ui.the_player_is_offline_or_in_another_match_9c75074f");
            case "该玩家已离线" -> tr("sgs.ui.that_player_is_offline_7da52c0b");
            case "房间需在等待中且留有空位" -> tr("sgs.ui.the_room_must_be_waiting_and_have_an_empty_seat_95ce5293");
            case "当前房间不允许聊天" -> tr("sgs.ui.chat_is_disabled_in_this_room_29b39c31");
            case "发送过快，请稍后重试" -> tr("sgs.ui.you_are_sending_too_quickly_please_wait_72886042");
            case "仅可旁观允许观战的进行中房间；请先离开自己的房间" -> tr("sgs.ui.leave_your_room_first_only_active_matches_with_s_4b17cf42");
            case "房间密码错误" -> tr("sgs.ui.incorrect_room_password_a9ec36e6");
            case "已进入旁观，只接收公开信息" -> tr("sgs.ui.you_are_spectating_and_receive_public_informatio_1b0a97ca");
            case "对局已结束，请刷新" -> tr("sgs.ui.the_match_has_ended_please_refresh_72f51bf8");
            case "已退出旁观" -> tr("sgs.ui.you_stopped_spectating_2b221bf8");
            case "座位无效" -> tr("sgs.ui.invalid_seat_7762afc6");
            case "此模式尚未接入" -> tr("sgs.ui.this_mode_is_not_available_yet_6c2b66eb");
            case "房间数量已达上限" -> tr("sgs.ui.the_room_limit_has_been_reached_c27da9e4");
            case "对局进行中，可暂离界面或返回对局" -> tr("sgs.ui.a_match_is_running_you_can_hide_the_screen_or_re_083a78dd");
            case "不支持的操作" -> tr("sgs.ui.unsupported_action_a2073de0");
            case "设置或输入无效，请检查名称、密码、时间和禁将" -> tr("sgs.ui.invalid_settings_check_the_room_name_password_ti_a17378d6");
            case "操作成功" -> tr("sgs.ui.action_completed_3c5be5f2");
            case "你已在房间中" -> tr("sgs.ui.you_are_already_in_a_room_808da62d");
            case "房间已满" -> tr("sgs.ui.the_room_is_full_84504ea4");
            case "房间已关闭" -> tr("sgs.ui.the_room_has_closed_2f9cb0de");
            case "你已在匹配队列中" -> tr("sgs.ui.you_are_already_in_a_matchmaking_queue_70739e66");
            case "当前没有匹配任务" -> tr("sgs.ui.you_are_not_in_a_matchmaking_queue_1b307052");
            case "当前不在房间中" -> tr("sgs.ui.you_are_not_in_a_room_3a32b8d5");
            case "只有房主可以调整 AI" -> tr("sgs.ui.only_the_owner_can_manage_ai_players_1b9a56cd");
            case "该座位已有玩家" -> tr("sgs.ui.that_seat_is_occupied_45e73328");
            case "请等待真人加入或添加 AI 补满空位" -> tr("sgs.ui.wait_for_players_or_fill_empty_seats_with_ai_9bef1094");
            case "请所有真人准备后再开始" -> tr("sgs.ui.all_human_players_must_be_ready_before_starting_cd58a426");
            case "房间设置无效" -> tr("sgs.ui.invalid_room_settings_e60a03a2");
            case "邀请已失效" -> tr("sgs.ui.the_invitation_has_expired_ec0fa251");
            case "不能邀请自己" -> tr("sgs.ui.you_cannot_invite_yourself_de2be0c4");
            case "该玩家已在其他房间" -> tr("sgs.ui.that_player_is_already_in_another_room_276576f7");
            case "不能移除房主" -> tr("sgs.ui.the_owner_cannot_be_removed_f55a6ef8");
            case "当前状态不允许此操作" -> tr("sgs.ui.this_action_is_not_allowed_in_the_current_state_6fdb7716");
            case "请求已处理，请刷新" -> tr("sgs.ui.this_request_was_already_processed_please_refres_e1483482");
            case "市场价格已变化，请刷新后购买" -> tr("sgs.ui.the_price_has_changed_refresh_before_purchasing_dde9cc68");
            case "招募消耗已变化，请刷新后重试" -> tr("sgs.ui.the_recruitment_cost_has_changed_refresh_and_ret_bddf1fc9");
            case "不支持的藏品操作" -> tr("sgs.ui.unsupported_collection_action_f4cda390");
            case "藏品服务暂不可用，未重置数据" -> tr("sgs.ui.collection_service_unavailable_your_data_has_bee_7092ba90");
            case "藏品服务暂不可用" -> tr("sgs.ui.collection_service_unavailable_5b257656");
            case "藏品服务暂不可用，数据已保留；可刷新重试" -> tr("sgs.ui.collection_service_unavailable_your_data_is_safe_4c069082");
            case "读取皮肤权限失败" -> tr("sgs.ui.failed_to_read_skin_permissions_fa29ebc8");
            case "读取武将藏品失败" -> tr("sgs.ui.failed_to_load_your_hero_collection_fb4ad09a");
            case "读取余额失败，数据已保留" -> tr("sgs.ui.failed_to_read_the_balance_your_data_has_been_pr_3a9b5355");
            case "读取任务失败，数据已保留" -> tr("sgs.ui.failed_to_load_tasks_your_data_has_been_preserve_156858d4");
            case "管理员已调整藏品资产" -> tr("sgs.ui.an_administrator_updated_your_collection_assets_53c44ce2");
            case "神话再临" -> tr("sgs.ui.mythic_return_5fd06088");
            case "扩展武将" -> tr("sgs.ui.expansion_heroes_4c6797e6");
            case "招募未启用，武将自由使用" -> tr("sgs.ui.recruitment_is_disabled_heroes_are_freely_availa_b8a27791");
            case "完局与任务奖励已关闭" -> tr("sgs.ui.match_and_task_rewards_are_disabled_777830c6");
            case "活动奖励由服主发放" -> tr("sgs.ui.event_rewards_are_issued_by_the_server_owner_5c9bc115");
            default -> message;
        };
    }
    public static String category(String value) {
        return switch(value) {
            case "全部" -> tr("sgs.ui.all_778fc8f9");
            case "全部武将" -> tr("sgs.ui.all_heroes_e6a5dc10");
            case "其他武将" -> tr("sgs.ui.other_heroes_088f11e3");
            case "魏" -> tr("sgs.ui.wei_9dc7e08d");
            case "蜀" -> tr("sgs.ui.shu_86c7a46b");
            case "吴" -> tr("sgs.ui.wu_27bfa7e7");
            case "群" -> tr("sgs.ui.independent_2a867dcf");
            case "神" -> tr("sgs.ui.god_87a94db4");
            case "主公" -> tr("sgs.ui.lord_70368eaf");
            case "友方" -> tr("sgs.ui.allies_aa051cdb");
            case "敌方" -> tr("sgs.ui.enemies_99d2b5e4");
            case "忠臣" -> tr("sgs.ui.loyalist_83bc6bab");
            case "反贼" -> tr("sgs.ui.rebel_487c2834");
            case "内奸" -> tr("sgs.ui.renegade_c5344088");
            case "我" -> tr("sgs.ui.you_34f201bc");
            case "友" -> tr("sgs.ui.ally_6cca7a3e");
            case "标准武将" -> tr("sgs.ui.standard_heroes_6322e497");
            case "经典扩展武将" -> tr("sgs.ui.classic_expansion_heroes_22de5bd4");
            case "神话再临·风" -> tr("sgs.ui.mythic_return_wind_d05badcf");
            case "神话再临·火" -> tr("sgs.ui.mythic_return_fire_db6f5d43");
            case "神话再临·林" -> tr("sgs.ui.mythic_return_forest_96e70c51");
            case "神话再临·山" -> tr("sgs.ui.mythic_return_mountain_c013f57d");
            default -> serverMessage(value);
        };
    }
    public static boolean english() {
        return net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected().startsWith("en_");
    }
}
