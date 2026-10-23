package dev.sgs.validation.client;

import dev.sgs.validation.collection.CollectionNetwork;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.client.Minecraft;

public final class CollectionClient {
    private static CollectionNetwork.State state;
    private static long request=System.currentTimeMillis();
    private static long pending;
    public static void initialize() {
        CollectionNetwork.failureReceiver=error->Minecraft.getInstance().execute(()->{if(error.request()==pending)pending=0;
            if(Minecraft.getInstance().screen instanceof CollectionScreen screen)screen.error(error.message());});
        CollectionNetwork.receiver=next->Minecraft.getInstance().execute(()->{
            state=next;if(next.request()==pending)pending=0;
            var client=Minecraft.getInstance();
            if(next.open() && !(client.screen instanceof CollectionScreen))client.setScreen(new CollectionScreen(client.screen));
            if(client.screen instanceof CollectionScreen screen)screen.update(next);
        });
        Balm.getEvents().onEvent(net.blay09.mods.balm.api.event.client.DisconnectedFromServerEvent.class,e->{state=null;pending=0;});
    }
    public static void open(){Minecraft.getInstance().setScreen(new CollectionScreen(Minecraft.getInstance().screen));send("refresh","");}
    public static void openOwned(){open();if(Minecraft.getInstance().screen instanceof CollectionScreen screen)screen.showOwned();}
    public static void openMarket(){open();if(Minecraft.getInstance().screen instanceof CollectionScreen screen)screen.showMarket();}
    static CollectionNetwork.State state(){return state;}
    static boolean pending(){return pending!=0;}
    static void send(String action,String hero){if(pending!=0 && !action.equals("refresh"))return;pending=++request;
        long price=state==null?-1:state.heroes().stream().filter(h->h.id().equals(hero)).mapToLong(CollectionNetwork.Hero::price).findFirst().orElse(-1);
        if(state!=null && (action.equals("draw")||action.equals("draw10")))price=Math.multiplyExact(state.drawTokenCost(),action.equals("draw10")?10:1);
        Balm.getNetworking().sendToServer(new CollectionNetwork.Intent(pending,action,hero,price));}
    public static boolean ownsSkin(String hero,String skin){return skin.equals("classic") || state!=null && (!state.enabled() || state.skins().contains(hero+"/"+skin));}
    static void smokeStalePurchase(String hero){pending=++request;Balm.getNetworking().sendToServer(new CollectionNetwork.Intent(pending,"buy",hero,1));}
}
