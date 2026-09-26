package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_ADD_QUEST_PROGRESS;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import lombok.val;

@QuestValueContent(QUEST_CONTENT_ADD_QUEST_PROGRESS)
public class ContentAddQuestProgress extends BaseContent {

    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition, String paramStr, int... params) {
        val progressId = condition.getParam()[0];
        val currentCount =
                quest.getOwner().getPlayerProgress().getCurrentProgress(String.valueOf(progressId));

        var targetAmount = condition.getCount();
        if (targetAmount == 0) {
            targetAmount = 1;
        }
        return currentCount >= targetAmount;
    }
}
