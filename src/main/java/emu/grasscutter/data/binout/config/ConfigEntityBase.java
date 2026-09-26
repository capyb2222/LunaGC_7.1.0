package emu.grasscutter.data.binout.config;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.binout.config.fields.*;
import java.util.Collection;
import javax.annotation.Nullable;
import lombok.Data;

@Data
public class ConfigEntityBase {
    @Nullable ConfigCommon configCommon;
    @Nullable ConfigCombat combat;
    Collection<ConfigAbilityData> abilities;
    @SerializedName(value = "globalValue", alternate = {"OCDDHEEDBBH"})
    ConfigGlobalValue globalValue;
}
