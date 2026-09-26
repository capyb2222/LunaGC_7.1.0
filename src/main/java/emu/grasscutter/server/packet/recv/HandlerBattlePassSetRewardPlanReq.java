package emu.grasscutter.server.packet.recv;

import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketBattlePassCurScheduleUpdateNotify;
import emu.grasscutter.server.packet.send.PacketEmptyRsp;

@Opcodes(PacketOpcodes.BattlePassSetRewardPlanReq)
public class HandlerBattlePassSetRewardPlanReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        int plan = firstVarint(payload);
        if (plan != 0) player.getBattlePassManager().setRewardPlan(plan);

        session.send(new PacketEmptyRsp(PacketOpcodes.BattlePassSetRewardPlanRsp, header));
        session.send(new PacketBattlePassCurScheduleUpdateNotify(player));
    }

    private static int firstVarint(byte[] payload) {
        try {
            for (var field : UnknownFieldSet.parseFrom(payload).asMap().values()) {
                for (long value : field.getVarintList()) {
                    if (value > 0 && value < Integer.MAX_VALUE) return (int) value;
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }
}
