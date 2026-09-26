package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.CutsceneData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketCutsceneBeginNotify;
import java.util.List;
import java.util.Locale;
import lombok.val;

@Command(
        label = "cutscene",
        aliases = {"c"},
        usage = {"<cutsceneId>", "list [<search>]"},
        permission = "player.cutscene",
        permissionTargeted = "player.cutscene.others")
public final class CutsceneCommand implements CommandHandler {

    private static final int MAX_RESULTS = 30;

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args.isEmpty()) {
            sendUsageMessage(sender);
            return;
        }

        if ("list".equalsIgnoreCase(args.get(0))) {
            this.list(sender, args.size() > 1 ? args.get(1) : "");
            return;
        }

        int cutsceneId;
        try {
            cutsceneId = Integer.parseInt(args.get(0));
        } catch (NumberFormatException ignored) {
            CommandHandler.sendMessage(sender, "'%s' is not a cutscene id. Try /cutscene list %s"
                    .formatted(args.get(0), args.get(0)));
            return;
        }

        val data = GameData.getCutsceneDataMap().get(cutsceneId);
        if (data != null) CommandHandler.sendMessage(sender, "Playing %d: %s".formatted(cutsceneId, data.getPath()));
        targetPlayer.sendPacket(new PacketCutsceneBeginNotify(cutsceneId));
    }

    private void list(Player sender, String search) {
        val needle = search.toLowerCase(Locale.ROOT);
        val matches = GameData.getCutsceneDataMap().values().stream()
                .filter(data -> data.getPath() != null)
                .filter(data -> needle.isEmpty() || data.getPath().toLowerCase(Locale.ROOT).contains(needle))
                .toList();

        if (matches.isEmpty()) {
            CommandHandler.sendMessage(sender, "No cutscene path contains '%s'.".formatted(search));
            return;
        }

        CommandHandler.sendMessage(sender, "%d cutscene(s)%s:"
                .formatted(matches.size(), needle.isEmpty() ? "" : " matching '%s'".formatted(search)));
        matches.stream()
                .limit(MAX_RESULTS)
                .forEach(data -> CommandHandler.sendMessage(
                        sender, "  %d - %s".formatted(data.getId(), data.getPath())));

        if (matches.size() > MAX_RESULTS) {
            CommandHandler.sendMessage(sender,
                    "  ...and %d more; narrow the search.".formatted(matches.size() - MAX_RESULTS));
        }
    }
}
