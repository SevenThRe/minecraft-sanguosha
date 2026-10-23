package dev.sgs.validation.client.layout;

import dev.sgs.core.match.dto.MatchViews.*;
import java.util.*;

/** Presentation only: consumes public receipts, never postpones a server decision. */
public final class PublicCardTimeline {
    public static final long ARRIVE=340, REFLOW=220, HOLD=1500, EXIT=280;
    public static final int LIMIT=8;
    public static final class Entry {
        public final CardView card;
        public final int actor;
        public final String reason;
        public final long arrived;
        private float from,target;
        private long moved;
        Entry(CardView card,int actor,String reason,long arrived){this.card=card;this.actor=actor;this.reason=reason;this.arrived=arrived;}
        public float x(long now){return from+(target-from)*ease((now-moved)/(float)REFLOW);}
        public float arrival(long now){return ease((now-arrived)/(float)ARRIVE);}
    }
    private long match=Long.MIN_VALUE,epoch=Long.MIN_VALUE,revision=-1,sequence,expires;
    private int viewer=-1;
    private boolean held;
    private final List<Entry> entries=new ArrayList<>();
    public static float ease(float t){t=Math.clamp(t,0,1);return 1-(1-t)*(1-t)*(1-t);}
    public void accept(long match,long epoch,int viewer,long revision,List<PublicMoveView> moves,List<CardView> processing,long now){
        boolean baseline=this.match!=match||this.epoch!=epoch||this.viewer!=viewer;
        if(!baseline&&revision<=this.revision)return;
        if(baseline){this.match=match;this.epoch=epoch;this.viewer=viewer;sequence=0;entries.clear();held=false;expires=0;}
        this.revision=revision;
        if(now>=expires&&!held)entries.clear();
        boolean changed=false;
        for(var move:moves.stream().sorted(Comparator.comparingLong(PublicMoveView::sequence)).toList()){
            if(move.sequence()<=sequence)continue;
            sequence=move.sequence();
            if(!Set.of("USE","RESPONSE","RECAST").contains(move.reason()))continue;
            // On reconnect only material still resolving is restored, not historical animations.
            for(var card:move.cards()){
                if(baseline&&processing.stream().noneMatch(c->c.instanceId()==card.instanceId()))continue;
                entries.removeIf(e->e.card.instanceId()==card.instanceId());
                entries.add(new Entry(card,move.actorSeat(),move.reason(),baseline?now-ARRIVE:now));
                changed=true;
            }
        }
        while(entries.size()>LIMIT)entries.removeFirst();
        boolean resolving=entries.stream().anyMatch(e->processing.stream().anyMatch(c->c.instanceId()==e.card.instanceId()));
        if(changed||held&&!resolving)expires=now+HOLD+EXIT;
        held=resolving;
        if(changed){
            float step=Math.min(112,510f/Math.max(1,entries.size()-1));
            for(int i=0;i<entries.size();i++){
                var e=entries.get(i);float target=(i-(entries.size()-1)/2f)*step;
                e.from=e.arrived==now||baseline?target:e.x(now);e.target=target;e.moved=now;
            }
        }
    }
    public List<Entry> visible(long now){return !held&&now>=expires?List.of():List.copyOf(entries);}
    public float opacity(long now){return held?1:Math.clamp((expires-now)/(float)EXIT,0,1);}
    public boolean replaces(CardMotionView event){return Set.of("USE","RESPONSE","RECAST","FINISH").contains(event.reason())
        &&!event.faces().isEmpty()&&event.faces().stream().allMatch(c->entries.stream().anyMatch(e->e.card.instanceId()==c.instanceId()));}
}
