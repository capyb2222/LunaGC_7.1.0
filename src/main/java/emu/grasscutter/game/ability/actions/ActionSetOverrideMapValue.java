package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.props.FightProperty;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.Grasscutter;

@AbilityAction(AbilityModifierAction.Type.SetOverrideMapValue)
public final class ActionSetOverrideMapValue extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
            var owner = ability.getOwner();
            var properties = new Object2FloatOpenHashMap<String>();

            for (var property : FightProperty.values()) {
      
       var name = property.name();
                var value = owner.getFightProperty(property);
                properties.put(name, value);
            }
        
            properties.putAll(ability.getAbilitySpecials());
        
            String overrideMapKey = action.overrideMapKey;
            float ratio = action.ratio.get(properties, 0f);
        ability.getAbilitySpecials().put(overrideMapKey, ratio);
        return true;
    }
}
