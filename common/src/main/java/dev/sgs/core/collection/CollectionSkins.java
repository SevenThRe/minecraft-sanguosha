package dev.sgs.core.collection;

import java.util.Set;

public final class CollectionSkins {
    public static final Set<String> ACTION_HEROES=Set.of("liubei","caocao","zhaoyun","zhangfei","guanyu","huanggai","huangyueying",
        "ganning","huatuo","sunquan","lumeng","simayi","guojia","zhouyu","luxun","xuchu","zhangliao","xiahoudun","zhenji",
        "zhugeliang","machao","daqiao","sunshangxiang","lvbu","diaochan");
    public static String key(String hero,String skin){
        if(!skin.equals("action") || !ACTION_HEROES.contains(hero))throw new IllegalArgumentException("此武将没有该皮肤");
        return hero+"/"+skin;
    }
}
