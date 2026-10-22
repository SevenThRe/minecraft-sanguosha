package dev.sgs.core.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Pure hero-selection logic: random candidate sets and timeout fallback. The MVP pool is small, so
 * candidate repetition is allowed when filling.
 */
public final class HeroSelection {

  public static final int CANDIDATE_COUNT = 3;

  private HeroSelection() {}

  /** Shuffle once and reserve every offered hero, including unchosen cards, for one seat. */
  public static List<List<String>> dealCandidates(List<String> heroPool,int players,int count,Random random){
    if(players<=0 || count<=0)throw new IllegalArgumentException("invalid offer dimensions");
    var deck=new ArrayList<>(new java.util.LinkedHashSet<>(heroPool));
    if(deck.size()<(long)players*count)throw new IllegalArgumentException("insufficient distinct heroes for disjoint offers");
    Collections.shuffle(deck,random);
    var offers=new ArrayList<List<String>>(players);
    for(int seat=0;seat<players;seat++)offers.add(List.copyOf(deck.subList(seat*count,(seat+1)*count)));
    return List.copyOf(offers);
  }

  public static List<String> randomCandidates(List<String> heroPool, Random random) {
    return randomCandidates(heroPool, CANDIDATE_COUNT, random);
  }

  public static List<String> randomCandidates(List<String> heroPool, int count, Random random) {
    if (heroPool == null || heroPool.isEmpty()) {
      throw new IllegalArgumentException("hero pool must not be empty");
    }
    if (count <= 0) {
      throw new IllegalArgumentException("candidate count must be positive");
    }
    List<String> candidates = new ArrayList<>(count);
    if (heroPool.size() >= count) {
      List<String> shuffled = new ArrayList<>(heroPool);
      Collections.shuffle(shuffled, random);
      candidates.addAll(shuffled.subList(0, count));
    } else {
      for (int i = 0; i < count; i++) {
        candidates.add(heroPool.get(random.nextInt(heroPool.size())));
      }
    }
    return List.copyOf(candidates);
  }

  /** Keep still-legal offer order, then deterministically fill holes by stable catalog ID. */
  public static List<String> refreshCandidates(List<String> current,List<String> heroPool,
      java.util.Set<String> taken,int count){
    if(count<=0)throw new IllegalArgumentException("candidate count must be positive");
    var available=new java.util.TreeSet<>(heroPool);available.removeAll(taken);
    var result=new java.util.LinkedHashSet<String>();
    for(String id:current)if(available.contains(id) && result.size()<count)result.add(id);
    for(String id:available)if(result.size()<count)result.add(id);
    return List.copyOf(result);
  }

  /** Timeout assignment: a uniformly random choice from the candidates. */
  public static String timeoutPick(List<String> candidates, Random random) {
    return candidates.get(random.nextInt(candidates.size()));
  }
}
