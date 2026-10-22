package dev.sgs.validation.client.mixin;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A server-issued kick is distinct from a transport failure, even with a custom literal reason. */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ServerDisconnectMixin {
    @Inject(method="handleDisconnect",at=@At("HEAD"))
    private void sgs$serverDisconnect(ClientboundDisconnectPacket packet,CallbackInfo ci){
        net.minecraft.client.Minecraft.getInstance().execute(()->dev.sgs.validation.client.MatchReconnect.serverDisconnect(packet.reason()));
    }
}
