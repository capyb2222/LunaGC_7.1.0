package emu.grasscutter.command.commands;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.packet.send.PacketWindSeedClientNotify;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;

@Command(label = "windy", usage = {"<scriptName>"}, aliases = { "w" }, permission = "player.windy", permissionTargeted = "player.windy.others")
public class WindyCommand implements CommandHandler
{
    @Override
    public void execute(final Player sender, final Player targetPlayer, final List<String> args) {
        if (args.isEmpty()) {
            this.sendUsageMessage(sender);
            return;
        }

        final String script = args.get(0);
        if (script.contains("/") || script.contains("\\") || script.contains("..")) {
            CommandHandler.sendMessage(sender, "Invalid script name.");
            return;
        }

        final String path = "C:/Windy/" + script + ".luac";
        if (!Files.isRegularFile(Paths.get(path))) {
            CommandHandler.sendMessage(sender, "No such script: " + path);
            return;
        }

        targetPlayer.sendPacket(new PacketWindSeedClientNotify(path));
        CommandHandler.sendMessage(sender, "Successfully executed the " + script + " Lua script!");
    }
}
