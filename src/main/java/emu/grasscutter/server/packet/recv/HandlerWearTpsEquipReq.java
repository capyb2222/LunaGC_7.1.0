package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.tps.TpsWeaponSystem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.WearTpsEquipReqOuterClass.WearTpsEquipReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketWearTpsEquipRsp;

@Opcodes(PacketOpcodes.WearTpsEquipReq)
public class HandlerWearTpsEquipReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = WearTpsEquipReq.parseFrom(payload);
        var player = session.getPlayer();

        int retcode;
        synchronized (player) {
            retcode = TpsWeaponSystem.wear(player, req.getAvatarGuid(), req.getEquipGuidListList());
        }
        session.send(
                new PacketWearTpsEquipRsp(retcode, req.getAvatarGuid(), req.getEquipGuidListList()));
    }
}
