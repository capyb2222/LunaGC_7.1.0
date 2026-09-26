package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.*;

@Opcodes(PacketOpcodes.SetWidgetSlotReq)
public class HandlerSetWidgetSlotReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        SetWidgetSlotReqOuterClass.SetWidgetSlotReq req =
                SetWidgetSlotReqOuterClass.SetWidgetSlotReq.parseFrom(payload);

        Player player = session.getPlayer();
        player.setWidgetId(req.getMaterialId());

        session.send(
                new PacketWidgetSlotChangeNotify(
                        WidgetSlotOpOuterClass.WidgetSlotOp.WidgetSlotOp_DETACH));

        if (req.getOp() == WidgetSlotOpOuterClass.WidgetSlotOp.WidgetSlotOp_ATTACH) {
            session.send(new PacketWidgetSlotChangeNotify(req.getMaterialId()));
        }

        session.send(new PacketSetWidgetSlotRsp(req.getMaterialId()));
    }
}
