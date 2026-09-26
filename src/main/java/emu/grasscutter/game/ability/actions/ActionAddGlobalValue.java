package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.ability.PredicateEvaluator;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.entity.GameEntity;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import java.util.List;
import java.util.Map;

@AbilityAction(AbilityModifierAction.Type.AddGlobalValue)
public final class ActionAddGlobalValue extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (action.predicates != null && !action.predicates.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> preds = (List<Map<String, Object>>) (List<?>) action.predicates;
            if (!PredicateEvaluator.all(preds, ability, ability.getOwner(), target, action)) return true;
        }
        var properties = propertiesFor(ability);
        String valueKey = action.key;
        float valueToAdd = action.ratio.get(properties, 0f);
        float maxValue = action.maxValue.get(properties, 0f);
        float minValue = action.minValue.get(properties, 0f);

        float currentGlobalValue = target.getGlobalAbilityValues().getOrDefault(valueKey, 0f);

        float newValue = currentGlobalValue + valueToAdd;
        if (newValue > maxValue) {
            newValue = maxValue;
        }
        if (newValue < minValue) {
            newValue = minValue;
        }

        target.getGlobalAbilityValues().put(valueKey, newValue);

        target.onAbilityValueUpdate();
        if (!AbilityManager.isServerOwnedChain() && target.getScene() != null && target.getScene().getHost() != null) {
            target
                    .getScene()
                    .getHost()
                    .sendPacket(new PacketServerGlobalValueChangeNotify(target, valueKey, newValue));
        }

        return true;
    }
}