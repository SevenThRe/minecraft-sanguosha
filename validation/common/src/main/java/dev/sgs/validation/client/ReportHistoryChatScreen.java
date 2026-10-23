package dev.sgs.validation.client;

import net.minecraft.client.gui.screens.ChatScreen;

/** Legacy /sgs reports command output only. Match conversation uses MatchChatPanel. */
final class ReportHistoryChatScreen extends ChatScreen {
    ReportHistoryChatScreen(){super("");}
    @Override public boolean keyPressed(int key,int scanCode,int modifiers){
        boolean handled=super.keyPressed(key,scanCode,modifiers);
        if(minecraft.screen==null)MatchClient.resume();
        return handled;
    }
    @Override public void onClose(){if(!MatchClient.resume())super.onClose();}
}
