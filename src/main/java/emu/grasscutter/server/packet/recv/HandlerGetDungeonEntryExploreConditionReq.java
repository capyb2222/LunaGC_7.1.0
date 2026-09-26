package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetDungeonEntryExploreConditionReqOuterClass.GetDungeonEntryExploreConditionReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketDungeonEntryToBeExploreNotify;

@Opcodes(PacketOpcodes.GetDungeonEntryExploreConditionReq)
public class HandlerGetDungeonEntryExploreConditionReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = GetDungeonEntryExploreConditionReq.parseFrom(payload);

        session.send(
                new PacketDungeonEntryToBeExploreNotify(
                        req.getDungeonEntryScenePointId(), req.getSceneId(), req.getDungeonEntryConfigId()));
    }
}
