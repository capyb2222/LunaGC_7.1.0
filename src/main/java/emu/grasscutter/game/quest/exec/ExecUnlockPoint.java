package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

@QuestValueExec(QuestExec.QUEST_EXEC_UNLOCK_POINT)
public class ExecUnlockPoint extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        int sceneId = Integer.parseInt(paramStr[0]);
        int pointId = Integer.parseInt(paramStr[1]);

        boolean isStatue = quest.getMainQuestId() == 303 || quest.getMainQuestId() == 352;

        return quest.getOwner().getProgressManager().unlockTransPoint(sceneId, pointId, isStatue);
    }
}
