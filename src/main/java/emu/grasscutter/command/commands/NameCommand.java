package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketSetPlayerNameRsp;
import emu.grasscutter.utils.RichTextUtils;
import java.util.List;

@Command(
        label = "name",
        usage = {
            "<text>",
            "gradient <text> <#startColor> <#endColor>",
            "uid",
            "reset"
        },
        aliases = {"nickname", "rename"},
        permission = "player.name",
        permissionTargeted = "player.name.others")
public final class NameCommand implements CommandHandler {
    private static final String DEFAULT_NICKNAME = "Traveler";

    private static final int MAX_GRADIENT_LENGTH = 32;

    private static final int MAX_STORED_LENGTH = 1024;

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args.isEmpty()) {
            sendUsageMessage(sender);
            return;
        }

        switch (args.get(0).toLowerCase()) {
            case "reset" -> apply(sender, targetPlayer, DEFAULT_NICKNAME);
            case "uid" -> apply(sender, targetPlayer, String.valueOf(targetPlayer.getUid()));
            case "gradient" -> gradient(sender, targetPlayer, args);
            default -> apply(sender, targetPlayer, String.join(" ", args));
        }
    }

    private void gradient(Player sender, Player targetPlayer, List<String> args) {
        if (args.size() != 4) {
            CommandHandler.sendMessage(sender, translate(sender, "commands.name.gradient_usage"));
            return;
        }

        var text = args.get(1);
        if (text.equalsIgnoreCase("uid")) {
            text = String.valueOf(targetPlayer.getUid());
        }

        if (text.length() > MAX_GRADIENT_LENGTH) {
            CommandHandler.sendMessage(
                    sender, translate(sender, "commands.name.too_long", MAX_GRADIENT_LENGTH));
            return;
        }

        int start = RichTextUtils.parseColor(args.get(2));
        int end = RichTextUtils.parseColor(args.get(3));
        if (start < 0 || end < 0) {
            CommandHandler.sendMessage(sender, translate(sender, "commands.name.bad_color"));
            return;
        }

        apply(sender, targetPlayer, RichTextUtils.gradient(text, start, end));
    }




    private void apply(Player sender, Player targetPlayer, String nickname) {
        if (nickname.isBlank()) {
            sendUsageMessage(sender);
            return;
        }

        if (nickname.length() > MAX_STORED_LENGTH) {
            CommandHandler.sendMessage(
                    sender, translate(sender, "commands.name.too_long", MAX_STORED_LENGTH));
            return;
        }

        targetPlayer.setNickname(nickname);
        targetPlayer.save();
        targetPlayer.sendPacket(new PacketSetPlayerNameRsp(targetPlayer));

        CommandHandler.sendMessage(
                sender, translate(sender, "commands.name.success", nickname.length()));
    }
}
