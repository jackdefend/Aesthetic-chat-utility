package dev.jackdefend.aestheticchat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.scores.TeamColor;

/** config/aesthetic-chat-utility.json5: JSON with // comments. The mod rewrites it from a template so comments stay. */
public final class AcuConfig {
    public enum Who { ADMINS, PLAYERS }
    public enum Accepts { BASE, HEX, BOTH }
    public enum Show { OFF, EXACT, NEAREST }
    public enum DefaultKind { VANILLA, LOCATOR, BASE }
    public enum NickDefaultKind { VANILLA, NAME, BASE }

    public static final class ColorSection {
        public Who who = Who.ADMINS;
        public Accepts accepts = Accepts.BOTH;
        @SerializedName("default")
        public String defaultColor = "LOCATOR";
        public boolean forceDefault = false;
    }

    public static final class ShowSection {
        public Show chat = Show.EXACT;
        public Show tabList = Show.EXACT;
        public Show locatorBar = Show.EXACT;
        public Show nametag = Show.NEAREST;
    }

    public static final class NicknameSection {
        public Who who = Who.PLAYERS;
        public String format = "{name} ({nick}) ";
        // nickname color: separate from the name color
        public Who colorWho = Who.PLAYERS;
        public Accepts colorAccepts = Accepts.BOTH;
        public String defaultColor = "VANILLA";
        public boolean inTabList = true;
        public int maxLength = 16;
        public String allowedPattern = "[A-Za-z0-9_ .-]+";
        public boolean blockImpersonation = true;
    }

    public static final class OverridesSection {
        public boolean realTeams = true;
        public boolean waypointColor = true;
    }

    public static final class MessagesSection {
        public boolean colorDeaths = true;
        public String deathColor = "red";
        public boolean colorAdvancements = true;
        public String advancementColor = "blue";
        public String advancementTitleColor = "VANILLA";
    }

    public ColorSection color = new ColorSection();
    public ShowSection show = new ShowSection();
    public NicknameSection nickname = new NicknameSection();
    public OverridesSection overrides = new OverridesSection();
    public MessagesSection messages = new MessagesSection();

    private transient DefaultKind defaultKind = DefaultKind.LOCATOR;
    private transient TeamColor defaultBase = null;
    private transient NickDefaultKind nickDefaultKind = NickDefaultKind.VANILLA;
    private transient TeamColor nickDefaultBase = null;
    private transient Pattern compiledPattern;
    private transient int deathRgb = 0xFF5555;
    private transient int advancementRgb = 0x5555FF;
    private transient Integer advancementTitleRgb = null; // null = keep the game's own color

    public DefaultKind defaultKind() { return defaultKind; }
    public TeamColor defaultBase() { return defaultBase; }
    public NickDefaultKind nickDefaultKind() { return nickDefaultKind; }
    public TeamColor nickDefaultBase() { return nickDefaultBase; }
    public Pattern pattern() { return compiledPattern; }
    public int deathRgb() { return deathRgb; }
    public int advancementRgb() { return advancementRgb; }
    public Integer advancementTitleRgb() { return advancementTitleRgb; }

    /** "Admin" = anyone with op (any op permission level, 1 and up). */
    public Permission adminPermission() {
        return Permissions.COMMANDS_MODERATOR;
    }

    public static TeamColor baseByName(String name) {
        if (name == null) return null;
        for (TeamColor c : TeamColor.VALUES) {
            if (c.getSerializedName().equalsIgnoreCase(name.trim())) return c;
        }
        return null;
    }

    private static final Pattern HEX = Pattern.compile("#?([0-9a-fA-F]{6})");

    /** A base color name or a hex code -> 0xRRGGBB, or -1 if it is neither. */
    public static int parseColor(String s) {
        if (s == null) return -1;
        String t = s.trim();
        java.util.regex.Matcher m = HEX.matcher(t);
        if (m.matches()) return Integer.parseInt(m.group(1), 16);
        TeamColor base = baseByName(t);
        return base == null ? -1 : base.rgb();
    }

    private void sanitize() {
        if (color == null) color = new ColorSection();
        if (show == null) show = new ShowSection();
        if (nickname == null) nickname = new NicknameSection();
        if (overrides == null) overrides = new OverridesSection();

        if (color.who == null) color.who = Who.ADMINS;
        if (color.accepts == null) color.accepts = Accepts.BOTH;
        String d = color.defaultColor == null ? "LOCATOR" : color.defaultColor.trim();
        TeamColor base = baseByName(d);
        if (d.equalsIgnoreCase("VANILLA")) {
            defaultKind = DefaultKind.VANILLA;
            defaultBase = null;
            color.defaultColor = "VANILLA";
        } else if (base != null) {
            defaultKind = DefaultKind.BASE;
            defaultBase = base;
            color.defaultColor = base.getSerializedName();
        } else {
            if (!d.equalsIgnoreCase("LOCATOR")) {
                AestheticChatUtility.LOGGER.warn("Unknown color.default '{}', using LOCATOR", d);
            }
            defaultKind = DefaultKind.LOCATOR;
            defaultBase = null;
            color.defaultColor = "LOCATOR";
        }

        if (show.chat == null) show.chat = Show.EXACT;
        if (show.tabList == null) show.tabList = Show.EXACT;
        if (show.locatorBar == null) show.locatorBar = Show.EXACT;
        if (show.nametag == null || show.nametag == Show.EXACT) show.nametag = Show.NEAREST; // teams only have base colors

        if (nickname.who == null) nickname.who = Who.PLAYERS;
        if (nickname.format == null || !nickname.format.contains("{nick}")) nickname.format = "{name} ({nick}) ";
        if (messages == null) messages = new MessagesSection();
        int dr = parseColor(messages.deathColor);
        if (dr < 0) {
            AestheticChatUtility.LOGGER.warn("Invalid messages.deathColor '{}', using red", messages.deathColor);
            messages.deathColor = "red";
            dr = TeamColor.RED.rgb();
        }
        deathRgb = dr;
        int ar = parseColor(messages.advancementColor);
        if (ar < 0) {
            AestheticChatUtility.LOGGER.warn("Invalid messages.advancementColor '{}', using blue", messages.advancementColor);
            messages.advancementColor = "blue";
            ar = TeamColor.BLUE.rgb();
        }
        advancementRgb = ar;
        String tt = messages.advancementTitleColor == null ? "VANILLA" : messages.advancementTitleColor.trim();
        if (tt.equalsIgnoreCase("VANILLA")) {
            advancementTitleRgb = null;
            messages.advancementTitleColor = "VANILLA";
        } else {
            int tr = parseColor(tt);
            if (tr < 0) {
                AestheticChatUtility.LOGGER.warn("Invalid messages.advancementTitleColor '{}', using VANILLA", tt);
                messages.advancementTitleColor = "VANILLA";
                advancementTitleRgb = null;
            } else {
                advancementTitleRgb = tr;
            }
        }

        if (nickname.colorWho == null) nickname.colorWho = Who.PLAYERS;
        if (nickname.colorAccepts == null) nickname.colorAccepts = Accepts.BOTH;
        String nd = nickname.defaultColor == null ? "VANILLA" : nickname.defaultColor.trim();
        TeamColor nb = baseByName(nd);
        if (nd.equalsIgnoreCase("NAME")) {
            nickDefaultKind = NickDefaultKind.NAME;
            nickDefaultBase = null;
            nickname.defaultColor = "NAME";
        } else if (nb != null) {
            nickDefaultKind = NickDefaultKind.BASE;
            nickDefaultBase = nb;
            nickname.defaultColor = nb.getSerializedName();
        } else {
            if (!nd.equalsIgnoreCase("VANILLA")) {
                AestheticChatUtility.LOGGER.warn("Unknown nickname.defaultColor '{}', using VANILLA", nd);
            }
            nickDefaultKind = NickDefaultKind.VANILLA;
            nickDefaultBase = null;
            nickname.defaultColor = "VANILLA";
        }
        if (nickname.maxLength < 1) nickname.maxLength = 16;
        if (nickname.maxLength > 64) nickname.maxLength = 64;
        try {
            compiledPattern = Pattern.compile(nickname.allowedPattern == null ? "[A-Za-z0-9_ .-]+" : nickname.allowedPattern);
        } catch (Exception e) {
            AestheticChatUtility.LOGGER.warn("Invalid nickname.allowedPattern, using default");
            nickname.allowedPattern = "[A-Za-z0-9_ .-]+";
            compiledPattern = Pattern.compile(nickname.allowedPattern);
        }
    }

    private static final Gson GSON = new Gson();
    private static final Gson QUOTE = new GsonBuilder().disableHtmlEscaping().create();

    private static String q(String s) { return QUOTE.toJson(s); }

    private static final String TEMPLATE = """
{
  // ===== Name color =====
  "color": {
    // Who can use /namecolor. ADMINS hides it from non-ops. Ops can always change anyone's
    // color by adding a username at the end: /namecolor gold jackdefend      ADMINS | PLAYERS
    "who": @color.who@,
    // What /namecolor accepts. "reset" always works.      BASE | HEX | BOTH
    "accepts": @color.accepts@,
    // The color players use until they pick one, and what /namecolor reset returns to.
    // VANILLA = no coloring at all, LOCATOR = their normal locator bar color (from their account),
    // or a base color name such as "gold".
    "default": @color.default@,
    // true = everyone always uses the default. Chosen colors are kept but ignored,
    // and /namecolor is disabled for everyone.
    "forceDefault": @color.forceDefault@
  },

  // ===== Where the color shows =====      OFF | EXACT | NEAREST (nearest of the 16 base colors)
  "show": {
    "chat": @show.chat@,
    "tabList": @show.tabList@,
    // OFF keeps the dot at its normal vanilla color even when the player is on a team.
    "locatorBar": @show.locatorBar@,
    // The name above the head comes from teams, which only have the 16 base colors.
    // OFF | NEAREST (EXACT is treated as NEAREST)
    "nametag": @show.nametag@
  },

  // ===== Nicknames =====
  "nickname": {
    // Who can use /nickname. ADMINS hides it from non-ops. Ops can always change anyone's
    // by adding a username at the end.      ADMINS | PLAYERS
    "who": @nickname.who@,
    // {name} is the username, {nick} the nickname. A trailing space puts it before the closing >.
    // "{nick}" alone shows only the nickname.
    "format": @nickname.format@,

    // ---- Nickname color: a second color, separate from the name color (/nickcolor) ----
    // Who can use /nickcolor. ADMINS hides it from non-ops. Ops can always change anyone's
    // by adding a username at the end.      ADMINS | PLAYERS
    "colorWho": @nickname.colorWho@,
    // What /nickcolor accepts. "reset" always works.      BASE | HEX | BOTH
    "colorAccepts": @nickname.colorAccepts@,
    // The nickname color until a player picks one, and what /nickcolor reset returns to.
    // VANILLA = plain (no color), NAME = same as the player's name color, or a base color such as "gray".
    "defaultColor": @nickname.defaultColor@,

    // true = the tab list shows the nickname as well.
    "inTabList": @nickname.inTabList@,
    "maxLength": @nickname.maxLength@,
    // Letters, numbers, underscore, space, dot, dash.
    "allowedPattern": @nickname.allowedPattern@,
    // true = nobody can use another online player's username as a nickname.
    "blockImpersonation": @nickname.blockImpersonation@
  },

  // ===== Overrides =====
  "overrides": {
    // true = our team replaces a real team the player is already in. false = real teams are left alone.
    "realTeams": @overrides.realTeams@,
    // true = the mod replaces colors set with /waypoint modify (re-checked every couple of seconds).
    "waypointColor": @overrides.waypointColor@
  },

  // ===== Death and advancement message colors =====
  "messages": {
    // true = death messages are colored. Player names inside them keep their own color.
    "colorDeaths": @messages.colorDeaths@,
    // A base color name (red, gold, ...) or a hex code such as "#ff5555".
    "deathColor": @messages.deathColor@,
    // true = advancement messages are colored. The [advancement name] keeps its own color.
    "colorAdvancements": @messages.colorAdvancements@,
    // A base color name (blue, aqua, ...) or a hex code such as "#5555ff".
    "advancementColor": @messages.advancementColor@,
    // The color of the [advancement name] inside the message. Its hover tooltip is unchanged.
    // VANILLA = the game's own color (green, or purple for challenges), or a base color name / hex code.
    // Only used when colorAdvancements is true.
    "advancementTitleColor": @messages.advancementTitleColor@
  }
}
""";

    private String render() {
        return TEMPLATE
                .replace("@color.who@", q(color.who.name()))
                .replace("@color.accepts@", q(color.accepts.name()))
                .replace("@color.default@", q(color.defaultColor))
                .replace("@color.forceDefault@", String.valueOf(color.forceDefault))
                .replace("@show.chat@", q(show.chat.name()))
                .replace("@show.tabList@", q(show.tabList.name()))
                .replace("@show.locatorBar@", q(show.locatorBar.name()))
                .replace("@show.nametag@", q(show.nametag.name()))
                .replace("@nickname.who@", q(nickname.who.name()))
                .replace("@nickname.format@", q(nickname.format))
                .replace("@nickname.colorWho@", q(nickname.colorWho.name()))
                .replace("@nickname.colorAccepts@", q(nickname.colorAccepts.name()))
                .replace("@nickname.defaultColor@", q(nickname.defaultColor))
                .replace("@nickname.inTabList@", String.valueOf(nickname.inTabList))
                .replace("@nickname.maxLength@", String.valueOf(nickname.maxLength))
                .replace("@nickname.allowedPattern@", q(nickname.allowedPattern))
                .replace("@nickname.blockImpersonation@", String.valueOf(nickname.blockImpersonation))
                .replace("@overrides.realTeams@", String.valueOf(overrides.realTeams))
                .replace("@overrides.waypointColor@", String.valueOf(overrides.waypointColor))
                .replace("@messages.colorDeaths@", String.valueOf(messages.colorDeaths))
                .replace("@messages.deathColor@", q(messages.deathColor))
                .replace("@messages.colorAdvancements@", String.valueOf(messages.colorAdvancements))
                .replace("@messages.advancementColor@", q(messages.advancementColor))
                .replace("@messages.advancementTitleColor@", q(messages.advancementTitleColor));
    }

    public static AcuConfig load(Path file) {
        AcuConfig cfg = null;
        if (Files.exists(file)) {
            try {
                JsonReader reader = new JsonReader(new StringReader(Files.readString(file)));
                reader.setLenient(true); // allows // comments
                cfg = GSON.fromJson(reader, AcuConfig.class);
            } catch (Exception e) {
                AestheticChatUtility.LOGGER.error("Could not read config, using defaults", e);
            }
        }
        if (cfg == null) cfg = new AcuConfig();
        cfg.sanitize();
        try {
            Files.writeString(file, cfg.render()); // rewrites with comments, adds any new options
        } catch (IOException e) {
            AestheticChatUtility.LOGGER.error("Could not write config", e);
        }
        return cfg;
    }
}
