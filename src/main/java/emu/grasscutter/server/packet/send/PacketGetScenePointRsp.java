package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetScenePointRspOuterClass.GetScenePointRsp;
import java.util.Collection;
import java.util.TreeSet;

public class PacketGetScenePointRsp extends BasePacket {

    public PacketGetScenePointRsp(Player player, int sceneId) {
        super(PacketOpcodes.GetScenePointRsp);

        GetScenePointRsp.Builder p = GetScenePointRsp.newBuilder().setSceneId(sceneId);

        var unlockedPoints = player.getUnlockedScenePoints(sceneId);
        boolean intro =
                emu.grasscutter.game.quest.PrologueIntro.isActive(player)
                        || (sceneId == 3 && emu.grasscutter.game.quest.PrologueIntro.wentThrough(player));
        if (!intro && (GameData.getScenePointIdList().size() == 0 || unlockedPoints.isEmpty())) {
            for (int i = 1; i < 1000; i++) {
                p.addUnlockedPointList(i);
                p.addUnhidePointList(i);
            }
        } else {
            p.addAllUnlockedPointList(unlockedPoints);
            p.addAllUnhidePointList(unlockedPoints);
        }

        p.addAllUnlockAreaList(areas(player, sceneId));

        this.setData(p);
    }

    private static Collection<Integer> areas(Player player, int sceneId) {
        var unlocked = player.getUnlockedSceneAreas(sceneId);
        if (!unlocked.isEmpty() || emu.grasscutter.game.quest.PrologueIntro.isActive(player)) return unlocked;
        var all = new TreeSet<Integer>();
        for (var area : GameData.getWorldAreaDataMap().values()) {
            if (area.getSceneId() == sceneId && area.getChildArea() == 0) all.add(area.getParentArea());
        }
        return all;
    }
}
