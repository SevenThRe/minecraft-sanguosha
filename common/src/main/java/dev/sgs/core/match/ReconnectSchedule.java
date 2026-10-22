package dev.sgs.core.match;

import java.util.Locale;

/** Pure retry state; bounded unknown failures, capped backoff, exactly one attempt in flight. */
public final class ReconnectSchedule {
    public enum Failure { TRANSIENT, TERMINAL, UNKNOWN }
    private int attempts,unknown;
    private long nextAt;
    private boolean pending,inFlight;
    public void reset(){attempts=unknown=0;nextAt=0;pending=inFlight=false;}
    public int attempts(){return attempts;}
    public long nextAt(){return nextAt;}
    public boolean failed(long now,Failure kind){
        inFlight=false;
        if(kind==Failure.TERMINAL || kind==Failure.UNKNOWN && ++unknown>3){pending=false;return false;}
        long delay=attempts==0?2000:attempts==1?5000:attempts==2?10000:15000;
        nextAt=now+delay+java.util.concurrent.ThreadLocalRandom.current().nextInt(251);pending=true;return true;
    }
    public boolean due(long now){return pending && !inFlight && now>=nextAt;}
    public void begin(){if(!pending || inFlight)throw new IllegalStateException("retry already active");pending=false;inFlight=true;attempts++;}
    public static Failure classify(String reason){
        String s=reason.toLowerCase(Locale.ROOT);
        for(String key:new String[]{"invalid_packet","kicked","banned","duplicate_login","invalidsession","invalid_session","authentication","outdated","incompatible","not_whitelisted","unverified_username","missing_public_key","unsigned_chat","封禁","踢出","白名单","版本不兼容","验证失败"})if(s.contains(key))return Failure.TERMINAL;
        for(String key:new String[]{"multiplayer.disconnect.generic","disconnect.genericreason","closedchannelexception","远程主机强迫关闭","server_shutdown","timeout","timed out","endofstream","end of stream","connection reset","connection refused","network is unreachable","连接超时","连接已重置"})if(s.contains(key))return Failure.TRANSIENT;
        return Failure.UNKNOWN;
    }
}
