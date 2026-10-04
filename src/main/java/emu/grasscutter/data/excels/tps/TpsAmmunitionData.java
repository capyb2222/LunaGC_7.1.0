package emu.grasscutter.data.excels.tps;

import emu.grasscutter.data.*;
import java.util.List;
import lombok.Getter;

@Getter
@ResourceType(name = "TpsAmmunitionExcelConfigData.json")
public final class TpsAmmunitionData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int tpsAmmoLimit;
    private List<Integer> ammoSlotIds = List.of();
}
