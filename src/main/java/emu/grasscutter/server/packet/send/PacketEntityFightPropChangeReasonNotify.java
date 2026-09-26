package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ChangeEnergyReasonOuterClass.ChangeEnergyReason;
import emu.grasscutter.net.proto.ChangeHpDebtsReasonOuterClass.ChangeHpDebtsReason;
import emu.grasscutter.net.proto.ChangeHpReasonOuterClass.ChangeHpReason;
import emu.grasscutter.net.proto.PropChangeDetailInfoOuterClass.PropChangeDetailInfo;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.DetailAbilityInfoOuterClass.DetailAbilityInfo;
import emu.grasscutter.net.proto.PropChangeDetailInfoOuterClass.PropChangeDetailInfo;
import emu.grasscutter.net.proto.EntityFightPropChangeReasonNotifyOuterClass.EntityFightPropChangeReasonNotify;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import java.util.*;
import lombok.Getter;
import java.util.List;

public class PacketEntityFightPropChangeReasonNotify extends BasePacket {
    @Getter private Ability ability;
    public PacketEntityFightPropChangeReasonNotify(
            GameEntity entity,
            FightProperty prop,
            Float value,
            List<Integer> param,
            PropChangeReason reason,
            ChangeHpReason changeHpReason) {
        super(PacketOpcodes.EntityFightPropChangeReasonNotify);

        EntityFightPropChangeReasonNotify.Builder proto =
                EntityFightPropChangeReasonNotify.newBuilder()
                        .setEntityId(entity.getId())
                        .setPropType(prop.getId())
                        .setPropDelta(value)
                        .setReason(reason)
                        .setChangeHpReason(changeHpReason);

        for (int p : param) {
            proto.addParamList(p);
        }

        this.setData(proto);
    }

    public PacketEntityFightPropChangeReasonNotify(
            GameEntity entity,
            FightProperty prop,
            Float value,
            PropChangeReason reason,
            ChangeHpReason changeHpReason) {
        super(PacketOpcodes.EntityFightPropChangeReasonNotify);

        var detailAbility = entity.getDetailAbilityInfo();
        PropChangeDetailInfo detailInfo = null;
        if (detailAbility != null) {
            detailInfo = PropChangeDetailInfo.newBuilder()
                .build();
        }


        var proto =
                EntityFightPropChangeReasonNotify.newBuilder()
                        .setEntityId(entity.getId())
                        .setPropType(prop.getId())
                        .setPropDelta(value)
                        .setReason(reason)
                        .setChangeHpReason(changeHpReason)
                        .build();

        this.setData(proto);
    }

    public PacketEntityFightPropChangeReasonNotify(
            GameEntity entity, FightProperty prop, Float value, PropChangeReason reason) {
        super(PacketOpcodes.EntityFightPropChangeReasonNotify);

        EntityFightPropChangeReasonNotify proto =
                EntityFightPropChangeReasonNotify.newBuilder()
                        .setEntityId(entity.getId())
                        .setPropType(prop.getId())
                        .setPropDelta(value)
                        .setReason(reason)
                        .build();

        this.setData(proto);
    }

    public PacketEntityFightPropChangeReasonNotify(
            GameEntity entity, FightProperty prop, Float value, ChangeEnergyReason reason) {
        super(PacketOpcodes.EntityFightPropChangeReasonNotify);


        EntityFightPropChangeReasonNotify proto =
                EntityFightPropChangeReasonNotify.newBuilder()
                        .setEntityId(entity.getId())
                        .setPropType(prop.getId())
                        .setPropDelta(value)
                        .setChangeEnergyReson(reason)
                        .build();

        this.setData(proto);
    }

    public PacketEntityFightPropChangeReasonNotify(
            GameEntity entity,
            FightProperty prop,
            Float value,
            PropChangeReason reason,
            ChangeHpDebtsReason changeHpDebts) {
        super(PacketOpcodes.EntityFightPropChangeReasonNotify);

        var detailAbility = entity.getDetailAbilityInfo();
        PropChangeDetailInfo detailInfo = null;
        if (detailAbility != null) {
            detailInfo = PropChangeDetailInfo.newBuilder()
                .build();
        }

        var proto =
                EntityFightPropChangeReasonNotify.newBuilder()
                        .setEntityId(entity.getId())
                        .setPropType(prop.getId())
                        .setPropDelta(value)
                        .setReason(reason)
                        .setChangeHpDebtsReason(changeHpDebts)
                        .build();
        this.setData(proto);
    }


    public PacketEntityFightPropChangeReasonNotify(
        GameEntity entity, FightProperty prop, Float value, PropChangeReason reason, ChangeEnergyReason energyReason) {
    super(PacketOpcodes.EntityFightPropChangeReasonNotify);

    var detailAbility = entity.getDetailAbilityInfo();
    PropChangeDetailInfo detailInfo = null;
    if (detailAbility != null) {
        detailInfo = PropChangeDetailInfo.newBuilder()
            .build();
    }

    EntityFightPropChangeReasonNotify proto =
            EntityFightPropChangeReasonNotify.newBuilder()
                    .setEntityId(entity.getId())
                    .setPropType(prop.getId())
                    .setPropDelta(value)
                    .setReason(reason)
                    .setChangeEnergyReson(energyReason)
                    .build();

    this.setData(proto);
}

}
