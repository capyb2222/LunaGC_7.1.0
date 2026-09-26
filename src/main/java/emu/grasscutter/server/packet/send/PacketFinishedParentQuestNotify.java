package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.GameMainQuest;
import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.FinishedParentQuestNotifyOuterClass.FinishedParentQuestNotify;

public class PacketFinishedParentQuestNotify extends BasePacket {

    public PacketFinishedParentQuestNotify(Player player) {
        super(PacketOpcodes.FinishedParentQuestNotify, true);

        FinishedParentQuestNotify.Builder proto = FinishedParentQuestNotify.newBuilder();

        var questingEnabled = emu.grasscutter.config.Configuration.GAME_OPTIONS.questing.enabled;

        for (GameMainQuest mainQuest : player.getQuestManager().getMainQuests().values()) {
            if (mainQuest.getState() == ParentQuestState.PARENT_QUEST_STATE_CANCELED) continue;
            if (!questingEnabled && mainQuest.getState() != ParentQuestState.PARENT_QUEST_STATE_FINISHED)
                continue;
            proto.addParentQuestList(mainQuest.toProto(false));
        }

        this.setData(proto);
    }
}
