package dev.jackdefend.aestheticchat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;

public final class NameStyler {
    private NameStyler() {}

    // ================= name color =================

    /** The default name color (color.default): null means VANILLA = no coloring. */
    public static Integer defaultColor(ServerPlayer player) {
        AcuConfig cfg = AestheticChatUtility.config();
        return switch (cfg.defaultKind()) {
            case VANILLA -> null;
            case LOCATOR -> Integer.valueOf(LocatorColors.uuidColor(player));
            case BASE -> Integer.valueOf(cfg.defaultBase().rgb());
        };
    }

    /**
     * The player's name color, used for every place the NAME is colored: their chosen color, or the default
     * (always the default when forceDefault is on). Exact hex; null = no coloring.
     */
    public static Integer effectiveColor(ServerPlayer player) {
        AcuConfig cfg = AestheticChatUtility.config();
        PlayerStore store = AestheticChatUtility.store();
        if (cfg == null || store == null) return null;
        if (!cfg.color.forceDefault) {
            Integer saved = store.getColor(player.getUUID());
            if (saved != null) return saved & 0xFFFFFF;
        }
        Integer d = defaultColor(player);
        return d == null ? null : (d & 0xFFFFFF);
    }

    /** The name color for one place: off = null, exact, or snapped to the nearest base color. */
    public static Integer place(ServerPlayer player, AcuConfig.Show mode) {
        return snap(effectiveColor(player), mode);
    }

    // ================= nickname color (separate) =================

    /** The nickname's own color: their chosen one, else nickname.defaultColor. null = plain. */
    public static Integer effectiveNickColor(ServerPlayer player) {
        AcuConfig cfg = AestheticChatUtility.config();
        PlayerStore store = AestheticChatUtility.store();
        if (cfg == null || store == null) return null;
        Integer saved = store.getNickColor(player.getUUID());
        if (saved != null) return saved & 0xFFFFFF;
        return switch (cfg.nickDefaultKind()) {
            case VANILLA -> null;
            case NAME -> effectiveColor(player);
            case BASE -> Integer.valueOf(cfg.nickDefaultBase().rgb());
        };
    }

    public static Integer placeNick(ServerPlayer player, AcuConfig.Show mode) {
        return snap(effectiveNickColor(player), mode);
    }

    private static Integer snap(Integer exact, AcuConfig.Show mode) {
        if (mode == AcuConfig.Show.OFF || exact == null) return null;
        return mode == AcuConfig.Show.NEAREST ? Integer.valueOf(ColorMath.nearestBase(exact).rgb()) : exact;
    }

    private static TextColor textColor(Integer rgb) {
        return rgb == null ? null : TextColor.fromRgb(rgb);
    }

    /**
     * Death and advancement messages are colored as a whole, so a name or nickname with no color of its own
     * would turn red / blue too. When those are on, uncolored parts are pinned to the vanilla chat white.
     */
    private static TextColor plainColor(AcuConfig cfg) {
        boolean pin = cfg.messages.colorDeaths || cfg.messages.colorAdvancements;
        return pin ? TextColor.fromRgb(0xFFFFFF) : null;
    }

    // ================= building the names =================

    /** The chat / display name. */
    public static Component style(ServerPlayer player, Component base) {
        AcuConfig cfg = AestheticChatUtility.config();
        PlayerStore store = AestheticChatUtility.store();
        if (cfg == null || store == null) return base;

        TextColor plain = plainColor(cfg);
        TextColor own = textColor(place(player, cfg.show.chat));
        TextColor nameColor = own != null ? own : plain;
        MutableComponent name = base.copy();
        if (nameColor != null) name.withStyle(style -> style.withColor(nameColor));

        String nick = store.getNickname(player.getUUID());
        if (nick == null) return name;
        TextColor ownNick = textColor(placeNick(player, cfg.show.chat));
        TextColor nickColor = ownNick != null ? ownNick : plain;
        return build(cfg.nickname.format, name, nick, nickColor, plain, base.getStyle());
    }

    /** The tab list name, or null to leave the vanilla tab list entry alone. */
    public static Component tabName(ServerPlayer player) {
        AcuConfig cfg = AestheticChatUtility.config();
        PlayerStore store = AestheticChatUtility.store();
        if (cfg == null || store == null) return null;

        String nick = store.getNickname(player.getUUID());
        boolean showNick = nick != null && cfg.nickname.inTabList;
        TextColor nameColor = textColor(place(player, cfg.show.tabList));
        if (nameColor == null && !showNick) return null;

        MutableComponent name = player.getName().copy();
        if (nameColor != null) name.withStyle(style -> style.withColor(nameColor));
        if (!showNick) return name;
        TextColor nickColor = textColor(placeNick(player, cfg.show.tabList));
        return build(cfg.nickname.format.strip(), name, nick, nickColor, null, Style.EMPTY);
    }

    /** Fills the nickname.format template: {name} = the colored username, {nick} = the nickname in its own color. */
    private static Component build(String fmt, MutableComponent name, String nick, TextColor nickColor,
                                   TextColor plainColor, Style baseStyle) {
        // plain text keeps the hover / click / insertion of the name
        Style shared = Style.EMPTY
                .withClickEvent(baseStyle.getClickEvent())
                .withHoverEvent(baseStyle.getHoverEvent())
                .withInsertion(baseStyle.getInsertion());
        Style sharedPlain = plainColor == null ? shared : shared.withColor(plainColor);
        MutableComponent out = Component.empty();
        int i = 0;
        while (i < fmt.length()) {
            int n = fmt.indexOf("{name}", i);
            int k = fmt.indexOf("{nick}", i);
            int next = n < 0 ? k : (k < 0 ? n : Math.min(n, k));
            if (next < 0) {
                out.append(Component.literal(fmt.substring(i)).withStyle(sharedPlain));
                break;
            }
            if (next > i) out.append(Component.literal(fmt.substring(i, next)).withStyle(sharedPlain));
            if (next == n) {
                out.append(name);
            } else {
                MutableComponent nickPart = Component.literal(nick).withStyle(shared);
                if (nickColor != null) nickPart.withStyle(style -> style.withColor(nickColor));
                out.append(nickPart);
            }
            i = next + 6; // "{name}" and "{nick}" are both 6 characters
        }
        return out;
    }
}
