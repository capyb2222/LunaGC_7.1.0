package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.FurnitureCurModuleArrangeCountNotifyOuterClass;

public class PacketFurnitureCurModuleArrangeCountNotify extends BasePacket {

    public PacketFurnitureCurModuleArrangeCountNotify() {
        super(PacketOpcodes.FurnitureCurModuleArrangeCountNotify);

        var proto =
                FurnitureCurModuleArrangeCountNotifyOuterClass.FurnitureCurModuleArrangeCountNotify
                        .newBuilder();


        this.setData(proto);
    }
}
