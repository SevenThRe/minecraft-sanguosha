package dev.sgs.validation.client;

import java.util.function.Function;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Presentation of already-redacted server records; never reads hidden card state. */
final class JournalText {
    private static final int ACTION=0xb98c66,HERO=0xffda19,CARD=0xf2dfaa,SKILL=0xc9de48;
    private static final Pattern TOKENS=Pattern.compile("\\{(card|skill):([a-z0-9_:]+)\\}");
    private static final Pattern PARTS=Pattern.compile("\\d+号「[^」]+」|【[^】]*】");
    static boolean involves(String line,int seat){
        return seat<0||Pattern.compile("(?<!\\d)"+(seat+1)+"号「").matcher(line).find();
    }
    private static Component text(String value,int color){return Component.literal(value).withStyle(style->style.withColor(color).withFont(dev.sgs.validation.ProbeMod.id("ink")));}
    static Component render(String raw,Function<String,String> cards,Function<String,String> skills){
        String line=raw.replaceFirst("^第\\d+回合 · ","");
        var matcher=TOKENS.matcher(line);var buffer=new StringBuffer();
        while(matcher.find())matcher.appendReplacement(buffer,java.util.regex.Matcher.quoteReplacement((matcher.group(1).equals("card")?cards:skills).apply(matcher.group(2))));
        matcher.appendTail(buffer);
        line=buffer.toString().replaceAll("【([^】]+)】([♠♥♣♦][A-Z0-9]+)","【$1$2】").replace("发动了技能","发动了");
        if(ClientText.english())return EnglishJournal.render(line);
        boolean turn=line.endsWith("的回合开始");
        if(turn)line="------"+line.substring(0,line.length()-2)+"------";
        MutableComponent result=Component.empty();matcher=PARTS.matcher(line);int offset=0;
        while(matcher.find()){
            result.append(text(line.substring(offset,matcher.start()),turn?CARD:ACTION));
            String part=matcher.group();
            if(part.contains("号「"))result.append(text(part.substring(part.indexOf('「')+1,part.length()-1),HERO));
            else if(line.substring(0,matcher.start()).endsWith("发动了"))result.append(text("“"+part.substring(1,part.length()-1)+"”",SKILL));
            else{
                int suit=-1;for(int i=0;i<part.length();i++)if("♠♥♣♦".indexOf(part.charAt(i))>=0){suit=i;break;}
                if(suit<0)result.append(text(part,CARD));
                else{result.append(text(part.substring(0,suit),CARD));result.append(text(part.substring(suit,part.length()-1),"♥♦".indexOf(part.charAt(suit))>=0?0xff4444:0xb0aaa0));result.append(text("】",CARD));}
            }
            offset=matcher.end();
        }
        result.append(text(line.substring(offset),turn?CARD:ACTION));return result;
    }
}
