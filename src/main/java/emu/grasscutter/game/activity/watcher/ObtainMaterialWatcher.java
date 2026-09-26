package emu.grasscutter.game.activity.watcher;

import emu.grasscutter.game.activity.ActivityWatcher;
import emu.grasscutter.game.activity.ActivityWatcherType;
import emu.grasscutter.game.props.WatcherTriggerType;

@ActivityWatcherType(WatcherTriggerType.TRIGGER_OBTAIN_MATERIAL_NUM)
public class ObtainMaterialWatcher extends ActivityWatcher {
    @Override
    protected boolean isMeet(String... param) {
        if (param.length < 1) return false;

        var paramList = getActivityWatcherData().getTriggerConfig().getParamList();
        if (paramList.isEmpty()) return false;

        return paramList.get(0).equals(param[0]);
    }

    @Override
    protected int getProgressDelta(String... param) {
        if (param.length < 2) return 1;
        try {
            return Integer.parseInt(param[1]);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
