package emu.grasscutter.game.dungeons.dungeon_results;

import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.dungeons.DungeonEndStats;
import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.tower.TowerManager;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.TowerLevelEndNotifyOuterClass.TowerLevelEndNotify;

public class TowerResult extends BaseDungeonResult {
    WorldChallenge challenge;
    boolean canJump;
    boolean hasNextLevel;
    int nextFloorId;
    int currentStars;

    public TowerResult(
            DungeonData dungeonData,
            DungeonEndStats dungeonStats,
            TowerManager towerManager,
            WorldChallenge challenge,
            int currentStars) {
        super(dungeonData, dungeonStats);
        this.challenge = challenge;
        this.canJump = towerManager.hasNextFloor();
        this.hasNextLevel = towerManager.hasNextLevel();
        this.nextFloorId = hasNextLevel ? 0 : towerManager.getNextFloorId();
        this.currentStars = currentStars;
    }

    private static final int CONTINUE_STATE_CAN_NOT_CONTINUE = 0;
    private static final int CONTINUE_STATE_CAN_ENTER_NEXT_LEVEL = 1;
    private static final int CONTINUE_STATE_CAN_ENTER_NEXT_FLOOR = 2;

    @Override
    protected void onProto(DungeonSettleNotifyOuterClass.DungeonSettleNotify.Builder builder) {
        var continueStatus = CONTINUE_STATE_CAN_NOT_CONTINUE;
        if (challenge != null && challenge.isSuccess()) {
            if (hasNextLevel) {
                continueStatus = CONTINUE_STATE_CAN_ENTER_NEXT_LEVEL;
            } else if (canJump) {
                continueStatus = CONTINUE_STATE_CAN_ENTER_NEXT_FLOOR;
            }
        }

        var towerLevelEndNotify =
                TowerLevelEndNotify.newBuilder()
                        .setIsSuccess(challenge.isSuccess())
                        .setContinueState(continueStatus)
                        .addRewardItemList(
                                ItemParamOuterClass.ItemParam.newBuilder().setItemId(201).setCount(1000));

        for (int i = 1; i <= currentStars; i++) {
            towerLevelEndNotify.addFinishedStarCondList(i);
        }

        if (nextFloorId > 0 && canJump) {
            towerLevelEndNotify.setNextFloorId(nextFloorId);
        }
        builder.setTowerLevelEndNotify(towerLevelEndNotify.build());
    }
}
