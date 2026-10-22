package dev.sgs.validation.client;

/** Presentation only. Receipt identity and results are never fabricated by the animation. */
public final class RecruitmentTimeline {
    private long receipt=Long.MIN_VALUE,started;
    private int count;
    private long revealStart=2200;
    private boolean skipped;
    public boolean start(long request,int size,long now,boolean skip){
        return start(request,size,now,skip,2200);
    }
    public boolean start(long request,int size,long now,boolean skip,long introMillis){
        if(size<=0 || request<=receipt)return false;
        receipt=request;count=size;started=now;skipped=skip;revealStart=Math.max(0,introMillis);return true;
    }
    public void skip(){skipped=true;}
    public long elapsed(long now){return Math.max(0,now-started);}
    public long duration(){return revealStart+600L+Math.max(0,count-1)*100L;}
    public boolean playing(long now){return count>0 && !skipped && elapsed(now)<duration();}
    public int revealed(long now){
        if(skipped)return count;
        return elapsed(now)<revealStart?0:Math.min(count,1+(int)((elapsed(now)-revealStart)/100));
    }
    public int count(){return count;}
}
