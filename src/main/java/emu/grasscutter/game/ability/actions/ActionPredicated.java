package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.ability.AbilityTargetSelector;
import emu.grasscutter.game.ability.PredicateEvaluator;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import java.util.List;

@AbilityAction(value = AbilityModifier.AbilityModifierAction.Type.Predicated)
public final class ActionPredicated extends AbilityActionHandler {

    @Override
    public boolean execute(Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        AbilityManager mgr = ability != null ? ability.getManager() : null;
        if (mgr == null) return true;

        List<GameEntity> selected = AbilityTargetSelector.select(action.otherTargets, ability, target);
        if (selected == null) {
            run(mgr, ability, action, abilityData, target, target);
            return true;
        }

        boolean any = false;
        for (var candidate : selected) {
            if (!PredicateEvaluator.all(action.targetPredicates, ability, ability.getOwner(), candidate, action)) {
                continue;
            }

            any = true;
            dispatch(mgr, ability, action.successActions, abilityData, target, candidate);
        }

        if (any) return true;

        if (blindScene(target)) {
            AbilityManager.runServerOwned(
                    () -> dispatchOwnerOnly(mgr, ability, action.successActions, abilityData, target));
            return true;
        }

        dispatch(mgr, ability, action.failActions, abilityData, target, target);
        return true;
    }

    private void run(AbilityManager mgr, Ability ability, AbilityModifierAction action,
                     ByteString abilityData, GameEntity self, GameEntity candidate) {
        boolean pass = PredicateEvaluator.all(action.targetPredicates, ability, ability.getOwner(), candidate, action);
        dispatch(mgr, ability, pass ? action.successActions : action.failActions, abilityData, self, candidate);
    }

    private boolean blindScene(GameEntity self) {
        var scene = self != null ? self.getScene() : null;
        if (scene == null) return false;

        return scene.getEntities().values().stream()
                .noneMatch(e -> e instanceof EntityMonster && e.isAlive());
    }

    private void dispatchOwnerOnly(AbilityManager mgr, Ability ability, AbilityModifierAction[] actions,
                                   ByteString abilityData, GameEntity self) {
        if (actions == null) return;
        for (var child : actions) {
            if (child == null || "Target".equals(child.target)) continue;
            if (!allowed(child)) continue;
            mgr.executeActionNow(ability, child, abilityData, self);
        }
    }

    private void dispatch(AbilityManager mgr, Ability ability, AbilityModifierAction[] actions,
                          ByteString abilityData, GameEntity self, GameEntity candidate) {
        if (actions == null) return;
        for (var child : actions) {
            if (child == null || !allowed(child)) continue;
            mgr.executeActionNow(ability, child, abilityData, "Target".equals(child.target) ? candidate : self);
        }
    }

    private boolean allowed(AbilityModifierAction action) {
        return !AbilityManager.isServerOwnedChain()
                || AbilityManager.isAllowedInServerOwnedChain(action.type);
    }
}
