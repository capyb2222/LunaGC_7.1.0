package emu.grasscutter.game.managers.energy;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import com.google.protobuf.InvalidProtocolBufferException;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.data.excels.monster.MonsterData.HpDrops;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.AbilityActionGenerateElemBallOuterClass.AbilityActionGenerateElemBall;
import emu.grasscutter.net.proto.AbilityIdentifierOuterClass.AbilityIdentifier;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import emu.grasscutter.net.proto.ChangeEnergyReasonOuterClass.ChangeEnergyReason;
import emu.grasscutter.net.proto.EvtBeingHitInfoOuterClass.EvtBeingHitInfo;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.server.game.GameSession;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;

public class EnergyManager extends BasePlayerManager {
    private static final Int2ObjectMap<List<EnergyDropInfo>> energyDropData =
            new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<List<SkillParticleGenerationInfo>>
            skillParticleGenerationData = new Int2ObjectOpenHashMap<>();
    private final Object2IntMap<EntityAvatar> avatarNormalProbabilities;
    @Getter private boolean energyUsage;

    public EnergyManager(Player player) {
        super(player);
        this.avatarNormalProbabilities = new Object2IntOpenHashMap<>();
        this.energyUsage = GAME_OPTIONS.energyUsage;
    }

    public static void initialize() {

        try {
            DataLoader.loadList("EnergyDrop.json", EnergyDropEntry.class)
                    .forEach(
                            entry -> {
                                energyDropData.put(entry.getDropId(), entry.getDropList());
                            });

            Grasscutter.getLogger().debug("Energy drop data successfully loaded.");
        } catch (Exception ex) {
            Grasscutter.getLogger().error("Unable to load energy drop data.", ex);
        }

        try {
            DataLoader.loadList("SkillParticleGeneration.json", SkillParticleGenerationEntry.class)
                    .forEach(
                            entry -> {
                                skillParticleGenerationData.put(entry.getAvatarId(), entry.getAmountList());
                            });

            Grasscutter.getLogger().debug("Skill particle generation data successfully loaded.");
        } catch (Exception ex) {
            Grasscutter.getLogger().error("Unable to load skill particle generation data data.", ex);
        }
    }

    private int getBallCountForAvatar(int avatarId) {

        int count = 2;

        if (!skillParticleGenerationData.containsKey(avatarId)) {
            Grasscutter.getLogger().warn("No particle generation data for avatarId {} found.", avatarId);
        }

        else {
            int roll = ThreadLocalRandom.current().nextInt(0, 100);
            int percentageStack = 0;
            for (SkillParticleGenerationInfo info : skillParticleGenerationData.get(avatarId)) {
                int chance = info.getChance();
                percentageStack += chance;
                if (roll < percentageStack) {
                    count = info.getValue();
                    break;
                }
            }
        }

        return count;
    }

    private int getBallIdForElement(ElementType element) {

        if (element == null) {
            return 2024;
        }

        return switch (element) {
            case Fire -> 2017;
            case Water -> 2018;
            case Grass -> 2019;
            case Electric -> 2020;
            case Wind -> 2021;
            case Ice -> 2022;
            case Rock -> 2023;
            default -> 2024;
        };
    }

    public void handleGenerateElemBall(AbilityInvokeEntry invoke)
            throws InvalidProtocolBufferException {

        AbilityActionGenerateElemBall action =
                AbilityActionGenerateElemBall.parseFrom(invoke.getAbilityData());
        if (action == null) {
            return;
        }

        int itemId = 2024;

        int amount = 2;

        Optional<EntityAvatar> avatarEntity =
                this.getCastingAvatarEntityForEnergy(invoke.getEntityId());

        if (avatarEntity.isPresent()) {
            Avatar avatar = avatarEntity.get().getAvatar();

            if (avatar != null) {
                int avatarId = avatar.getAvatarId();
                AvatarSkillDepotData skillDepotData = avatar.getSkillDepot();

                amount = this.getBallCountForAvatar(avatarId);

                if (skillDepotData != null) {
                    ElementType element = skillDepotData.getElementType();
                    itemId = this.getBallIdForElement(element);
                }
            }
        }

        var pos = new Position(action.getPos());
        for (int i = 0; i < amount; i++) {
            this.generateElemBall(itemId, pos, 1);
        }
    }

    private void generateEnergyForNormalAndCharged(EntityAvatar avatar) {

        WeaponType weaponType = avatar.getAvatar().getAvatarData().getWeaponType();

        if (!this.avatarNormalProbabilities.containsKey(avatar)) {
            this.avatarNormalProbabilities.put(avatar, weaponType.getEnergyGainInitialProbability());
        }

        int currentProbability = this.avatarNormalProbabilities.getInt(avatar);
        int roll = ThreadLocalRandom.current().nextInt(0, 100);

        if (roll < currentProbability) {
            avatar.addEnergy(1.0f, PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY, true);
            this.avatarNormalProbabilities.put(avatar, weaponType.getEnergyGainInitialProbability());
        }

        else {
            this.avatarNormalProbabilities.put(
                    avatar, currentProbability + weaponType.getEnergyGainIncreaseProbability());
        }
    }

    public void handleAttackHit(EvtBeingHitInfo hitInfo) {

        AttackResult attackRes = hitInfo.getAttackResult();

        Optional<EntityAvatar> attackerEntity =
                this.getCastingAvatarEntityForEnergy(attackRes.getAttackerId());
        if (attackerEntity.isEmpty()
                || this.player.getTeamManager().getCurrentAvatarEntity().getId()
                        != attackerEntity.get().getId()) {
            return;
        }

        GameEntity targetEntity = this.player.getScene().getEntityById(attackRes.getDefenseId());
        if (!(targetEntity instanceof EntityMonster targetMonster)) {
            return;
        }

        MonsterType targetType = targetMonster.getMonsterData().getType();
        if (targetType != MonsterType.MONSTER_ORDINARY && targetType != MonsterType.MONSTER_BOSS) {
            return;
        }

        AbilityIdentifier ability = attackRes.getAbilityIdentifier();

        if (ability != AbilityIdentifier.getDefaultInstance()) {
            return;
        }

        this.generateEnergyForNormalAndCharged(attackerEntity.get());
    }

    private void handleBurstCast(Avatar avatar, int skillId) {

        if (!GAME_OPTIONS.energyUsage || !this.energyUsage) {
            return;
        }

        var skillData = GameData.getAvatarSkillDataMap().get(skillId);

        if ((avatar.getSkillDepot() != null && skillId == avatar.getSkillDepot().getEnergySkill())
                || (skillData != null && skillData.getCostElemVal() > 0)) {
            avatar.getAsEntity().clearEnergy(ChangeEnergyReason.ChangeEnergyReason_CHANGE_ENERGY_SKILL_START);
        }
    }

    public void handleEvtDoSkillSuccNotify(GameSession session, int skillId, int casterId) {

        Optional<EntityAvatar> caster =
                this.player.getTeamManager().getActiveTeam().stream()
                        .filter(character -> character.getId() == casterId)
                        .findFirst();

        if (caster.isEmpty()) {
            return;
        }

        EntityAvatar casterEntity = caster.get();
        Avatar avatar = casterEntity.getAvatar();

        this.handleBurstCast(avatar, skillId);

        if (avatar.getAvatarId() == 10000096) {
            this.player.getAbilityManager().onArlecchinoSkillNotify(skillId);
        }
    }

    private void generateElemBallDrops(EntityMonster monster, int dropId) {

        if (!energyDropData.containsKey(dropId)) {
            Grasscutter.getLogger().warn("No drop data for dropId {} found.", dropId);
            return;
        }

        for (EnergyDropInfo info : energyDropData.get(dropId)) {
            this.generateElemBall(info.getBallId(), monster.getPosition(), info.getCount());
        }
    }

    public void handleMonsterEnergyDrop(
            EntityMonster monster, float hpBeforeDamage, float hpAfterDamage) {

        MonsterType type = monster.getMonsterData().getType();
        if (type != MonsterType.MONSTER_ORDINARY && type != MonsterType.MONSTER_BOSS) {
            return;
        }

        float maxHp = monster.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float thresholdBefore = hpBeforeDamage / maxHp;
        float thresholdAfter = hpAfterDamage / maxHp;

        for (HpDrops drop : monster.getMonsterData().getHpDrops()) {
            if (drop.getDropId() == 0) {
                continue;
            }

            float threshold = drop.getHpPercent() / 100.0f;
            if (threshold < thresholdBefore && threshold >= thresholdAfter) {
                this.generateElemBallDrops(monster, drop.getDropId());
            }
        }

        if (hpAfterDamage <= 0 && monster.getMonsterData().getKillDropId() != 0) {
            this.generateElemBallDrops(monster, monster.getMonsterData().getKillDropId());
        }
    }

    private void generateElemBall(int ballId, Position position, int count) {

        ItemData itemData = GameData.getItemDataMap().get(ballId);
        if (itemData == null) {
            return;
        }

        EntityItem energyBall =
                new EntityItem(this.getPlayer().getScene(), this.getPlayer(), itemData, position, count);
        this.getPlayer().getScene().addEntity(energyBall);
    }

    private Optional<EntityAvatar> getCastingAvatarEntityForEnergy(int invokeEntityId) {

        GameEntity entity = this.player.getScene().getEntityById(invokeEntityId);

        int avatarEntityId =
                (!(entity instanceof EntityClientGadget))
                        ? invokeEntityId
                        : ((EntityClientGadget) entity).getOriginalOwnerEntityId();

        return this.player.getTeamManager().getActiveTeam().stream()
                .filter(character -> character.getId() == avatarEntityId)
                .findFirst();
    }

    public boolean refillActiveEnergy() {
        var activeEntity = this.player.getTeamManager().getCurrentAvatarEntity();
        return activeEntity.addEnergy(
                activeEntity.getAvatar().getSkillDepot().getEnergySkillData().getCostElemVal());
    }

    public void refillTeamEnergy(PropChangeReason changeReason, boolean isFlat) {
        for (var entityAvatar : this.player.getTeamManager().getActiveTeam()) {

            var skillDepot = entityAvatar.getAvatar().getSkillDepot();
            if (skillDepot != null) {
                entityAvatar.addEnergy(
                        skillDepot.getEnergySkillData().getCostElemVal(), changeReason, isFlat);
            }
        }
    }

    public void setEnergyUsage(boolean energyUsage) {
        this.energyUsage = energyUsage;
        if (!energyUsage) {
            this.refillTeamEnergy(PropChangeReason.PropChangeReason_PROP_CHANGE_GM, true);
        }
    }
}
