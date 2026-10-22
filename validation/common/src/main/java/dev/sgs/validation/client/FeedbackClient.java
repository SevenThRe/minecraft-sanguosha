package dev.sgs.validation.client;
import dev.sgs.validation.social.FeedbackNetwork;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.client.Minecraft;
/** Only caches server-issued receipts; never computes or claims awards locally. */
public final class FeedbackClient {
    private static final AtomicLong REQUESTS=new AtomicLong(System.currentTimeMillis());
    private static final Map<Long,FeedbackNetwork.RewardView> REWARDS=new HashMap<>();
    private FeedbackClient(){}
    public static void initialize(){FeedbackNetwork.receiver=r->Minecraft.getInstance().execute(()->{
        if(r.reward()!=null)REWARDS.put(r.matchId(),r.reward());
        if(Minecraft.getInstance().screen instanceof MatchScreen s)s.feedback(r);
    });}
    public static FeedbackNetwork.RewardView reward(long match){return REWARDS.get(match);}
    public static long request(long match,String kind,int target,String reason,String detail){
        var c=new FeedbackNetwork.Command(match,REQUESTS.incrementAndGet(),kind,target,reason,detail);
        Balm.getNetworking().sendToServer(new FeedbackNetwork.Intent(FeedbackNetwork.JSON.toJson(c)));
        return c.requestId();
    }
}
