package emu.grasscutter.game.entity;

import emu.grasscutter.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.proto.AbilityControlBlockOuterClass.AbilityControlBlock;
import emu.grasscutter.net.proto.AbilityEmbryoOuterClass.AbilityEmbryo;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.net.proto.AbilityAppliedAbilityOuterClass.AbilityAppliedAbility;
import emu.grasscutter.net.proto.AnimatorParameterValueInfoPairOuterClass.AnimatorParameterValueInfoPair;
import emu.grasscutter.net.proto.ChangeEnergyReasonOuterClass.ChangeEnergyReason;
import emu.grasscutter.net.proto.ChangeHpReasonOuterClass.ChangeHpReason;
import emu.grasscutter.net.proto.EntityAuthorityInfoOuterClass.EntityAuthorityInfo;
import emu.grasscutter.net.proto.EntityClientDataOuterClass.EntityClientData;
import emu.grasscutter.net.proto.EntityRendererChangedInfoOuterClass.EntityRendererChangedInfo;
import emu.grasscutter.net.proto.PlayerDieTypeOuterClass.PlayerDieType;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.net.proto.PropPairOuterClass.PropPair;
import emu.grasscutter.net.proto.ProtEntityTypeOuterClass.ProtEntityType;
import emu.grasscutter.net.proto.SceneAvatarInfoOuterClass.SceneAvatarInfo;
import emu.grasscutter.net.proto.SceneEntityAiInfoOuterClass.SceneEntityAiInfo;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import emu.grasscutter.net.proto.VectorOuterClass.Vector;
import emu.grasscutter.server.event.player.PlayerMoveEvent;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;
import emu.grasscutter.utils.helpers.ProtoHelper;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import lombok.*;

public class EntityAvatar extends GameEntity {
    @Getter private final Avatar avatar;
    private static long lastExecutionTime = 0;
    public static final long COOLDOWN = 14000;

    @Getter private PlayerDieType killedType;
    @Getter private int killedBy;

    public EntityAvatar(Avatar avatar) {
        this(null, avatar);
    }

    public EntityAvatar(Scene scene, Avatar avatar) {
        super(scene);

        this.avatar = avatar;
        this.avatar.setCurrentEnergy();

        if (scene != null) {
            var world = scene.getWorld();
            this.id = world.getNextEntityId(EntityIdType.AVATAR);

            var weapon = this.getAvatar().getWeapon();
            if (weapon != null) {
                if (!(weapon.getWeaponEntity() != null && weapon.getWeaponEntity().getScene() == scene)) {
                    weapon.setWeaponEntity(
                            new EntityWeapon(this.getPlayer().getScene(), weapon.getItemData().getGadgetId()));
                    scene.getWeaponEntities().put(weapon.getWeaponEntity().getId(), weapon.getWeaponEntity());
                }
            }
        } else {
            Grasscutter.getLogger()
                    .error("Unable to create EntityAvatar instance; provided scene is null.");
        }

        this.initAbilities();

        this.checkIfDead();
    }
    public long getLastExecutionTime() {
        return this.lastExecutionTime;
    }
    @Override
        public float getNyxValue() {
            if (this.getGlobalAbilityValues().containsKey("NyxValue")) {
                return this.getGlobalAbilityValues().get("NyxValue");
            } else {
                Grasscutter.getLogger().debug("NyxValue from entityavatar not found");
                return 0f;
            }
        }

    public void setLastExecutionTime(long time) {
        this.lastExecutionTime = time;
    }

    @Override
    public int getEntityTypeId() {
        return this.getAvatar().getAvatarId();
    }

    public Player getPlayer() {
        return this.avatar.getPlayer();
    }

    @Override
    public Position getPosition() {
        return getPlayer().getPosition();
    }

    @Override
    public Position getRotation() {
        return getPlayer().getRotation();
    }

    @Override
    public Int2FloatMap getFightProperties() {
        return getAvatar().getFightProperties();
    }

    public int getWeaponEntityId() {
        var avatar = this.getAvatar();

        if (avatar.getWeapon() != null && avatar.getWeapon().getWeaponEntity() != null) {
            return avatar.getWeapon().getWeaponEntity().getId();
        } else return 0;
    }

    @Override
    public void onDeath(int killerId) {
        super.onDeath(killerId);

        this.killedType = PlayerDieType.PlayerDieType_PLAYER_DIE_KILL_BY_MONSTER;
        this.killedBy = killerId;
        clearEnergy(ChangeEnergyReason.ChangeEnergyReason_CHANGE_ENERGY_NONE);
    }

    public void onDeath(PlayerDieType dieType, int killerId) {
        super.onDeath(killerId);

        this.killedType = dieType;
        this.killedBy = killerId;
        clearEnergy(ChangeEnergyReason.ChangeEnergyReason_CHANGE_ENERGY_NONE);
    }

    @Override
    public void initAbilities() {}

    private void addConfigAbility(String abilityName) {
        var data = GameData.getAbilityData(abilityName);
        if (data != null)
            this.getScene().getWorld().getHost().getAbilityManager().addAbilityToEntity(this, data);
    }

    @Override
    public float heal(float amount, boolean mute) {

        var currentHp = this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        if (currentHp <= 0) {
            return 0f;
        }

        if (currentHp > 0 && this.isDead()) {
            this.setDead(false);
            mute = false;
        }

        float healed = super.heal(amount, mute);
        if (healed > 0f) {
            getScene()
                    .broadcastPacket(
                            new PacketEntityFightPropChangeReasonNotify(
                                    this,
                                    FightProperty.FIGHT_PROP_CUR_HP,
                                    healed,
                                    mute
                                            ? PropChangeReason.PropChangeReason_PROP_CHANGE_NONE
                                            : PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY,
                                    ChangeHpReason.ChangeHpReason_CHANGE_HP_SUB_ABILITY));
        }

        return healed;
    }

    @Override
    public float heal(float amount) {
        return this.heal(amount, false);
    }
        public FightProperty GetEnergyProp(Avatar avatar) {
        val energySkill = avatar.getSkillDepot().getEnergySkillData();
        if (energySkill != null && energySkill.getSpecialEnergyMin() > 0) {
            return FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY;
        }
        return avatar.getSkillDepot().getElementType().getCurEnergyProp();
    }

    public void clearEnergy(ChangeEnergyReason reason) {

        val curEnergyProp = GetEnergyProp(this.getAvatar());
        float curEnergy = this.getFightProperty(curEnergyProp);
        Grasscutter.getLogger().debug("EnergyProp: {}", curEnergyProp.name());

        this.avatar.setCurrentEnergy(curEnergyProp, 0);
        getPlayer().sendPacket(new PacketAvatarFightPropNotify(this.getAvatar()));

        this.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(this, curEnergyProp));

        if (reason == ChangeEnergyReason.ChangeEnergyReason_CHANGE_ENERGY_SKILL_START) {
            this.getScene()
                    .broadcastPacket(
                            new PacketEntityFightPropChangeReasonNotify(this, curEnergyProp, -curEnergy, reason));
        }
    }

    public boolean addEnergy(float amount) {
        var curEnergyProp = this.getAvatar().getSkillDepot().getElementType().getCurEnergyProp();
        var curEnergy = this.getFightProperty(curEnergyProp);
        if (curEnergy == amount) return false;

        this.getAvatar().setCurrentEnergy(curEnergyProp, curEnergy + amount);

        this.getScene().broadcastPacket(new PacketEntityFightPropUpdateNotify(this, curEnergyProp));
        return true;
    }

    public void addEnergy(float amount, PropChangeReason reason) {
        this.addEnergy(amount, reason, false);
    }

    public void addEnergy(float amount, PropChangeReason reason, boolean isFlat) {

        val elementType = this.getAvatar().getSkillDepot().getElementType();
        val curEnergyProp = elementType.getCurEnergyProp();
        val maxEnergyProp = elementType.getMaxEnergyProp();

        float curEnergy = this.getFightProperty(curEnergyProp);
        float maxEnergy = this.getFightProperty(maxEnergyProp);

        if (!isFlat) {
            amount *= this.getFightProperty(FightProperty.FIGHT_PROP_CHARGE_EFFICIENCY);
        }

        float newEnergy = Math.min(curEnergy + amount, maxEnergy);

        if (newEnergy != curEnergy) {
            this.avatar.setCurrentEnergy(curEnergyProp, newEnergy);

            this.getScene()
                    .broadcastPacket(new PacketAvatarFightPropUpdateNotify(this.getAvatar(), curEnergyProp));
            this.getScene()
                    .broadcastPacket(
                            new PacketEntityFightPropChangeReasonNotify(this, curEnergyProp, newEnergy, reason));
        }
    }

    public SceneAvatarInfo getSceneAvatarInfo() {
        val avatar = this.getAvatar();
        val player = this.getPlayer();
        SceneAvatarInfo.Builder avatarInfo =
                SceneAvatarInfo.newBuilder()
                        .setUid(player.getUid())
                        .setAvatarId(avatar.getAvatarId())
                        .setGuid(avatar.getGuid())
                        .setPeerId(player.getPeerId())
                        .addAllTalentIdList(avatar.getTalentIdList())
                        .setCoreProudSkillLevel(avatar.getCoreProudSkillLevel())
                        .putAllSkillLevelMap(avatar.getSkillLevelMap())
                        .setSkillDepotId(avatar.getSkillDepotId())
                        .addAllInherentProudSkillList(avatar.getProudSkillList())
                        .putAllProudSkillExtraLevelMap(avatar.getProudSkillBonusMap())
                        .addAllTeamResonanceList(player.getTeamManager().getTeamResonances())
                        .setWearingFlycloakId(avatar.getFlyCloak())
                        .setCostumeId(avatar.getCostume())
                        .setTraceEffectId(avatar.getTraceEffect())
                        .setBornTime(avatar.getBornTime());

        for (GameItem item : avatar.getEquips().values()) {
            if (item.getItemData().getEquipType() == EquipType.EQUIP_WEAPON) {
                avatarInfo.setWeapon(item.createSceneWeaponInfo());
            } else {
                avatarInfo.addReliquaryList(item.createSceneReliquaryInfo());
            }
            avatarInfo.addEquipIdList(item.getItemId());
        }

        return avatarInfo.build();
    }

    @Override
    public SceneEntityInfo toProto() {
        EntityAuthorityInfo authority =
                EntityAuthorityInfo.newBuilder()
                        .setAbilityInfo(AbilitySyncStateInfo.newBuilder())
                        .setRendererChangedInfo(EntityRendererChangedInfo.newBuilder())
                        .setAiInfo(
                                SceneEntityAiInfo.newBuilder().setIsAiOpen(true))
                        .setBornPos(Vector.newBuilder())
                        .build();

        SceneEntityInfo.Builder entityInfo =
                SceneEntityInfo.newBuilder()
                        .setEntityId(getId())
                        .setEntityType(ProtEntityType.ProtEntityType_PROT_ENTITY_AVATAR)
                        .addAnimatorParaList(AnimatorParameterValueInfoPair.newBuilder())
                        .setEntityClientData(EntityClientData.newBuilder())
                        .setEntityAuthorityInfo(authority)
                        .setLastMoveSceneTimeMs(this.getLastMoveSceneTimeMs())
                        .setLastMoveReliableSeq(this.getLastMoveReliableSeq())
                        .setLifeState(this.getLifeState().getValue());

        if (this.getScene() != null) {
            entityInfo.setMotionInfo(this.getMotionInfo());
            this.injectIntMotionInfo(entityInfo);
        }

        this.addAllFightPropsToEntityInfo(entityInfo);

        PropPair pair =
                PropPair.newBuilder()
                        .setType(PlayerProperty.PROP_LEVEL.getId())
                        .setPropValue(
                                ProtoHelper.newPropValue(PlayerProperty.PROP_LEVEL, getAvatar().getLevel()))
                        .build();
        entityInfo.addPropList(pair);

        entityInfo.setAvatar(this.getSceneAvatarInfo());

        return entityInfo.build();
    }

    public AbilityControlBlock getAbilityControlBlock() {
        AvatarData data = this.getAvatar().getAvatarData();
        AbilityControlBlock.Builder abilityControlBlock = AbilityControlBlock.newBuilder();
        int embryoId = 0;

        if (data.getAbilities() != null) {
            for (int id : data.getAbilities()) {
                AbilityEmbryo emb =
                        AbilityEmbryo.newBuilder()
                                .setAbilityId(++embryoId)
                                .setAbilityNameHash(id)
                                .setAbilityOverrideNameHash(GameConstants.DEFAULT_ABILITY_NAME)
                                .build();
                abilityControlBlock.addAbilityEmbryoList(emb);
            }
        }

        boolean inNatlan = this.getPlayer().getScene() != null && this.getPlayer().getScene().getId() == 101;
        int phlogistonHash = Utils.abilityHash("DynamicAbility_Phlogiston");
        for (int id : GameConstants.DEFAULT_ABILITY_HASHES) {
            if (id == phlogistonHash && !inNatlan) continue;
            AbilityEmbryo emb =
                    AbilityEmbryo.newBuilder()
                            .setAbilityId(++embryoId)
                            .setAbilityNameHash(id)
                            .setAbilityOverrideNameHash(GameConstants.DEFAULT_ABILITY_NAME)
                            .build();
            abilityControlBlock.addAbilityEmbryoList(emb);
        }

        for (int id : this.getPlayer().getTeamManager().getTeamResonancesConfig()) {
            AbilityEmbryo emb =
                    AbilityEmbryo.newBuilder()
                            .setAbilityId(++embryoId)
                            .setAbilityNameHash(id)
                            .setAbilityOverrideNameHash(GameConstants.DEFAULT_ABILITY_NAME)
                            .build();
            abilityControlBlock.addAbilityEmbryoList(emb);
        }

        AvatarSkillDepotData skillDepot =
                GameData.getAvatarSkillDepotDataMap().get(this.getAvatar().getSkillDepotId());
        if (skillDepot != null && skillDepot.getAbilities() != null) {
            for (int id : skillDepot.getAbilities()) {
                AbilityEmbryo emb =
                        AbilityEmbryo.newBuilder()
                                .setAbilityId(++embryoId)
                                .setAbilityNameHash(id)
                                .setAbilityOverrideNameHash(GameConstants.DEFAULT_ABILITY_NAME)
                                .build();
                abilityControlBlock.addAbilityEmbryoList(emb);
            }
        }

        if (this.getAvatar().getExtraAbilityEmbryos().size() > 0) {
            for (String skill : this.getAvatar().getExtraAbilityEmbryos()) {
                AbilityEmbryo emb =
                        AbilityEmbryo.newBuilder()
                                .setAbilityId(++embryoId)
                                .setAbilityNameHash(Utils.abilityHash(skill))
                                .setAbilityOverrideNameHash(GameConstants.DEFAULT_ABILITY_NAME)
                                .build();
                abilityControlBlock.addAbilityEmbryoList(emb);
            }
        }

        return abilityControlBlock.build();
    }

    @Override
    public void move(Position newPosition, Position rotation) {

        PlayerMoveEvent event =
                new PlayerMoveEvent(
                        this.getPlayer(), PlayerMoveEvent.MoveType.PLAYER, this.getPosition(), newPosition);
        event.call();

        super.move(event.getDestination(), rotation);
    }

    @Override
    public void onAbilityValueUpdate() {
        super.onAbilityValueUpdate();

        if (this.getGlobalAbilityValues().containsKey("_ABILITY_UziExplode_Count")) {
            var count = this.getGlobalAbilityValues().get("_ABILITY_UziExplode_Count");
            if (count == 2f) {
                this.getGlobalAbilityValues().remove("_ABILITY_UziExplode_Count");
                this.getPlayer().getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_SKILL, 10006);
            }
        }
    }
}
