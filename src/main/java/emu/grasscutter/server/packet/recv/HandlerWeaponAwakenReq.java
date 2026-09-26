package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.WeaponAwakenReqOuterClass.WeaponAwakenReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.WeaponAwakenReq)
public class HandlerWeaponAwakenReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        WeaponAwakenReq req = WeaponAwakenReq.parseFrom(payload);

        var items = req.getItemGuidListList();
        for (long item : items) {
            session
                .getServer()
                .getInventorySystem()
                .refineWeapon(session.getPlayer(), req.getTargetWeaponGuid(), item);

        }
    }
}
