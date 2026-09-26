package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;

public class PacketEmptyRsp extends BasePacket {

    public PacketEmptyRsp(int opcode, byte[] header) {
        super(opcode, clientSequence(header));
    }

    private static int clientSequence(byte[] header) {
        try {
            return PacketHead.parseFrom(header).getClientSequenceId();
        } catch (Exception e) {
            return 0;
        }
    }
}
