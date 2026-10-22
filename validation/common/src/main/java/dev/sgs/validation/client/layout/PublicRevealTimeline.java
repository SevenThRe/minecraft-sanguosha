package dev.sgs.validation.client.layout;

import dev.sgs.core.match.dto.MatchViews.PublicCardRevealView;
import java.util.*;

/** Holds a server disclosure while resolving, then briefly shows completion. No log/hand inference. */
public final class PublicRevealTimeline {
    public static final long COMPLETED_MILLIS=1800;
    private long match=Long.MIN_VALUE,epoch=Long.MIN_VALUE,until;
    private PublicCardRevealView current;
    private final LinkedHashMap<String,Boolean> seen=new LinkedHashMap<>();
    public void accept(long matchId,long connectionEpoch,List<PublicCardRevealView> values,long now){
        boolean first=match!=matchId||epoch!=connectionEpoch;
        if(first){match=matchId;epoch=connectionEpoch;current=null;seen.clear();until=0;}
        var incoming=new HashSet<String>();
        for(var value:values){
            incoming.add(value.id());Boolean held=seen.put(value.id(),value.held());
            if(value.held()){
                if(first||held==null||!held||current!=null&&current.id().equals(value.id())){current=value;until=Long.MAX_VALUE;}
            }else if(!first&&(held==null||held)){
                current=value;until=now+COMPLETED_MILLIS;
            }
        }
        if(current!=null&&current.held()&&!incoming.contains(current.id())){
            current=new PublicCardRevealView(current.id(),current.ownerSeat(),current.subjectSeat(),current.reason(),current.cards(),false);
            until=now+COMPLETED_MILLIS;
        }
        while(seen.size()>256)seen.remove(seen.keySet().iterator().next());
    }
    public Optional<PublicCardRevealView> visible(long now){return current!=null&&now<until?Optional.of(current):Optional.empty();}
}
