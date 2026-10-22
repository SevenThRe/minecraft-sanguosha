package dev.sgs.core.social;

import java.util.*;

/** Append-only local moderation audit. Reports never impose an automatic sanction. */
public final class ModerationLedger {
    public enum Action { DISMISS, WARN, SUSPEND, REVOKE }
    public record Entry(long id,long reportId,UUID target,String moderator,Action action,String note,long at,long until) {}
    public record Snapshot(int version,List<Entry> entries) {
        public Snapshot { entries=List.copyOf(entries); }
    }
    private final List<Entry> entries=new ArrayList<>();
    public List<Entry> entries(){return List.copyOf(entries);}
    public Entry decision(long reportId){return entries.stream().filter(e->e.reportId()==reportId).findFirst().orElse(null);}
    public long suspendedUntil(UUID target,long now){
        long until=0;
        for(var e:entries)if(e.target().equals(target)){
            if(e.action()==Action.REVOKE)until=0;
            else if(e.action()==Action.SUSPEND)until=Math.max(until,e.until());
        }
        return until>now?until:0;
    }
    public Entry review(MatchLedger reports,long reportId,String moderator,Action action,String note,long now,long minutes){
        var report=reports.reports().stream().filter(r->r.id()==reportId).findFirst().orElseThrow(()->new IllegalArgumentException("举报不存在"));
        if(decision(reportId)!=null)throw new IllegalArgumentException("该举报已处理");
        if(action==null || action==Action.REVOKE)throw new IllegalArgumentException("无效处理方式");
        long until=0;
        if(action==Action.SUSPEND){
            if(minutes<1 || minutes>525600)throw new IllegalArgumentException("禁赛时长为1至525600分钟");
            until=Math.addExact(now,Math.multiplyExact(minutes,60000));
        }else if(minutes!=0)throw new IllegalArgumentException("此处理不需要时长");
        return append(reportId,report.target(),moderator,action,note,now,until);
    }
    public Entry revoke(UUID target,String moderator,String note,long now){
        if(suspendedUntil(target,now)==0)throw new IllegalArgumentException("该玩家没有生效中的禁赛");
        return append(0,target,moderator,Action.REVOKE,note,now,0);
    }
    private Entry append(long report,UUID target,String moderator,Action action,String note,long at,long until){
        validateText(moderator,128);validateText(note,240);
        if(target==null || at<0)throw new IllegalArgumentException("无效处理记录");
        var entry=new Entry(entries.size()+1L,report,target,moderator,action,note.strip(),at,until);entries.add(entry);return entry;
    }
    private static void validateText(String text,int max){
        if(text==null || text.isBlank() || text.length()>max || text.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("处理说明为空、过长或含控制字符");
    }
    public Snapshot snapshot(){return new Snapshot(1,entries);}
    public static ModerationLedger restore(Snapshot snapshot){
        if(snapshot==null || snapshot.version()!=1)throw new IllegalArgumentException("无效管理数据版本");
        var result=new ModerationLedger();var reviewed=new HashSet<Long>();
        for(var e:snapshot.entries()){
            if(e==null || e.id()!=result.entries.size()+1L || e.action()==null
                || (e.action()==Action.REVOKE?e.reportId()!=0:e.reportId()<=0 || !reviewed.add(e.reportId()))
                || (e.action()==Action.SUSPEND?e.until()<=e.at() || e.until()-e.at()>525600L*60000:e.until()!=0))throw new IllegalArgumentException("无效管理记录");
            result.append(e.reportId(),e.target(),e.moderator(),e.action(),e.note(),e.at(),e.until());
        }
        return result;
    }
}
