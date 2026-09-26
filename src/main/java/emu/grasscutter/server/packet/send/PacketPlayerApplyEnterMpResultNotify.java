package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerApplyEnterMpResultNotifyOuterClass;
import emu.grasscutter.net.proto.PlayerApplyEnterMpResultNotifyOuterClass.PlayerApplyEnterMpResultNotify;
import emu.grasscutter.net.proto.ReasonOuterClass;

public class PacketPlayerApplyEnterMpResultNotify extends BasePacket {

    public PacketPlayerApplyEnterMpResultNotify(
            Player target,
            boolean isAgreed,
            ReasonOuterClass.Reason reason) {
        super(PacketOpcodes.PlayerApplyEnterMpResultNotify);

        PlayerApplyEnterMpResultNotify proto =
                PlayerApplyEnterMpResultNotify.newBuilder()
                        .setTargetUid(target.getUid())
                        .setTargetNickname(target.getNickname())
                        .setIsAgreed(isAgreed)
                        .setReasonValue(reason.getNumber())
                        .build();

        this.setData(proto);
    }

    public PacketPlayerApplyEnterMpResultNotify(
            int targetId,
            String targetName,
            boolean isAgreed,
            ReasonOuterClass.Reason reason) {
        super(PacketOpcodes.PlayerApplyEnterMpResultNotify);

        PlayerApplyEnterMpResultNotify proto =
                PlayerApplyEnterMpResultNotify.newBuilder()
                        .setTargetUid(targetId)
                        .setTargetNickname(targetName)
                        .setIsAgreed(isAgreed)
                        .setReasonValue(reason.getNumber())
                        .build();

        this.setData(proto);
    }
}
