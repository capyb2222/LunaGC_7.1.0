package emu.grasscutter.game.dungeons.challenge.factory;

import static emu.grasscutter.game.dungeons.challenge.enums.ChallengeType.CHALLENGE_TRIGGER_IN_TIME;

import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.dungeons.challenge.enums.ChallengeType;
import emu.grasscutter.game.dungeons.challenge.trigger.*;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.data.SceneGroup;
import java.util.List;

public class TriggerInTimeChallengeFactoryHandler implements ChallengeFactoryHandler {
    @Override
    public boolean isThisType(ChallengeType challengeType) {
        return challengeType == CHALLENGE_TRIGGER_IN_TIME;
    }

    @Override
    public WorldChallenge build(
            int challengeIndex,
            int challengeId,
            int timeLimit,
            int param4,
            int triggerTag,
            int triggerCount,
            Scene scene,
            SceneGroup group) {
        return new WorldChallenge(
                scene,
                group,
                challengeId,
                challengeIndex,
                List.of(timeLimit, triggerCount),
                timeLimit,
                triggerCount,
                List.of(new InTimeTrigger(), new TriggerGroupTriggerTrigger(Integer.toString(triggerTag))));
    }
}
