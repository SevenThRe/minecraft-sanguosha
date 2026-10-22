package dev.sgs.core.social;
import java.util.*;
/** Stable-ID catalog; adding a gift does not modify the currency or UI switches. */
public final class GiftCatalog {
    private final Map<String,GiftDefinition> entries;
    public GiftCatalog(List<GiftDefinition> additions){
        var all=new LinkedHashMap<String,GiftDefinition>();for(var gift:builtins())all.put(gift.id(),gift);
        for(var gift:additions){if(gift.id().startsWith("sgs:") || all.putIfAbsent(gift.id(),gift)!=null)throw new IllegalArgumentException("Reserved/duplicate gift: "+gift.id());}
        if(all.size()>32)throw new IllegalArgumentException("Gift catalog limit32");entries=Collections.unmodifiableMap(all);
    }
    public List<GiftDefinition> gifts(){return List.copyOf(entries.values());}
    public GiftDefinition require(String id){var gift=entries.get(id);if(gift==null)throw new IllegalArgumentException("UNKNOWN_GIFT");return gift;}
    public GiftDefinition resolve(String id,int amount){
        String canonical=switch(id){case "POPPY"->"sgs:poppy";case "TULIP"->"sgs:tulip_bouquet";case "EGG"->amount==10?"sgs:egg_volley":"sgs:egg";default->id;};
        var gift=require(canonical);if(gift.received()!=amount)throw new IllegalArgumentException("INVALID_TIER");return gift;
    }
    public static List<GiftDefinition> builtins(){return List.of(
        sprite("sgs:poppy","虞美人",true,1,1,"poppy",32,10,10,14,22,GiftDefinition.Animation.PETALS,0xe65253),
        sprite("sgs:tulip_bouquet","郁金香花束",true,10,10,"tulip",144,24,18,93,111,GiftDefinition.Animation.PETALS,0xf79783),
        sprite("sgs:egg","鸡蛋",false,1,1,"egg",16,3,3,10,11,GiftDefinition.Animation.SPLASH,0xeeb82c),
        sprite("sgs:egg_volley","连投鸡蛋",false,10,10,"egg",16,3,3,10,11,GiftDefinition.Animation.SPLASH,0xeeb82c));}
    private static GiftDefinition sprite(String id,String name,boolean good,int cost,int received,String art,int pixels,int u,int v,int w,int h,GiftDefinition.Animation animation,int color){
        return new GiftDefinition(id,name,good?GiftDefinition.Polarity.POSITIVE:GiftDefinition.Polarity.NEGATIVE,cost,received,"sgs:textures/gui/gifts/"+art+".png",pixels,pixels,u,v,w,h,1,80,animation,color);
    }
}
