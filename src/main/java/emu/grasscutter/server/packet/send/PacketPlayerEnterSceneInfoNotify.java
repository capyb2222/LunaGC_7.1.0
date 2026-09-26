package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.OpenConfigEntry;
import emu.grasscutter.data.binout.OpenConfigEntry.AbilityVarSetter;
import emu.grasscutter.data.binout.OpenConfigEntry.TalentParamEntry;
import emu.grasscutter.data.excels.ProudSkillData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.data.excels.avatar.AvatarTalentData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AbilityAppliedAbilityOuterClass.AbilityAppliedAbility;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.net.proto.AvatarEnterSceneInfoOuterClass.AvatarEnterSceneInfo;
import emu.grasscutter.net.proto.MPLevelEntityInfoOuterClass.MPLevelEntityInfo;
import emu.grasscutter.net.proto.PlayerEnterSceneInfoNotifyOuterClass.PlayerEnterSceneInfoNotify;
import emu.grasscutter.net.proto.TeamEnterSceneInfoOuterClass.TeamEnterSceneInfo;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AbilityStringOuterClass;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.utils.Utils;
import java.util.Set;
import java.util.stream.Collectors;

public class PacketPlayerEnterSceneInfoNotify extends BasePacket {

    private static volatile Set<Integer> HEX_IDS_CACHE;
    private static volatile Set<Integer> MOON_IDS_CACHE;

    public static Set<Integer> getHexenzirkelIds() {
        Set<Integer> s = HEX_IDS_CACHE;
        if (s == null) HEX_IDS_CACHE = s = buildTaggedSet("AVATAR_TAG_HEXENZIRKEL");
        return s;
    }

    public static Set<Integer> getMoonphaseIds() {
        Set<Integer> s = MOON_IDS_CACHE;
        if (s == null) MOON_IDS_CACHE = s = buildTaggedSet("AVATAR_TAG_MOONPHASE");
        return s;
    }

    private static AbilityScalarValueEntry verdantDew(Player player) {
        return AbilityScalarValueEntry.newBuilder()
                .setKey(AbilityString.newBuilder()
                        .setHash(Utils.abilityHash(TeamManager.VERDANT_DEW))
                        .setStr(TeamManager.VERDANT_DEW)
                        .build())
                .setFloatValue(player.getTeamManager().getEntity().getGlobalAbilityValues()
                        .getOrDefault(TeamManager.VERDANT_DEW, 0f))
                .build();
    }

    private static Set<Integer> buildTaggedSet(String tag) {
        return GameData.getAvatarDataMap().values().stream()
            .filter(a -> a.getTags() != null && a.getTags().contains(tag))
            .map(a -> a.getId())
            .collect(Collectors.toUnmodifiableSet());
    }

    public PacketPlayerEnterSceneInfoNotify(Player player) {
        super(PacketOpcodes.PlayerEnterSceneInfoNotify);

        AbilityScalarValueEntry scalarValue = AbilityScalarValueEntry.newBuilder()
                .setKey(AbilityStringOuterClass.AbilityString.newBuilder().setHash(Utils.abilityHash("SGV_PlayerTeam_Phlogiston"))
                        .setStr("SGV_PlayerTeam_Phlogiston")
                        .build())
                        .setFloatValue(100)
                .build();
                player.setPhlogistonValue(100);

        long hexCount = player.getTeamManager().getActiveTeam().stream()
                .filter(e -> getHexenzirkelIds().contains(e.getAvatar().getAvatarId()))
                .count();

        AbilityScalarValueEntry hexLevel = AbilityScalarValueEntry.newBuilder()
                .setKey(AbilityString.newBuilder()
                        .setHash(Utils.abilityHash("SGV_HexenzirkelLevel"))
                        .setStr("SGV_HexenzirkelLevel")
                        .build())
                .setFloatValue(hexCount)
                .build();

        int moonsignLevel = player.getTeamManager().getMoonsignLevel();

        AbilityScalarValueEntry moonPhaseLevel = AbilityScalarValueEntry.newBuilder()
                .setKey(AbilityString.newBuilder()
                        .setHash(Utils.abilityHash("SGV_MoonPhaseLevel"))
                        .setStr("SGV_MoonPhaseLevel")
                        .build())
                .setFloatValue(moonsignLevel)
                .build();

        AbilitySyncStateInfo.Builder teamInfo = AbilitySyncStateInfo.newBuilder()
                .addSgvDynamicValueMap(scalarValue)
                .addSgvDynamicValueMap(hexLevel)
                .addSgvDynamicValueMap(moonPhaseLevel);

        if (moonsignLevel > 0) {
            teamInfo.addDynamicValueMap(verdantDew(player));
        }

        AbilitySyncStateInfo phlogiston = teamInfo.build();
        AbilitySyncStateInfo empty = AbilitySyncStateInfo.newBuilder().build();

        PlayerEnterSceneInfoNotify.Builder proto =
                PlayerEnterSceneInfoNotify.newBuilder()
                        .setCurAvatarEntityId(player.getTeamManager().getCurrentAvatarEntity().getId())
                        .setEnterSceneToken(player.getEnterSceneToken());

        proto.setTeamEnterInfo(
                TeamEnterSceneInfo.newBuilder()
                        .setTeamEntityId(player.getTeamManager().getEntity().getId())
                        .setTeamAbilityInfo(phlogiston)
                        .setAbilityControlBlock(player.getTeamManager().getAbilityControlBlock()));
        proto.setMpLevelEntityInfo(
                MPLevelEntityInfo.newBuilder()
                        .setEntityId(player.getWorld().getLevelEntityId())
                        .setAuthorityPeerId(player.getWorld().getHostPeerId())
                        .setAbilityInfo(empty));

        for (EntityAvatar avatarEntity : player.getTeamManager().getActiveTeam()) {
            GameItem weapon = avatarEntity.getAvatar().getWeapon();
            long weaponGuid = weapon != null ? weapon.getGuid() : 0;

            AbilitySyncStateInfo.Builder avatarAbilityInfo = AbilitySyncStateInfo.newBuilder();
            buildAvatarAbilityVars(avatarEntity, avatarAbilityInfo);

            AvatarEnterSceneInfo avatarInfo =
                    AvatarEnterSceneInfo.newBuilder()
                            .setAvatarGuid(avatarEntity.getAvatar().getGuid())
                            .setAvatarEntityId(avatarEntity.getId())
                            .setWeaponGuid(weaponGuid)
                            .setWeaponEntityId(avatarEntity.getWeaponEntityId())
                            .setAvatarAbilityInfo(avatarAbilityInfo)
                            .setWeaponAbilityInfo(AbilitySyncStateInfo.newBuilder())
                            .build();

            proto.addAvatarEnterInfo(avatarInfo);
        }

        this.setData(proto.build());
    }

    public static void buildAvatarAbilityVars(EntityAvatar avatarEntity, AbilitySyncStateInfo.Builder info) {
        Avatar avatar = avatarEntity.getAvatar();

        if (getMoonphaseIds().contains(avatar.getAvatarId())) {
            info.addDynamicValueMap(verdantDew(avatarEntity.getPlayer()));
        }

        for (int proudSkillId : avatar.getProudSkillList()) {
            applyProudSkillVars(proudSkillId, info);
        }

        AvatarSkillDepotData depotData = GameData.getAvatarSkillDepotDataMap().get(avatar.getSkillDepotId());
        if (depotData != null) {
            var skillLevelMap = avatar.getSkillLevelMap();
            depotData.getSkillsAndEnergySkill().forEach(skillId -> {
                var skillCfg = GameData.getAvatarSkillDataMap().get(skillId);
                if (skillCfg == null || skillCfg.getProudSkillGroupId() == 0) return;
                int level = skillLevelMap.getOrDefault(skillId, 1);
                applyProudSkillVars(skillCfg.getProudSkillGroupId() * 100 + level, info);
            });
        }

        for (int talentId : avatar.getTalentIdList()) {
            AvatarTalentData talentData = GameData.getAvatarTalentDataMap().get(talentId);
            if (talentData == null || talentData.getOpenConfig() == null) continue;
            OpenConfigEntry entry = GameData.getOpenConfigEntries().get(talentData.getOpenConfig());
            if (entry == null || entry.getTalentParamEntries() == null) continue;
            for (TalentParamEntry param : entry.getTalentParamEntries()) {
                info.addAppliedAbilities(buildTalentParamAbility(param));
            }
        }
    }

    private static void applyProudSkillVars(int proudSkillId, AbilitySyncStateInfo.Builder info) {
        ProudSkillData skillData = GameData.getProudSkillDataMap().get(proudSkillId);
        if (skillData == null || skillData.getOpenConfig() == null) return;

        OpenConfigEntry entry = GameData.getOpenConfigEntries().get(skillData.getOpenConfig());
        if (entry == null) return;

        boolean hasVarSetters = entry.getAbilityVarSetters() != null;
        boolean hasTalentParams = entry.getTalentParamEntries() != null;
        if (!hasVarSetters && !hasTalentParams) return;

        if (hasVarSetters) {
            float[] paramList = skillData.getParamList();
            for (AbilityVarSetter setter : entry.getAbilityVarSetters()) {
                int idx = setter.getParamIndex();
                if (paramList == null || idx >= paramList.length) continue;
                info.addDynamicValueMap(AbilityScalarValueEntry.newBuilder()
                        .setKey(AbilityString.newBuilder()
                                .setHash(Utils.abilityHash(setter.getVarName()))
                                .setStr(setter.getVarName())
                                .build())
                        .setFloatValue(paramList[idx])
                        .build());
            }
        }

        if (hasTalentParams) {
            for (TalentParamEntry param : entry.getTalentParamEntries()) {
                info.addAppliedAbilities(buildTalentParamAbility(param));
            }
        }
    }

    private static AbilityAppliedAbility buildTalentParamAbility(TalentParamEntry param) {
        return AbilityAppliedAbility.newBuilder()
                .setAbilityName(AbilityString.newBuilder()
                        .setStr(param.getAbilityName())
                        .setHash(Utils.abilityHash(param.getAbilityName()))
                        .build())
                .addOverrideMap(AbilityScalarValueEntry.newBuilder()
                        .setKey(AbilityString.newBuilder()
                                .setStr(param.getParamName())
                                .setHash(Utils.abilityHash(param.getParamName()))
                                .build())
                        .setFloatValue(1.0f)
                        .build())
                .build();
    }
}
