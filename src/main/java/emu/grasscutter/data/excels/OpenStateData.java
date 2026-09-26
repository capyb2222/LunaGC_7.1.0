package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import lombok.Getter;

import java.util.*;

@ResourceType(name = "OpenStateConfigData.json", loadPriority = ResourceType.LoadPriority.HIGHEST)
public class OpenStateData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;
    @Getter
    private boolean defaultState;
    @Getter
    private boolean allowClientOpen;
    @Getter
    private int systemOpenUiId;
    @Getter
    private List<OpenStateCond> cond;

    @Override
    public void onLoad() {
        GameData.getOpenStateList().add(this);

        if (this.cond != null) {
            this.cond.removeIf(c -> c.getCondType() == null);
        } else {
            this.cond = new ArrayList<>();
        }
    }

    public enum OpenStateCondType {
        OPEN_STATE_COND_PLAYER_LEVEL,
        OPEN_STATE_COND_QUEST,
        OPEN_STATE_OFFERING_LEVEL,
        OPEN_STATE_CITY_REPUTATION_LEVEL,
        OPEN_STATE_COND_PARENT_QUEST
    }

    public static class OpenStateCond {
        @Getter
        private OpenStateCondType condType;
        @Getter
        private int param;
        @Getter
        private int param2;
    }
}
