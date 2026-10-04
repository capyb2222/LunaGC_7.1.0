package emu.grasscutter.game.tps;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ConstValueData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.data.excels.trial.TrialAvatarData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.dungeons.DungeonTrialTeam;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.GrantReasonOuterClass.GrantReason;
import java.util.*;
import javax.annotation.Nullable;

public final class TpsAvatarSystem {
    private static final String CONST_AVATAR_MALE = "CONST_VALUE_TPS_AVATAR_CONFIG_ID_MALE";
    private static final String CONST_AVATAR_FEMALE = "CONST_VALUE_TPS_AVATAR_CONFIG_ID_FEMALE";
    private static final String CONST_INIT_WEAPON = "CONST_VALUE_INIT_TPS_WEAPON_ID";
    private static final String CONST_STAMINA_LIMIT = "CONST_VALUE_TPS_STAMINA_LIMIT";
    private static final int STAMINA_SCALE = 100;
    private static final String CONFIG_NAME_MALE = "AetherShadow";
    private static final String CONFIG_NAME_FEMALE = "LumineShadow";

    private TpsAvatarSystem() {}

    public static int getMaleAvatarId() {
        return ConstValueData.getInt(CONST_AVATAR_MALE, 0, 0);
    }

    public static int getFemaleAvatarId() {
        return ConstValueData.getInt(CONST_AVATAR_FEMALE, 0, 0);
    }

    public static int getStaminaLimit() {
        return ConstValueData.getInt(CONST_STAMINA_LIMIT, 0, 0) * STAMINA_SCALE;
    }

    public static int getInitWeaponId() {
        return ConstValueData.getInt(CONST_INIT_WEAPON, 0, 0);
    }

    public static boolean isTpsAvatar(int avatarId) {
        return avatarId > 0 && (avatarId == getMaleAvatarId() || avatarId == getFemaleAvatarId());
    }

    public static boolean isTpsAvatar(@Nullable Avatar avatar) {
        return avatar != null && isTpsAvatar(avatar.getAvatarId());
    }

    @Nullable public static String getConfigName(int avatarId) {
        if (avatarId <= 0) return null;
        if (avatarId == getMaleAvatarId()) return CONFIG_NAME_MALE;
        if (avatarId == getFemaleAvatarId()) return CONFIG_NAME_FEMALE;
        return null;
    }

    public static int getTpsAvatarId(Player player) {
        var main = GameData.getAvatarDataMap().get(player.getMainCharacterId());
        var female = GameData.getAvatarDataMap().get(getFemaleAvatarId());
        boolean isFemale =
                main != null
                        && female != null
                        && Objects.equals(main.getBodyType(), female.getBodyType());
        return isFemale ? getFemaleAvatarId() : getMaleAvatarId();
    }

    public static boolean isTpsScene(@Nullable SceneData sceneData) {
        if (sceneData == null || sceneData.getSpecifiedAvatarList() == null) return false;
        return sceneData.getSpecifiedAvatarList().stream().anyMatch(TpsAvatarSystem::isTpsAvatar);
    }

    public static int getTrialAvatarId(Player player) {
        int avatarId = getTpsAvatarId(player);
        return GameData.getTrialAvatarDataMap().values().stream()
                .filter(data -> data.getTrialAvatarParamList() != null)
                .filter(data -> !data.getTrialAvatarParamList().isEmpty())
                .filter(data -> data.getTrialAvatarParamList().get(0) == avatarId)
                .mapToInt(TrialAvatarData::getTrialAvatarId)
                .min()
                .orElse(0);
    }

    @Nullable public static DungeonTrialTeam getTrialTeam(Player player, @Nullable Scene scene) {
        if (scene == null || !isTpsScene(scene.getSceneData())) return null;
        int trialAvatarId = getTrialAvatarId(player);
        if (trialAvatarId == 0) return null;
        return new DungeonTrialTeam(
                new ArrayList<>(List.of(trialAvatarId)), GrantReason.GRANT_REASON_BY_TRIAL_AVATAR_ACTIVITY);
    }

    public static boolean isTravelerActive(Player player) {
        return player.getTeamManager().isUsingTrialTeam()
                && player.getTeamManager().getTrialAvatars().values().stream()
                        .anyMatch(TpsAvatarSystem::isTpsAvatar);
    }

    public static boolean enterTraveler(Player player) {
        if (player.getTeamManager().isUsingTrialTeam()) return false;
        int trialAvatarId = getTrialAvatarId(player);
        if (trialAvatarId == 0) return false;
        player.getTeamManager().addTrialAvatars(List.of(trialAvatarId));
        return true;
    }

    public static boolean leaveTraveler(Player player) {
        if (!isTravelerActive(player)) return false;
        player.setTpsLoadoutPending(false);
        player.getTeamManager().getTrialAvatars().values().stream()
                .filter(TpsAvatarSystem::isTpsAvatar)
                .forEach(TpsWeaponSystem::dropWeaponEntities);
        player.getTeamManager().removeTrialAvatar();
        return true;
    }

    public static void onTrialTeamReady(Player player) {
        boolean hasTpsAvatar = false;
        for (Avatar avatar : player.getTeamManager().getTrialAvatars().values()) {
            if (!isTpsAvatar(avatar)) continue;
            hasTpsAvatar = true;
            TpsWeaponSystem.sendEquipChange(avatar);
            if (!avatar.getTpsWeaponIds().equals(player.getTpsLoadout())) {
                player.setTpsLoadoutPending(true);
            }
        }
        if (hasTpsAvatar) TpsWeaponSystem.sendSceneAmmunition(player);
    }

    public static void onAbilityInitFinish(Player player) {
        if (!player.isTpsLoadoutPending()) return;
        var entity = player.getTeamManager().getCurrentAvatarEntity();
        if (entity == null || !isTpsAvatar(entity.getAvatar())) return;

        player.setTpsLoadoutPending(false);
        var avatar = entity.getAvatar();
        avatar.getTpsWeaponIds().clear();
        avatar.getTpsWeaponIds().addAll(player.getTpsLoadout());
        avatar.recalcStats();
        TpsWeaponSystem.sendEquipChange(avatar);
        TpsWeaponSystem.sendAmmunition(player);
    }

    public static void onTrialAvatarCreated(Avatar avatar) {
        if (!isTpsAvatar(avatar) || avatar.getPlayer() == null) return;
        var player = avatar.getPlayer();
        var loadout = player.getTpsLoadout();

        loadout.removeIf(itemId -> TpsWeaponSystem.findOwnedWeapon(player, itemId) == null);
        if (loadout.isEmpty()) {
            int initWeaponId = getInitWeaponId();
            if (initWeaponId > 0 && TpsWeaponSystem.findOwnedWeapon(player, initWeaponId) == null) {
                player.getInventory().addItem(initWeaponId);
            }
            if (TpsWeaponSystem.findOwnedWeapon(player, initWeaponId) != null) {
                loadout.add(initWeaponId);
            }
        }

        avatar.getTpsWeaponIds().clear();
        if (!loadout.isEmpty()) avatar.getTpsWeaponIds().add(loadout.get(0));
        player.getTpsAmmunitionSent().clear();
        player.setTpsLoadoutPending(false);
    }
}
