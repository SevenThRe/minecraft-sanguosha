package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Opt-in test driver uses ordinary block placement/use; no direct lobby-open request or bypass. */
final class NativeTableEntry {
  private static long nextAttempt;
    private static boolean requestedLobby;
  static boolean open(Minecraft client){
    if(client.player==null||client.level==null||client.gameMode==null)return false;
        if(requestedLobby && client.screen instanceof LobbyScreen lobby){
            if(lobby.smokeView()==null || lobby.smokePending())return false;
            ProbeMod.LOG.info("SGS_NATIVE_TABLE_ACK server_view=true role={}",System.getProperty("sgs.matchRole",System.getProperty("sgs.lobbyRole","single")));
            return true;
        }
        // A user cannot interact with the world through terrain-loading or other screens.
        // Sending a use packet alone is not acknowledgement that the lobby actually opened.
        if(client.screen!=null || client.getOverlay()!=null)return false;
    long now=System.nanoTime();if(now<nextAttempt)return false;
    var table=new BlockPos(1,90,0);
    if(!client.level.getBlockState(table).is(ProbeMod.table)){
      if(!client.player.getInventory().getItem(0).is(ProbeMod.table.asItem()))return false;
      client.player.getInventory().selected=0;
      client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(1.5,90,0.5),Direction.UP,table.below(),false));nextAttempt=now+2_000_000_000L;return false;
    }
    client.player.getInventory().selected=1;
    client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(table),Direction.SOUTH,table,false));nextAttempt=now+500_000_000L;
        requestedLobby=true;
        ProbeMod.LOG.info("SGS_NATIVE_TABLE_ENTRY real_block_interaction=true role={}",System.getProperty("sgs.matchRole",System.getProperty("sgs.lobbyRole","single")));return false;
  }
}
