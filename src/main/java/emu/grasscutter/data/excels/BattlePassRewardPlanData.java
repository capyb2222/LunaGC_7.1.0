package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import lombok.Getter;

@ResourceType(name = "BattlePassRewardPlanExcelConfigData.json")
@Getter
public class BattlePassRewardPlanData extends GameResource {
    private int levelRewardIndexId;

    @Override
    public int getId() {
        return this.levelRewardIndexId;
    }

    public static int defaultPlan() {
        int lowest = 0;
        for (int id : GameData.getBattlePassRewardPlanDataMap().keySet()) {
            if (lowest == 0 || id < lowest) lowest = id;
        }
        return lowest;
    }
}
