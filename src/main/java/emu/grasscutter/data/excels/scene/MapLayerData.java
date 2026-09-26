package emu.grasscutter.data.excels.scene;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import lombok.Getter;

@ResourceType(name = "MapLayerExcelConfigData.json")
@Getter
public final class MapLayerData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    @SerializedName(value = "BDPONBHJAHL", alternate = {"AFHDCMCENLN", "JBACHLCJFPA"})
    private int idk1;

    @SerializedName(value = "HNHPONNDEHO", alternate = "MHIONMOOBAA")
    private float level;
}