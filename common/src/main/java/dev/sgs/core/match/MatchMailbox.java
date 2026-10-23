package dev.sgs.core.match;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.function.Consumer;

/**
 * Single-consumer action mailbox for one match worker. Network threads only enqueue; the worker
 * polls with a deadline. Envelopes that do not match the current wait (for example a play action
 * arriving while a prompt response is awaited) stay queued in FIFO order and are reconsidered by
 * later polls, so a valid action is never silently dropped.
 */
public final class MatchMailbox {

  /** One envelope: the sender id plus the action. */
  public record Envelope(UUID senderId, MatchAction action,long epoch,long controlVersion) {
    public Envelope(UUID senderId,MatchAction action,long epoch){this(senderId,action,epoch,-1);}
    public Envelope(UUID senderId,MatchAction action){this(senderId,action,0,-1);}
  }
  public static Envelope envelope(UUID id,MatchAction action,long epoch,long version){return new Envelope(id,action,epoch,version);}
  /** Transport lifecycle controls are not client actions and cannot be forged via MatchAction. */
  public sealed interface Control permits Connectivity, Spectator, Rejection, TeamSharing, SaveCheckpoint, AutoControl, TargetQuery {}
  public record TargetQuery(UUID playerId,long epoch,String skill,java.util.List<Long> costs,int declared,int primary,java.util.function.Consumer<java.util.Map<Integer,String>> reply) implements Control {}
  public record AutoControl(UUID playerId,boolean enabled,long epoch,long version,int nonce) implements Control {}
  public record SaveCheckpoint(java.util.concurrent.CompletableFuture<dev.sgs.core.persistence.MatchCheckpoint> result,boolean pause) implements Control {}
  public record TeamSharing(boolean enabled) implements Control {}
  public record Connectivity(UUID playerId,boolean online,long epoch) implements Control {
    public Connectivity(UUID playerId,boolean online){this(playerId,online,-1);}
  }
  public record Spectator(MatchEndpoint endpoint,boolean joined) implements Control {}
  public record Rejection(UUID playerId,int nonce,String reason) implements Control {}

  private final Deque<Envelope> queue = new ArrayDeque<>();
  private final Deque<Control> controls = new ArrayDeque<>();
  private final Lock lock = new ReentrantLock();
  private final Condition condition = lock.newCondition();
  private long wakeVersion;

  /** Non-blocking enqueue from the network thread. */
  public void submit(UUID senderId, MatchAction action) {submit(senderId,action,0);}
  public void submit(UUID senderId,MatchAction action,long epoch){submit(senderId,action,epoch,-1);}
  public void submit(UUID senderId,MatchAction action,long epoch,long controlVersion){
    lock.lock();
    try {
      queue.addLast(new Envelope(senderId, action,epoch,controlVersion));
      condition.signalAll();
    } finally {
      lock.unlock();
    }
  }

  public void submitControl(Control control){
    lock.lock();try{controls.addLast(java.util.Objects.requireNonNull(control));condition.signalAll();}
    finally{lock.unlock();}
  }

  /** Drain the control prefix at an AI decision boundary, without holding the producer lock. */
  public void drainControls(Consumer<Control> handler){
    while(true){Control next;lock.lock();try{next=controls.pollFirst();}finally{lock.unlock();}
      if(next==null)return;handler.accept(next);}
  }

  /**
   * Waits up to {@code timeoutMillis} for the first envelope matching the predicate; unmatched
   * envelopes remain queued. Returns null on timeout or wake.
   */
  public Envelope poll(Predicate<Envelope> filter, long timeoutMillis) {
    return poll(filter,timeoutMillis,ignored->{});
  }

  /** Service lifecycle events on the consumer thread without ending or extending its game wait. */
  public Envelope poll(Predicate<Envelope> filter,long timeoutMillis,Consumer<Control> handler){
    long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(Math.max(timeoutMillis,0L));
    lock.lock();
    try {
      long observedWake=wakeVersion;
      while (true) {
        if(wakeVersion!=observedWake)return null;
        if(!controls.isEmpty()){
          Control control=controls.removeFirst();
          // A slow endpoint must not hold the producer's mailbox lock.
          lock.unlock();try{handler.accept(control);}finally{lock.lock();}
        }
        if(wakeVersion!=observedWake)return null;
        Envelope found = null;
        for (Envelope envelope : queue) {
          if (filter.test(envelope)) {
            found = envelope;
            break;
          }
        }
        if (found != null) {
          queue.remove(found);
          return found;
        }
        long remaining=end-System.nanoTime();
        if (remaining <= 0L) {
          return null;
        }
        if(!controls.isEmpty())continue;
        condition.awaitNanos(remaining);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    } finally {
      lock.unlock();
    }
  }

  /** Wakes every blocked poll without delivering an envelope (session shutdown). */
  public void wake() {
    lock.lock();
    try {
      wakeVersion++;
      condition.signalAll();
    } finally {
      lock.unlock();
    }
  }
}
