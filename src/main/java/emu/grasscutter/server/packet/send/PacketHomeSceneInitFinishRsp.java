package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;

public class PacketHomeSceneInitFinishRsp extends BasePacket {

    public PacketHomeSceneInitFinishRsp() {
        super(PacketOpcodes.HomeSceneInitFinishRsp);
    }
}
