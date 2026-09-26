package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketAvatarLifeStateChangeNotify;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import java.util.List;

@AbilityAction(AbilityModifierAction.Type.ReviveAvatar)
public final class ActionReviveAvatar extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
            Player player = ability.getPlayerOwner();

            var owner = ability.getOwner();
            if (owner instanceof EntityClientGadget ownerGadget) {
                owner = ownerGadget.getScene().getEntityById(ownerGadget.getOwnerEntityId());
            }
            var properties = new Object2FloatOpenHashMap<String>();
            for (var property : FightProperty.values()) {
                var name = property.name();
                var value = owner.getFightProperty(property);
                properties.put(name, value);
            }
    
        properties.putAll(ability.getAbilitySpecials());

        float ratio = action.amountByTargetMaxHPRatio.get(properties, 0.0f);
        player.getTeamManager().getActiveTeam().forEach(entityAvatar -> {
            boolean wasDead = !entityAvatar.isAlive();

            if (wasDead) {
                float maxHp = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
                float healAmount = maxHp * ratio;

                entityAvatar.getWorld().broadcastPacket(
                    new PacketAvatarLifeStateChangeNotify(entityAvatar.getAvatar())
                );
                entityAvatar.heal(healAmount, false);
            }

        });
        return true;
    }
}