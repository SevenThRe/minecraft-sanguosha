package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.match.MatchChatNetwork;
import java.util.*;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Opt-in real two-client delivery, replay and cross-match rejection test. */
final class MatchChatMultiplayerChecks {
    private static int stage,ticks,hold;private static boolean done;private static long sent;
    private static final List<MatchChatNetwork.Update> receipts=new ArrayList<>();
    static boolean tick(Minecraft client,MatchScreen screen){
        if(!Boolean.getBoolean("sgs.matchChatMultiplayer"))return false;
        if(done)return true;
        var view=screen.stateForSmoke();if(view.snapshot().status()!=1||view.snapshot().openingHand()!=null)return false;
        if(++ticks>600)throw new IllegalStateException("chat multiplayer timeout stage="+stage);
        String role=System.getProperty("sgs.matchRole","");String text="CHAT_NATIVE_"+role+" 中文 /help";
        switch(stage){
            case 0 -> {
                if(!MatchChatClient.enabled)return true;
                var prior=MatchChatNetwork.receiver;MatchChatNetwork.receiver=r->{prior.accept(r);client.execute(()->receipts.add(r));};
                screen.clickForSmoke("chat");screen.chatDraftForSmoke(text);screen.keyPressed(257,0,0);sent=MatchChatClient.pending;
                if(sent<=0)throw new IllegalStateException("chat submit failed");stage=1;
            }
            case 1 -> {
                if(MatchChatClient.pending!=0||MatchChatClient.messages.values().stream().filter(m->m.text().startsWith("CHAT_NATIVE_")).count()!=2)return true;
                if(!MatchChatClient.draft.isEmpty())throw new IllegalStateException("ack did not clear draft");
                Balm.getNetworking().sendToServer(new MatchChatNetwork.Intent(view.matchId(),view.connectionEpoch(),sent,text));stage=2;
            }
            case 2 -> {
                if(receipts.stream().filter(r->r.request()==sent&&r.accepted()).count()<2)return true;
                if(MatchChatClient.messages.size()!=2)throw new IllegalStateException("duplicate chat delivery");
                Balm.getNetworking().sendToServer(new MatchChatNetwork.Intent(view.matchId()+99999,view.connectionEpoch(),991,"CROSS_MATCH_LEAK"));stage=3;
            }
            case 3 -> {
                if(receipts.stream().noneMatch(r->r.request()==991&&!r.accepted()&&r.reason().equals("NOT_IN_MATCH")))return true;
                Balm.getNetworking().sendToServer(new MatchChatNetwork.Intent(view.matchId(),view.connectionEpoch(),0,""));stage=4;
            }
            case 4 -> {
                if(receipts.stream().noneMatch(r->r.request()==0&&r.messages().size()==2))return true;
                if(++hold<25)return true;
                if(MatchChatClient.messages.size()!=2)throw new IllegalStateException("history duplicated messages");
                try{
                    var chat=client.gui.getChat();
                    for(var field:chat.getClass().getDeclaredFields())if(List.class.isAssignableFrom(field.getType())){
                        field.setAccessible(true);String contents=String.valueOf(field.get(chat));
                        if(contents.contains("CHAT_NATIVE_")||contents.contains("CROSS_MATCH_LEAK"))throw new IllegalStateException("match text leaked to vanilla chat");
                    }
                }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
                Screenshot.grab(client.gameDirectory,"sgs-match-chat-"+role+".png",client.getMainRenderTarget(),m->{});
                done=true;ProbeMod.LOG.info("SGS_MATCH_CHAT_MULTIPLAYER_PASS role={} messages=2 replay=true history=true cross_match_rejected=true vanilla_chat_clean=true",role);
            }
            default -> throw new IllegalStateException("invalid stage");
        }
        return true;
    }
}
