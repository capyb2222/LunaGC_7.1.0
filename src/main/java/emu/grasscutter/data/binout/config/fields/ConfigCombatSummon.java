package emu.grasscutter.data.binout.config.fields;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.*;

@Data
public class ConfigCombatSummon {
    List<SummonTag> summonTags;

    @Getter
    public final class SummonTag {
        @SerializedName(value = "summonTag", alternate = {"PCLFAKBGHCI"})
        int summonTag;
    }
}
