package emu.grasscutter.game.entity.gadget.platform;

import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.scripts.data.SceneGadget;
import lombok.*;

public class PointArrayRoute extends BaseRoute {

    @Getter @Setter int currentPoint;
    @Getter @Setter int pointArrayId;

    public PointArrayRoute(SceneGadget gadget) {
        super(gadget);
    }

    public PointArrayRoute(
            Position startRot, boolean startRoute, boolean isActive, int pointArrayId) {
        super(startRot, startRoute, isActive);
        this.pointArrayId = pointArrayId;
    }

    @Override
    public PlatformInfoOuterClass.PlatformInfo.Builder toProto() {
        return super.toProto()
                .setMovingPlatformType(
                        MovingPlatformTypeOuterClass.MovingPlatformType.MovingPlatformType_MOVING_PLATFORM_ROUTE);
    }
}
