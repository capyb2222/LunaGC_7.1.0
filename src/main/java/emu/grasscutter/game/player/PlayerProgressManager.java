package emu.grasscutter.game.player;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;
import static emu.grasscutter.scripts.constants.EventType.EVENT_UNLOCK_TRANS_POINT;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.data.excels.OpenStateData;
import emu.grasscutter.data.excels.OpenStateData.OpenStateCondType;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.quest.PrologueIntro;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.server.packet.send.*;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class PlayerProgressManager extends BasePlayerDataManager {
    public static final Set<Integer> BLACKLIST_OPEN_STATES = Set.of();

    public static final Set<Integer> IGNORED_OPEN_STATES =
            Set.of(
                    1404
                    );

    public static final Set<Integer> QUEST_GATED_7_0_OPEN_STATES =
            Set.of(6701, 6702, 6706, 7011, 7014, 7015, 7016, 7021, 7025, 7055, 7056, 7059);

    public static final Set<Integer> DEFAULT_OPEN_STATES =
            GameData.getOpenStateList().stream()
                    .filter(
                            s ->
                                    s.isDefaultState() && !s.isAllowClientOpen()
                                            || ((s.getCond().size() == 1)
                                                    && (s.getCond().get(0).getCondType()
                                                            == OpenStateCondType.OPEN_STATE_COND_PLAYER_LEVEL)
                                                    && (s.getCond().get(0).getParam() == 1))
                                            || (s.getCond().stream()
                                                    .anyMatch(
                                                            c ->
                                                                    c.getCondType() == OpenStateCondType.OPEN_STATE_OFFERING_LEVEL
                                                                            || c.getCondType()
                                                                                    == OpenStateCondType.OPEN_STATE_CITY_REPUTATION_LEVEL))
                                            || QUEST_GATED_7_0_OPEN_STATES.contains(s.getId())
                                            || s.getId() == 1)
                    .map(OpenStateData::getId)
                    .filter(s -> !BLACKLIST_OPEN_STATES.contains(s))
                    .filter(
                            s ->
                                    !IGNORED_OPEN_STATES.contains(s))
                    .collect(Collectors.toSet());

    public PlayerProgressManager(Player player) {
        super(player);
    }

    public void onPlayerLogin() {
        this.tryUnlockOpenStates(false);

        player.getSession().send(new PacketOpenStateUpdateNotify(this.player));

        this.addStatueQuestsOnLogin();

        if (!GAME_OPTIONS.questing.enabled && !PrologueIntro.isActive(this.player)) {
            if (!PrologueIntro.wentThrough(this.player)) this.player.getUnlockedScenePoints(3).add(7);
            this.player.getUnlockedSceneAreas(3).add(1);
        }
    }

    public int getOpenState(int openState) {
        return this.player.getOpenStates().getOrDefault(openState, 0);
    }

    private void setOpenState(int openState, int value, boolean sendNotify) {
        int previousValue = this.player.getOpenStates().getOrDefault(openState, -1 );

        if (value != previousValue) {
            this.player.getOpenStates().put(openState, value);

            this.player
                    .getQuestManager()
                    .queueEvent(QuestCond.QUEST_COND_OPEN_STATE_EQUAL, openState, value);

            if (sendNotify) {
                player.getSession().send(new PacketOpenStateChangeNotify(openState, value));
            }
        }
    }

    private void setOpenState(int openState, int value) {
        this.setOpenState(openState, value, true);
    }

    private boolean areConditionsMet(OpenStateData openState) {
        for (var condition : openState.getCond()) {
            switch (condition.getCondType()) {
                case OPEN_STATE_COND_PLAYER_LEVEL -> {
                    if (this.player.getLevel() < condition.getParam()) {
                        return false;
                    }
                }
                case OPEN_STATE_COND_QUEST -> {
                    if (this.questsGateFeatures()) {
                        var quest = this.player.getQuestManager().getQuestById(condition.getParam());
                        if (quest == null || quest.getState() != QuestState.QUEST_STATE_FINISHED) {
                            return false;
                        }
                    }
                }
                case OPEN_STATE_COND_PARENT_QUEST -> {
                    if (this.questsGateFeatures()) {
                        var mainQuest = this.player.getQuestManager().getMainQuestById(condition.getParam());
                        if (mainQuest == null
                                || mainQuest.getState() != ParentQuestState.PARENT_QUEST_STATE_FINISHED) {
                            return false;
                        }
                    }
                }
                case OPEN_STATE_OFFERING_LEVEL, OPEN_STATE_CITY_REPUTATION_LEVEL -> {}
            }
        }

        return true;
    }

    private boolean questsGateFeatures() {
        return GAME_OPTIONS.questing.enabled || PrologueIntro.isActive(this.player);
    }

    public void setOpenStateFromClient(int openState, int value) {
        OpenStateData data = GameData.getOpenStateDataMap().get(openState);
        if (data == null) {
            this.player.sendPacket(new PacketSetOpenStateRsp(Retcode.RET_FAIL));
            return;
        }

        if (!data.isAllowClientOpen() || !this.areConditionsMet(data)) {
            this.player.sendPacket(new PacketSetOpenStateRsp(Retcode.RET_FAIL));
            return;
        }

        this.setOpenState(openState, value);
        this.player.sendPacket(new PacketSetOpenStateRsp(openState, value));
    }

    public void forceSetOpenState(int openState, int value) {
        this.setOpenState(openState, value);
    }

    public void tryUnlockOpenStates(boolean sendNotify) {
        var lockedStates =
                GameData.getOpenStateList().stream()
                        .filter(s -> this.player.getOpenStates().getOrDefault(s.getId(), 0) == 0)
                        .toList();

        for (var state : lockedStates) {
            if (!state.isAllowClientOpen()
                    && this.areConditionsMet(state)
                    && !BLACKLIST_OPEN_STATES.contains(state.getId())
                    && !IGNORED_OPEN_STATES.contains(state.getId())) {
                this.setOpenState(state.getId(), 1, sendNotify);
            }
        }
    }

    public void markClientOpenStatesSeen() {
        for (var state : GameData.getOpenStateList()) {
            if (state.isAllowClientOpen() && this.getOpenState(state.getId()) == 0) {
                this.setOpenState(state.getId(), 1, false);
            }
        }
    }

    public void tryUnlockOpenStates() {
        this.tryUnlockOpenStates(true);
    }

    private void addStatueQuestsOnLogin() {
        var statueMainQuest = GameData.getMainQuestDataMap().get(303);
        var statueSubQuests = statueMainQuest.getSubQuests();

        var statueGameMainQuest = this.player.getQuestManager().getMainQuestById(303);
        if (statueGameMainQuest == null) {
            this.player.getQuestManager().addQuest(30302);
            statueGameMainQuest = this.player.getQuestManager().getMainQuestById(303);
        }

        for (var subData : statueSubQuests) {
            var subGameQuest = statueGameMainQuest.getChildQuestById(subData.getSubId());
            if (subGameQuest != null && subGameQuest.getState() == QuestState.QUEST_STATE_UNSTARTED) {
                this.player.getQuestManager().addQuest(subData.getSubId());
            }
        }
    }

    public boolean unlockTransPoint(int sceneId, int pointId, boolean isStatue) {
        ScenePointEntry scenePointEntry = GameData.getScenePointEntryById(sceneId, pointId);

        if (scenePointEntry == null || this.player.getUnlockedScenePoints(sceneId).contains(pointId)) {
            return false;
        }

        this.player.getUnlockedScenePoints(sceneId).add(pointId);

        this.player.getInventory().addItem(201, 5, ActionReason.UnlockPointReward);
        this.player.getInventory().addItem(102, isStatue ? 50 : 10, ActionReason.UnlockPointReward);

        this.player
                .getQuestManager()
                .queueEvent(QuestContent.QUEST_CONTENT_UNLOCK_TRANS_POINT, sceneId, pointId);
        this.player
                .getScene()
                .getScriptManager()
                .callEvent(new ScriptArgs(0, EVENT_UNLOCK_TRANS_POINT, sceneId, pointId));

        this.player.sendPacket(new PacketScenePointUnlockNotify(sceneId, pointId));
        return true;
    }

    public void unlockSceneArea(int sceneId, int areaId) {
        this.player.getUnlockedSceneAreas(sceneId).add(areaId);

        this.player.sendPacket(new PacketSceneAreaUnlockNotify(sceneId, areaId));
    }

    public void addReplaceCostumes() {
        var currentPlayerCostumes = player.getCostumeList();
        GameData.getAvatarReplaceCostumeDataMap()
                .keySet()
                .forEach(
                        costumeId -> {
                            if (GameData.getAvatarCostumeDataMap().get(costumeId) == null
                                    || currentPlayerCostumes.contains(costumeId)) {
                                return;
                            }
                            this.player.addCostume(costumeId);
                        });
    }

    public void addQuestProgress(int id, int count) {
        var newCount = player.getPlayerProgress().addToCurrentProgress(String.valueOf(id), count);
        player.save();
        player
                .getQuestManager()
                .queueEvent(QuestContent.QUEST_CONTENT_ADD_QUEST_PROGRESS, id, newCount);
    }

    public void addItemObtainedHistory(int id, int count) {
        var newCount = player.getPlayerProgress().addToItemHistory(id, count);
        player.save();
        player.getQuestManager().queueEvent(QuestCond.QUEST_COND_HISTORY_GOT_ANY_ITEM, id, newCount);
    }

    public void addSceneTag(int sceneId, int sceneTagId) {
        player.getSceneTags().computeIfAbsent(sceneId, k -> new HashSet<>()).add(sceneTagId);
        player.sendPacket(new PacketPlayerWorldSceneInfoListNotify(player));
    }

    public void delSceneTag(int sceneId, int sceneTagId) {
        if (player.getSceneTags().get(sceneId) == null) {
            return;
        }
        player.getSceneTags().get(sceneId).remove(sceneTagId);
        player.sendPacket(new PacketPlayerWorldSceneInfoListNotify(player));
    }

    public boolean checkSceneTag(int sceneId, int sceneTagId) {
        return player.getSceneTags().get(sceneId).contains(sceneTagId);
    }
}
