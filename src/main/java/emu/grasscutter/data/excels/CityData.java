package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.world.WorldAreaData;
import java.util.List;
import lombok.*;
import lombok.experimental.FieldDefaults;

@ResourceType(name = "CityConfigData.json", loadPriority = ResourceType.LoadPriority.HIGH)
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CityData extends GameResource {
    int cityId;
    int sceneId;
    List<Integer> areaIdVec;

    @Override
    public int getId() {
        return this.cityId;
    }

    public int getSceneId() {
        if (this.sceneId != 0 || this.areaIdVec == null) return this.sceneId;

        for (var areaId : this.areaIdVec) {
            WorldAreaData area = GameData.getWorldAreaDataMap().get(areaId.intValue());
            if (area != null && area.getSceneId() != 0) {
                this.sceneId = area.getSceneId();
                break;
            }
        }

        return this.sceneId;
    }
}
