package emu.grasscutter.game.quest;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketFinishedParentQuestUpdateNotify;
import emu.grasscutter.utils.FileUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ForcedQuests {
    private ForcedQuests() {}

    private static final int BATCH = 512;

    private static List<Integer> allMainQuests;

    public static synchronized List<Integer> allMainQuests() {
        if (allMainQuests != null) return allMainQuests;

        var ids = new ArrayList<Integer>();
        try {
            var raw = FileUtils.readResource("/quests/main_quest_ids.txt");
            for (var line : new String(raw, StandardCharsets.UTF_8).split("\\R")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    ids.add(Integer.parseInt(line));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception e) {
            Grasscutter.getLogger().error("Could not read the bundled main quest id list.", e);
        }
        allMainQuests = ids;
        return ids;
    }

    public static void notify(Player player, Collection<Integer> questIds) {
        var batch = new ArrayList<Integer>(BATCH);
        for (var id : questIds) {
            batch.add(id);
            if (batch.size() == BATCH) {
                send(player, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) send(player, batch);
    }

    public static int apply(Player player, Collection<Integer> questIds) {
        var forced = player.getForcedFinishedQuests();
        int before = forced.size();
        forced.addAll(questIds);
        player.save();
        notify(player, questIds);
        return forced.size() - before;
    }

    private static void send(Player player, List<Integer> ids) {
        var arr = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) arr[i] = ids.get(i);
        player.sendPacket(new PacketFinishedParentQuestUpdateNotify(arr));
    }
}
