package emu.grasscutter.data.excels.tps;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import java.util.List;
import lombok.Getter;

@Getter
@ResourceType(name = "TpsWeaponExcelConfigData.json")
public final class TpsWeaponData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int gadgetId;
    private String tpsWeaponType;
    private List<Integer> ammoSlotIds = List.of();
    private List<Integer> tpsWeaponBaseAffix = List.of();

    @SerializedName(value = "equipAffixId", alternate = "PIHHHIMPCAM")
    private int equipAffixId;

    @SerializedName(value = "wearSlotType", alternate = "NNJGEAPMJFL")
    private int wearSlotType;
}
