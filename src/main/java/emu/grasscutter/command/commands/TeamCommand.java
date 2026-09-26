package emu.grasscutter.command.commands;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.command.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.NameIndex;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketChangeMpTeamAvatarRsp;
import java.util.*;

@Command(
        label = "team",
        usage = {"add <avatarId,...>", "(remove|set) [index|first|last|index-index,...]"},
        permission = "player.team",
        permissionTargeted = "player.team.others")
public final class TeamCommand implements CommandHandler {
    private static final int BASE_AVATARID = 10000000;

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args.isEmpty()) {
            sendUsageMessage(sender);
            return;
        }

        switch (args.get(0)) {
            case "add":
                if (!addCommand(sender, targetPlayer, args)) return;
                break;

            case "remove":
                if (!removeCommand(sender, targetPlayer, args)) return;
                break;

            case "set":
                if (!setCommand(sender, targetPlayer, args)) return;
                break;

            default:
                CommandHandler.sendTranslatedMessage(sender, "commands.team.invalid_usage");
                sendUsageMessage(sender);
                return;
        }

        targetPlayer
                .getTeamManager()
                .updateTeamEntities(
                        new PacketChangeMpTeamAvatarRsp(
                                targetPlayer, targetPlayer.getTeamManager().getCurrentTeamInfo()));
    }

    private static boolean isNumber(String text) {
        try {
            Integer.parseInt(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean addCommand(Player sender, Player targetPlayer, List<String> args) {
        if (args.size() < 2) {
            CommandHandler.sendTranslatedMessage(sender, "commands.team.invalid_usage");
            sendUsageMessage(sender);
            return false;
        }

        var typed = args.get(1);
        if (!typed.contains(",") && !isNumber(typed)) {
            var rest = new ArrayList<>(args.subList(2, args.size()));
            var before = rest.size();
            var named = NameIndex.resolve(typed, rest);

            if (GameData.getAvatarDataMap().containsKey(named)) {
                for (var i = before - rest.size(); i > 0; i--) args.remove(2);
                args.set(1, String.valueOf(named));
            }
        }

        int index = -1;
        if (args.size() > 2) {
            try {
                index = Integer.parseInt(args.get(2)) - 1;
                if (index < 0) index = 0;
            } catch (Exception e) {
                CommandHandler.sendTranslatedMessage(sender, "commands.team.invalid_index");
                return false;
            }
        }

        var avatarIds = args.get(1).split(",");
        var currentTeamAvatars = targetPlayer.getTeamManager().getCurrentTeamInfo().getAvatars();

        if (currentTeamAvatars.size() + avatarIds.length > GAME_OPTIONS.avatarLimits.singlePlayerTeam) {
            CommandHandler.sendTranslatedMessage(
                    sender, "commands.team.add_too_much", GAME_OPTIONS.avatarLimits.singlePlayerTeam);
            return false;
        }

        for (var avatarId : avatarIds) {
            int id;
            if (isNumber(avatarId)) {
                id = Integer.parseInt(avatarId);
            } else {
                id = NameIndex.resolve(avatarId, new ArrayList<>());
                if (!GameData.getAvatarDataMap().containsKey(id)) {
                    CommandHandler.sendTranslatedMessage(
                            sender, "commands.team.failed_to_add_avatar", avatarId);
                    continue;
                }
            }

            if (!addAvatar(sender, targetPlayer, id, index))
                CommandHandler.sendTranslatedMessage(
                        sender, "commands.team.failed_to_add_avatar", avatarId);
            if (index > 0) ++index;
        }
        return true;
    }

    private boolean removeCommand(Player sender, Player targetPlayer, List<String> args) {
        if (args.size() < 2) {
            CommandHandler.sendTranslatedMessage(sender, "commands.team.invalid_usage");
            sendUsageMessage(sender);
            return false;
        }

        var currentTeamAvatars = targetPlayer.getTeamManager().getCurrentTeamInfo().getAvatars();
        var avatarCount = currentTeamAvatars.size();

        var metaIndexList = args.get(1).split(",");
        var indexes = new HashSet<Integer>();
        var ignoreList = new ArrayList<Integer>();
        for (var metaIndex : metaIndexList) {
            var subIndexes = transformToIndexes(metaIndex, avatarCount);
            if (subIndexes == null) {
                CommandHandler.sendTranslatedMessage(
                        sender, "commands.team.failed_to_parse_index", metaIndex);
                continue;
            }

            for (var avatarIndex : subIndexes) {
                try {
                    indexes.add(currentTeamAvatars.get(avatarIndex - 1));
                } catch (Exception e) {
                    ignoreList.add(avatarIndex);
                    continue;
                }
            }
        }

        if (indexes.size() >= avatarCount) {
            CommandHandler.sendTranslatedMessage(sender, "commands.team.remove_too_much");
            return false;
        }

        if (!ignoreList.isEmpty()) {
            CommandHandler.sendTranslatedMessage(sender, "commands.team.ignore_index", ignoreList);
        }

        currentTeamAvatars.removeAll(indexes);
        return true;
    }

    private boolean setCommand(Player sender, Player targetPlayer, List<String> args) {
        if (args.size() < 3) {
            CommandHandler.sendTranslatedMessage(sender, "commands.team.invalid_usage");
            sendUsageMessage(sender);
            return false;
        }

        var currentTeamAvatars = targetPlayer.getTeamManager().getCurrentTeamInfo().getAvatars();

        int index;
        try {
            index = Integer.parseInt(args.get(1)) - 1;
            if (index < 0) index = 0;
        } catch (Exception e) {
            CommandHandler.sendTranslatedMessage(
                    sender, "commands.team.failed_to_parse_index", args.get(1));
            return false;
        }

        if (index + 1 > currentTeamAvatars.size()) {
            CommandHandler.sendTranslatedMessage(sender, "commands.team.index_out_of_range");
            return false;
        }

        int avatarId;
        try {
            avatarId = Integer.parseInt(args.get(2));
        } catch (Exception e) {
            CommandHandler.sendTranslatedMessage(
                    sender, "commands.team.failed_parse_avatar_id", args.get(2));
            return false;
        }
        if (avatarId < BASE_AVATARID) {
            avatarId += BASE_AVATARID;
        }

        if (currentTeamAvatars.contains(avatarId)) {
            CommandHandler.sendTranslatedMessage(
                    sender, "commands.team.avatar_already_in_team", avatarId);
            return false;
        }

        if (!targetPlayer.getAvatars().hasAvatar(avatarId)) {
            CommandHandler.sendTranslatedMessage(sender, "commands.team.avatar_not_found", avatarId);
            return false;
        }

        currentTeamAvatars.set(index, avatarId);
        return true;
    }

    private boolean addAvatar(Player sender, Player targetPlayer, int avatarId, int index) {
        if (avatarId < BASE_AVATARID) {
            avatarId += BASE_AVATARID;
        }
        var currentTeamAvatars = targetPlayer.getTeamManager().getCurrentTeamInfo().getAvatars();
        if (currentTeamAvatars.contains(avatarId)) {
            CommandHandler.sendTranslatedMessage(
                    sender, "commands.team.avatar_already_in_team", avatarId);
            return false;
        }
        if (!targetPlayer.getAvatars().hasAvatar(avatarId)) {
            CommandHandler.sendTranslatedMessage(sender, "commands.team.avatar_not_found", avatarId);
            return false;
        }
        if (index < 0) {
            currentTeamAvatars.add(avatarId);
        } else {
            currentTeamAvatars.add(index, avatarId);
        }
        return true;
    }

    private List<Integer> transformToIndexes(String metaIndexes, int listLength) {
        if (metaIndexes.equals("first")) {
            return List.of(1);
        } else if (metaIndexes.equals("last")) {
            return List.of(listLength);
        }

        if (metaIndexes.contains("-")) {
            var range = metaIndexes.split("-");
            if (range.length < 2) {
                return null;
            }

            int min, max;
            try {
                min =
                        switch (range[0]) {
                            case "first" -> 1;
                            case "last" -> listLength;
                            default -> Integer.parseInt(range[0]);
                        };

                max =
                        switch (range[1]) {
                            case "first" -> 1;
                            case "last" -> listLength;
                            default -> Integer.parseInt(range[1]);
                        };
            } catch (Exception e) {
                return null;
            }

            if (min > max) {
                min ^= max;
                max ^= min;
                min ^= max;
            }

            var indexes = new ArrayList<Integer>();
            for (int i = min; i <= max; ++i) {
                indexes.add(i);
            }
            return indexes;
        }

        try {
            int index = Integer.parseInt(metaIndexes);
            return List.of(index);
        } catch (Exception e) {
            return null;
        }
    }
}
