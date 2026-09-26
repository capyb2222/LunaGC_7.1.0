package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketWindSeedClientNotify;
import emu.grasscutter.server.packet.send.PacketWindSeedUID;
import emu.grasscutter.utils.WatermarkUtils;

import java.util.List;

@Command(label = "uid", usage = {
    "set <what you want>",
    "default"
}, permissionTargeted = "player.windy.others")
public class SetUIDWatermarkTextCommand implements CommandHandler {

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args.isEmpty()) {
            this.sendUsageMessage(sender);
            return;
        }

        switch (args.remove(0)) {
            case "set" -> this.set(sender, targetPlayer, args);
            case "default" -> this.setToDefault(sender, targetPlayer);
            default -> this.sendUsageMessage(sender);
        }
    }

    private void set(Player sender, Player target, List<String> args) {
        String text = String.join(" ", args);
        if (!WatermarkUtils.fits(text)) {
            CommandHandler.sendMessage(sender, "The number of characters you entered is too large!");
            return;
        }
        CommandHandler.sendMessage(sender, "Successfully changed your UID to " + text);

        target.sendPacket(new PacketWindSeedClientNotify(WatermarkUtils.buildLuac(text)));
    }

    private void setToDefault(Player sender, Player target) {
        CommandHandler.sendMessage(sender, "Successfully restored your UID to the server-default one.");
        target.sendPacket(new PacketWindSeedUID());
    }

}
