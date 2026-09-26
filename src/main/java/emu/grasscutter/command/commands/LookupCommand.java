package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.data.NameIndex;
import emu.grasscutter.game.player.Player;
import java.util.List;

@Command(
        label = "lookup",
        aliases = {"find", "search"},
        usage = {"<part of a name>"},
        targetRequirement = Command.TargetRequirement.NONE)
public final class LookupCommand implements CommandHandler {
    private static final int LIMIT = 15;

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args.isEmpty()) {
            sendUsageMessage(sender);
            return;
        }

        var query = String.join(" ", args);
        var found = NameIndex.search(query, LIMIT);

        if (found.isEmpty()) {
            CommandHandler.sendMessage(sender, "Nothing is called anything like \"" + query + "\".");
            return;
        }

        CommandHandler.sendMessage(
                sender,
                found.size() < LIMIT
                        ? found.size() + " match" + (found.size() == 1 ? "" : "es") + " for \"" + query + "\":"
                        : "First " + LIMIT + " matches for \"" + query + "\":");
        found.forEach(entry -> CommandHandler.sendMessage(sender, "  " + entry));
    }
}
