package dev.sgs.validation.client;

import com.google.gson.Gson;
import dev.sgs.validation.lobby.LobbyNetwork;
import java.util.concurrent.atomic.AtomicLong;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.client.Minecraft;

public final class LobbyClient {
    private static final Gson JSON = new Gson();
    private static final AtomicLong REQUESTS = new AtomicLong(System.currentTimeMillis());
    private static boolean open;
    static LobbyNetwork.Intent lastIntent;
    public static void initialize() {
        LobbyNetwork.openReceiver=()->Minecraft.getInstance().execute(LobbyClient::open);
        LobbyNetwork.clientReceiver = packet -> Minecraft.getInstance().execute(() -> {
            if (!open) return;
            var view = JSON.fromJson(packet.json(),LobbyNetwork.View.class);
            var client = Minecraft.getInstance();
            if (client.screen instanceof LobbyScreen screen) screen.update(view);
        });
    }
    public static void open() {
        open=true;
        Minecraft.getInstance().setScreen(new LobbyScreen());
        request(0,"refresh","",0);
    }
    public static long request(long revision,String action,String mode,long room) {
        return request(revision,action,mode,room,"");
    }
    public static long request(long revision,String action,String mode,long room,String data) {
        long request=REQUESTS.incrementAndGet();
        lastIntent=new LobbyNetwork.Intent(request,revision,action,mode,room,data==null?"":data);
        Balm.getNetworking().sendToServer(lastIntent);
        return request;
    }
    public static void close() {open=false;request(0,"close","",0);}
}
