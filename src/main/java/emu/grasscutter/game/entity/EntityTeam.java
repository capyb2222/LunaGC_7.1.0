package emu.grasscutter.game.entity;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.EntityIdType;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import it.unimi.dsi.fastutil.ints.*;

public class EntityTeam extends GameEntity {

    private Player player;

    public EntityTeam(Player player) {
        super(player.getScene());        
        this.player = player;
        initAbilities();
        this.id = player.getWorld().getNextEntityId(EntityIdType.TEAM);
    }


    @Override
    public void initAbilities() {
        var defaultAbilities = GameData.getConfigGlobalCombat().getDefaultAbilities();
        if (defaultAbilities.getDefaultTeamAbilities() != null)
            for (var ability : defaultAbilities.getDefaultTeamAbilities()) {
                AbilityData data = GameData.getAbilityData(ability);
                if (data != null)
                    player.getWorld().getHost().getAbilityManager().addAbilityToEntity(this, data);
            }
    }

    @Override
    public World getWorld() {
        return player.getWorld();
    }

    @Override
    public int getEntityTypeId() {
        return EntityIdType.TEAM.getId();
    }

    @Override
    public Int2FloatMap getFightProperties() {
        return new Int2FloatArrayMap();
    }

    @Override
    public Position getPosition() {
        return new Position(0, 0, 0);
    }

    @Override
    public Position getRotation() {
        return new Position(0, 0, 0);
    }

    @Override
    public SceneEntityInfo toProto() {
        return null;
    }
}