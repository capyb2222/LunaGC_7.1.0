package emu.grasscutter.game.ability.actions;

import java.util.stream.Collectors;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.FightProperty;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import emu.grasscutter.game.entity.GameEntity;

public abstract class AbilityActionHandler {
    public abstract boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target);
            protected AbilityManager abilityManager;
          
            public AbilityActionHandler setManager(AbilityManager mgr) {
                this.abilityManager = mgr;
                return this;
            }
    protected static Object2FloatMap<String> propertiesFor(Ability ability) {
        var properties = new Object2FloatOpenHashMap<String>();
        var owner = ability.getOwner();

        if (owner != null) {
            for (var property : FightProperty.values()) {
                properties.put(property.name(), owner.getFightProperty(property));
            }

            owner.getGlobalAbilityValues().forEach(properties::put);
        }

        properties.putAll(ability.getAbilitySpecials());
        return properties;
    }

    protected GameEntity getTarget(Ability ability, GameEntity entity, String target) {
        return resolveTarget(ability, entity, target);
    }

    public static GameEntity resolveTarget(Ability ability, GameEntity entity, String target) {
        if (target == null) return entity;

        var playerOwner = ability.getPlayerOwner();
        var teamManager = playerOwner != null ? playerOwner.getTeamManager() : null;

        return switch (target) {
            case "Self", "Target", "Applier" -> entity;
            case "Caster", "Owner" -> ability.getOwner();
            case "Team" -> teamManager != null ? teamManager.getEntity() : null;
            case "OriginOwner", "CurLocalAvatar" -> teamManager != null
                    ? teamManager.getCurrentAvatarEntity()
                    : null;
            case "CasterOriginOwner" -> null;
            default -> {
                Grasscutter.getLogger().debug("Unknown ability target type: {}", target);
                yield entity;
            }
        };
    }
}
