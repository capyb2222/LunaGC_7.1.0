package emu.grasscutter.game.dungeons.challenge.factory;

import static emu.grasscutter.game.dungeons.challenge.enums.ChallengeType.CHALLENGE_KILL_COUNT_GUARD_HP;

import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.dungeons.challenge.enums.ChallengeType;
import emu.grasscutter.game.dungeons.challenge.trigger.*;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.data.SceneGroup;
import java.util.List;
import lombok.val;

public class KillAndGuardChallengeFactoryHandler implements ChallengeFactoryHandler {
    @Override
    public boolean isThisType(ChallengeType challengeType) {
        return challengeType == CHALLENGE_KILL_COUNT_GUARD_HP;
    }

    @Override
    public WorldChallenge build(
            int challengeIndex,
            int challengeId,
            int groupId,
            int monstersToKill,
            int gadgetCFGId,
            int unused,
            Scene scene,
            SceneGroup group) {
        val realGroup = scene.getScriptManager().getGroupById(groupId);
        return new WorldChallenge(
                scene,
                realGroup,
                challengeId,
                challengeIndex,
                List.of(monstersToKill, gadgetCFGId),
                0,
                monstersToKill,
                List.of(new KillMonsterCountTrigger(), new GuardTrigger(gadgetCFGId)));
    }
}
