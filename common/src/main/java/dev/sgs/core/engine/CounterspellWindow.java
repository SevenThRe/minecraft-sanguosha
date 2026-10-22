package dev.sgs.core.engine;
import dev.sgs.core.card.CardType;
import java.util.*;
import java.util.function.*;
/** One owning-worker competition, with no client-visible eligibility or decline list. */
public final class CounterspellWindow {
  public enum Receipt { INVALID, DECLINED, SCOPE_PASSED, WON, CLOSED, LATE }
  private final long id,deadline;private final int subject,depth;
  private final List<PromptRequest> requests;
  private final Map<UUID,PromptRequest> pending=new LinkedHashMap<>();
  private final Set<UUID> scopePasses=new HashSet<>();
  private final BiFunction<PromptRequest,PlayDecision,Optional<PlayDecision>> validator;
  private final LongSupplier now;private final Thread owner=Thread.currentThread();
  private CounterspellResult.Answer winner;private boolean closed;
  public CounterspellWindow(long id,int subject,int depth,long deadline,List<PromptRequest> requests,
      LongSupplier now,BiFunction<PromptRequest,PlayDecision,Optional<PlayDecision>> validator){
    this.id=id;this.subject=subject;this.depth=depth;this.deadline=deadline;this.requests=List.copyOf(requests);
    this.now=Objects.requireNonNull(now);this.validator=Objects.requireNonNull(validator);
    var ids=new HashSet<Long>();
    for(var p:requests){if(p.type()!=PromptType.WUXIE_COUNTER || p.deadlineMillis()!=deadline || !p.allowedTypes().equals(List.of(CardType.WUXIE))
        || !ids.add(p.id()) || pending.putIfAbsent(p.asker().id(),p)!=null)throw new IllegalArgumentException("invalid counterspell window");}
  }
  private void checkOwner(){if(Thread.currentThread()!=owner)throw new IllegalStateException("counterspell window requires owning worker");}
  public long windowId(){return id;}public int subjectSeat(){return subject;}public int chainDepth(){return depth;}public long deadlineMillis(){return deadline;}
  public List<PromptRequest> requests(){return requests;}
  public List<PromptRequest> pendingRequests(){checkOwner();return List.copyOf(pending.values());}
  public List<Integer> scopePassSeats(){checkOwner();return requests.stream().filter(p->scopePasses.contains(p.asker().id())).map(p->p.asker().seat()).toList();}
  void restoreProgress(Set<Integer> remaining,Set<Integer> passed){
    checkOwner();var seats=requests.stream().map(p->p.asker().seat()).collect(java.util.stream.Collectors.toSet());
    if(remaining.isEmpty() || !seats.containsAll(remaining) || !seats.containsAll(passed) || !Collections.disjoint(remaining,passed))throw new IllegalArgumentException("invalid saved counterspell progress");
    pending.entrySet().removeIf(e->!remaining.contains(e.getValue().asker().seat()));
    requests.stream().filter(p->passed.contains(p.asker().seat())).forEach(p->scopePasses.add(p.asker().id()));
  }
  public void retainEligible(Predicate<PromptRequest> eligible){checkOwner();pending.values().removeIf(p->!eligible.test(p));}
  public boolean expired(){return now.getAsLong()>=deadline;}
  public boolean closed(){checkOwner();return closed || pending.isEmpty();}
  public Receipt offer(UUID actor,long promptId,PromptResponder.WuxieResponse reply){
    checkOwner();if(closed())return Receipt.CLOSED;if(expired())return Receipt.LATE;
    var p=pending.get(actor);if(p==null || p.id()!=promptId || reply==null)return Receipt.INVALID;
    if(!p.asker().alive())return Receipt.INVALID;
    if(reply.passScope()){scopePasses.add(actor);pending.remove(actor);return Receipt.SCOPE_PASSED;}
    if(reply.decision().isEmpty()){pending.remove(actor);return Receipt.DECLINED;}
    var canonical=validator.apply(p,reply.decision().get());if(expired())return Receipt.LATE;if(canonical.isEmpty())return Receipt.INVALID;
    winner=new CounterspellResult.Answer(actor,canonical.get());closed=true;pending.clear();return Receipt.WON;
  }
  public CounterspellResult finish(){checkOwner();closed=true;pending.clear();return new CounterspellResult(Optional.ofNullable(winner),scopePasses);}
}
