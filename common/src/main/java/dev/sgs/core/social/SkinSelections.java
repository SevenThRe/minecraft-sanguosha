package dev.sgs.core.social;

import dev.sgs.core.collection.CollectionSkins;
import java.util.*;

/** Server-owned cosmetic ids, never client-supplied texture paths. */
public final class SkinSelections {
    public record Selection(UUID player,String hero,String skin) {}
    private final Map<UUID,Map<String,String>> choices=new LinkedHashMap<>();
    public static boolean supported(String hero,String skin){
        return hero!=null && hero.matches("[a-z0-9_.:-]{1,128}") && skin!=null
            && (skin.equals("classic") || skin.equals("legacy") || skin.equals("action") && CollectionSkins.ACTION_HEROES.contains(hero));
    }
    public static boolean allowed(String hero,String skin,boolean restricted,Set<String> owned){
        return supported(hero,skin) && (!skin.equals("action") || !restricted || owned.contains(hero+"/action"));
    }
    public void select(UUID player,String hero,String skin,boolean restricted,Set<String> owned){
        if(player==null || !allowed(hero,skin,restricted,owned))throw new IllegalArgumentException("此皮肤未解锁或不存在");
        choices.computeIfAbsent(player,k->new LinkedHashMap<>()).put(hero,skin);
    }
    public String selected(UUID player,String hero,boolean restricted,Set<String> owned){
        String selected=choices.getOrDefault(player,Map.of()).getOrDefault(hero,CollectionSkins.ACTION_HEROES.contains(hero)?"action":"classic");
        return allowed(hero,selected,restricted,owned)?selected:"classic";
    }
    public List<Selection> snapshot(){
        var result=new ArrayList<Selection>();choices.forEach((p,map)->map.forEach((h,s)->result.add(new Selection(p,h,s))));return List.copyOf(result);
    }
    public static SkinSelections restore(List<Selection> rows){
        var result=new SkinSelections();var keys=new HashSet<String>();
        for(var row:rows){
            if(row==null || row.player()==null || !supported(row.hero(),row.skin()) || !keys.add(row.player()+"/"+row.hero()))throw new IllegalArgumentException("无效皮肤记录");
            result.select(row.player(),row.hero(),row.skin(),false,Set.of());
        }
        return result;
    }
}
