package emu.grasscutter.game.dailytask;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.annotations.IndexOptions;
import dev.morphia.annotations.Indexed;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.DailyTaskData;
import emu.grasscutter.data.excels.DailyTaskLevelData;
import emu.grasscutter.data.excels.DailyTaskRewardData;
import emu.grasscutter.data.excels.RewardPreviewData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.server.packet.send.PacketDailyTaskDataNotify;
import emu.grasscutter.server.packet.send.PacketDailyTaskProgressNotify;
import emu.grasscutter.server.packet.send.PacketWorldOwnerDailyTaskNotify;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.nio.file.Files;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.Getter;
import org.bson.types.ObjectId;
import java.time.LocalDate;
import java.time.ZoneId;

@Getter
@Entity(value = "dailytasks", useDiscriminator = false)
public class DailyTaskManager {
    private static final int DAILY_TASK_COUNT = 4;

	private static final int RANDOM_CITY_ID = 0;
	private static final int DEFAULT_CITY_ID = RANDOM_CITY_ID;

	private static final int TEYVAT_SCENE_ID = 3;

    private static final String SUPPORTED_TASK_TYPE =
            "DAILY_TASK_SCENE";

    private static final String SUPPORTED_FINISH_TYPE =
            "DAILY_FINISH_MONSTER_NUM";
			
	private static final ConcurrentMap<Integer, Boolean> GROUP_RESOURCE_SUPPORT_CACHE =
        new ConcurrentHashMap<>();

    @Id
    private ObjectId id;

    @Indexed(options = @IndexOptions(unique = true))
    private int ownerUid;

    private transient Player player;

    private List<DailyTask> dailyTasks = new ArrayList<>();

    private int cityId = DEFAULT_CITY_ID;

    private boolean scoreRewardTaken;
	
	private int lastGenerationDate;

    public DailyTaskManager() {}

    public DailyTaskManager(Player player) {
        this.ownerUid = player.getUid();
        this.player = player;
    }

    public void setPlayer(Player player) {
        this.player = player;

        if (this.dailyTasks == null) {
            this.dailyTasks = new ArrayList<>();
        }

		if (this.cityId < RANDOM_CITY_ID) {
			this.cityId = DEFAULT_CITY_ID;
		}
    }

	private static final int OPEN_STATE_DAILY_TASK = 4510;

	public boolean isUnlocked() {
		return this.player != null
				&& this.player.getProgressManager().getOpenState(OPEN_STATE_DAILY_TASK) == 1;
	}

	public void onPlayerLogin() {
		if (this.player == null || !this.isUnlocked()) {
			return;
		}

		if (this.dailyTasks == null) {
			this.dailyTasks = new ArrayList<>();
		}

		this.ensureDailyTasksForToday(false);

		if (this.lastGenerationDate == getCurrentDateKey()
				&& this.dailyTasks.size() != DAILY_TASK_COUNT) {
			Grasscutter.getLogger()
					.warn(
							"[DailyTask] UID {} has {} stored commission(s) for today {}. "
									+ "The set will NOT be automatically rerolled.",
							this.ownerUid,
							this.dailyTasks.size(),
							this.lastGenerationDate);
		}

		this.player.sendPacket(
				new PacketDailyTaskDataNotify(this.player));
	}

	public synchronized int ensureDailyTasksForToday() {
		return this.ensureDailyTasksForToday(true);
	}

	private synchronized int ensureDailyTasksForToday(
			boolean syncClient) {
		if (this.player == null || !this.isUnlocked()) {
			return 0;
		}

		if (this.dailyTasks == null) {
			this.dailyTasks = new ArrayList<>();
		}

		int today =
				getCurrentDateKey();

		if (this.lastGenerationDate == today) {
			Grasscutter.getLogger()
					.debug(
							"[DailyTask] UID {} already has today's commission quota "
									+ "(date={}, tasks={}).",
							this.ownerUid,
							today,
							this.dailyTasks.size());

			return this.dailyTasks.size();
		}

		if (this.lastGenerationDate == 0
				&& this.dailyTasks.size() == DAILY_TASK_COUNT) {
			this.lastGenerationDate = today;

			Grasscutter.getLogger()
					.info(
							"[DailyTask] Migrated existing commission set for UID {} "
									+ "to daily date {} without rerolling.",
							this.ownerUid,
							today);

			this.save();

			return this.dailyTasks.size();
		}

		return this.generateDailyTasks(
				syncClient,
				today);
	}

	public synchronized int resetDailyTasksForNewDay() {
		return this.ensureDailyTasksForToday(true);
	}

	public synchronized int resetDailyTasks() {
		return this.generateDailyTasks(
				true,
				getCurrentDateKey());
	}

	private synchronized int generateDailyTasks(
			boolean syncClient,
			int generationDate) {
		if (this.player == null) {
			return 0;
		}

		Set<Integer> previousGroupIds =
				this.collectTaskGroupIds(false);

		int selectedCityId =
				this.resolveCityIdForReset();

		if (selectedCityId == RANDOM_CITY_ID) {
			Grasscutter.getLogger()
					.warn(
							"[DailyTask] No region contains at least {} supported daily commissions.",
							DAILY_TASK_COUNT);

			return 0;
		}

		List<DailyTaskData> candidates =
				GameData.getDailyTaskDataMap()
						.values()
						.stream()
						.filter(
								data ->
										data.getCityId()
												== selectedCityId)
						.filter(DailyTaskManager::isSupportedTask)
						.collect(
								java.util.stream.Collectors.toCollection(
										ArrayList::new));

		Collections.shuffle(candidates);

		this.dailyTasks.clear();

		for (DailyTaskData data : candidates) {
			if (this.dailyTasks.size() >= DAILY_TASK_COUNT) {
				break;
			}

			DailyTask task =
					DailyTask.create(
							this.player,
							data.getId());

			if (task != null) {
				this.dailyTasks.add(task);
			}
		}

		this.scoreRewardTaken = false;

		if (this.dailyTasks.size() < DAILY_TASK_COUNT) {
			Grasscutter.getLogger()
					.warn(
							"[DailyTask] Only {} supported commissions were found for city {}.",
							this.dailyTasks.size(),
							selectedCityId);
		} else {
			Grasscutter.getLogger()
					.info(
							"[DailyTask] Generated {} commissions from city {} "
									+ "for date {}. Filter city is {}.",
							this.dailyTasks.size(),
							selectedCityId,
							generationDate,
							this.cityId);
		}

		this.lastGenerationDate =
				generationDate;

		this.save();

		if (syncClient) {
			if (this.player.isOnline()
					&& this.player.getScene() != null) {
				this.unloadGroups(
						this.player.getScene(),
						previousGroupIds);

				this.loadActiveGroups(
						this.player.getScene());
			}

			this.syncAll();
		}

		return this.dailyTasks.size();
	}

	public synchronized boolean setCityIdAndReset(int newCityId) {
		if (newCityId == RANDOM_CITY_ID) {
			this.cityId = RANDOM_CITY_ID;

			return this.resetDailyTasks()
					== DAILY_TASK_COUNT;
		}

		if (!this.getSupportedCityIds().contains(newCityId)) {
			return false;
		}

		this.cityId = newCityId;

		return this.resetDailyTasks()
				== DAILY_TASK_COUNT;
	}

	private static boolean isBaseSupportedTask(DailyTaskData data) {
		return data != null
				&& SUPPORTED_TASK_TYPE.equals(data.getType())
				&& SUPPORTED_FINISH_TYPE.equals(data.getFinishType())
				&& data.getFinishProgress() > 0
				&& data.getTaskRewardId() > 0
				&& data.getNewGroupVec() != null
				&& !data.getNewGroupVec().isEmpty();
	}

	private static int getBlockIdFromGroupId(int groupId) {
		return (groupId / 1000) % 10000;
	}

	private static boolean hasUsableGroupResources(int groupId) {
		return GROUP_RESOURCE_SUPPORT_CACHE.computeIfAbsent(
				groupId,
				DailyTaskManager::probeGroupResources);
	}

	private static boolean probeGroupResources(int groupId) {
		if (groupId <= 0) {
			return false;
		}

		int blockId = getBlockIdFromGroupId(groupId);

		var sceneMeta =
				ScriptLoader.getSceneMeta(TEYVAT_SCENE_ID);

		if (sceneMeta == null
				|| sceneMeta.blocks == null) {
			Grasscutter.getLogger()
					.warn(
							"[DailyTask] Cannot validate group {} because Scene {} metadata is unavailable.",
							groupId,
							TEYVAT_SCENE_ID);

			return false;
		}

		if (!sceneMeta.blocks.containsKey(blockId)) {
			Grasscutter.getLogger()
					.debug(
							"[DailyTask] Rejecting group {}: block {} is not registered in scene {}.",
							groupId,
							blockId,
							TEYVAT_SCENE_ID);

			return false;
		}

		String blockScript =
				"Scene/%d/scene%d_block%d.lua"
						.formatted(
								TEYVAT_SCENE_ID,
								TEYVAT_SCENE_ID,
								blockId);

		String groupScript =
				"Scene/%d/scene%d_group%d.lua"
						.formatted(
								TEYVAT_SCENE_ID,
								TEYVAT_SCENE_ID,
								groupId);

		if (!Files.isRegularFile(
				FileUtils.getScriptPath(blockScript))) {
			Grasscutter.getLogger()
					.debug(
							"[DailyTask] Rejecting group {}: missing block script {}.",
							groupId,
							blockScript);

			return false;
		}

		if (!Files.isRegularFile(
				FileUtils.getScriptPath(groupScript))) {
			Grasscutter.getLogger()
					.debug(
							"[DailyTask] Rejecting group {}: missing group script {}.",
							groupId,
							groupScript);

			return false;
		}

		return true;
	}

	private static boolean isSupportedTask(DailyTaskData data) {
		if (!isBaseSupportedTask(data)) {
			return false;
		}

		return data.getNewGroupVec()
				.stream()
				.allMatch(DailyTaskManager::hasUsableGroupResources);
	}

	public List<Integer> getSupportedCityIds() {
		return GameData.getDailyTaskDataMap()
				.values()
				.stream()
				.filter(DailyTaskManager::isSupportedTask)
				.map(DailyTaskData::getCityId)
				.distinct()
				.filter(this::hasEnoughSupportedTasks)
				.sorted()
				.toList();
	}

	private boolean hasEnoughSupportedTasks(int cityId) {
		return GameData.getDailyTaskDataMap()
				.values()
				.stream()
				.filter(data -> data.getCityId() == cityId)
				.filter(DailyTaskManager::isSupportedTask)
				.limit(DAILY_TASK_COUNT)
				.count() >= DAILY_TASK_COUNT;
	}

	private int resolveCityIdForReset() {
		List<Integer> supportedCities =
				this.getSupportedCityIds();

		if (supportedCities.isEmpty()) {
			return RANDOM_CITY_ID;
		}

		if (this.cityId > RANDOM_CITY_ID
				&& supportedCities.contains(this.cityId)) {
			return this.cityId;
		}

		return supportedCities.get(
				ThreadLocalRandom.current()
						.nextInt(supportedCities.size()));
	}

	public int getActiveCityId() {
		if (this.dailyTasks == null
				|| this.dailyTasks.isEmpty()) {
			return RANDOM_CITY_ID;
		}

		for (DailyTask task : this.dailyTasks) {
			if (task == null) {
				continue;
			}

			DailyTaskData data =
					GameData.getDailyTaskDataMap()
							.get(task.getTaskId());

			if (data != null) {
				return data.getCityId();
			}
		}

		return RANDOM_CITY_ID;
	}

    public DailyTask getDailyTask(int taskId) {
        if (this.dailyTasks == null) {
            return null;
        }

        return this.dailyTasks.stream()
                .filter(task -> task.getTaskId() == taskId)
                .findFirst()
                .orElse(null);
    }

    public int getFinishedCount() {
        if (this.dailyTasks == null) {
            return 0;
        }

        return (int)
                this.dailyTasks.stream()
                        .filter(DailyTask::isFinished)
                        .count();
    }

	private Set<Integer> collectTaskGroupIds(boolean unfinishedOnly) {
		Set<Integer> groupIds = new LinkedHashSet<>();

		if (this.dailyTasks == null) {
			return groupIds;
		}

		for (DailyTask task : this.dailyTasks) {
			if (task == null) {
				continue;
			}

			if (unfinishedOnly && task.isFinished()) {
				continue;
			}

			DailyTaskData data =
					GameData.getDailyTaskDataMap()
							.get(task.getTaskId());

			if (!isSupportedTask(data)
					|| data.getNewGroupVec() == null
					|| data.getNewGroupVec().isEmpty()) {
				continue;
			}

			groupIds.addAll(data.getNewGroupVec());
		}

		return groupIds;
	}

	public Set<Integer> getActiveGroupIds() {
		return Set.copyOf(this.collectTaskGroupIds(true));
	}

	private boolean isGroupLoaded(Scene scene, int groupId) {
		if (scene == null) {
			return false;
		}

		return scene.getLoadedGroups()
				.stream()
				.anyMatch(group -> group.id == groupId);
	}

	public synchronized int loadActiveGroups(Scene scene) {
		if (scene == null
				|| !this.isUnlocked()
				|| scene.getId() != TEYVAT_SCENE_ID
				|| scene.getScriptManager() == null
				|| !scene.getScriptManager().isInit()) {
			return 0;
		}

		int ready = 0;

		for (int groupId : this.collectTaskGroupIds(true)) {
			if (this.isGroupLoaded(scene, groupId)) {
				ready++;

				Grasscutter.getLogger()
						.debug(
								"[DailyTask] Commission group {} is already loaded in scene {}.",
								groupId,
								scene.getId());

				continue;
			}

			int suiteId = scene.loadDynamicGroup(groupId);

			if (suiteId <= 0) {
				Grasscutter.getLogger()
						.warn(
								"[DailyTask] Failed to activate dynamic commission group {} in scene {}.",
								groupId,
								scene.getId());
				continue;
			}

			ready++;

			Grasscutter.getLogger()
					.info(
							"[DailyTask] Activated dynamic commission group {} with init suite {} in scene {}.",
							groupId,
							suiteId,
							scene.getId());
		}

		return ready;
	}

	private void unloadGroups(Scene scene, Collection<Integer> groupIds) {
		if (scene == null
				|| scene.getId() != TEYVAT_SCENE_ID
				|| groupIds == null
				|| groupIds.isEmpty()) {
			return;
		}

		for (int groupId : groupIds) {
			if (!this.isGroupLoaded(scene, groupId)) {
				continue;
			}

			if (scene.unregisterDynamicGroup(groupId)) {
				Grasscutter.getLogger()
						.debug(
								"[DailyTask] Unloaded dynamic commission group {} from scene {}.",
								groupId,
								scene.getId());
			}
		}
	}

	private void unloadTaskGroups(Scene scene, DailyTask task) {
		if (scene == null || task == null) {
			return;
		}

		DailyTaskData data =
				GameData.getDailyTaskDataMap()
						.get(task.getTaskId());

		if (data == null
				|| data.getNewGroupVec() == null
				|| data.getNewGroupVec().isEmpty()) {
			return;
		}

		this.unloadGroups(scene, data.getNewGroupVec());
	}

    private DailyTaskLevelData getCurrentLevelData() {
        if (this.player == null) {
            return null;
        }

        int playerLevel = this.player.getLevel();

        return GameData.getDailyTaskLevelDataMap()
                .values()
                .stream()
                .filter(
                        data ->
                                data.getMinPlayerLevel() <= playerLevel
                                        && playerLevel
                                                <= data.getMaxPlayerLevel())
                .findFirst()
                .orElse(null);
    }

    public int getRewardId(int taskRewardId) {
        DailyTaskLevelData levelData = this.getCurrentLevelData();

        if (levelData == null) {
            return 0;
        }

        DailyTaskRewardData rewardData =
                GameData.getDailyTaskRewardDataMap()
                        .get(taskRewardId);

        if (rewardData == null
                || rewardData.getDropVec() == null) {
            return 0;
        }

        int rewardIndex = levelData.getId() - 1;

        if (rewardIndex < 0
                || rewardIndex >= rewardData.getDropVec().size()) {
            return 0;
        }

        return rewardData.getDropVec()
                .get(rewardIndex)
                .getPreviewRewardId();
    }

    public int getScoreRewardId() {
        DailyTaskLevelData levelData = this.getCurrentLevelData();

        if (levelData == null) {
            return 0;
        }

        return levelData.getScorePreviewRewardId();
    }

	public synchronized void onMonsterDeath(
			Scene scene,
			int groupId) {
		if (groupId <= 0
				|| this.player == null
				|| !this.isUnlocked()
				|| this.dailyTasks == null) {
			return;
		}

		boolean changed = false;

		for (DailyTask task : this.dailyTasks) {
			if (task.isFinished()) {
				continue;
			}

			DailyTaskData data =
					GameData.getDailyTaskDataMap()
							.get(task.getTaskId());

			if (!isSupportedTask(data)) {
				continue;
			}

			if (!data.getNewGroupVec().contains(groupId)) {
				continue;
			}

			if (!task.addProgress(1)) {
				continue;
			}

			changed = true;

			Grasscutter.getLogger()
					.debug(
							"[DailyTask] Task {} progress {}/{} from group {}.",
							task.getTaskId(),
							task.getProgress(),
							task.getFinishProgress(),
							groupId);

			this.broadcastProgress(task);

			if (task.isFinished()) {
				Grasscutter.getLogger()
						.info(
								"[DailyTask] Commission {} completed from group {}.",
								task.getTaskId(),
								groupId);

				this.grantPreviewReward(
						task.getRewardId(),
						ActionReason.DailyTaskHost);

				this.unloadTaskGroups(
						scene,
						task);
			} else {
				this.scheduleStalledWaveRecovery(
						scene,
						task);
			}
		}

		if (!changed) {
			return;
		}

		this.tryAutoClaimScoreReward();

		this.save();
		this.syncAll();
	}

	private void scheduleStalledWaveRecovery(
			Scene scene,
			DailyTask task) {
		if (scene == null
				|| task == null
				|| task.isFinished()) {
			return;
		}

		int taskId =
				task.getTaskId();

		Grasscutter.getGameServer()
				.getScheduler()
				.scheduleDelayedTask(
						() ->
								this.recoverStalledTwoSuiteTask(
										scene,
										taskId),
						2);
	}

	private boolean hasLivingMonsterForTask(
			Scene scene,
			DailyTaskData data) {
		if (scene == null
				|| data == null
				|| data.getNewGroupVec() == null) {
			return false;
		}

		return scene.getEntities()
				.values()
				.stream()
				.anyMatch(
						entity ->
								entity instanceof EntityMonster monster
										&& data.getNewGroupVec()
												.contains(
														monster.getGroupId()));
	}

	private synchronized void recoverStalledTwoSuiteTask(
			Scene scene,
			int taskId) {
		if (this.player == null
				|| scene == null
				|| this.player.getScene() != scene) {
			return;
		}

		DailyTask task =
				this.getDailyTask(taskId);

		if (task == null
				|| task.isFinished()) {
			return;
		}

		DailyTaskData data =
				GameData.getDailyTaskDataMap()
						.get(taskId);

		if (!isSupportedTask(data)) {
			return;
		}

		if (this.hasLivingMonsterForTask(
				scene,
				data)) {
			return;
		}

		var scriptManager =
				scene.getScriptManager();

		for (int groupId : data.getNewGroupVec()) {
			var group =
					scriptManager.getGroupById(groupId);

			var instance =
					scriptManager.getGroupInstanceById(groupId);

			if (group == null
					|| instance == null
					|| group.suites == null
					|| group.suites.size() != 2) {
				continue;
			}

			if (group.init_config == null
					|| group.init_config.rand_suite
					|| group.init_config.suite != 1
					|| instance.getActiveSuiteId() != 1) {
				continue;
			}

			var firstSuite =
					group.getSuiteByIndex(1);

			var secondSuite =
					group.getSuiteByIndex(2);

			if (firstSuite == null
					|| secondSuite == null) {
				continue;
			}

			int firstWaveCount =
					firstSuite.sceneMonsters.size();

			int secondWaveCount =
					secondSuite.sceneMonsters.size();

			if (firstWaveCount <= 0
					|| secondWaveCount <= 0) {
				continue;
			}

			if (firstWaveCount >= task.getFinishProgress()) {
				continue;
			}

			if (firstWaveCount + secondWaveCount
					< task.getFinishProgress()) {
				continue;
			}

			if (task.getProgress() < firstWaveCount) {
				continue;
			}

			Grasscutter.getLogger()
					.warn(
							"[DailyTask] Recovering stalled second wave for task {} group {}: progress={}/{}, suite1={}, suite2={}.",
							taskId,
							groupId,
							task.getProgress(),
							task.getFinishProgress(),
							firstWaveCount,
							secondWaveCount);

			scriptManager.addGroupSuite(
					instance,
					secondSuite);

			return;
		}

		Grasscutter.getLogger()
				.warn(
						"[DailyTask] Task {} has no living commission monsters at progress {}/{}, but no safe two-suite recovery was found. groups={}",
						taskId,
						task.getProgress(),
						task.getFinishProgress(),
						data.getNewGroupVec());
	}

	public synchronized boolean finishDailyTask(int taskId) {
		DailyTask task =
				this.getDailyTask(taskId);

		if (task == null) {
			return false;
		}

		if (!task.finish()) {
			return false;
		}

		this.broadcastProgress(task);

		this.grantPreviewReward(
				task.getRewardId(),
				ActionReason.DailyTaskHost);

		if (this.player != null
				&& this.player.getScene() != null) {
			this.unloadTaskGroups(
					this.player.getScene(),
					task);
		}

		this.tryAutoClaimScoreReward();

		this.save();
		this.syncAll();

		return true;
	}

	private boolean tryAutoClaimScoreReward() {
		if (this.player == null) {
			return false;
		}

		if (this.scoreRewardTaken) {
			return false;
		}

		if (this.getFinishedCount() < DAILY_TASK_COUNT) {
			return false;
		}

		int rewardId =
				this.getScoreRewardId();

		if (!this.grantPreviewReward(
				rewardId,
				ActionReason.DailyTaskScore)) {
			Grasscutter.getLogger()
					.warn(
							"[DailyTask] Failed to automatically grant the four-commission reward {} to UID {}.",
							rewardId,
							this.player.getUid());

			return false;
		}

		this.scoreRewardTaken = true;

		Grasscutter.getLogger()
				.info(
						"[DailyTask] Automatically granted four-commission reward {} to UID {}.",
						rewardId,
						this.player.getUid());

		return true;
	}

	public boolean isScoreRewardTaken() {
		return this.scoreRewardTaken;
	}

	public static int getDailyTaskCount() {
		return DAILY_TASK_COUNT;
	}

	public List<ItemParamData> getScoreRewardItems() {
		RewardPreviewData reward =
				GameData.getRewardPreviewDataMap()
						.get(this.getScoreRewardId());

		if (reward == null || reward.getPreviewItems() == null) {
			return List.of();
		}

		return Arrays.asList(reward.getPreviewItems());
	}

	public synchronized boolean claimScoreReward() {
		if (!this.tryAutoClaimScoreReward()) {
			return false;
		}

		this.save();
		this.syncAll();

		return true;
	}

    private boolean grantPreviewReward(
            int rewardPreviewId,
            ActionReason reason) {
        if (this.player == null || rewardPreviewId <= 0) {
            return false;
        }

        RewardPreviewData reward =
                GameData.getRewardPreviewDataMap()
                        .get(rewardPreviewId);

        if (reward == null) {
            Grasscutter.getLogger()
                    .warn(
                            "[DailyTask] RewardPreview {} was not found.",
                            rewardPreviewId);
            return false;
        }

        ItemParamData[] items = reward.getPreviewItems();

        if (items == null || items.length == 0) {
            Grasscutter.getLogger()
                    .warn(
                            "[DailyTask] RewardPreview {} contains no usable items.",
                            rewardPreviewId);
            return false;
        }

        this.player
                .getInventory()
                .addItemParamDatas(
                        Arrays.asList(items),
                        reason);

        this.player.save();

        return true;
    }

    private void broadcastProgress(DailyTask task) {
        if (this.player == null || !this.player.isOnline()) {
            return;
        }

        var packet =
                new PacketDailyTaskProgressNotify(task);

        if (this.player.getScene() != null) {
            this.player.getScene().broadcastPacket(packet);
        } else {
            this.player.sendPacket(packet);
        }
    }

	public long getDefinedCombatTaskCount(int cityId) {
		return GameData.getDailyTaskDataMap()
				.values()
				.stream()
				.filter(data -> data.getCityId() == cityId)
				.filter(DailyTaskManager::isBaseSupportedTask)
				.count();
	}

	public long getResourceBackedTaskCount(int cityId) {
		return GameData.getDailyTaskDataMap()
				.values()
				.stream()
				.filter(data -> data.getCityId() == cityId)
				.filter(DailyTaskManager::isSupportedTask)
				.count();
	}

	private static int getCurrentDateKey() {
		LocalDate today =
				LocalDate.now(ZoneId.systemDefault());

		return today.getYear() * 10000
				+ today.getMonthValue() * 100
				+ today.getDayOfMonth();
	}

	private int getStoredTaskCount() {
		return this.dailyTasks == null
				? 0
				: this.dailyTasks.size();
	}

    public void syncAll() {
        if (this.player == null || !this.player.isOnline()) {
            return;
        }

        this.player.sendPacket(
                new PacketDailyTaskDataNotify(this.player));

        var packet =
                new PacketWorldOwnerDailyTaskNotify(this.player);

        if (this.player.getScene() != null) {
            this.player.getScene().broadcastPacket(packet);
        } else {
            this.player.sendPacket(packet);
        }
    }

    public void save() {
        DatabaseHelper.saveDailyTaskManager(this);
    }
}