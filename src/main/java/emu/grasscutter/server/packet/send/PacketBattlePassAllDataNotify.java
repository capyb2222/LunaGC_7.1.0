package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.BattlePassAllDataNotifyOuterClass.BattlePassAllDataNotify;

public class PacketBattlePassAllDataNotify extends BasePacket {
    public PacketBattlePassAllDataNotify(Player player) {
        super(PacketOpcodes.BattlePassAllDataNotify);

        var proto = BattlePassAllDataNotify.newBuilder();

        proto.setHaveCurSchedule(true)
                .setIsViewed(player.getBattlePassManager().isViewed())
                .setCurSchedule(player.getBattlePassManager().getScheduleProto())
                .setBattlePassPlan(player.getBattlePassManager().getRewardPlan());

        for (var missionData : GameData.getBattlePassMissionDataMap().values()) {
            if (!missionData.isValidRefreshType()) {
                continue;
            }

            if (player.getBattlePassManager().hasMission(missionData.getId())) {
                proto.addMissionList(
                        player.getBattlePassManager().loadMissionById(missionData.getId()).toProto());
            } else {
                proto.addMissionList(missionData.toProto());
            }
        }

        setData(proto.build());
    }
}
