package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;

@AbilityAction(AbilityModifierAction.Type.KillGadget)
public final class ActionKillGadget extends AbilityActionHandler {

    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        var configId =
                (action.gadgetInfo != null && action.gadgetInfo.configID > 0)
                        ? action.gadgetInfo.configID
                        : action.gadgetID;
        if (configId == 0 || target == null) return false;

        var scene = target.getScene();
        var owner = ability != null ? ability.getOwner() : null;
        if (scene == null || owner == null) return false;

        var doomed =
                scene.getEntities().values().stream()
                        .filter(
                                entity ->
                                        entity instanceof EntityGadget gadget
                                                && gadget.getGadgetId() == configId
                                                && gadget.getOwner() == owner)
                        .toList();

        doomed.forEach(scene::killEntity);
        return true;
    }
}
