package emu.grasscutter.utils.objects;

import com.google.gson.*;
import emu.grasscutter.server.dispatch.IDispatcher;
import emu.grasscutter.utils.Utils;
import java.lang.reflect.Field;
import java.util.HashMap;

public interface FieldFetch {
    default JsonObject fetchFields(String... fields) {
        var fieldValues = new JsonObject();
        var fieldMap = new HashMap<String, Field>();
        Utils.getAllFields(this.getClass()).forEach(field -> fieldMap.put(field.getName(), field));

        for (var fieldName : fields) {
            try {
                var field = fieldMap.get(fieldName);
                if (field == null) fieldValues.add(fieldName, JsonNull.INSTANCE);
                else {
                    var wasAccessible = field.canAccess(this);
                    field.setAccessible(true);
                    fieldValues.add(fieldName, IDispatcher.JSON.toJsonTree(field.get(this)));
                    field.setAccessible(wasAccessible);
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }

        return fieldValues;
    }
}
