package emu.grasscutter.game.activity.condition;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.activity.ActivityCondExcelConfigData;
import emu.grasscutter.game.activity.PlayerActivityData;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.ints.AbstractInt2ObjectMap.BasicEntry;
import java.util.Map;

public class PlayerActivityDataMappingBuilder {

    private final Map<Integer, PlayerActivityData> playerActivityDataMap;

    private final Int2ObjectMap<ActivityCondExcelConfigData> activityCondMap;

    public static Int2ObjectMap<PlayerActivityData> buildPlayerActivityDataByActivityCondId(
            Map<Integer, PlayerActivityData> activities) {
        return new PlayerActivityDataMappingBuilder(activities).buildMappings();
    }

    public PlayerActivityDataMappingBuilder(Map<Integer, PlayerActivityData> playerActivityDataMap) {
        this.playerActivityDataMap = playerActivityDataMap;
        activityCondMap = GameData.getActivityCondExcelConfigDataMap();
    }

    private Int2ObjectMap<PlayerActivityData> buildMappings() {
        Int2ObjectMap<PlayerActivityData> result = new Int2ObjectRBTreeMap<>();

        activityCondMap.int2ObjectEntrySet().stream()
                .map(
                        entry ->
                                new BasicEntry<>(
                                        entry.getIntKey(), getPlayerActivityDataByCondId(entry.getIntKey())))
                .filter(entry -> entry.getValue() != null)
                .forEach(entry -> result.put(entry.getIntKey(), entry.getValue()));

        return result;
    }

    private PlayerActivityData getPlayerActivityDataByCondId(Integer key) {
        return playerActivityDataMap.get(detectActivityDataIdByCondId(key));
    }

    private Integer detectActivityDataIdByCondId(Integer key) {
        if (key / 10 == 1001 || key / 10 == 1002) {
            return 1001;
        } else if (key / 100 == 5001) {
            return key / 100;
        } else {
            return key / 1000;
        }
    }
}
