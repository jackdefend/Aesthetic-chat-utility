package dev.jackdefend.aestheticchat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ARGB;

public final class LocatorColors {
    private LocatorColors() {}

    /** The vanilla automatic locator bar color from the player's UUID (same formula the client uses). */
    public static int uuidColor(ServerPlayer player) {
        int argb = ARGB.setBrightness(ARGB.color(255, player.getUUID().hashCode()), 0.9F);
        return argb & 0xFFFFFF;
    }
}
