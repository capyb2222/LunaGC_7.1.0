package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.WearTpsEquipRspOuterClass.WearTpsEquipRsp;
import java.util.List;

public class PacketWearTpsEquipRsp extends BasePacket {
    public PacketWearTpsEquipRsp(int retcode, long avatarGuid, List<Long> equipGuids) {
        super(PacketOpcodes.WearTpsEquipRsp);

        this.setData(
                WearTpsEquipRsp.newBuilder()
                        .setRetcode(retcode)
                        .setAvatarGuid(avatarGuid)
                        .addAllEquipGuidList(equipGuids));
    }
}
