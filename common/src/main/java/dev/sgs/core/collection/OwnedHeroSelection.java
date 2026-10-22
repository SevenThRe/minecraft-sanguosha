package dev.sgs.core.collection;

import java.util.*;

/** Reserve one distinct hero for every seat by bipartite matching, then distribute extras. */
public final class OwnedHeroSelection {
    public static final class Unavailable extends IllegalArgumentException {public Unavailable(String message){super(message);}}
    public static List<List<String>> deal(List<String> catalog,List<Set<String>> eligible,int count,Random random) {
        var choices=new ArrayList<List<String>>();
        for(var own:eligible){var pool=new ArrayList<>(catalog.stream().filter(own::contains).distinct().toList());Collections.shuffle(pool,random);choices.add(pool);}
        var assignments=new HashMap<String,Integer>();
        for(int seat=0;seat<choices.size();seat++)if(!reserve(seat,choices,assignments,new HashSet<>()))
            throw new Unavailable("玩家拥有的武将不足或重叠，无法为每个座位分配独立武将");
        var result=new ArrayList<List<String>>();for(int i=0;i<choices.size();i++)result.add(new ArrayList<>());
        assignments.forEach((hero,seat)->result.get(seat).add(hero));var used=new HashSet<>(assignments.keySet());
        for(int pass=1;pass<count;pass++)for(int seat=0;seat<choices.size();seat++)for(String hero:choices.get(seat))if(used.add(hero)){result.get(seat).add(hero);break;}
        return result.stream().map(List::copyOf).toList();
    }
    private static boolean reserve(int seat,List<List<String>> choices,Map<String,Integer> assigned,Set<String> visited) {
        for(String hero:choices.get(seat))if(visited.add(hero)){
            var owner=assigned.get(hero);if(owner==null || reserve(owner,choices,assigned,visited)){assigned.put(hero,seat);return true;}
        }
        return false;
    }
}
