package dev.sgs.validation.client.layout;

import dev.sgs.core.match.dto.MatchViews.CardMotionView;
import java.util.*;

/** Bounded, monotonic client presentation of committed, recipient-filtered server moves. */
public final class CardMotionTimeline {
    public static final int MAX_FLIGHTS=64,MAX_GROUP=8;
    public static final long DURATION=440,STAGGER=45;
    public record Flight(CardMotionView event,int index,long start) {
        public long end(){return start+DURATION;}
        public float progress(long now){return Math.clamp((now-start)/(float)DURATION,0,1);}
        public float eased(long now){float t=progress(now);return 1-(1-t)*(1-t)*(1-t);}
    }
    private long match=Long.MIN_VALUE,epoch=Long.MIN_VALUE,revision=-1,sequence;
    private int viewer=Integer.MIN_VALUE;
    private final List<Flight> flights=new ArrayList<>();
    public void accept(long match,long epoch,int viewer,long revision,List<CardMotionView> events,long now){
        if(this.match!=match || this.epoch!=epoch || this.viewer!=viewer){
            this.match=match;this.epoch=epoch;this.viewer=viewer;this.revision=revision;flights.clear();
            sequence=events.stream().mapToLong(CardMotionView::sequence).max().orElse(0);return;
        }
        if(revision<=this.revision)return;
        this.revision=revision;flights.removeIf(f->now>=f.end());
        var incoming=events.stream().filter(e->e.sequence()>sequence).sorted(Comparator.comparingLong(CardMotionView::sequence)).toList();
        for(var event:incoming){
            if(event.sequence()<=sequence)continue;sequence=event.sequence();
            for(int i=0;i<Math.min(MAX_GROUP,event.count());i++){
                long start=now+i*STAGGER;
                if(!event.faces().isEmpty()){
                    long id=event.faces().get(i).instanceId();
                    for(var old:flights)if(!old.event().faces().isEmpty() && old.event().faces().get(old.index()).instanceId()==id)start=Math.max(start,old.end());
                    // Compress long bursts to the newest location instead of accumulating seconds of stale moves.
                    if(start>now+1200){flights.removeIf(f->!f.event().faces().isEmpty()&&f.event().faces().get(f.index()).instanceId()==id);start=now+i*STAGGER;}
                }
                flights.add(new Flight(event,i,start));
            }
        }
        while(flights.size()>MAX_FLIGHTS)flights.removeFirst();
    }
    public List<Flight> visible(long now){flights.removeIf(f->now>=f.end());return flights.stream().filter(f->now>=f.start()).toList();}
    public boolean arriving(long card,int seat,long now){return flights.stream().anyMatch(f->now<f.end()&&f.event().toKind().equals("HAND")&&f.event().toSeat()==seat&&!f.event().faces().isEmpty()&&f.event().faces().get(f.index()).instanceId()==card);}
    public int queued(){return flights.size();}
}
