package dev.sgs.core.match;

import java.util.*;

/** A shared ban pool disjoint from a proven four-per-seat matching. */
public final class HeroBanRound {
  private final List<String> catalog, candidates;
  private final List<Set<String>> eligible;
  private final Map<Integer,String> choices=new LinkedHashMap<>();
  private final int count;
  public HeroBanRound(List<String> catalog,List<Set<String>> eligible,int count,Random random){
    this.catalog=List.copyOf(catalog);this.eligible=eligible.stream().map(Set::copyOf).toList();this.count=count;
    var reserved=deal(catalog,eligible,count,random).stream().flatMap(List::stream).collect(java.util.stream.Collectors.toSet());
    var surplus=new ArrayList<>(catalog.stream().filter(id->!reserved.contains(id)).toList());Collections.shuffle(surplus,random);
    candidates=List.copyOf(surplus.subList(0,Math.min(10,surplus.size())));
  }
  public List<String> candidates(){return candidates;}
  public synchronized List<String> availableCandidates(){return candidates.stream().filter(id->!choices.containsValue(id)).toList();}
  public Map<Integer,String> choices(){return Map.copyOf(choices);}
  public boolean complete(){return choices.size()==Math.min(eligible.size(),candidates.size());}
  public synchronized String choose(int seat,String hero){
    if(seat<0 || seat>=eligible.size())return "INVALID_SEAT";
    if(choices.containsKey(seat))return "BAN_ALREADY_CHOSEN";
    if(hero==null || !candidates.contains(hero))return "INVALID_BAN_HERO";
    if(choices.containsValue(hero))return "BAN_HERO_TAKEN";
    choices.put(seat,hero);return null;
  }
  public synchronized void autoChoose(int seat,Random random){
    var available=candidates.stream().filter(id->!choices.containsValue(id)).toList();
    if(!choices.containsKey(seat)&&!available.isEmpty())choose(seat,available.get(random.nextInt(available.size())));
  }
  public List<List<String>> offers(Random random){
    var bans=new HashSet<>(choices.values());
    return deal(catalog.stream().filter(id->!bans.contains(id)).toList(),eligible,count,random);
  }
  private static List<List<String>> deal(List<String> catalog,List<Set<String>> eligible,int count,Random random){
    var slots=new ArrayList<Set<String>>();for(var own:eligible)for(int i=0;i<count;i++)slots.add(own);
    var single=dev.sgs.core.collection.OwnedHeroSelection.deal(catalog,slots,1,random);
    var result=new ArrayList<List<String>>();
    for(int seat=0;seat<eligible.size();seat++){var hand=new ArrayList<String>();for(int i=0;i<count;i++)hand.addAll(single.get(seat*count+i));result.add(List.copyOf(hand));}
    return List.copyOf(result);
  }
}
