package emu.grasscutter.data.excels;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import java.util.List;
import lombok.*;
import lombok.experimental.FieldDefaults;

@ResourceType(name = "CoopChapterExcelConfigData.json")
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CoopChapterData extends GameResource {
    @Getter(onMethod_ = @Override)
    int id;

    int avatarId;
    List<CoopCondition> unlockCond;

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    private static class CoopCondition {
        @SerializedName(
                value = "_condType",
                alternate = {"condType"})
        String type = "COOP_COND_NONE";

        @SerializedName(
                value = "_args",
                alternate = {"args"})
        int[] args;
    }
}
