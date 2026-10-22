package dev.sgs.validation.client;

import net.minecraft.client.gui.screens.ChatScreen;

/** Native server chat with a return to the latest match snapshot. */
final class MatchChatScreen extends ChatScreen {
    MatchChatScreen(){super("");}
    @Override public boolean keyPressed(int key,int scanCode,int modifiers){
        boolean handled=super.keyPressed(key,scanCode,modifiers);
        if(minecraft.screen==null)MatchClient.resume();
        return handled;
    }
    @Override public void onClose(){if(!MatchClient.resume())super.onClose();}
}
