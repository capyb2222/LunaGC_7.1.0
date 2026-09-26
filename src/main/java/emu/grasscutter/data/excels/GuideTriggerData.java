package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import lombok.*;

@Data
@EqualsAndHashCode(callSuper = false)
@ResourceType(name = "GuideTriggerExcelConfigData.json")
public class GuideTriggerData extends GameResource {
    private String guideName;
    private String type;
    private String openState;

    @Override
    public int getId() {
        return this.guideName == null ? 0 : this.guideName.hashCode();
    }

    public void onLoad() {
        GameData.getGuideTriggerDataStringMap().put(getGuideName(), this);
    }
}
