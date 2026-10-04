package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.tps.TpsAvatarSystem;
import emu.grasscutter.game.tps.TpsWeaponSystem;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import java.util.ArrayList;
import java.util.List;

@Command(
        label = "tps",
        usage = {
            "give [weapon ...]",
            "accessory [weapon ...]",
            "wear [weapon ...]",
            "traveler [off]",
            "refill",
            "list"
        },
        permission = "player.tps",
        permissionTargeted = "player.tps.others")
public final class TpsCommand implements CommandHandler {
    private static final String ICON_PREFIX = "UI_EquipIcon_";

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args.isEmpty()) {
            sendUsageMessage(sender);
            return;
        }

        if (args.get(0).equalsIgnoreCase("traveler")) {
            this.traveler(sender, targetPlayer, args.size() > 1 && args.get(1).equalsIgnoreCase("off"));
            return;
        }

        var ids = new ArrayList<Integer>();
        for (String arg : args.subList(1, args.size())) {
            int weaponId = this.resolveWeapon(sender, arg);
            if (weaponId == 0) return;
            ids.add(weaponId);
        }

        switch (args.get(0).toLowerCase()) {
            case "give" -> this.give(sender, targetPlayer, ids);
            case "accessory" -> this.accessory(sender, targetPlayer, ids);
            case "wear" -> this.wear(sender, targetPlayer, ids);
            case "refill" -> {
                TpsWeaponSystem.refillAmmunition(targetPlayer);
                CommandHandler.sendMessage(sender, "TPS ammunition refilled.");
            }
            case "list" -> this.list(sender, targetPlayer);
            default -> sendUsageMessage(sender);
        }
    }

    private static String weaponName(int weaponId) {
        var item = GameData.getItemDataMap().get(weaponId);
        if (item == null || item.getIcon() == null) return String.valueOf(weaponId);
        return item.getIcon().replace(ICON_PREFIX, "").toLowerCase();
    }

    private int resolveWeapon(Player sender, String arg) {
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException ignored) {
        }

        var matches =
                GameData.getTpsWeaponDataMap().keySet().intStream()
                        .sorted()
                        .filter(weaponId -> weaponName(weaponId).contains(arg.toLowerCase()))
                        .boxed()
                        .toList();
        if (matches.size() == 1) return matches.get(0);

        CommandHandler.sendMessage(
                sender,
                matches.isEmpty()
                        ? "No TPS weapon matches '" + arg + "'."
                        : "'" + arg + "' matches " + matches.stream().map(TpsCommand::weaponName).toList() + ".");
        return 0;
    }

    private List<Integer> weaponIdsOrAll(List<Integer> ids) {
        if (!ids.isEmpty()) return ids;
        return GameData.getTpsWeaponDataMap().keySet().intStream().sorted().boxed().toList();
    }

    private void give(Player sender, Player target, List<Integer> ids) {
        var given = new ArrayList<Integer>();
        for (int weaponId : this.weaponIdsOrAll(ids)) {
            if (!GameData.getTpsWeaponDataMap().containsKey(weaponId)) {
                CommandHandler.sendMessage(sender, weaponId + " is not a TPS weapon.");
                continue;
            }
            if (TpsWeaponSystem.findOwnedWeapon(target, weaponId) != null) continue;
            if (target.getInventory().addItem(weaponId)) given.add(weaponId);
        }
        CommandHandler.sendMessage(sender, "TPS weapons given: " + given);
    }

    private void accessory(Player sender, Player target, List<Integer> ids) {
        var given = new ArrayList<Integer>();
        for (int weaponId : this.weaponIdsOrAll(ids)) {
            if (TpsWeaponSystem.findOwnedWeapon(target, weaponId) == null) continue;
            for (int materialId : TpsWeaponSystem.getAccessoryMaterials(weaponId)) {
                if (target.getInventory().getItemById(materialId) != null) continue;
                if (target.getInventory().addItem(materialId)) given.add(materialId);
            }
        }
        CommandHandler.sendMessage(sender, "TPS accessory items given: " + given);
    }

    private void wear(Player sender, Player target, List<Integer> ids) {
        var entity = target.getTeamManager().getCurrentAvatarEntity();
        if (entity == null) {
            CommandHandler.sendMessage(sender, "No avatar is on the field.");
            return;
        }

        if (!TpsAvatarSystem.isTpsAvatar(entity.getAvatar())) {
            CommandHandler.sendMessage(
                    sender, "Only the TPS traveler can wear these. Use /tps traveler first.");
            return;
        }

        var guids = new ArrayList<Long>();
        for (int weaponId : ids) {
            var weapon = TpsWeaponSystem.findOwnedWeapon(target, weaponId);
            if (weapon == null) {
                CommandHandler.sendMessage(sender, "TPS weapon " + weaponId + " is not owned.");
                return;
            }
            guids.add(weapon.getGuid());
        }

        int retcode;
        synchronized (target) {
            retcode = TpsWeaponSystem.wear(target, entity.getAvatar().getGuid(), guids);
        }
        if (retcode == Retcode.RET_SUCC_VALUE) {
            CommandHandler.sendMessage(
                    sender, "Avatar " + entity.getAvatar().getAvatarId() + " now wears " + ids + ".");
        } else {
            CommandHandler.sendMessage(sender, "Could not wear " + ids + " (retcode " + retcode + ").");
        }
    }

    private void traveler(Player sender, Player target, boolean off) {
        if (off) {
            CommandHandler.sendMessage(
                    sender,
                    TpsAvatarSystem.leaveTraveler(target)
                            ? "Back to your own team."
                            : "The TPS traveler is not active.");
            return;
        }
        CommandHandler.sendMessage(
                sender,
                TpsAvatarSystem.enterTraveler(target)
                        ? "Switched to the TPS traveler."
                        : "Could not switch to the TPS traveler (a trial team is already active).");
    }

    private void list(Player sender, Player target) {
        var owned = TpsWeaponSystem.getOwnedWeapons(target);
        if (owned.isEmpty()) {
            CommandHandler.sendMessage(sender, "No TPS weapons owned.");
            return;
        }
        for (var weapon : owned) {
            CommandHandler.sendMessage(
                    sender,
                    weapon.getItemId()
                            + " "
                            + weaponName(weapon.getItemId())
                            + " accessories "
                            + weapon.getTpsAccessoryIds());
        }
        for (var avatar : TpsWeaponSystem.getWearers(target)) {
            CommandHandler.sendMessage(
                    sender, "Avatar " + avatar.getAvatarId() + " wears " + avatar.getTpsWeaponIds());
        }
    }
}
