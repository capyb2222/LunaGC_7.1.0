package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.Grasscutter;

@AbilityAction(AbilityModifierAction.Type.TriggerAbility)
public final class ActionTriggerAbility extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        Grasscutter.getLogger().debug("[Ability] TriggerAbility: {}", action.abilityName);
        
        var player = ability.getPlayerOwner();
        if (player == null) {
            Grasscutter.getLogger().error("No player owner found for ability {}", ability);
            return false;
        }
        
        if (target == null || action.abilityName == null) return false;
        if (target == ability.getOwner()
                && ability.getData() != null
                && action.abilityName.equals(ability.getData().abilityName)) {
            return true;
        }

        player.getWorld().getHost().getAbilityManager().addAbilityToEntity(target, action.abilityName);
        
        return true;
    }
}
