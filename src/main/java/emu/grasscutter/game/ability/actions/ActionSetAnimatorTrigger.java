package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.net.proto.AnimatorParameterValueInfoOuterClass.AnimatorParameterValueInfo;
import emu.grasscutter.net.proto.CombatInvokeEntryOuterClass.CombatInvokeEntry;
import emu.grasscutter.net.proto.EvtAnimatorParameterInfoOuterClass.EvtAnimatorParameterInfo;
import emu.grasscutter.server.packet.send.PacketCombatInvocationsNotify;
import emu.grasscutter.utils.Utils;

@AbilityAction(AbilityModifierAction.Type.SetAnimatorTrigger)
public final class ActionSetAnimatorTrigger extends AbilityActionHandler {
    private static final int TRIGGER_TYPE = 9;

    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (action.animatorParamName == null || target.getScene() == null) return true;
        broadcastAnimatorParam(target, action.animatorParamName, TRIGGER_TYPE, true);
        return true;
    }

    static void broadcastAnimatorParam(GameEntity target, String paramName, int paraType, boolean boolVal) {
        if (AbilityManager.isServerOwnedChain()) return;
        var value = AnimatorParameterValueInfo.newBuilder()
                .setParaType(paraType)
                .setBoolVal(boolVal)
                .build();
        var evtInfo = EvtAnimatorParameterInfo.newBuilder()
                .setEntityId(target.getId())
                .setNameId(Utils.animatorHash(paramName))
                .setValue(value)
                .build();
        var entry = CombatInvokeEntry.newBuilder()
                .setCombatData(evtInfo.toByteString())
                .setArgumentTypeValue(6)
                .build();
        target.getScene().broadcastPacket(new PacketCombatInvocationsNotify(entry));
    }

    static void broadcastAnimatorParamInt(GameEntity target, String paramName, int paraType, int intVal) {
        if (AbilityManager.isServerOwnedChain()) return;
        var value = AnimatorParameterValueInfo.newBuilder()
                .setParaType(paraType)
                .setIntVal(intVal)
                .build();
        var evtInfo = EvtAnimatorParameterInfo.newBuilder()
                .setEntityId(target.getId())
                .setNameId(Utils.animatorHash(paramName))
                .setValue(value)
                .build();
        var entry = CombatInvokeEntry.newBuilder()
                .setCombatData(evtInfo.toByteString())
                .setArgumentTypeValue(6)
                .build();
        target.getScene().broadcastPacket(new PacketCombatInvocationsNotify(entry));
    }

    static void broadcastAnimatorParamFloat(GameEntity target, String paramName, int paraType, float floatVal) {
        if (AbilityManager.isServerOwnedChain()) return;
        var value = AnimatorParameterValueInfo.newBuilder()
                .setParaType(paraType)
                .setFloatVal(floatVal)
                .build();
        var evtInfo = EvtAnimatorParameterInfo.newBuilder()
                .setEntityId(target.getId())
                .setNameId(Utils.animatorHash(paramName))
                .setValue(value)
                .build();
        var entry = CombatInvokeEntry.newBuilder()
                .setCombatData(evtInfo.toByteString())
                .setArgumentTypeValue(6)
                .build();
        target.getScene().broadcastPacket(new PacketCombatInvocationsNotify(entry));
    }
}
