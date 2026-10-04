package emu.grasscutter.data.excels.tps;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import java.util.List;
import lombok.Getter;

@Getter
@ResourceType(name = "TpsWeaponAccessoryExcelConfigData.json")
public final class TpsWeaponAccessoryData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int tpsWeaponId;
    private List<Integer> tpsWeaponBaseAffix = List.of();

    @SerializedName(value = "equipAffixId", alternate = "PIHHHIMPCAM")
    private int equipAffixId;

    @SerializedName(value = "unlockMaterialId", alternate = "KOHCBGDKMPG")
    private int unlockMaterialId;
}
