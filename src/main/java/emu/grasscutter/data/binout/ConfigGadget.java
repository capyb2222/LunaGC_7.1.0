package emu.grasscutter.data.binout;

import javax.annotation.Nullable;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConfigGadget {
    @Nullable ConfigGadgetCombat combat;
}
