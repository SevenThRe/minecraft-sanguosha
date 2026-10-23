package dev.sgs.core.social;

import java.util.*;

/** Bounded, match-local text channel. Owned exclusively by the server thread. */
public final class MatchChat {
    public record Message(long sequence, UUID sender, int seat, String name, String text, long time) {}
    public record Reply(boolean accepted, String reason, Message message, boolean fresh) {}
    private record Receipt(long epoch,long request,Reply reply) {}
    private final Set<UUID> members;
    private final boolean enabled;
    private final Deque<Message> history=new ArrayDeque<>();
    private final Map<UUID,Long> lastSent=new HashMap<>();
    private final Map<UUID,Receipt> receipts=new HashMap<>();
    private long sequence;
    public MatchChat(Collection<UUID> members,boolean enabled){this.members=Set.copyOf(members);this.enabled=enabled;}
    public boolean enabled(){return enabled;}
    public List<Message> history(){return List.copyOf(history);}
    public static String invalid(String text){
        if(text==null || text.isBlank())return "EMPTY";
        if(text.codePointCount(0,text.length())>120)return "TOO_LONG";
        if(text.codePoints().anyMatch(c->Character.isISOControl(c)||c==0x00a7||c>=0xd800&&c<=0xdfff))return "INVALID_TEXT";
        return "";
    }
    public Reply send(UUID actor,long epoch,long request,int seat,String name,String text,long now){
        if(!members.contains(actor)||seat<0)return new Reply(false,"NOT_IN_MATCH",null,false);
        if(request<=0||epoch<0)return new Reply(false,"STALE",null,false);
        var prior=receipts.get(actor);
        if(prior!=null){
            if(epoch==prior.epoch&&request==prior.request){var r=prior.reply;return new Reply(r.accepted,r.reason,r.message,false);}
            if(epoch<prior.epoch||epoch==prior.epoch&&request<prior.request)return new Reply(false,"STALE",null,false);
        }
        String reason=!enabled?"DISABLED":invalid(text);
        if(reason.isEmpty()&&lastSent.containsKey(actor)&&now-lastSent.get(actor)<700)reason="RATE_LIMITED";
        Message message=null;
        if(reason.isEmpty()){
            message=new Message(++sequence,actor,seat,name,text.strip(),now);history.addLast(message);
            while(history.size()>100)history.removeFirst();lastSent.put(actor,now);
        }
        var reply=new Reply(reason.isEmpty(),reason,message,message!=null);receipts.put(actor,new Receipt(epoch,request,reply));return reply;
    }
}
