package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import emu.grasscutter.Grasscutter;

@AbilityAction(AbilityModifierAction.Type.ClearGlobalValue)
public final class ActionClearGlobalValue extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        var valueKey = action.key;
        if (valueKey == null || valueKey.isEmpty()) {
            return false;
        }

        var globalValues = target.getGlobalAbilityValues();
        if (globalValues.containsKey(valueKey)) {
            globalValues.remove(valueKey);

            target.onAbilityValueUpdate();

            if (target.getScene() != null && target.getScene().getHost() != null) {
                target.getScene()
                      .getHost()
                      .sendPacket(new PacketServerGlobalValueChangeNotify(target, valueKey, 0.0f));
            }

            return true;
        }

        return false;
    }
}
