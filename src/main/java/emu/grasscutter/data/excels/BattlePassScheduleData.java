package emu.grasscutter.data.excels;

import emu.grasscutter.GameConstants;
import emu.grasscutter.data.*;
import java.util.List;
import lombok.Getter;

@ResourceType(name = "BattlePassScheduleExcelConfigData.json")
@Getter
public class BattlePassScheduleData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private List<Integer> cycleList;
    private int cyclePointUpperLimit;

    public static int currentId() {
        int wanted = GameConstants.VERSION_PARTS[0] * 1000 + GameConstants.VERSION_PARTS[1] * 100;
        int best = 0;
        int lowest = Integer.MAX_VALUE;
        for (int id : GameData.getBattlePassScheduleDataMap().keySet()) {
            if (id <= wanted && id > best) best = id;
            if (id < lowest) lowest = id;
        }
        return best != 0 ? best : (lowest == Integer.MAX_VALUE ? 0 : lowest);
    }
}
