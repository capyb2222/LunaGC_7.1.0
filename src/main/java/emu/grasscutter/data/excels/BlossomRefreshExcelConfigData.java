package emu.grasscutter.data.excels;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import java.util.List;
import lombok.Getter;

@ResourceType(name = "BlossomRefreshExcelConfigData.json")
@Getter
public class BlossomRefreshExcelConfigData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;
    private long nameTextMapHash;
    private long descTextMapHash;
    private String icon;
    private String clientShowType;

    private String refreshType;
    private int refreshCount;
    private String refreshTime;
    @SerializedName(value = "RefreshCondVec", alternate = "refreshCondVec")
    private RefreshCond[] refreshCondVec;

    private int cityId;
    private int blossomChestId;
    @SerializedName(value = "DropVec", alternate = "dropVec")
    private Drop[] dropVec;

    @Getter
    public static class Drop {
        int dropId;
        int previewReward;
    }

    @Getter
    public static class RefreshCond {
        String type;
        List<Integer> param;
    }
}
