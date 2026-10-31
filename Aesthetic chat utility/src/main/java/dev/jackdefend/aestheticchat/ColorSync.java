package dev.jackdefend.aestheticchat;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;
import net.minecraft.world.waypoints.Waypoint;

/** Pushes a player's color to the places the server controls: team (nametag), locator dot, tab list. */
public final class ColorSync {
    private ColorSync() {}

    public static final String TEAM_PREFIX = "acu_";

    public static String teamName(TeamColor c) {
        return TEAM_PREFIX + c.getSerializedName();
    }

    /** Creates the 16 teams if they don't exist yet (they are saved with the world). */
    public static void ensureTeams(MinecraftServer server) {
        AcuConfig cfg = AestheticChatUtility.config();
        if (cfg == null || cfg.show.nametag == AcuConfig.Show.OFF) return;
        Scoreboard sb = server.getScoreboard();
        for (TeamColor c : TeamColor.VALUES) {
            PlayerTeam team = sb.getPlayerTeam(teamName(c));
            if (team == null) team = sb.addPlayerTeam(teamName(c));
            if (team.getColor().isEmpty() || team.getColor().get() != c) {
                team.setColor(Optional.of(c));
            }
            Component display = Component.literal("Aesthetic Chat Utility: " + c.getSerializedName());
            if (!display.getString().equals(team.getDisplayName().getString())) {
                team.setDisplayName(display);
            }
        }
    }

    public static void applyAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) apply(p);
    }

    public static void apply(ServerPlayer player) {
        applyTeam(player);
        applyLocator(player);
        refreshTabList(player);
    }

    private static boolean inOurTeam(ServerPlayer player) {
        PlayerTeam t = player.level().getServer().getScoreboard().getPlayersTeam(player.getScoreboardName());
        return t != null && t.getName().startsWith(TEAM_PREFIX);
    }

    // ---- nametag: always the nearest of the 16 base colors, via our teams ----
    private static void applyTeam(ServerPlayer player) {
        AcuConfig cfg = AestheticChatUtility.config();
        MinecraftServer server = player.level().getServer();
        Scoreboard sb = server.getScoreboard();
        String who = player.getScoreboardName();
        PlayerTeam current = sb.getPlayersTeam(who);
        boolean ours = current != null && current.getName().startsWith(TEAM_PREFIX);

        if (cfg.show.nametag == AcuConfig.Show.OFF) {
            if (ours) sb.removePlayerFromTeam(who);
            return;
        }
        // a real team the player is already on wins, unless the config says otherwise
        if (current != null && !ours && !cfg.overrides.realTeams) return;

        Integer exact = NameStyler.effectiveColor(player);
        if (exact == null) {
            if (ours) sb.removePlayerFromTeam(who);
            return;
        }
        TeamColor target = ColorMath.nearestBase(exact);
        PlayerTeam team = sb.getPlayerTeam(teamName(target));
        if (team == null) {
            ensureTeams(server);
            team = sb.getPlayerTeam(teamName(target));
            if (team == null) return;
        }
        if (current != team) sb.addPlayerToTeam(who, team);
    }

    // ---- locator bar dot ----
    private static Integer wantedLocator(ServerPlayer player, AcuConfig cfg) {
        return switch (cfg.show.locatorBar) {
            // OFF: keep the dot at its vanilla color, even though our team would otherwise recolor it
            case OFF -> inOurTeam(player) ? Integer.valueOf(LocatorColors.uuidColor(player)) : null;
            case EXACT, NEAREST -> NameStyler.place(player, cfg.show.locatorBar);
        };
    }

    public static void applyLocator(ServerPlayer player) {
        AcuConfig cfg = AestheticChatUtility.config();
        PlayerStore store = AestheticChatUtility.store();
        Waypoint.Icon icon = player.waypointIcon();
        Integer ours = store.getLocatorSet(player.getUUID());

        boolean hasColor = icon.color.isPresent();
        boolean currentIsOurs = hasColor && ours != null && (icon.color.get() & 0xFFFFFF) == (ours & 0xFFFFFF);
        boolean adminColor = hasColor && !currentIsOurs; // a color set with /waypoint modify

        Integer want = wantedLocator(player, cfg);
        // leave an admin's /waypoint color alone, unless the config says to override it and we have a color to set
        if (adminColor && (!cfg.overrides.waypointColor || want == null)) return;

        Optional<Integer> newColor = want == null ? Optional.empty() : Optional.of(want & 0xFFFFFF);

        store.setLocatorSet(player.getUUID(), newColor.orElse(null));
        if (icon.color.equals(newColor)) return;

        icon.color = newColor;
        // existing connections hold a copy of the old icon, so re-track to push the new color
        if (player.isTransmittingWaypoint()) {
            ServerWaypointManager wm = player.level().getWaypointManager();
            wm.untrackWaypoint(player);
            wm.trackWaypoint(player);
        }
    }

    /** Called every couple of seconds: puts the mod's locator color back if someone changed it with /waypoint. */
    public static void enforceLocator(MinecraftServer server) {
        AcuConfig cfg = AestheticChatUtility.config();
        if (cfg == null || !cfg.overrides.waypointColor) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) applyLocator(p);
    }

    // ---- tab list ----
    private static void refreshTabList(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), List.of(player)));
    }
}
