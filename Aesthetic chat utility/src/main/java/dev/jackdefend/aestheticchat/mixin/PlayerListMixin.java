package dev.jackdefend.aestheticchat.mixin;

import dev.jackdefend.aestheticchat.MessageColors;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Deaths and advancements are announced with PlayerList#broadcastSystemMessage(Component, boolean).
 * require = 0: if that method ever changes, the game still starts and only these colors stop working.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @ModifyVariable(
            method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"),
            argsOnly = true,
            require = 0)
    private Component aestheticchat$colorAnnouncement(Component message) {
        return MessageColors.apply(message);
    }
}
