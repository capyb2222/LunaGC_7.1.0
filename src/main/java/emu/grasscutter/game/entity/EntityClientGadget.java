package emu.grasscutter.game.entity;

import java.util.List;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.config.ConfigEntityGadget;
import emu.grasscutter.data.binout.config.fields.ConfigAbilityData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.world.*;
import emu.grasscutter.server.packet.send.PacketPlayerEnterSceneInfoNotify;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.net.proto.AnimatorParameterValueInfoPairOuterClass.AnimatorParameterValueInfoPair;

import emu.grasscutter.net.proto.ClientGadgetInfoOuterClass;
import emu.grasscutter.net.proto.EntityAuthorityInfoOuterClass.EntityAuthorityInfo;
import emu.grasscutter.net.proto.EntityClientDataOuterClass.EntityClientData;
import emu.grasscutter.net.proto.EntityRendererChangedInfoOuterClass.EntityRendererChangedInfo;
import emu.grasscutter.net.proto.EvtCreateGadgetNotifyOuterClass.EvtCreateGadgetNotify;
import emu.grasscutter.net.proto.MotionInfoOuterClass.MotionInfo;
import emu.grasscutter.net.proto.PropPairOuterClass.PropPair;
import emu.grasscutter.net.proto.ProtEntityTypeOuterClass.ProtEntityType;
import emu.grasscutter.net.proto.FightPropPairOuterClass.FightPropPair;
import emu.grasscutter.net.proto.GadgetBornTypeOuterClass.GadgetBornType;
import emu.grasscutter.net.proto.SceneEntityAiInfoOuterClass.SceneEntityAiInfo;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass.SceneGadgetInfo;
import emu.grasscutter.net.proto.VectorOuterClass.Vector;
import emu.grasscutter.utils.helpers.ProtoHelper;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import lombok.Getter;

public class EntityClientGadget extends EntityBaseGadget {
    @Getter private final Player owner;

    @Getter(onMethod_ = @Override)
    public int gadgetId;

    @Getter private int ownerEntityId;
    @Getter private int targetEntityIdList;
    @Getter private int propOwnerEntityId;
    @Getter private int localId;

    @Getter private long guid;
    @Getter private int targetEntityId;
    @Getter private int gadgetState;
    @Getter private int gadgetType;
    @Getter private int nameId;
    @Getter private float floatVal;
    @Getter private int targetLockPointIndexList;
    @Getter private GadgetBornType bornType;
    @Getter private int intVal;
    @Getter private int paraType;
    @Getter private boolean asyncLoad;
    @Getter private boolean isPeerIdFromPlayer;
    @Getter private int originalOwnerEntityId;
    @Getter private final GadgetData gadgetData;
    @Getter private ConfigEntityGadget configGadget;

    public EntityClientGadget(Scene scene, Player player, EvtCreateGadgetNotify notify) {
        super(
                scene,
                new Position(notify.getInitPos()),
                new Position(notify.getInitEulerAngles()),
                notify.getCampId(),
                notify.getCampType());
        this.owner = player;
        this.id = notify.getEntityId();
        this.guid = notify.getGuid();
        this.localId = notify.getLocalId();
        this.gadgetId = notify.getConfigId();
        this.ownerEntityId = notify.getOwnerEntityId();
        this.propOwnerEntityId = notify.getPropOwnerEntityId();
        this.targetEntityId = notify.getTargetEntityId();
        this.asyncLoad = false;

        this.gadgetData = GameData.getGadgetDataMap().get(gadgetId);
        String jsonName = (gadgetData != null) ? gadgetData.getJsonName() : null;
        if (jsonName != null) {
            this.configGadget = GameData.getGadgetConfigData().get(jsonName);
        }

        GameEntity ownerEntity = scene.getEntityById(this.ownerEntityId);
        String ownerTypeBeforeResolve = ownerEntity != null ? ownerEntity.getClass().getSimpleName() : "null";
        ownerEntity = findOwnerEntity(ownerEntity);
        if (ownerEntity == null) {
            ownerEntity = scene.getEntityById(16777225);
        }
        if (ownerEntity instanceof EntityClientGadget ownerGadget) {
            this.originalOwnerEntityId = ownerGadget.getOriginalOwnerEntityId();
        } else {
            this.originalOwnerEntityId = this.ownerEntityId;
        }

        this.initAbilities();
    }

    private GameEntity findOwnerEntity(GameEntity owner) {
        if (owner instanceof EntityClientGadget ownerGadget) {

        GameEntity nextOwner = ownerGadget.getScene().getEntityById(ownerGadget.getOwnerEntityId());

        if (nextOwner instanceof EntityClientGadget) {
            return findOwnerEntity((EntityClientGadget) nextOwner);
        }

        return nextOwner;
        }
        return owner;
    }

    @Override
    public void initAbilities() {
        if (this.configGadget != null && this.configGadget.getAbilities() != null) {
            for (var ability : this.configGadget.getAbilities()) {
                addConfigAbility(ability);
            }
        }
    }

    private void addConfigAbility(ConfigAbilityData abilityData) {
        var data = GameData.getAbilityData(abilityData.getAbilityName());
        if (data != null) owner.getAbilityManager().addAbilityToEntity(this, data);
    }

    @Override
    public void onDeath(int killerId) {
        super.onDeath(killerId);
    }

    @Getter(onMethod_ = @Override, lazy = true)
    private final Int2FloatMap fightProperties = new Int2FloatOpenHashMap();

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
                        .setEntityType(ProtEntityType.ProtEntityType_PROT_ENTITY_GADGET)
                        .setMotionInfo(
                                MotionInfo.newBuilder()
                                        .setPos(getPosition().toProto())
                                        .setRot(getRotation().toProto())
                                        .setSpeed(Vector.newBuilder()))
                        .addAnimatorParaList(AnimatorParameterValueInfoPair.newBuilder())
                        .setEntityClientData(EntityClientData.newBuilder())
                        .setEntityAuthorityInfo(authority)
                        .setLifeState(1);

        PropPair pair =
                PropPair.newBuilder()
                        .setType(PlayerProperty.PROP_LEVEL.getId())
                        .setPropValue(ProtoHelper.newPropValue(PlayerProperty.PROP_LEVEL, 1))
                        .build();
        entityInfo.addPropList(pair);
        FightPropPair pair2 =
                FightPropPair.newBuilder()
                .build();
        entityInfo.addFightPropList(pair2);

        ClientGadgetInfoOuterClass.ClientGadgetInfo clientGadget =
                ClientGadgetInfoOuterClass.ClientGadgetInfo.newBuilder()
                        .setCampId(this.getCampId())
                        .setCampType(this.getCampType())
                        .setGuid(this.getGuid())
                        .setOwnerEntityId(this.getOwnerEntityId())
                        .setTargetEntityId(this.getTargetEntityId())
                        .setAsyncLoad(this.isAsyncLoad())
                        .setIsPeerIdFromPlayer(this.isPeerIdFromPlayer())
                        .build();

        SceneGadgetInfo.Builder gadgetInfo =
                SceneGadgetInfo.newBuilder()
                        .setGadgetId(this.getGadgetId())
                        .setOwnerEntityId(this.getOwnerEntityId())
                        .setBornType(this.bornType != null ? this.bornType : GadgetBornType.GadgetBornType_GADGET_BORN_PLAYER)
                        .setGadgetState(this.getGadgetState())

                        .setIsEnableInteract(true)
                        .setPropOwnerEntityId(this.getPropOwnerEntityId())
                        .setClientGadget(clientGadget)
                        .setPropOwnerEntityId(this.getOwnerEntityId())
                        .setAuthorityPeerId(this.getOwner().getPeerId());

        entityInfo.setGadget(gadgetInfo);

        return entityInfo.build();
    }
}
