package emu.grasscutter.data.binout;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConfigGadgetCombat {
    ConfigGadgetCombatProperty property;
}
