package dev.jackdefend.aestheticchat.mixin;

import dev.jackdefend.aestheticchat.NameStyler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chat builds the sender name with ChatType.bind(key, entity), which calls entity.getDisplayName().
 * In 26.x Player overrides getDisplayName() without calling Entity's version, so the hook
 * has to live here, not on Entity.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void aestheticchat$style(CallbackInfoReturnable<Component> cir) {
        if ((Object) this instanceof ServerPlayer player) {
            cir.setReturnValue(NameStyler.style(player, cir.getReturnValue()));
        }
    }
}
