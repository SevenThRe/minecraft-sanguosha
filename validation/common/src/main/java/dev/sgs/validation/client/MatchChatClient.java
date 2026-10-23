package dev.sgs.validation.client;

import dev.sgs.core.social.MatchChat;
import dev.sgs.validation.match.*;
import java.util.*;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.client.Minecraft;

/** Independent of Screen/widget recreation and the game's action nonce. */
final class MatchChatClient {
    static long match=-1,epoch=-1,request,readSequence,pending,pendingAt,draftVersion,sentVersion;
    static boolean enabled,open,quick,follow=true;
    static String draft="",error="";
    static int scroll;
    static final NavigableMap<Long,MatchChat.Message> messages=new TreeMap<>();
    static void initialize(){MatchChatNetwork.receiver=r->Minecraft.getInstance().execute(()->receive(r));}
    static void clear(){match=-1;epoch=-1;request=0;readSequence=0;pending=0;enabled=false;open=false;quick=false;follow=true;scroll=0;draft="";error="";draftVersion=0;messages.clear();}
    static void bind(MatchNetwork.View view){
        if(match!=view.matchId()){clear();match=view.matchId();}
        if(epoch==view.connectionEpoch())return;
        epoch=view.connectionEpoch();pending=0;enabled=false;
        if(view.snapshot().viewerSeat()>=0&&Minecraft.getInstance().getConnection()!=null)
            Balm.getNetworking().sendToServer(new MatchChatNetwork.Intent(match,epoch,0,""));
    }
    static void receive(MatchChatNetwork.Update update){
        if(update.match()!=match||update.epoch()!=epoch)return;
        enabled=update.enabled();
        for(var message:update.messages())messages.putIfAbsent(message.sequence(),message);
        while(messages.size()>100)messages.pollFirstEntry();
        if(update.request()==pending&&pending>0){
            pending=0;
            if(update.accepted()){if(draftVersion==sentVersion){draft="";draftVersion++;}error="";}
            else error=update.reason();
        }else if(!update.accepted())error=update.reason();
    }
    static void edit(String value){if(!draft.equals(value)){draft=value;draftVersion++;error="";}}
    static int unread(){return (int)messages.tailMap(readSequence,false).size();}
    static void read(){if(!messages.isEmpty())readSequence=messages.lastKey();}
    static void tick(){if(pending>0&&net.minecraft.Util.getMillis()-pendingAt>10000){pending=0;error="TIMEOUT";}}
    static boolean canSend(){return enabled&&pending==0&&MatchChat.invalid(draft).isEmpty();}
    static void send(){
        tick();if(!canSend())return;
        if(Minecraft.getInstance().getConnection()==null){error="OFFLINE";return;}
        pending=++request;pendingAt=net.minecraft.Util.getMillis();sentVersion=draftVersion;error="";
        Balm.getNetworking().sendToServer(new MatchChatNetwork.Intent(match,epoch,pending,draft));
    }
}
