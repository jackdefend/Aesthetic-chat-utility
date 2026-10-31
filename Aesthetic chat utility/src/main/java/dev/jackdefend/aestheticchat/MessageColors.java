package dev.jackdefend.aestheticchat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Colors death and advancement announcements. */
public final class MessageColors {
    private MessageColors() {}

    public static Component apply(Component message) {
        AcuConfig cfg = AestheticChatUtility.config();
        if (cfg == null || message == null) return message;
        if (!(message.getContents() instanceof TranslatableContents tc)) return message;

        String key = tc.getKey();
        Integer rgb = null;
        boolean advancement = false;
        if (cfg.messages.colorDeaths && key.startsWith("death.")) {
            rgb = cfg.deathRgb();
        } else if (cfg.messages.colorAdvancements && key.startsWith("chat.type.advancement.")) {
            rgb = cfg.advancementRgb();
            advancement = true;
        }
        if (rgb == null) return message;

        // only the message's own text gets the color: player names, nicknames and the [advancement name]
        // carry their own colors
        TextColor color = TextColor.fromRgb(rgb);

        Integer titleRgb = cfg.advancementTitleRgb();
        if (advancement && titleRgb != null) {
            Object[] args = tc.getArgs();
            if (args.length >= 2 && args[1] instanceof Component title) {
                // the second argument is the [advancement name]: recolor it, keeping its hover tooltip
                TextColor titleColor = TextColor.fromRgb(titleRgb);
                Object[] newArgs = args.clone();
                newArgs[1] = title.copy().withStyle(style -> style.withColor(titleColor));
                MutableComponent rebuilt = Component.translatable(key, newArgs).withStyle(message.getStyle());
                for (Component sibling : message.getSiblings()) rebuilt.append(sibling);
                return rebuilt.withStyle(style -> style.withColor(color));
            }
        }
        return message.copy().withStyle(style -> style.withColor(color));
    }
}
