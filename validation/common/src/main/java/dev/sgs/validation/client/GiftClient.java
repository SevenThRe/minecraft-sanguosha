package dev.sgs.validation.client;
import dev.sgs.validation.social.*;
import java.util.concurrent.atomic.AtomicLong;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.client.Minecraft;
public final class GiftClient {
    private static final AtomicLong REQUESTS=new AtomicLong(System.currentTimeMillis());
    public static void initialize(){GiftNetwork.receiver=r->Minecraft.getInstance().execute(()->{if(Minecraft.getInstance().screen instanceof MatchScreen screen)screen.gift(r);});}
    public static long request(long match,int target,String kind,int amount){
        long id=REQUESTS.incrementAndGet();
        Balm.getNetworking().sendToServer(new GiftNetwork.Intent(FeedbackNetwork.JSON.toJson(new GiftNetwork.Command(match,id,target,kind,amount))));return id;
    }
}
