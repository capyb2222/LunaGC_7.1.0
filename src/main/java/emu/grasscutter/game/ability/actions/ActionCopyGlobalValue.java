package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;

@AbilityAction(AbilityModifierAction.Type.CopyGlobalValue)
public final class ActionCopyGlobalValue extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity entity) {
        var source = this.getTarget(ability, entity, action.srcTarget);
        var destination = this.getTarget(ability, entity, action.dstTarget);
        if (source == null || destination == null) {
            Grasscutter.getLogger().debug("ActionCopyGlobalValue: source or destination is null");
            return false;
        }

        if (action.srcKey == null || action.dstKey == null) {
            Grasscutter.getLogger().debug("ActionCopyGlobalValue: source or destination key is null");
            return false;
        }

        var value = source.getGlobalAbilityValues().get(action.srcKey);
        if (value == null) {
            Grasscutter.getLogger().debug("ActionCopyGlobalValue: source value is null");
            return false;
        }

        destination.getGlobalAbilityValues().put(action.dstKey, value);
        destination.onAbilityValueUpdate();

        return true;
    }
}
