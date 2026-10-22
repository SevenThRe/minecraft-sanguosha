package dev.sgs.core.social;
import java.util.*;
public final class Emotes {
    public static final List<String> IDS=List.of("happy","laugh","cry","angry","surprised","thumbsup","heart","sleep");
    public static String name(String id){return switch(id){case "happy"->"微笑";case "laugh"->"大笑";case "cry"->"流泪";case "angry"->"生气";case "surprised"->"惊讶";case "thumbsup"->"赞";case "heart"->"爱心";case "sleep"->"困了";default->throw new IllegalArgumentException("UNKNOWN_EMOTE");};}
}
