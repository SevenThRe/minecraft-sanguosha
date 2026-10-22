package dev.sgs.core.engine;

import java.util.*;

/** Shared rescue-ring cursor. Only a completed payment counts as a successful round response. */
public record RescueRound(List<Integer> ring,int cursor,boolean played) {
  public RescueRound {
    ring=List.copyOf(ring);
    if(ring.size()>8||new HashSet<>(ring).size()!=ring.size()||ring.stream().anyMatch(s->s<0||s>7)
        ||cursor<0||cursor>ring.size()||cursor==0&&played)throw new IllegalArgumentException("invalid rescue round");
  }
  public static RescueRound start(List<Integer> ring){return new RescueRound(ring,0,false);}
  public boolean exhausted(){return cursor==ring.size();}
  public int asker(){if(exhausted())throw new IllegalStateException("rescue round exhausted");return ring.get(cursor);}
  public RescueRound completed(boolean paid){
    if(exhausted())throw new IllegalStateException("rescue round exhausted");
    return new RescueRound(ring,cursor+1,played||paid);
  }
  public RescueRound next(List<Integer> livingRing){
    if(!exhausted()||!played)throw new IllegalStateException("no successful completed rescue round");
    return start(livingRing);
  }
}
