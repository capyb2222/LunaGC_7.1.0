package emu.grasscutter.game.quest;

import static emu.grasscutter.GameConstants.DEBUG;
import static emu.grasscutter.config.Configuration.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.GivingRecordOuterClass.GivingRecord;
import emu.grasscutter.server.packet.send.*;
import io.netty.util.concurrent.FastThreadLocalThread;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.*;

public final class QuestManager extends BasePlayerManager {
    @Getter private final Player player;

    @Getter private final Int2ObjectMap<GameMainQuest> mainQuests;
    @Getter private Int2ObjectMap<int[]> acceptProgressLists;
    @Getter private final List<Integer> loggedQuests;

    private long lastHourCheck = 0;
    private long lastDayCheck = 0;

    public static final ExecutorService eventExecutor =
            new ThreadPoolExecutor(
                    1,
                    1,
                    60,
                    TimeUnit.SECONDS,
                    new LinkedBlockingDeque<>(1000),
                    FastThreadLocalThread::new,
                    new ThreadPoolExecutor.CallerRunsPolicy());

    public static long getQuestKey(int mainQuestId) {
        QuestEncryptionKey questEncryptionKey = GameData.getMainQuestEncryptionMap().get(mainQuestId);
        return questEncryptionKey != null ? questEncryptionKey.getEncryptionKey() : 0L;
    }

    public QuestManager(Player player) {
        super(player);

        this.player = player;
        this.mainQuests = new Int2ObjectOpenHashMap<>();
        this.loggedQuests = new ArrayList<>();
        this.acceptProgressLists = new Int2ObjectOpenHashMap<>();

        if (DEBUG) {
            this.loggedQuests.addAll(
                    List.of(
                            31101,
                            35001,
                            30901,
                            30903,
                            30904,
                            46904,

                            35104,

                            30700,
                            30800,
                            47001,
                            47002,
                            47003,
                            47004,
                            2010103,
                            2010144,
                            2012
                            ));
        }
    }

    public boolean isQuestingEnabled() {
        if (!SERVER.game.enableScriptInBigWorld) {
            Grasscutter.getLogger().warn("Questing is disabled without scripts enabled.");
            return false;
        }

        return GAME_OPTIONS.questing.enabled;
    }

    public void addGiveItemAction(int givingId) throws IllegalStateException {
        var progress = this.player.getPlayerProgress();
        var givings = progress.getItemGivings();

        if (!givings.containsKey(givingId)) {
            givings.put(givingId, ItemGiveRecord.resolve(givingId));
            player.save();
        }

        this.sendGivingRecords();
    }

    public void markCompleted(int givingId) {
        var progress = this.player.getPlayerProgress();
        var givings = progress.getItemGivings();

        if (!givings.containsKey(givingId)) {
            throw new IllegalStateException("Giving action " + givingId + " is not active.");
        }

        givings.get(givingId).setFinished(true);
        player.save();

        this.sendGivingRecords();
    }

    public void removeGivingItemAction(int givingId) {
        var progress = this.player.getPlayerProgress();
        var givings = progress.getItemGivings();

        if (!givings.containsKey(givingId)) {
            throw new IllegalStateException("Giving action " + givingId + " is not active.");
        }

        givings.remove(givingId);
        player.save();

        this.sendGivingRecords();
    }

    public Collection<GivingRecord> getGivingRecords() {
        return this.getPlayer().getPlayerProgress().getItemGivings().values().stream()
                .map(ItemGiveRecord::toProto)
                .toList();
    }

    public void startBargain(int bargainId) {
        var progress = this.player.getPlayerProgress();
        var bargains = progress.getBargains();

        if (bargains.containsKey(bargainId)) {
            throw new IllegalStateException("Bargain " + bargainId + " is already active.");
        }

        var bargain = BargainRecord.resolve(bargainId);
        bargains.put(bargainId, bargain);
        this.player.save();

        this.player.sendPacket(new PacketBargainStartNotify(bargain));
    }

    public void stopBargain(int bargainId) {
        var progress = this.player.getPlayerProgress();
        var bargains = progress.getBargains();

        if (!bargains.containsKey(bargainId)) {
            throw new IllegalStateException("Bargain " + bargainId + " is not active.");
        }

        bargains.remove(bargainId);
        this.player.save();

        this.player.sendPacket(new PacketBargainTerminateNotify(bargainId));
    }

    public void sendGivingRecords() {
        this.player.sendPacket(new PacketGivingRecordNotify(this.getGivingRecords()));
    }

    public void onLogin() {
        if (this.isQuestingEnabled()) {
            this.enableQuests();
            this.sendGivingRecords();
        }

        List<GameMainQuest> activeQuests = getActiveMainQuests();
        List<GameQuest> activeSubs = new ArrayList<>(activeQuests.size());
        for (GameMainQuest quest : activeQuests) {
            List<Position> rewindPos = quest.rewind();
            var activeQuest = quest.getActiveQuests();
            if (rewindPos != null) {
                getPlayer().getPosition().set(rewindPos.get(0));
                getPlayer().getRotation().set(rewindPos.get(1));
            }
            if (activeQuest != null && rewindPos != null) {
            }
            quest.checkProgress();
        }

        if (this.player.getActivityManager() != null)
            this.player.getActivityManager().triggerActivityConditions();
    }

    public void onTick() {
        var world = this.getPlayer().getWorld();
        if (world == null) return;

        this.checkTimeVars();
        this.queueEvent(QuestContent.QUEST_CONTENT_GAME_TIME_TICK);
    }

    private void checkTimeVars() {
        val currentDays = player.getWorld().getTotalGameTimeDays();
        val currentHours = player.getWorld().getTotalGameTimeHours();
        boolean checkDays = currentDays != lastDayCheck;
        boolean checkHours = currentHours != lastHourCheck;

        if (!checkDays && !checkHours) {
            return;
        }

        this.lastDayCheck = currentDays;
        this.lastHourCheck = currentHours;

        player
                .getActiveQuestTimers()
                .forEach(
                        mainQuestId -> {
                            if (checkHours) {
                                this.queueEvent(QuestCond.QUEST_COND_TIME_VAR_GT_EQ, mainQuestId);
                                this.queueEvent(QuestContent.QUEST_CONTENT_TIME_VAR_GT_EQ, mainQuestId);
                            }
                            if (checkDays) {
                                this.queueEvent(QuestCond.QUEST_COND_TIME_VAR_PASS_DAY, mainQuestId);
                                this.queueEvent(QuestContent.QUEST_CONTENT_TIME_VAR_PASS_DAY, mainQuestId);
                            }
                        });
    }

    private List<GameMainQuest> addMultMainQuests(Set<Integer> mainQuestIds) {
        List<GameMainQuest> newQuests = new ArrayList<>();
        for (Integer id : mainQuestIds) {
            getMainQuests().put(id.intValue(), new GameMainQuest(this.player, id));
            getMainQuestById(id).save();
            newQuests.add(getMainQuestById(id));
        }
        return newQuests;
    }

    public void enableQuests() {
        GameData.getBeginCondQuestMap()
                .keySet()
                .forEach(
                        x -> {
                            if (x.contains("QUEST_COND_STATE_NOT_EQUAL"))
                                this.triggerEvent(
                                        QuestCond.QUEST_COND_STATE_NOT_EQUAL, null, Integer.parseInt(x.substring(26)));
                            if (x.contains("QUEST_COND_STATE_EQUAL"))
                                this.triggerEvent(
                                        QuestCond.QUEST_COND_STATE_EQUAL, null, Integer.parseInt(x.substring(22)));
                        });
        this.triggerEvent(QuestCond.QUEST_COND_NONE, null, 0);
        this.triggerEvent(QuestCond.QUEST_COND_PLAYER_LEVEL_EQUAL_GREATER, null, 1);
    }

    public int getGlobalVarDefault(int variable) {
        var questGlobalVarData = GameData.getQuestGlobalVarDataMap().get(variable);
        return questGlobalVarData != null ? questGlobalVarData.getDefaultValue() : 0;
    }

    public Integer getQuestGlobalVarValue(Integer variable) {
        return getPlayer()
                .getQuestGlobalVariables()
                .computeIfAbsent(variable, k -> this.getGlobalVarDefault(variable));
    }

    public void setQuestGlobalVarValue(int variable, int setVal) {
        var prevVal = this.getPlayer().getQuestGlobalVariables().put(variable, setVal);
        if (prevVal == null) {
            prevVal = this.getGlobalVarDefault(variable);
        }
        var newVal = this.getQuestGlobalVarValue(variable);

        Grasscutter.getLogger()
                .debug("Changed questGlobalVar {} value from {} to {}", variable, prevVal, newVal);
        this.triggerQuestGlobalVarAction(variable, setVal);
    }

    public void incQuestGlobalVarValue(int variable, int inc) {
        var prevVal = getQuestGlobalVarValue(variable);
        var newVal = getPlayer().getQuestGlobalVariables().compute(variable, (k, v) -> prevVal + inc);

        Grasscutter.getLogger()
                .debug("Incremented questGlobalVar {} value from {} to {}", variable, prevVal, newVal);
        this.triggerQuestGlobalVarAction(variable, newVal);
    }

    public void decQuestGlobalVarValue(int variable, int dec) {
        var prevVal = getQuestGlobalVarValue(variable);
        this.getPlayer().getQuestGlobalVariables().put(variable, prevVal - dec);
        var newVal = getQuestGlobalVarValue(variable);

        Grasscutter.getLogger()
                .debug("Decremented questGlobalVar {} value from {} to {}", variable, prevVal, newVal);
        this.triggerQuestGlobalVarAction(variable, newVal);
    }

    public void triggerQuestGlobalVarAction(int variable, int value) {
        this.queueEvent(QuestCond.QUEST_COND_QUEST_GLOBAL_VAR_EQUAL, variable, value);
        this.queueEvent(QuestCond.QUEST_COND_QUEST_GLOBAL_VAR_GREATER, variable, value);
        this.queueEvent(QuestCond.QUEST_COND_QUEST_GLOBAL_VAR_LESS, variable, value);
        this.getPlayer().sendPacket(new PacketQuestGlobalVarNotify(getPlayer()));
    }

    public GameMainQuest getMainQuestById(int mainQuestId) {
        return getMainQuests().get(mainQuestId);
    }

    public GameMainQuest getMainQuestByTalkId(int talkId) {
        var mainQuestId = GameData.getQuestTalkMap().getOrDefault(talkId, 0);
        return getMainQuestById(mainQuestId);
    }

    public GameQuest getQuestById(int questId) {
        var questConfig = GameData.getQuestDataMap().get(questId);
        if (questConfig == null) {
            return null;
        }

        var mainQuest = getMainQuests().get(questConfig.getMainId());
        if (mainQuest == null) {
            return null;
        }

        return mainQuest.getChildQuests().get(questId);
    }

    public void forEachQuest(Consumer<GameQuest> callback) {
        for (var mainQuest : getMainQuests().values()) {
            for (var quest : mainQuest.getChildQuests().values()) {
                callback.accept(quest);
            }
        }
    }

    public void forEachMainQuest(Consumer<GameMainQuest> callback) {
        for (var mainQuest : getMainQuests().values()) {
            callback.accept(mainQuest);
        }
    }

    public void forEachActiveQuest(Consumer<GameQuest> callback) {
        for (var mainQuest : getMainQuests().values()) {
            for (var quest : mainQuest.getChildQuests().values()) {
                if (quest.getState() != QuestState.QUEST_STATE_FINISHED) {
                    callback.accept(quest);
                }
            }
        }
    }

    public GameMainQuest addMainQuest(QuestData questConfig) {
        var mainQuest = new GameMainQuest(getPlayer(), questConfig.getMainId());
        this.getMainQuests().put(mainQuest.getParentQuestId(), mainQuest);
        this.getPlayer().sendPacket(new PacketFinishedParentQuestUpdateNotify(mainQuest));

        return mainQuest;
    }

    public GameQuest addQuest(int questId) {
        var questConfig = GameData.getQuestDataMap().get(questId);
        if (questConfig == null) {
            return null;
        }

        return this.addQuest(questConfig);
    }

    public GameQuest addQuest(@Nonnull QuestData questConfig) {
        var mainQuest = this.getMainQuestById(questConfig.getMainId());

        if (mainQuest == null) {
            mainQuest = addMainQuest(questConfig);
        }

        var quest = mainQuest.getChildQuestById(questConfig.getSubId());
        quest.start();
        this.checkQuestAlreadyFulfilled(quest);

        return quest;
    }

    public void startMainQuest(int mainQuestId) {
        var mainQuestData = GameData.getMainQuestDataMap().get(mainQuestId);

        if (mainQuestData == null) {
            return;
        }

        Arrays.stream(mainQuestData.getSubQuests())
                .min(Comparator.comparingInt(MainQuestData.SubQuestData::getOrder))
                .map(MainQuestData.SubQuestData::getSubId)
                .ifPresent(this::addQuest);
    }

    public void queueEvent(QuestCond condType, int... params) {
        queueEvent(condType, "", params);
    }

    public void queueEvent(QuestContent condType, int... params) {
        queueEvent(condType, "", params);
    }

    public void queueEvent(QuestContent condType, String paramStr, int... params) {
        eventExecutor.submit(() -> triggerEvent(condType, paramStr, params));
    }

    public void queueEvent(QuestCond condType, String paramStr, int... params) {
        eventExecutor.submit(() -> triggerEvent(condType, paramStr, params));
    }

    public void triggerEvent(QuestCond condType, String paramStr, int... params) {
        Grasscutter.getLogger().trace("Trigger Event {}, {}, {}", condType, paramStr, params);
        var potentialQuests = GameData.getQuestDataByConditions(condType, params[0], paramStr);
        if (potentialQuests == null) {
            return;
        }

        var questSystem = getPlayer().getServer().getQuestSystem();
        var owner = getPlayer();

        potentialQuests.forEach(
                questData -> {
                    if (this.wasSubQuestStarted(questData)) {
                        return;
                    }
                    val acceptCond = questData.getAcceptCond();
                    acceptProgressLists.putIfAbsent(questData.getId(), new int[acceptCond.size()]);
                    for (int i = 0; i < acceptCond.size(); i++) {
                        val condition = acceptCond.get(i);
                        if (condition.getType() == condType) {
                            boolean result =
                                    questSystem.triggerCondition(owner, questData, condition, paramStr, params);
                            acceptProgressLists.get(questData.getId())[i] = result ? 1 : 0;
                        }
                    }

                    boolean shouldAccept =
                            LogicType.calculate(
                                    questData.getAcceptCondComb(), acceptProgressLists.get(questData.getId()));
                    if (this.loggedQuests.contains(questData.getId())) {
                        Grasscutter.getLogger()
                                .debug(
                                        ">>> Quest {} will be {} as a result of event trigger {} ({}, {}).",
                                        questData.getId(),
                                        shouldAccept ? "accepted" : "not accepted",
                                        condType.name(),
                                        paramStr,
                                        Arrays.stream(params)
                                                .mapToObj(String::valueOf)
                                                .collect(Collectors.joining(", ")));
                        for (var i = 0; i < acceptCond.size(); i++) {
                            var condition = acceptCond.get(i);
                            Grasscutter.getLogger()
                                    .debug(
                                            "^ Condition {} has params {} with result {}.",
                                            condition.getType().name(),
                                            Arrays.stream(condition.getParam())
                                                    .filter(value -> value > 0)
                                                    .mapToObj(String::valueOf)
                                                    .collect(Collectors.joining(", ")),
                                            acceptProgressLists.get(questData.getId())[i] == 1 ? "success" : "failure");
                        }
                    }

                    if (shouldAccept) {
                        GameQuest quest = owner.getQuestManager().addQuest(questData);
                        Grasscutter.getLogger().debug("Added quest {}", questData.getSubId());
                    }
                });
    }

    public boolean wasSubQuestStarted(QuestData questData) {
        var quest = getQuestById(questData.getId());
        if (quest == null) return false;

        return quest.state != QuestState.QUEST_STATE_UNSTARTED;
    }

    public void triggerEvent(QuestContent condType, String paramStr, int... params) {
        Grasscutter.getLogger().trace("Trigger Event {}, {}, {}", condType, paramStr, params);

        List<GameMainQuest> checkMainQuests =
                this.getMainQuests().values().stream()
                        .filter(i -> i.getState() != ParentQuestState.PARENT_QUEST_STATE_FINISHED)
                        .toList();
        for (GameMainQuest mainQuest : checkMainQuests) {
            mainQuest.tryFailSubQuests(condType, paramStr, params);
            mainQuest.tryFinishSubQuests(condType, paramStr, params);
        }
    }

    public void checkQuestAlreadyFulfilled(GameQuest quest) {
        Grasscutter.getThreadPool()
                .submit(
                        () -> {
                            for (var condition : quest.getQuestData().getFinishCond()) {
                                switch (condition.getType()) {
                                    case QUEST_CONTENT_OBTAIN_ITEM, QUEST_CONTENT_ITEM_LESS_THAN -> {
                                        var item = getPlayer().getInventory().getItemByGuid(condition.getParam()[0]);
                                        queueEvent(
                                                condition.getType(),
                                                condition.getParam()[0],
                                                item != null ? item.getCount() : 0);
                                    }
                                    case QUEST_CONTENT_UNLOCK_TRANS_POINT -> {
                                        var scenePoints =
                                                getPlayer().getUnlockedScenePoints().get(condition.getParam()[0]);
                                        if (scenePoints != null && scenePoints.contains(condition.getParam()[1])) {
                                            queueEvent(
                                                    condition.getType(), condition.getParam()[0], condition.getParam()[1]);
                                        }
                                    }
                                    case QUEST_CONTENT_UNLOCK_AREA -> {
                                        var sceneAreas =
                                                getPlayer().getUnlockedSceneAreas().get(condition.getParam()[0]);
                                        if (sceneAreas != null && sceneAreas.contains(condition.getParam()[1])) {
                                            queueEvent(
                                                    condition.getType(), condition.getParam()[0], condition.getParam()[1]);
                                        }
                                    }
                                    case QUEST_CONTENT_PLAYER_LEVEL_UP -> queueEvent(
                                            condition.getType(), player.getLevel());
                                }
                            }
                        });
    }

    public List<QuestGroupSuite> getSceneGroupSuite(int sceneId) {
        return getMainQuests().values().stream()
                .filter(i -> i.getState() != ParentQuestState.PARENT_QUEST_STATE_FINISHED)
                .map(GameMainQuest::getQuestGroupSuites)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(i -> i.getScene() == sceneId)
                .toList();
    }

    public void loadFromDatabase() {
        List<GameMainQuest> quests = DatabaseHelper.getAllQuests(getPlayer());

        for (GameMainQuest mainQuest : quests) {
            boolean cancelAdd = false;
            mainQuest.setOwner(this.getPlayer());

            for (GameQuest quest : mainQuest.getChildQuests().values()) {
                QuestData questConfig = GameData.getQuestDataMap().get(quest.getSubQuestId());

                if (questConfig == null) {
                    mainQuest.delete();
                    cancelAdd = true;
                    break;
                }

                quest.setMainQuest(mainQuest);
                quest.setConfig(questConfig);
            }

            if (!cancelAdd) {
                this.getMainQuests().put(mainQuest.getParentQuestId(), mainQuest);
            }
        }
    }

    public List<GameMainQuest> getActiveMainQuests() {
        return getMainQuests().values().stream().filter(p -> !p.isFinished()).toList();
    }

    public List<Integer> questsForDungeon(ScenePointEntry point) {
        var pointId = point.getPointData().getId();
        return this.getActiveMainQuests().stream()
                .map(GameMainQuest::getChildQuests)
                .map(Map::values)
                .map(
                        quests ->
                                quests.stream()
                                        .map(GameQuest::getDungeonIds)
                                        .map(
                                                ids ->
                                                        ids.stream()
                                                                .filter(id -> id.rightInt() == pointId)
                                                                .toList())
                                        .map(
                                                ids ->
                                                        ids.stream()
                                                                .map(IntIntImmutablePair::leftInt)
                                                                .toList())
                                        .flatMap(Collection::stream)
                                        .toList())
                .flatMap(Collection::stream)
                .toList();
    }
}
