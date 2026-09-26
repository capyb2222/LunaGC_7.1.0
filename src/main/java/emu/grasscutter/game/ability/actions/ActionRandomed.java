package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.entity.GameEntity;
import java.util.concurrent.ThreadLocalRandom;

@AbilityAction(AbilityModifier.AbilityModifierAction.Type.Randomed)
public final class ActionRandomed extends AbilityActionHandler {

    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        var manager = ability != null ? ability.getManager() : null;
        if (manager == null) return true;

        var chance = action.chance.get(propertiesFor(ability), 0f);
        var taken =
                ThreadLocalRandom.current().nextFloat() < chance
                        ? action.successActions
                        : action.failActions;
        if (taken == null) return true;

        for (var child : taken) {
            if (child == null) continue;
            if (AbilityManager.isServerOwnedChain()
                    && !AbilityManager.isAllowedInServerOwnedChain(child.type)) continue;

            manager.executeActionNow(ability, child, abilityData, target);
        }

        return true;
    }
}
