package emu.grasscutter.server.event.types;

import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.server.event.Event;

public abstract class EntityEvent extends Event {
    protected final GameEntity entity;

    public EntityEvent(GameEntity entity) {
        this.entity = entity;
    }

    public GameEntity getEntity() {
        return this.entity;
    }
}
