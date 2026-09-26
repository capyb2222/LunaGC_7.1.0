package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import lombok.val;

import static emu.grasscutter.utils.Utils.random;

@QuestValueExec(QuestExec.QUEST_EXEC_REFRESH_GROUP_SUITE_RANDOM)
public class ExecRefreshGroupSuiteRandom extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        if (paramStr == null || paramStr.length < 2) {
            return false;
        }

        val sceneId = Integer.parseInt(paramStr[0]);
        val entries = paramStr[1].split(";");
        val scene = quest.getOwner().getWorld().getSceneById(sceneId);
        if (scene == null) {
            return false;
        }

        val scriptManager = scene.getScriptManager();
        boolean result = true;

        for (var entry : entries) {
            val entryArray = entry.split(",");
            val groupId = Integer.parseInt(entryArray[0]);

            if (entryArray.length > 1) {
                val randomSuiteIndex = random.nextInt(1, entryArray.length);
                val suiteId = Integer.parseInt(entryArray[randomSuiteIndex]);

                if (!scriptManager.refreshGroupSuite(groupId, suiteId, quest)) {
                    result = false;
                }
            }
        }
        return result;
    }
}