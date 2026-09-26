package emu.grasscutter.data.binout.config.fields;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConfigCombat {
    ConfigCombatProperty property;
    ConfigCombatSummon summon;
}
