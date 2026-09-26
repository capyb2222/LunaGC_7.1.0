package emu.grasscutter.data.excels;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import java.util.List;
import lombok.*;
import lombok.experimental.FieldDefaults;

@ResourceType(name = "PersonalLineExcelConfigData.json")
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PersonalLineData extends GameResource {
    @Getter(onMethod_ = @Override)
    int id;

    @SerializedName(value = "avatarId", alternate = "avatarID")
    int avatarID;
    List<Integer> preQuestId;
    int startQuestId;
    int chapterId;
}
