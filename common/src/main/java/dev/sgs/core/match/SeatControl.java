package dev.sgs.core.match;

/** Worker-owned control state. Account ownership never changes when AI takes over. */
public final class SeatControl {
  public enum Source { MANUAL, USER, DISCONNECT }
  public record Snapshot(Source source,long version,boolean awaitingReconnect,long offlineRemainingMillis){
    public Snapshot{
      java.util.Objects.requireNonNull(source);
      if(version<0 || version>Long.MAX_VALUE-2 || offlineRemainingMillis < -1 || offlineRemainingMillis>300000
          || awaitingReconnect && offlineRemainingMillis!=-1 || source==Source.DISCONNECT && (awaitingReconnect || offlineRemainingMillis!=0))
        throw new IllegalArgumentException("invalid saved seat control");
    }
  }
  private Source source=Source.MANUAL;
  private long version,offlineAt=-1;
  private long restoredDeadline=-1;
  private boolean awaitingReconnect;
  public Source source(){return source;}
  public long version(){return version;}
  public long offlineAt(){return offlineAt;}
  public boolean automatic(){return source!=Source.MANUAL;}
  public boolean awaitingReconnect(){return awaitingReconnect;}
  public void awaitConnection(){awaitingReconnect=true;offlineAt=-1;restoredDeadline=-1;}
  public Snapshot snapshot(long now,long grace){
    long remaining=offlineAt<0?-1:source==Source.DISCONNECT?0:Math.max(0,restoredDeadline>=0?restoredDeadline-now:grace-(now-offlineAt));
    return new Snapshot(source,version,awaitingReconnect,remaining);
  }
  public void restore(Snapshot saved,long now){
    source=saved.source();version=Math.incrementExact(saved.version());awaitingReconnect=saved.awaitingReconnect();
    offlineAt=saved.offlineRemainingMillis()<0?-1:now;
    restoredDeadline=offlineAt<0?-1:Math.addExact(now,saved.offlineRemainingMillis());
  }
  public void restoreUser(boolean user){if(user)change(Source.USER);}
  public void connection(boolean online,long now){
    awaitingReconnect=false;
    if(online)restoredDeadline=-1;
    offlineAt=online?-1:offlineAt<0?now:offlineAt;
    if(online && source==Source.DISCONNECT)change(Source.MANUAL);
    // A connection boundary invalidates already queued decisions even within the grace period.
    version=Math.incrementExact(version);
  }
  public boolean request(boolean enabled,long expected){
    if(expected!=version)return false;
    change(enabled?Source.USER:Source.MANUAL);return true;
  }
  public boolean expire(long now,long grace){
    if(source==Source.MANUAL && offlineAt>=0 && (restoredDeadline>=0?now>=restoredDeadline:now-offlineAt>=grace)){change(Source.DISCONNECT);return true;}
    return false;
  }
  private void change(Source next){if(source!=next){source=next;version=Math.incrementExact(version);}}
}
