package emu.grasscutter.game.player;

import dev.morphia.annotations.*;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestContent;
import it.unimi.dsi.fastutil.ints.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.*;

@Getter
@Entity
public class PlayerProgress {
    @Setter @Transient private Player player;
    private Map<Integer, ItemEntry> itemHistory;

    private IntArrayList completedDungeons;

    private Map<String, Integer> questProgressCountMap;

    private Map<Integer, ItemGiveRecord> itemGivings;
    private Map<Integer, BargainRecord> bargains;

    public PlayerProgress() {
        this.questProgressCountMap = new ConcurrentHashMap<>();
        this.completedDungeons = new IntArrayList();
        this.itemHistory = new Int2ObjectOpenHashMap<>();
        this.itemGivings = new Int2ObjectOpenHashMap<>();
        this.bargains = new Int2ObjectOpenHashMap<>();
    }

    public void markDungeonAsComplete(int dungeonId) {
        if (this.getCompletedDungeons().contains(dungeonId)) return;

        this.getCompletedDungeons().add(dungeonId);
        if (this.getPlayer() != null) {
            this.getPlayer()
                    .getQuestManager()
                    .queueEvent(QuestContent.QUEST_CONTENT_FINISH_DUNGEON, dungeonId);
        } else {
            Grasscutter.getLogger()
                    .warn("Unable to execute 'QUEST_CONTENT_FINISH_DUNGEON'. The player is null.");
        }

        Grasscutter.getLogger()
                .debug("Dungeon {} has been marked complete for {}.", dungeonId, this.getPlayer().getUid());
    }

    public boolean hasPlayerObtainedItemHistorically(int itemId) {
        return itemHistory.containsKey(itemId);
    }

    public int addToItemHistory(int itemId, int count) {
        val itemEntry = itemHistory.computeIfAbsent(itemId, (key) -> new ItemEntry(itemId));
        return itemEntry.addToObtainedCount(count);
    }

    public int getCurrentProgress(String progressId) {
        return questProgressCountMap.getOrDefault(progressId, -1);
    }

    public int addToCurrentProgress(String progressId, int count) {
        return questProgressCountMap.merge(progressId, count, Integer::sum);
    }

    public int resetCurrentProgress(String progressId) {
        return questProgressCountMap.merge(progressId, 0, Integer::min);
    }

    @Entity
    @NoArgsConstructor
    public static class ItemEntry {
        @Getter private int itemId;
        @Getter @Setter private int obtainedCount;

        ItemEntry(int itemId) {
            this.itemId = itemId;
        }

        int addToObtainedCount(int amount) {
            this.obtainedCount += amount;
            return this.obtainedCount;
        }
    }
}
