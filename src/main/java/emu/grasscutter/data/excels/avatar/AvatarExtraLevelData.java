package emu.grasscutter.data.excels.avatar;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import java.util.Arrays;
import lombok.Getter;

@ResourceType(name = "AvatarExtraLevelExcelConfigData.json")
@Getter
public class AvatarExtraLevelData extends GameResource {
    @SerializedName(value = "requireLevel", alternate = {"DEJBOJMHHJB"})
    private int requireLevel;

    @SerializedName(value = "maxLevel", alternate = {"BBPEGJLEEEC"})
    private int maxLevel;

    private ItemParamData[] costItems;

    @Override
    public int getId() {
        return this.requireLevel;
    }

    @Override
    public void onLoad() {
        this.costItems =
                this.costItems == null
                        ? new ItemParamData[0]
                        : Arrays.stream(this.costItems)
                                .filter(item -> item.getId() > 0)
                                .toArray(ItemParamData[]::new);

        if (this.requireLevel <= 0 || this.maxLevel <= this.requireLevel) {
            Grasscutter.getLogger()
                    .warn(
                            "AvatarExtraLevelExcelConfigData row has requireLevel {} and maxLevel {} - its"
                                    + " level keys were probably renamed",
                            this.requireLevel,
                            this.maxLevel);
        }
    }
}
