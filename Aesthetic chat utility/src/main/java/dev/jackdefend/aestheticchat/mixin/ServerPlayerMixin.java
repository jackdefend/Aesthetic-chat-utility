package dev.jackdefend.aestheticchat.mixin;

import dev.jackdefend.aestheticchat.NameStyler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The tab list asks ServerPlayer#getTabListDisplayName (vanilla returns null = plain name). */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
    private void aestheticchat$tabName(CallbackInfoReturnable<Component> cir) {
        Component name = NameStyler.tabName((ServerPlayer) (Object) this);
        if (name != null) cir.setReturnValue(name);
    }
}
