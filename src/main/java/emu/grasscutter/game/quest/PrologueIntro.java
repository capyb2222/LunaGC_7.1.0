package emu.grasscutter.game.quest;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;
import java.util.ArrayList;
import java.util.Set;

public final class PrologueIntro {
    private static final Set<Integer> INTRO_MAIN_QUESTS = Set.of(351, 352);
    private static final Set<Integer> PROLOGUE_MAIN_QUESTS = Set.of(351, 352, 353, 354, 355, 356, 357);
    public static final int FIRST_QUEST = 35104;
    private static final int STATUE_SCENE = 3;
    private static final int STATUE_AREA = 1;

    public static final int STAGE_NONE = 0;
    public static final int STAGE_PENDING = 1;
    public static final int STAGE_RUNNING = 2;
    public static final int STAGE_DONE = 3;

    private PrologueIntro() {}

    public static boolean isEnabled() {
        return GAME_OPTIONS.newAccountIntro.meetPaimon && !GAME_OPTIONS.questing.enabled;
    }

    public static boolean isActive(Player player) {
        int stage = player.getPrologueIntroStage();
        return stage == STAGE_PENDING || stage == STAGE_RUNNING;
    }

    public static void markNewAccount(Player player) {
        if (!isEnabled() || player.getPrologueIntroStage() != STAGE_NONE) return;
        player.setPrologueIntroStage(STAGE_PENDING);
    }

    public static void onLogin(Player player) {
        switch (player.getPrologueIntroStage()) {
            case STAGE_PENDING -> start(player);
            case STAGE_RUNNING -> resume(player);
            default -> {}
        }
    }

    public static void onQuestFinished(GameQuest quest) {
        var player = quest.getOwner();
        if (player.getPrologueIntroStage() != STAGE_RUNNING) return;
        if (quest.getSubQuestId() == FIRST_QUEST) conclude(player, "the opening scene finished");
    }

    public static boolean onClientPlotFinished(Player player, int plotId) {
        if (player.getPrologueIntroStage() != STAGE_RUNNING || plotId != FIRST_QUEST) return false;
        player.sendPacket(new PacketDelQuestNotify(FIRST_QUEST));
        conclude(player, "the opening scene finished");
        return true;
    }

    public static boolean wentThrough(Player player) {
        return player.getPrologueIntroStage() != STAGE_NONE;
    }

    public static boolean allowAccept(Player player, QuestData questData) {
        if (GAME_OPTIONS.questing.enabled || !PROLOGUE_MAIN_QUESTS.contains(questData.getMainId())) return true;
        return isActive(player) && INTRO_MAIN_QUESTS.contains(questData.getMainId());
    }

    public static boolean isVisible(Player player, GameQuest quest) {
        return player.getPrologueIntroStage() == STAGE_RUNNING
                && INTRO_MAIN_QUESTS.contains(quest.getMainQuestId());
    }

    public static boolean isVisible(Player player, GameMainQuest mainQuest) {
        return player.getPrologueIntroStage() == STAGE_RUNNING
                && INTRO_MAIN_QUESTS.contains(mainQuest.getParentQuestId());
    }

    private static void start(Player player) {
        player.setPrologueIntroStage(STAGE_RUNNING);
        player.save();

        var quest = player.getQuestManager().addQuestSilently(FIRST_QUEST);
        if (quest == null) {
            conclude(player, "the prologue quests are missing");
            return;
        }
        quest.setAcceptTime(Utils.getCurrentSeconds());
        quest.setStartTime(quest.getAcceptTime());
        quest.setState(QuestState.QUEST_STATE_UNFINISHED);
        quest.save();
        Grasscutter.getLogger().info("[intro] {} started the prologue (quest {}).", player.getUid(), FIRST_QUEST);
    }

    private static void resume(Player player) {
        conclude(player, "the opening scene was interrupted on an earlier login");
    }

    private static void conclude(Player player, String reason) {
        int marked = finishRemaining(player);

        var world = player.getWorld();
        if (world != null && world.isTimeLocked()) world.lockTime(false);

        var mainAvatar = player.getAvatars().getAvatarById(player.getMainCharacterId());
        if (mainAvatar != null && mainAvatar.getSkillDepot() != null
                && mainAvatar.getSkillDepot().getElementType() != ElementType.Wind) {
            mainAvatar.changeElement(ElementType.Wind);
            mainAvatar.save();
        }

        player.setPrologueIntroStage(STAGE_DONE);
        player.getUnlockedSceneAreas(STATUE_SCENE).add(STATUE_AREA);
        player.getProgressManager().tryUnlockOpenStates(false);
        player.getProgressManager().markClientOpenStatesSeen();
        player.save();

        if (player.hasSentLoginPackets()) {
            player.sendPacket(new PacketOpenStateUpdateNotify(player));
            player.sendPacket(new PacketSceneForceUnlockNotify(1, true));
        }

        Grasscutter.getLogger()
                .info("[intro] {} finished the prologue intro: {} (marked {} step(s) finished).",
                        player.getUid(), reason, marked);
    }

    private static int finishRemaining(Player player) {
        var questManager = player.getQuestManager();
        var finished = new ArrayList<GameQuest>();

        for (int mainId : INTRO_MAIN_QUESTS) {
            var mainData = GameData.getMainQuestDataMap().get(mainId);
            if (mainData == null) continue;

            for (var sub : mainData.getSubQuests()) {
                var quest = questManager.getQuestById(sub.getSubId());
                if (quest == null) quest = questManager.addQuestSilently(sub.getSubId());
                if (quest == null || quest.getState() == QuestState.QUEST_STATE_FINISHED) continue;

                quest.setState(QuestState.QUEST_STATE_FINISHED);
                quest.setFinishTime(Utils.getCurrentSeconds());
                quest.save();
                finished.add(quest);
            }
        }

        if (!finished.isEmpty() && player.hasSentLoginPackets()) {
            player.sendPacket(new PacketQuestListUpdateNotify(finished));
        }

        var finishedMains = new ArrayList<GameMainQuest>();
        for (int mainId : INTRO_MAIN_QUESTS) {
            var mainQuest = questManager.getMainQuestById(mainId);
            if (mainQuest == null || mainQuest.getState() == ParentQuestState.PARENT_QUEST_STATE_FINISHED) continue;
            mainQuest.finishWithoutRewards();
            finishedMains.add(mainQuest);
        }
        if (!finishedMains.isEmpty() && player.hasSentLoginPackets()) {
            player.sendPacket(new PacketFinishedParentQuestUpdateNotify(finishedMains));
        }
        return finished.size();
    }
}
