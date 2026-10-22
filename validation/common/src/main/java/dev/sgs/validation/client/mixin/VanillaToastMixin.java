package dev.sgs.validation.client.mixin;

import dev.sgs.validation.client.SgsPresentationPolicy;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep vanilla notifications off the SGS canvas without deleting them or changing user options. */
@Mixin(ToastComponent.class)
public abstract class VanillaToastMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void sgs$renderOutsideBoard(GuiGraphics graphics,CallbackInfo ci){
        if(SgsPresentationPolicy.ownsCanvas())ci.cancel();
    }
}
