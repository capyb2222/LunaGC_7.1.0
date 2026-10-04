package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import java.util.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@ResourceType(name = "ConstValueExcelConfigData.json", loadPriority = ResourceType.LoadPriority.HIGHEST)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConstValueData extends GameResource {
    private static final Map<String, ConstValueData> byName = new HashMap<>();

    @Getter String name;
    List<String> value;

    @Override
    public int getId() {
        return this.name == null ? 0 : this.name.hashCode();
    }

    @Override
    public void onLoad() {
        if (this.name != null) byName.put(this.name, this);
    }

    public static List<String> get(String name) {
        var constant = byName.get(name);
        return constant == null || constant.value == null ? List.of() : constant.value;
    }

    public static String get(String name, int index) {
        var values = get(name);
        return index < values.size() && values.get(index) != null ? values.get(index).trim() : "";
    }

    public static int getInt(String name, int index, int fallback) {
        try {
            return Integer.parseInt(get(name, index));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
