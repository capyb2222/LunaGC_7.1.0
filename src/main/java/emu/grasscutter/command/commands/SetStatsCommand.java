package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import java.util.*;

@Command(
        label = "setStats",
        aliases = {"stats", "stat"},
        usage = {
            "[set] <stat> <value>",
            "(lock|freeze) <stat> [<value>]",
            "(unlock|unfreeze) <stat>"
        },
        permission = "player.setstats",
        permissionTargeted = "player.setstats.others")
public final class SetStatsCommand implements CommandHandler {
    private final Map<String, Stat> stats;

    public SetStatsCommand() {
        this.stats = new HashMap<>();
        for (String key : FightProperty.getShortNames()) {
            this.stats.put(key, new Stat(FightProperty.getPropByShortName(key)));
        }
        for (FightProperty prop : FightProperty.values()) {
            String name = prop.toString().substring(10);
            String key = name.toLowerCase();
            name = name.substring(1);
            this.stats.put(key, new Stat(name, prop));
        }

        this.stats.put("mhp", this.stats.get("maxhp"));
        this.stats.put("hp", this.stats.get("_cur_hp"));
        this.stats.put("atk", this.stats.get("_cur_attack"));
        this.stats.put("def", this.stats.get("_cur_defense"));
        this.stats.put(
                "atkb",
                this.stats.get(
                        "_base_attack"));
        this.stats.put("eanemo", this.stats.get("anemo%"));
        this.stats.put("ecryo", this.stats.get("cryo%"));
        this.stats.put("edendro", this.stats.get("dendro%"));
        this.stats.put("edend", this.stats.get("dendro%"));
        this.stats.put("eelectro", this.stats.get("electro%"));
        this.stats.put("eelec", this.stats.get("electro%"));
        this.stats.put("ethunder", this.stats.get("electro%"));
        this.stats.put("egeo", this.stats.get("geo%"));
        this.stats.put("ehydro", this.stats.get("hydro%"));
        this.stats.put("epyro", this.stats.get("pyro%"));
        this.stats.put("ephys", this.stats.get("phys%"));
    }

    public static float parsePercent(String input) throws NumberFormatException {
        if (input.endsWith("%")) {
            return Float.parseFloat(input.substring(0, input.length() - 1)) / 100f;
        } else {
            return Float.parseFloat(input);
        }
    }

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        String statStr = null;
        String valueStr;
        float value = 0f;

        if (args.size() < 2) {
            sendUsageMessage(sender);
            return;
        }

        String arg0 = args.remove(0).toLowerCase();
        Action action =
                switch (arg0) {
                    default -> {
                        statStr = arg0;
                        yield Action.ACTION_SET;
                    }
                    case "set" -> Action.ACTION_SET;
                    case "lock", "freeze" -> Action.ACTION_LOCK;
                    case "unlock", "unfreeze" -> Action.ACTION_UNLOCK;
                };
        if (statStr == null) {
            statStr = args.remove(0).toLowerCase();
        }
        if (!stats.containsKey(statStr)) {
            sendUsageMessage(sender);
            return;
        }
        Stat stat = stats.get(statStr);
        EntityAvatar entity = targetPlayer.getTeamManager().getCurrentAvatarEntity();
        Avatar avatar = entity.getAvatar();

        try {
            switch (action) {
                case ACTION_LOCK:
                    if (args.isEmpty()) {
                        value = avatar.getFightProperty(stat.prop);
                        break;
                    }
                case ACTION_SET:
                    value = parsePercent(args.remove(0));
                    break;
                case ACTION_UNLOCK:
                    break;
            }
        } catch (NumberFormatException ignored) {
            CommandHandler.sendTranslatedMessage(sender, "commands.generic.invalid.statValue");
            return;
        } catch (IndexOutOfBoundsException ignored) {
            sendUsageMessage(sender);
            return;
        }

        if (!args.isEmpty()) {
            sendUsageMessage(sender);
            return;
        }

        switch (action) {
            case ACTION_SET:
                entity.setFightProperty(stat.prop, value);
                entity.getWorld().broadcastPacket(new PacketEntityFightPropUpdateNotify(entity, stat.prop));
                break;
            case ACTION_LOCK:
                avatar.getFightPropOverrides().put(stat.prop.getId(), value);
                avatar.recalcStats();
                break;
            case ACTION_UNLOCK:
                avatar.getFightPropOverrides().remove(stat.prop.getId());
                avatar.recalcStats();
                break;
        }

        if (FightProperty.isPercentage(stat.prop)) {
            valueStr = String.format("%.1f%%", value * 100f);
        } else {
            valueStr = String.format("%.0f", value);
        }
        if (targetPlayer == sender) {
            CommandHandler.sendTranslatedMessage(sender, action.messageKeySelf, stat.name, valueStr);
        } else {
            String uidStr = targetPlayer.getAccount().getId();
            CommandHandler.sendTranslatedMessage(
                    sender, action.messageKeyOther, stat.name, uidStr, valueStr);
        }
    }

    private enum Action {
        ACTION_SET("commands.generic.set_to", "commands.generic.set_for_to"),
        ACTION_LOCK("commands.setStats.locked_to", "commands.setStats.locked_for_to"),
        ACTION_UNLOCK("commands.setStats.unlocked", "commands.setStats.unlocked_for");
        public final String messageKeySelf;
        public final String messageKeyOther;

        Action(String messageKeySelf, String messageKeyOther) {
            this.messageKeySelf = messageKeySelf;
            this.messageKeyOther = messageKeyOther;
        }
    }

    private static class Stat {
        String name;
        FightProperty prop;

        public Stat(FightProperty prop) {
            this.name = prop.toString();
            this.prop = prop;
        }

        public Stat(String name, FightProperty prop) {
            this.name = name;
            this.prop = prop;
        }
    }
}
