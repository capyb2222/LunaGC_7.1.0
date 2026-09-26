package emu.grasscutter.data.excels;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import lombok.Getter;

@ResourceType(name = "CutsceneExcelConfigData.json")
@Getter
public final class CutsceneData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private String path;
}
