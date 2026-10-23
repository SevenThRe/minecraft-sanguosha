package dev.sgs.validation.client;

import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Translate the public journal grammar only, keeping hero/card spans and redaction intact. */
final class EnglishJournal {
    private static final String TOKEN="(\\{#\\d+})";
    private static final Pattern PARTS=Pattern.compile("\\d+号「[^」]+」|【[^】]*】");
    private static final Pattern MARKERS=Pattern.compile(TOKEN);
    private record Rule(Pattern pattern,String key) {}
    private static Rule rule(String pattern,String key){return new Rule(Pattern.compile(pattern),"sgs.journal."+key);}
    private static final List<Rule> RULES=List.of(
        rule(TOKEN+"的回合开始","turn"),
        rule(TOKEN+"发动了(?:技能)?"+TOKEN,"skill"),
        rule(TOKEN+"摸了(\\d+)张牌","draw"),
        rule(TOKEN+"对"+TOKEN+"结算(.+)","resolve"),
        rule(TOKEN+"使用了(.+)","use"),
        rule(TOKEN+"打出了(.+)","respond"),
        rule("结算后，(.+)进入弃牌堆","finish"),
        rule(TOKEN+"装备了(.+)","equip"),
        rule(TOKEN+"替换装备，弃置(.+)","replace_equipment"),
        rule(TOKEN+"将(.+)置入"+TOKEN+"的判定区","delay_use"),
        rule("(.+)从"+TOKEN+"的判定区移至"+TOKEN+"的判定区","delay_pass"),
        rule(TOKEN+"的判定牌：(.+)","judgment"),
        rule(TOKEN+"替换判定牌：(.+)","replace_judgment"),
        rule(TOKEN+"展示了(.+)","reveal"),
        rule(TOKEN+"查看了(.+)","view"),
        rule(TOKEN+"将(.+)放回牌堆","return"),
        rule(TOKEN+"从(.+)获得了(.+)","gain"),
        rule(TOKEN+"弃置了"+TOKEN+"的(.+)","discard_other"),
        rule(TOKEN+"弃置了(.+)","discard"),
        rule(TOKEN+"阵亡，弃置(.+)","death_discard"),
        rule(TOKEN+"重铸了(.+)","recast"),
        rule(TOKEN+"受到来自"+TOKEN+"的(\\d+)点伤害，剩余体力(-?\\d+)","damage_from"),
        rule(TOKEN+"受到(\\d+)点伤害，剩余体力(-?\\d+)","damage"),
        rule(TOKEN+"失去(\\d+)点体力，剩余体力(-?\\d+)","lose_hp"),
        rule(TOKEN+"回复(\\d+)点体力，当前体力(-?\\d+)","recover"),
        rule(TOKEN+"阵亡","death"),
        rule(TOKEN+"脱离濒死，剩余体力(-?\\d+)","rescue"),
        rule(TOKEN+"翻面","turn_over"),
        rule(TOKEN+"闪避了"+TOKEN+"的杀","dodge"),
        rule(TOKEN+"因兵粮寸断跳过摸牌阶段","skip_draw"),
        rule(TOKEN+"因乐不思蜀跳过出牌阶段","skip_play"),
        rule(TOKEN+"进入连环状态","chain"),
        rule(TOKEN+"解除连环状态","unchain"),
        rule(TOKEN+"将上述牌当作(.+)打出","virtual_response"),
        rule(TOKEN+"将上述牌当作(.+)使用","virtual_use"),
        rule("对局结束","end")
    );
    private static Component span(String value,int color){return Component.literal(value).withStyle(s->s.withColor(color).withFont(dev.sgs.validation.ProbeMod.id("ink")));}
    static Component render(String line){
        var parts=new ArrayList<Component>();var skeleton=new StringBuffer();var matcher=PARTS.matcher(line);
        while(matcher.find()){
            String part=matcher.group();
            if(part.contains("号「"))parts.add(span(ClientText.heroLabel(part.substring(part.indexOf('「')+1,part.length()-1)),0xffda19));
            else{
                boolean skill=line.substring(0,matcher.start()).endsWith("发动了");
                String value=part.substring(1,part.length()-1);if(skill)value=ClientText.skillLabel(value);
                if(skill)parts.add(span("“"+value+"”",0xc9de48));
                else{
                    int suit=-1;for(int i=0;i<value.length();i++)if("♠♥♣♦".indexOf(value.charAt(i))>=0){suit=i;break;}
                    var card=Component.empty().append(span("["+(suit<0?value:value.substring(0,suit)),0xf2dfaa));
                    if(suit>=0)card.append(span(value.substring(suit),"♥♦".indexOf(value.charAt(suit))>=0?0xff4444:0xb0aaa0));
                    parts.add(card.append(span("]",0xf2dfaa)));
                }
            }
            matcher.appendReplacement(skeleton,java.util.regex.Matcher.quoteReplacement("{#"+(parts.size()-1)+"}"));
        }
        matcher.appendTail(skeleton);String value=skeleton.toString();
        for(var rule:RULES){
            var action=rule.pattern().matcher(value);if(!action.matches())continue;
            Object[] args=new Object[action.groupCount()];
            for(int i=0;i<args.length;i++)args[i]=zone(action.group(i+1));
            value=Component.translatable(rule.key(),args).getString();break;
        }
        MutableComponent result=Component.empty();matcher=MARKERS.matcher(value);int offset=0;
        while(matcher.find()){
            result.append(span(value.substring(offset,matcher.start()),0xb98c66));
            int index=Integer.parseInt(matcher.group().substring(2,matcher.group().length()-1));
            result.append(parts.get(index));offset=matcher.end();
        }
        return result.append(span(value.substring(offset),0xb98c66));
    }
    private static String zone(String text){
        return text.replace("的手牌区","'s hand").replace("的装备区","'s equipment")
            .replace("的判定区","'s judgment area").replace("的武将牌区","'s hero area")
            .replace("弃牌堆","discard pile").replace("处理区","processing area").replace("牌堆","deck")
            .replaceAll("(\\d+)张牌","$1 cards").replace("、",", ");
    }
}
