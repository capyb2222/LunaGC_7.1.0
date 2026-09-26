package emu.grasscutter.command;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.event.game.ReceiveCommandFeedbackEvent;
import java.util.*;

public interface CommandHandler {

    static void sendMessage(Player player, String message) {
        ReceiveCommandFeedbackEvent event = new ReceiveCommandFeedbackEvent(player, message);
        event.call();
        if (event.isCanceled()) {
            return;
        }

        if (player == null) {
            Grasscutter.getLogger().info(event.getMessage());
        } else {
            player.dropMessage(event.getMessage().replace("\n\t", "\n\n"));
        }
    }

    static void sendTranslatedMessage(Player player, String messageKey, Object... args) {
        sendMessage(player, translate(player, messageKey, args));
    }

    default String getUsageString(Player player, String... args) {
        Command annotation = this.getClass().getAnnotation(Command.class);
        String usage_prefix = translate(player, "commands.execution.usage_prefix");
        String command = annotation.label();
        for (String alias : annotation.aliases()) {
            if (alias.length() < command.length()) command = alias;
        }
        if (player != null) {
            command = "/" + command;
        }
        String target =
                switch (annotation.targetRequirement()) {
                    case NONE -> "";
                    case OFFLINE -> "@<UID> ";
                    case ONLINE -> (player == null)
                            ? "@<UID> "
                            : "[@<UID>] ";
                    case PLAYER -> (player == null) ? "@<UID> " : "[@<UID>] ";
                };
        String[] usages = annotation.usage();
        StringJoiner joiner = new StringJoiner("\n\t");
        for (String usage : usages) joiner.add(usage_prefix + command + " " + target + usage);
        return joiner.toString();
    }

    default void sendUsageMessage(Player player, String... args) {
        sendMessage(player, getUsageString(player, args));
    }

    default String getLabel() {
        return this.getClass().getAnnotation(Command.class).label();
    }

    default String getDescriptionKey() {
        Command annotation = this.getClass().getAnnotation(Command.class);
        return "commands.%s.description".formatted(annotation.label());
    }

    default String getDescriptionString(Player player) {
        return translate(player, getDescriptionKey());
    }

    default void execute(Player sender, Player targetPlayer, List<String> args) {}
}
