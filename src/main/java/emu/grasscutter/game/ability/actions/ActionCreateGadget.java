package emu.grasscutter.game.ability.actions;

import com.google.protobuf.*;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.CampTargetType;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.AbilityActionCreateGadgetOuterClass.AbilityActionCreateGadget;

@AbilityAction(AbilityModifierAction.Type.CreateGadget)
public class ActionCreateGadget extends AbilityActionHandler {

    private static boolean clientOwnsChain(GameEntity entity) {
        if (entity instanceof EntityClientGadget || entity instanceof EntityAvatar) return true;

        // Owners are only ever set at creation, to an entity that already exists, so walking up
        // cannot come back around.
        while (entity instanceof EntityGadget summon && summon.getOwner() != null) {
            entity = summon.getOwner();
            if (entity instanceof EntityAvatar || entity instanceof EntityClientGadget) return true;
        }
        return false;
    }

    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        var entity = ability.getOwner();
        if (entity instanceof EntityClientGadget) return true;

        if (clientOwnsChain(entity)) {
            return true;
        }

        AbilityActionCreateGadget createGadget;
        try {
            createGadget = AbilityActionCreateGadget.parseFrom(abilityData);
        } catch (InvalidProtocolBufferException e) {
            return false;
        }

        var pos =
                createGadget.hasPos() ? new Position(createGadget.getPos()) : entity.getPosition().clone();
        var rot =
                createGadget.hasRot() ? new Position(createGadget.getRot()) : entity.getRotation().clone();

        var entityCreated =
                new EntityGadget(
                        entity.getScene(),
                        action.gadgetID,
                        pos,
                        rot,
                        action.campID,
                        CampTargetType.getTypeByName(action.campTargetType).getValue());
        var owner = action.ownerIsTarget ? target : entity;
        entityCreated.setOwner(owner);

        // Nothing on this side runs the summon's own KillSelf, so without this every charged attack
        // would leave another copy standing in the scene. One summon of a given kind per owner.
        entity.getScene().getEntities().values().stream()
                .filter(e -> e instanceof EntityGadget g
                        && g.getGadgetId() == action.gadgetID
                        && g.getOwner() == owner)
                .toList()
                .forEach(stale -> entity.getScene().removeEntity(stale));

        entity.getScene().addEntity(entityCreated);

        Grasscutter.getLogger()
                .trace(
                        "Gadget {} created at pos {} rot {}",
                        action.gadgetID,
                        entityCreated.getPosition(),
                        entityCreated.getRotation());

        return true;
    }
}
