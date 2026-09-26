package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.DungeonEntryBlockReasonOuterClass.DungeonEntryBlockReason;
import emu.grasscutter.net.proto.DungeonEntryCondOuterClass.DungeonEntryCond;
import emu.grasscutter.net.proto.GetDungeonEntryExploreConditionRspOuterClass.GetDungeonEntryExploreConditionRsp;

public class PacketGetDungeonEntryExploreConditionRsp extends BasePacket {
    public PacketGetDungeonEntryExploreConditionRsp(int dungeonId) {
        super(PacketOpcodes.GetDungeonEntryExploreConditionRsp);

        var data =
                GameData.getDungeonEntryDataMap().values().stream()
                        .filter(d -> d.getId() == dungeonId)
                        .toList()
                        .get(0);

        var level = data.getLevelCondition();
        var quest = data.getQuestCondition();
        var proto =
                GetDungeonEntryExploreConditionRsp.newBuilder()
                        .setRetcode(0)
                        .setDungeonEntryCond(
                                DungeonEntryCond.newBuilder()
                                        .setCondReason(DungeonEntryBlockReason.DUNGEON_ENTRY_REASON_LEVEL)
                                        .setParam1(level))
                        .build();

        this.setData(proto);
    }
}
