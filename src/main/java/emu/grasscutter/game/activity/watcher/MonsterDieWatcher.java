package emu.grasscutter.game.activity.watcher;

import emu.grasscutter.game.activity.ActivityWatcher;
import emu.grasscutter.game.activity.ActivityWatcherType;
import emu.grasscutter.game.props.WatcherTriggerType;

@ActivityWatcherType(WatcherTriggerType.TRIGGER_BATTLE_FOR_MONSTER_DIE_OR)
public class MonsterDieWatcher extends ActivityWatcher {
    @Override
    protected boolean isMeet(String... param) {
        if (param.length < 1) return false;

        var paramList = getActivityWatcherData().getTriggerConfig().getParamList();
        if (paramList.isEmpty()) return false;

        for (String entry : paramList) {
            for (String monsterId : entry.split(",")) {
                if (monsterId.trim().equals(param[0])) return true;
            }
        }
        return false;
    }
}
