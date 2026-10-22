package dev.sgs.core.social;

import dev.sgs.core.Identity;
import dev.sgs.core.engine.GameOutcome;
import java.util.*;

/** Server-owned local reports and noncommercial rewards. Call from one owner thread. */
public final class MatchLedger {
  public static final int DATA_VERSION=1;
  public static final String REWARD_RULE="participation20-win30-honor1-v1";
  public static final int MAX_DETAIL=240;
  public enum ReportReason {ABUSE,GRIEFING,CHEATING,OTHER}
  public record Account(UUID player,long xp,long honor){}
  public record Receipt(long matchId,UUID player,boolean won,int xpAward,int honorAward,
      long totalXp,long totalHonor,String receiptId,String ruleVersion){}
  public record Report(long id,long matchId,UUID reporter,UUID target,int targetSeat,ReportReason reason,String detail,long createdAt){}
  public record MatchRecord(long id,int capacity,List<UUID> humans,Map<UUID,Integer> seats,GameOutcome outcome,long completedAt){
    public MatchRecord{humans=List.copyOf(humans);seats=Map.copyOf(seats);}
  }
  public record FinishSeat(int seat,boolean human,Identity identity,int team,boolean alive){
    public FinishSeat(int seat, boolean human, Identity identity, int team) { this(seat,human,identity,team,true); }
  }
  public record Reply(boolean accepted,String reason,Receipt receipt){
    public static Reply reject(String reason){return new Reply(false,reason,null);}
    public static Reply ok(Receipt receipt){return new Reply(true,"OK",receipt);}
  }
  public record Snapshot(int version,long lastMatchId,long lastReportId,List<MatchRecord> matches,
      List<Account> accounts,List<Receipt> receipts,List<Report> reports){
    public Snapshot{matches=List.copyOf(matches);accounts=List.copyOf(accounts);receipts=List.copyOf(receipts);reports=List.copyOf(reports);}
  }
  private long lastMatchId,lastReportId;
  private final Map<Long,MatchRecord> matches=new LinkedHashMap<>();
  private final Map<UUID,Account> accounts=new LinkedHashMap<>();
  private final Map<String,Receipt> receipts=new LinkedHashMap<>();
  private final List<Report> reports=new ArrayList<>();

  /** Persistent monotonic allocator also tolerates clock rollback. Gaps from failed starts are OK. */
  public long allocateMatchId(long now){lastMatchId=Math.max(Math.addExact(lastMatchId,1),Math.max(1,now));return lastMatchId;}
  public void begin(long id,int capacity,List<UUID> humans){
    if(id<=0 || id>lastMatchId || matches.containsKey(id) || !Set.of(4,6,8).contains(capacity)
        || humans.isEmpty() || humans.size()>capacity || humans.stream().distinct().count()!=humans.size())throw new IllegalArgumentException("invalid match roster");
    matches.put(id,new MatchRecord(id,capacity,humans,Map.of(),GameOutcome.ONGOING,0));
  }
  public void assignSeat(long id,UUID human,int seat){
    var match=matches.get(id);if(match==null || !match.humans().contains(human) || seat<0 || seat>=match.capacity())throw new IllegalArgumentException("unknown match participant");
    Integer existing=match.seats().get(human);if(existing!=null){if(existing!=seat)throw new IllegalStateException("seat changed");return;}
    if(match.seats().containsValue(seat))throw new IllegalStateException("duplicate human seat");
    var seats=new LinkedHashMap<>(match.seats());seats.put(human,seat);
    matches.put(id,new MatchRecord(id,match.capacity(),match.humans(),seats,match.outcome(),match.completedAt()));
  }
  public Account account(UUID player){return accounts.getOrDefault(player,new Account(player,0,0));}
  public MatchRecord match(long id){return matches.get(id);}
  public List<Report> reports(){return List.copyOf(reports);}
  private static String key(long match,UUID player){return match+":"+player;}

  /** Only trusted terminal match snapshots reach this method; client intents never carry results. */
  public List<Receipt> finish(long id,GameOutcome outcome,List<FinishSeat> seats,long now){
    var match=matches.get(id);if(match==null)throw new IllegalArgumentException("unknown match");
    if(outcome==null || outcome==GameOutcome.ONGOING)throw new IllegalArgumentException("not terminal");
    if(match.outcome()!=GameOutcome.ONGOING){
      if(match.outcome()!=outcome)throw new IllegalStateException("conflicting settlement");
      return match.humans().stream().map(player->receipts.get(key(id,player))).toList();
    }
    if(match.seats().size()!=match.humans().size() || seats.size()!=match.capacity()
        || seats.stream().map(FinishSeat::seat).distinct().count()!=match.capacity())throw new IllegalStateException("incomplete roster");
    var bySeat=new HashMap<Integer,FinishSeat>();
    for(var row:seats){
      if(row.seat()<0 || row.seat()>=match.capacity() || row.human()!=match.seats().containsValue(row.seat()) || row.identity()==null)throw new IllegalArgumentException("invalid result seat");
      bySeat.put(row.seat(),row);
    }
    boolean teams=outcome==GameOutcome.TEAM_A_WIN || outcome==GameOutcome.TEAM_B_WIN;
    if(teams && match.capacity()!=4)throw new IllegalArgumentException("team outcome outside four-seat mode");
    var awarded=new ArrayList<Receipt>();var updated=new ArrayList<Account>();
    for(UUID player:match.humans()){
      var row=bySeat.get(match.seats().get(player));
      boolean won=switch(outcome){
        case LORD_WIN->row.identity()==Identity.LORD || row.identity()==Identity.LOYALIST;
        case REBELS_WIN->row.identity()==Identity.REBEL;
        case RENEGADE_WIN->row.identity()==Identity.RENEGADE && row.alive();
        case TEAM_A_WIN->row.team()==0;
        case TEAM_B_WIN->row.team()==1;
        default->false;
      };
      var before=account(player);int xp=won?50:20,honor=won?1:0;
      var next=new Account(player,Math.addExact(before.xp(),xp),Math.addExact(before.honor(),honor));
      updated.add(next);awarded.add(new Receipt(id,player,won,xp,honor,next.xp(),next.honor(),key(id,player),REWARD_RULE));
    }
    // Prepare the entire update before touching totals, so validation/overflow cannot partly pay.
    for(var next:updated)accounts.put(next.player(),next);
    for(var receipt:awarded)receipts.put(receipt.receiptId(),receipt);
    matches.put(id,new MatchRecord(id,match.capacity(),match.humans(),match.seats(),outcome,now));
    return List.copyOf(awarded);
  }
  public Reply result(UUID actor,long matchId){
    var match=matches.get(matchId);if(match==null)return Reply.reject("MATCH_NOT_FOUND");
    if(!match.humans().contains(actor))return Reply.reject("NOT_PARTICIPANT");
    var receipt=receipts.get(key(matchId,actor));return receipt==null?Reply.reject("RESULT_NOT_READY"):Reply.ok(receipt);
  }
  public Reply report(UUID actor,long matchId,int targetSeat,String reason,String detail,long now){
    var match=matches.get(matchId);if(match==null)return Reply.reject("MATCH_NOT_FOUND");
    if(!match.humans().contains(actor))return Reply.reject("NOT_PARTICIPANT");
    if(targetSeat<0 || targetSeat>=match.capacity())return Reply.reject("INVALID_TARGET");
    UUID target=match.seats().entrySet().stream().filter(e->e.getValue()==targetSeat).map(Map.Entry::getKey).findFirst().orElse(null);
    if(target==null)return Reply.reject("TARGET_AI");
    if(target.equals(actor))return Reply.reject("TARGET_SELF");
    ReportReason parsed;try{parsed=ReportReason.valueOf(reason);}catch(RuntimeException e){return Reply.reject("INVALID_REASON");}
    if(detail==null)detail="";
    if(detail.length()>MAX_DETAIL)return Reply.reject("DETAIL_TOO_LONG");
    if(detail.codePoints().anyMatch(c->Character.isISOControl(c)))return Reply.reject("INVALID_DETAIL");
    for(var report:reports){
      if(report.matchId()==matchId && report.reporter().equals(actor) && report.target().equals(target))return Reply.reject("DUPLICATE_REPORT");
    }
    long previous=reports.stream().filter(r->r.reporter().equals(actor)).mapToLong(Report::createdAt).max().orElse(Long.MIN_VALUE);
    if(previous!=Long.MIN_VALUE && (now<previous || now-previous<10_000))return Reply.reject("RATE_LIMITED");
    reports.add(new Report(++lastReportId,matchId,actor,target,targetSeat,parsed,detail.strip(),now));return Reply.ok(null);
  }
  public Snapshot snapshot(){return new Snapshot(DATA_VERSION,lastMatchId,lastReportId,List.copyOf(matches.values()),List.copyOf(accounts.values()),List.copyOf(receipts.values()),reports);}
  public static MatchLedger restore(Snapshot snapshot){
    if(snapshot==null || snapshot.version()!=DATA_VERSION || snapshot.lastMatchId()<0 || snapshot.lastReportId()<0)throw new IllegalArgumentException("unsupported ledger data");
    var restored=new MatchLedger();restored.lastMatchId=snapshot.lastMatchId();restored.lastReportId=snapshot.lastReportId();
    for(var match:snapshot.matches()){
      if(match.id()<=0 || match.id()>restored.lastMatchId || match.outcome()==null || !Set.of(4,6,8).contains(match.capacity())
          || match.humans().isEmpty() || match.humans().size()>match.capacity() || match.humans().stream().distinct().count()!=match.humans().size()
          || !match.humans().containsAll(match.seats().keySet()) || match.seats().values().stream().anyMatch(s->s<0 || s>=match.capacity())
          || match.seats().values().stream().distinct().count()!=match.seats().size() || restored.matches.putIfAbsent(match.id(),match)!=null)throw new IllegalArgumentException("invalid stored match");
    }
    for(var a:snapshot.accounts())if(a.player()==null || a.xp()<0 || a.honor()<0 || restored.accounts.putIfAbsent(a.player(),a)!=null)throw new IllegalArgumentException("invalid stored account");
    for(var r:snapshot.receipts()){
      var match=restored.matches.get(r.matchId());
      if(match==null || match.outcome()==GameOutcome.ONGOING || !match.humans().contains(r.player()) || !key(r.matchId(),r.player()).equals(r.receiptId())
          || !REWARD_RULE.equals(r.ruleVersion()) || r.xpAward()!=(r.won()?50:20) || r.honorAward()!=(r.won()?1:0)
          || r.totalXp()<r.xpAward() || r.totalHonor()<r.honorAward() || restored.receipts.putIfAbsent(r.receiptId(),r)!=null)throw new IllegalArgumentException("invalid stored receipt");
    }
    var reportIds=new HashSet<Long>();var reportPairs=new HashSet<String>();
    for(var r:snapshot.reports()){
      var match=restored.matches.get(r.matchId());
      if(r.id()<=0 || r.id()>restored.lastReportId || !reportIds.add(r.id()) || match==null || r.reporter()==null || r.target()==null || r.reporter().equals(r.target())
          || !match.humans().contains(r.reporter()) || !Objects.equals(match.seats().get(r.target()),r.targetSeat()) || r.reason()==null || r.detail()==null
          || r.detail().length()>MAX_DETAIL || !reportPairs.add(key(r.matchId(),r.reporter())+":"+r.target()))throw new IllegalArgumentException("invalid stored report");
      restored.reports.add(r);
    }
    // Receipts are the source of totals; never accept mismatched cumulative balances after reload.
    for(var a:restored.accounts.values()){
      long xp=0,honor=0;for(var r:restored.receipts.values())if(r.player().equals(a.player())){xp=Math.addExact(xp,r.xpAward());honor=Math.addExact(honor,r.honorAward());}
      if(xp!=a.xp() || honor!=a.honor())throw new IllegalArgumentException("receipt totals mismatch");
    }
    for(var match:restored.matches.values())if(match.outcome()!=GameOutcome.ONGOING)
      for(var human:match.humans())if(!restored.receipts.containsKey(key(match.id(),human)) || !restored.accounts.containsKey(human))throw new IllegalArgumentException("incomplete settlement");
    return restored;
  }
}
