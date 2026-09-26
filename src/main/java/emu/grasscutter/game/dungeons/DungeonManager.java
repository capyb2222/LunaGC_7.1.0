package emu.grasscutter.game.dungeons;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.dungeon.*;
import emu.grasscutter.game.activity.trialavatar.TrialAvatarActivityHandler;
import emu.grasscutter.game.dungeons.dungeon_results.BaseDungeonResult;
import emu.grasscutter.game.dungeons.enums.DungeonPassConditionType;
import emu.grasscutter.game.dungeons.fallback.MissingDomainFallbackManager;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.server.event.player.PlayerFinishDungeonEvent;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;
import java.util.stream.*;
import javax.annotation.Nullable;
import lombok.*;

public final class DungeonManager {
    @Getter private final Scene scene;
    @Getter private final DungeonData dungeonData;
    @Getter private final DungeonPassConfigData passConfigData;

    @Getter private final int[] finishedConditions;
    private final IntSet rewardedPlayers = new IntOpenHashSet();
    private final Set<Integer> activeDungeonWayPoints = new HashSet<>();
    private boolean ended = false;
    private int newestWayPoint = 0;
    @Getter private int startSceneTime = 0;
    @Setter @Getter private boolean towerDungeon = false;

    DungeonTrialTeam trialTeam = null;

    public DungeonManager(@NonNull Scene scene, @NonNull DungeonData dungeonData) {
        this.scene = scene;
        this.dungeonData = dungeonData;
        if (dungeonData.getPassCond() == 0) {
            this.passConfigData = new DungeonPassConfigData();
            this.passConfigData.setConds(new ArrayList<>());
        } else {
            this.passConfigData = GameData.getDungeonPassConfigDataMap().get(dungeonData.getPassCond());
        }
        this.finishedConditions = new int[this.passConfigData.getConds().size()];
    }

    public void triggerEvent(DungeonPassConditionType conditionType, int... params) {
        if (ended) {
            return;
        }
        for (int i = 0; i < passConfigData.getConds().size(); i++) {
            var cond = passConfigData.getConds().get(i);
            if (conditionType == cond.getCondType()) {
                if (getScene().getWorld().getServer().getDungeonSystem().triggerCondition(cond, params)) {
                    finishedConditions[i] = 1;
                }
            }
        }

        if (isFinishedSuccessfully()) {
            ended = true;
            finishDungeon();
        }
    }

    public boolean isFinishedSuccessfully() {
        if (passConfigData.getConds() == null) return false;
        return LogicType.calculate(passConfigData.getLogicType(), finishedConditions);
    }

    public int getLevelForMonster(int id) {
        if (isTowerDungeon() && !scene.getPlayers().isEmpty()) {
            return scene.getPlayers().get(0).getTowerManager().getCurrentMonsterLevel();
        } else {
            return dungeonData.getShowLevel();
        }
    }

    public boolean activateRespawnPoint(int pointId) {
        val respawnPoint = GameData.getScenePointEntryById(scene.getId(), pointId);

        if (respawnPoint == null) {
            Grasscutter.getLogger().warn("trying to activate unknown respawn point {}", pointId);
            return false;
        }

        scene.broadcastPacket(
                new PacketDungeonWayPointNotify(
                        activeDungeonWayPoints.add(pointId), activeDungeonWayPoints));
        newestWayPoint = pointId;

        Grasscutter.getLogger().debug("[unimplemented respawn] activated respawn point {}", pointId);
        return true;
    }

    @Nullable public Position getRespawnLocation() {
        if (newestWayPoint == 0) {
            return null;
        }
        var pointData = GameData.getScenePointEntryById(scene.getId(), newestWayPoint).getPointData();
        return pointData.getTranPos() != null ? pointData.getTranPos() : pointData.getPos();
    }

    public Position getRespawnRotation() {
        if (newestWayPoint == 0) {
            return null;
        }
        val pointData = GameData.getScenePointEntryById(scene.getId(), newestWayPoint).getPointData();
        return pointData.getRot() != null ? pointData.getRot() : null;
    }

    public boolean getStatueDrops(Player player, boolean useCondensed, int groupId) {
        if (!isFinishedSuccessfully()
                || dungeonData.getRewardPreviewData() == null
                || dungeonData.getRewardPreviewData().getPreviewItems().length == 0) {
            return false;
        }

        if (rewardedPlayers.contains(player.getUid())) {
            return false;
        }

        if (!handleCost(player, useCondensed)) {
            return false;
        }

        List<GameItem> rewards;
        if (usesLegacyLostValleyDrops(dungeonData.getId())) {
            rewards = new ArrayList<>(this.rollRewards(useCondensed));
        } else {
            int statueDropId = dungeonData.getStatueDrop();

            int fallbackStatueDropId =
                    MissingDomainFallbackManager.getStatueDropOverride(dungeonData.getId());
            if (fallbackStatueDropId != 0) {
                statueDropId = fallbackStatueDropId;
            }

            rewards =
                    player.getServer().getDropSystem().handleDungeonRewardDrop(statueDropId, useCondensed);

            if (rewards.isEmpty()) {
                Grasscutter.getLogger().debug("dungeon drop failed for {}", dungeonData.getId());
                rewards = new ArrayList<>(this.rollRewards(useCondensed));
            }
        }
        player.getInventory().addItems(rewards, ActionReason.DungeonStatueDrop);
        player.sendPacket(new PacketGadgetAutoPickDropInfoNotify(rewards));

        rewardedPlayers.add(player.getUid());

        scene.getScriptManager().callEvent(new ScriptArgs(groupId, EventType.EVENT_DUNGEON_REWARD_GET));
        return true;
    }

    private static boolean usesLegacyLostValleyDrops(int dungeonId) {
        return switch (dungeonId) {
            case 5125, 5126, 5127, 5128 -> true;
            default -> false;
        };
    }

    public boolean handleCost(Player player, boolean useCondensed) {
        int resinCost = dungeonData.getStatueCostCount() != 0 ? dungeonData.getStatueCostCount() : 20;
        if (resinCost == 0) {
            return true;
        }
        if (useCondensed) {
            if (resinCost != 20) {
                return false;
            }

            return player.getResinManager().useCondensedResin(1);
        } else if (dungeonData.getStatueCostID() == 106) {
            return player.getResinManager().useResin(resinCost);
        }
        return true;
    }

    private List<GameItem> rollRewards(boolean useCondensed) {
        List<GameItem> rewards = new ArrayList<>();
        int dungeonId = this.dungeonData.getId();
        if (GameData.getDungeonDropDataMap().containsKey(dungeonId)) {
            List<DungeonDropEntry> dropEntries = GameData.getDungeonDropDataMap().get(dungeonId);

            for (var entry : dropEntries) {
                int start = entry.getCounts().get(0);
                int end = entry.getCounts().get(entry.getCounts().size() - 1);
                var candidateAmounts = IntStream.range(start, end + 1).boxed().collect(Collectors.toList());

                int amount = Utils.drawRandomListElement(candidateAmounts, entry.getProbabilities());

                if (useCondensed) {
                    amount += Utils.drawRandomListElement(candidateAmounts, entry.getProbabilities());
                }

                if (entry.isMpDouble() && this.getScene().getPlayerCount() > 1) {
                    amount *= 2;
                }

                if (entry.getItems().size() == 1) {
                    rewards.add(new GameItem(entry.getItems().get(0), amount));
                } else {
                    for (int i = 0; i < amount; i++) {
                        int itemId =
                                Utils.drawRandomListElement(entry.getItems(), entry.getItemProbabilities());
                        rewards.add(new GameItem(itemId, 1));
                    }
                }
            }
        }
        else {
            Grasscutter.getLogger()
                    .info("No drop data found or dungeon {}, falling back to preview data ...", dungeonId);
            for (ItemParamData param : dungeonData.getRewardPreviewData().getPreviewItems()) {
                rewards.add(new GameItem(param.getId(), Math.max(param.getCount(), 1)));
            }
        }

        return rewards;
    }

    public void applyTrialTeam(Player player) {
        if (getDungeonData() == null) return;

        switch (getDungeonData().getType()) {
            case DUNGEON_ACTIVITY -> {
                switch (getDungeonData().getPlayType()) {
                    case DUNGEON_PLAY_TYPE_TRIAL_AVATAR -> {
                        val activityHandler =
                                player
                                        .getActivityManager()
                                        .getActivityHandlerAs(
                                                ActivityType.NEW_ACTIVITY_TRIAL_AVATAR, TrialAvatarActivityHandler.class);
                        activityHandler.ifPresent(
                                trialAvatarActivityHandler ->
                                        this.trialTeam = trialAvatarActivityHandler.getTrialAvatarDungeonTeam());
                    }
                }
            }
            case DUNGEON_ELEMENT_CHALLENGE -> {}
        }

        if (this.trialTeam != null) {
            player.getTeamManager().addTrialAvatars(trialTeam.trialAvatarIds);
        }
    }

    public void unsetTrialTeam(Player player) {
        if (this.trialTeam == null) return;

        player.getTeamManager().removeTrialAvatar();
        this.trialTeam = null;
    }

    public void startDungeon() {
        this.startSceneTime = scene.getSceneTimeSeconds();
        scene
                .getPlayers()
                .forEach(
                        p -> {
                            p.getQuestManager()
                                    .queueEvent(QuestContent.QUEST_CONTENT_ENTER_DUNGEON, dungeonData.getId());
                            applyTrialTeam(p);
                        });
    }

    public void finishDungeon() {
        this.notifyEndDungeon(true);
        this.endDungeon(BaseDungeonResult.DungeonEndReason.COMPLETED);

        new PlayerFinishDungeonEvent(this.getScene().getPlayers(), this.getScene(), this).call();

        if (this.dungeonData.getPassJumpDungeon() != 0) {
            for (var player : this.getScene().getPlayers()) {
                player
                        .getServer()
                        .getDungeonSystem()
                        .enterDungeon(player, 0, this.dungeonData.getPassJumpDungeon(), false);
            }
        }
    }

    public void quitDungeon() {
        this.notifyEndDungeon(false);
        this.endDungeon(BaseDungeonResult.DungeonEndReason.QUIT);
    }

    public void failDungeon() {
        this.notifyEndDungeon(false);
        this.endDungeon(BaseDungeonResult.DungeonEndReason.FAILED);
    }

    public void notifyEndDungeon(boolean successfully) {
        scene
                .getPlayers()
                .forEach(
                        p -> {
                            if (successfully) {
                                var dungeonId = this.getDungeonData().getId();
                                p.getPlayerProgress().markDungeonAsComplete(dungeonId);
                            } else {
                                p.getQuestManager()
                                        .queueEvent(QuestContent.QUEST_CONTENT_FAIL_DUNGEON, dungeonData.getId());
                            }

                            if (dungeonData.getType().isCountsToBattlepass() && successfully) {
                                p.getBattlePassManager().triggerMission(WatcherTriggerType.TRIGGER_FINISH_DUNGEON);
                            }
                        });
        var future =
                scene
                        .getScriptManager()
                        .callEvent(new ScriptArgs(0, EventType.EVENT_DUNGEON_SETTLE, successfully ? 1 : 0));
        try {
            future.get();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void endDungeon(BaseDungeonResult.DungeonEndReason endReason) {
        if (scene.getDungeonSettleListeners() != null) {
            scene.getDungeonSettleListeners().forEach(o -> o.onDungeonSettle(this, endReason));
        }
        if (isTowerDungeon()) {
            scene.getPlayers().get(0).getTowerManager().onEnd();
        }
        ended = true;
    }

    public void restartDungeon() {
        this.scene.setKilledMonsterCount(0);
        this.rewardedPlayers.clear();
        Arrays.fill(finishedConditions, 0);
        this.ended = false;
        this.activeDungeonWayPoints.clear();
    }

    public void cleanUpScene() {
        this.scene.setDungeonManager(null);
        this.scene.setKilledMonsterCount(0);
    }
}
