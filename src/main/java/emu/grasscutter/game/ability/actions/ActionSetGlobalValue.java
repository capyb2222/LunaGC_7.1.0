package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;

@AbilityAction({
    AbilityModifierAction.Type.SetGlobalValue,
    AbilityModifierAction.Type.SetGlobalValueV2
})
public final class ActionSetGlobalValue extends AbilityActionHandler {

    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
                var properties = propertiesFor(ability);

                var valueKey = action.key;
                float computedValue = action.writtenValue().get(properties, 0f);
                target.getGlobalAbilityValues().put(valueKey, computedValue);
                target.onAbilityValueUpdate();

            if (!AbilityManager.isServerOwnedChain()) {
                var scene = target.getScene();
                var host = scene == null ? null : scene.getHost();
                if (host != null) {
                    host.sendPacket(
                            new PacketServerGlobalValueChangeNotify(target, valueKey, computedValue));
                }
            }
        return true;
    }
}
