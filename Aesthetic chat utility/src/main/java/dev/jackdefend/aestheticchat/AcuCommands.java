package dev.jackdefend.aestheticchat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.TeamColor;

public final class AcuCommands {
    private AcuCommands() {}

    private static final Pattern HEX = Pattern.compile("#?([0-9a-fA-F]{6})");

    /** The two separate colors a player has. */
    private enum Kind {
        NAME("namecolor", "name color"),
        NICK("nickcolor", "nickname color");

        final String command;
        final String label;

        Kind(String command, String label) {
            this.command = command;
            this.label = label;
        }
    }

    private static AcuConfig cfg() { return AestheticChatUtility.config(); }

    private static AcuConfig.Who who(Kind k) {
        return k == Kind.NAME ? cfg().color.who : cfg().nickname.colorWho;
    }

    private static AcuConfig.Accepts accepts(Kind k) {
        return k == Kind.NAME ? cfg().color.accepts : cfg().nickname.colorAccepts;
    }

    private static List<String> baseNames() {
        List<String> names = new ArrayList<>();
        for (TeamColor c : TeamColor.VALUES) names.add(c.getSerializedName());
        return names;
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        registerColorCommand(d, Kind.NAME);
        registerColorCommand(d, Kind.NICK);

        // /nickname <nickname|reset> [player]
        d.register(Commands.literal("nickname")
                .requires(AcuCommands::nickVisible)
                .executes(AcuCommands::nickHelp)
                .then(Commands.argument("nick", StringArgumentType.string())
                        .executes(ctx -> setNick(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> setNick(ctx, EntityArgument.getPlayer(ctx, "player"))))));

        d.register(Commands.literal("aestheticchat")
                .requires(AcuCommands::isAdmin)
                .then(Commands.literal("reload").executes(ctx -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    AestheticChatUtility.reloadConfig();
                    ColorSync.ensureTeams(server);
                    ColorSync.applyAll(server);
                    // command visibility depends on the config, so resend the command list
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) server.getCommands().sendCommands(p);
                    ctx.getSource().sendSuccess(() -> Component.literal("Aesthetic Chat Utility config reloaded."), true);
                    return 1;
                })));
    }

    /** /namecolor and /nickcolor: <#hex|color name|reset> [player]. Greedy because '#' is not valid in a plain Brigadier word. */
    private static void registerColorCommand(CommandDispatcher<CommandSourceStack> d, Kind kind) {
        d.register(Commands.literal(kind.command)
                .requires(src -> colorVisible(src, kind))
                .executes(ctx -> colorHelp(ctx, kind))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .suggests((ctx, b) -> suggestColorArgs(ctx, b, kind))
                        .executes(ctx -> setColor(ctx, kind))));
    }

    // ---------- permissions / visibility ----------

    private static boolean isAdmin(CommandSourceStack src) {
        return src.permissions().hasPermission(cfg().adminPermission());
    }

    private static boolean colorVisible(CommandSourceStack src, Kind kind) {
        if (kind == Kind.NAME && cfg().color.forceDefault) return false;
        return who(kind) == AcuConfig.Who.PLAYERS || isAdmin(src);
    }

    private static boolean nickVisible(CommandSourceStack src) {
        return cfg().nickname.who == AcuConfig.Who.PLAYERS || isAdmin(src);
    }

    private static boolean isOther(CommandSourceStack src, ServerPlayer target) {
        ServerPlayer self = src.getPlayer();
        return self == null || !self.getUUID().equals(target.getUUID());
    }

    /** Sends the failure message itself and returns false when not allowed. */
    private static boolean allowed(CommandSourceStack src, ServerPlayer target, AcuConfig.Who selfAccess, String what) {
        boolean admin = isAdmin(src);
        boolean other = isOther(src, target);
        if (other && !admin) {
            src.sendFailure(Component.literal("Only admins can change other players' " + what + "."));
            return false;
        }
        if (!other && selfAccess == AcuConfig.Who.ADMINS && !admin) {
            src.sendFailure(Component.literal("Only admins can change " + what + " on this server."));
            return false;
        }
        return true;
    }

    private static int fail(CommandSourceStack src, String msg) {
        src.sendFailure(Component.literal(msg));
        return 0;
    }

    // ---------- /namecolor and /nickcolor ----------

    private static String colorUsage(CommandSourceStack src, Kind kind) {
        String what = switch (accepts(kind)) {
            case BASE -> "<color name|reset>";
            case HEX -> "<#hex|reset>";
            case BOTH -> "<#hex|color name|reset>";
        };
        return "Usage: /" + kind.command + " " + what + (isAdmin(src) ? " [player]" : "");
    }

    private static int colorHelp(CommandContext<CommandSourceStack> ctx, Kind kind) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer self = src.getPlayer();
        if (self != null) {
            Integer c = kind == Kind.NAME ? NameStyler.effectiveColor(self) : NameStyler.effectiveNickColor(self);
            if (c != null) {
                src.sendSuccess(() -> Component.literal("Your " + kind.label + " is ")
                        .append(Component.literal(hex(c)).withStyle(s -> s.withColor(TextColor.fromRgb(c)))), false);
            } else {
                src.sendSuccess(() -> Component.literal("Your " + kind.label + " is plain (no color)."), false);
            }
        }
        src.sendSuccess(() -> Component.literal(colorUsage(src, kind)), false);
        return 1;
    }

    private static void saveColor(Kind kind, PlayerStore store, ServerPlayer target, Integer rgb) {
        if (kind == Kind.NAME) store.setColor(target.getUUID(), rgb);
        else store.setNickColor(target.getUUID(), rgb);
    }

    private static int setColor(CommandContext<CommandSourceStack> ctx, Kind kind) {
        CommandSourceStack src = ctx.getSource();
        AcuConfig cfg = cfg();
        PlayerStore store = AestheticChatUtility.store();

        if (kind == Kind.NAME && cfg.color.forceDefault) {
            return fail(src, "Name colors are fixed to the default on this server.");
        }

        String[] parts = StringArgumentType.getString(ctx, "args").trim().split("\\s+");
        if (parts.length > 2) return fail(src, colorUsage(src, kind));

        ServerPlayer target;
        if (parts.length == 2) {
            target = src.getServer().getPlayerList().getPlayerByName(parts[1]);
            if (target == null) return fail(src, "Player '" + parts[1] + "' is not online.");
        } else {
            target = src.getPlayer();
            if (target == null) return fail(src, "Console must specify a player: " + kind.command + " <color> <player>");
        }
        if (!allowed(src, target, who(kind), kind.label)) return 0;

        String input = parts[0];
        boolean other = isOther(src, target);
        String targetName = target.getName().getString();

        if (input.equalsIgnoreCase("reset")) {
            saveColor(kind, store, target, null);
            src.sendSuccess(() -> Component.literal("Reset " + kind.label + " for " + targetName + " to the default."), false);
            if (other) target.sendSystemMessage(Component.literal("An admin reset your " + kind.label + " to the default."));
            ColorSync.apply(target);
            return 1;
        }

        boolean isHex = HEX.matcher(input).matches();
        TeamColor base = isHex ? null : AcuConfig.baseByName(input);
        Integer rgb;
        if (isHex) {
            if (accepts(kind) == AcuConfig.Accepts.BASE) {
                return fail(src, "Hex colors are disabled for this. Use one of: " + String.join(", ", baseNames()));
            }
            rgb = Integer.parseInt(HEX.matcher(input).replaceFirst("$1"), 16);
        } else if (base != null) {
            if (accepts(kind) == AcuConfig.Accepts.HEX) {
                return fail(src, "Color names are disabled for this. Use a hex code like #129f30.");
            }
            rgb = base.rgb();
        } else {
            return fail(src, "'" + input + "' is not a valid color. " + colorUsage(src, kind));
        }

        saveColor(kind, store, target, rgb);
        Component shown = Component.literal(hex(rgb)).withStyle(s -> s.withColor(TextColor.fromRgb(rgb)));
        src.sendSuccess(() -> Component.literal(capitalize(kind.label) + " for " + targetName + " set to ").append(shown), false);
        if (other) target.sendSystemMessage(Component.literal("An admin set your " + kind.label + " to ").append(shown));
        ColorSync.apply(target);
        return 1;
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String hex(int rgb) {
        return String.format("#%06x", rgb & 0xFFFFFF);
    }

    private static CompletableFuture<Suggestions> suggestColorArgs(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b, Kind kind) {
        String rem = b.getRemaining();
        int sp = rem.lastIndexOf(' ');
        if (sp < 0) {
            List<String> words = new ArrayList<>();
            words.add("reset");
            if (accepts(kind) != AcuConfig.Accepts.HEX) words.addAll(baseNames());
            return SharedSuggestionProvider.suggest(words, b);
        }
        if (isAdmin(ctx.getSource())) {
            SuggestionsBuilder nb = b.createOffset(b.getStart() + sp + 1);
            return SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), nb);
        }
        return b.buildFuture();
    }

    // ---------- /nickname ----------

    private static int nickHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer self = src.getPlayer();
        if (self != null) {
            String nick = AestheticChatUtility.store().getNickname(self.getUUID());
            src.sendSuccess(() -> Component.literal(nick == null ? "You have no nickname." : "Your nickname is " + nick + "."), false);
        }
        src.sendSuccess(() -> Component.literal("Usage: /nickname <nickname|reset>" + (isAdmin(src) ? " [player]" : "")), false);
        return 1;
    }

    private static int setNick(CommandContext<CommandSourceStack> ctx, ServerPlayer explicitTarget) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        AcuConfig cfg = cfg();
        PlayerStore store = AestheticChatUtility.store();

        ServerPlayer target = explicitTarget != null ? explicitTarget : src.getPlayer();
        if (target == null) return fail(src, "Console must specify a player: nickname <nickname> <player>");
        if (!allowed(src, target, cfg.nickname.who, "nicknames")) return 0;

        String nick = StringArgumentType.getString(ctx, "nick").trim();
        boolean other = isOther(src, target);
        String targetName = target.getName().getString();

        if (nick.equalsIgnoreCase("reset") || nick.equalsIgnoreCase("clear")) {
            store.setNickname(target.getUUID(), null);
            src.sendSuccess(() -> Component.literal("Removed nickname for " + targetName + "."), false);
            if (other) target.sendSystemMessage(Component.literal("An admin removed your nickname."));
            ColorSync.apply(target);
            return 1;
        }

        if (nick.isEmpty() || nick.length() > cfg.nickname.maxLength) {
            return fail(src, "Nicknames must be 1-" + cfg.nickname.maxLength + " characters.");
        }
        if (!cfg.pattern().matcher(nick).matches()) {
            return fail(src, "That nickname contains characters that are not allowed.");
        }
        if (cfg.nickname.blockImpersonation) {
            for (ServerPlayer p : src.getServer().getPlayerList().getPlayers()) {
                if (!p.getUUID().equals(target.getUUID()) && p.getName().getString().equalsIgnoreCase(nick)) {
                    return fail(src, "That nickname matches another player's username.");
                }
            }
        }

        store.setNickname(target.getUUID(), nick);
        src.sendSuccess(() -> Component.literal("Nickname for " + targetName + " set to " + nick + "."), false);
        if (other) target.sendSystemMessage(Component.literal("An admin set your nickname to " + nick + "."));
        ColorSync.apply(target);
        return 1;
    }
}
