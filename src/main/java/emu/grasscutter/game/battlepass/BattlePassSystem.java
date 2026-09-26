package emu.grasscutter.game.battlepass;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.BattlePassMissionData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.server.event.player.PlayerFinishBattlePassMission;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.send.PacketBattlePassMissionUpdateNotify;
import java.util.*;

public class BattlePassSystem extends BaseGameSystem {
    private final Map<WatcherTriggerType, List<BattlePassMissionData>> cachedTriggers;

    public BattlePassSystem(GameServer server) {
        super(server);

        this.cachedTriggers = new HashMap<>();

        for (BattlePassMissionData missionData : GameData.getBattlePassMissionDataMap().values()) {
            if (missionData.isValidRefreshType()) {
                List<BattlePassMissionData> triggerList =
                        getTriggers().computeIfAbsent(missionData.getTriggerType(), e -> new ArrayList<>());
                triggerList.add(missionData);
            }
        }
    }

    public GameServer getServer() {
        return server;
    }

    private Map<WatcherTriggerType, List<BattlePassMissionData>> getTriggers() {
        return cachedTriggers;
    }

    public void triggerMission(Player player, WatcherTriggerType triggerType) {
        triggerMission(player, triggerType, 0, 1);
    }

    public void triggerMission(
            Player player, WatcherTriggerType triggerType, int param, int progress) {
        List<BattlePassMissionData> triggerList = getTriggers().get(triggerType);

        if (triggerList == null || triggerList.isEmpty()) return;

        for (BattlePassMissionData data : triggerList) {
            if (param != 0) {
                if (!data.getMainParams().contains(param)) {
                    continue;
                }
            }

            BattlePassMission mission = player.getBattlePassManager().loadMissionById(data.getId());

            if (mission.isFinshed()) continue;

            mission.addProgress(progress, data.getProgress());

            if (mission.getProgress() >= data.getProgress()) {
                mission.setStatus(BattlePassMissionStatus.MISSION_STATUS_FINISHED);

                new PlayerFinishBattlePassMission(player, mission).call();
            }

            player.getBattlePassManager().save();

            player.sendPacket(new PacketBattlePassMissionUpdateNotify(mission));
        }
    }
}
