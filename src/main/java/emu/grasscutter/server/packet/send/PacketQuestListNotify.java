package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.QuestListNotifyOuterClass.QuestListNotify;

public class PacketQuestListNotify extends BasePacket {

    public PacketQuestListNotify(Player player) {
        super(PacketOpcodes.QuestListNotify, true);

        QuestListNotify.Builder proto = QuestListNotify.newBuilder();

        var questingEnabled = emu.grasscutter.config.Configuration.GAME_OPTIONS.questing.enabled;

        player
                .getQuestManager()
                .forEachQuest(
                        quest -> {
                            var state = quest.getState();
                            if (state == QuestState.QUEST_STATE_UNSTARTED) return;
                            if (!questingEnabled
                                    && state != QuestState.QUEST_STATE_FINISHED
                                    && !emu.grasscutter.game.quest.PrologueIntro.isVisible(player, quest)) return;
                            proto.addQuestList(quest.toProto());
                        });

        this.setData(proto);
    }
}
