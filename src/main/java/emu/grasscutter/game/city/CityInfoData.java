package emu.grasscutter.game.city;

import dev.morphia.annotations.Entity;
import emu.grasscutter.net.proto.CityInfoOuterClass.CityInfo;
import lombok.*;

@Entity
public class CityInfoData {
    @Getter @Setter private int cityId;

    @Getter @Setter
    private int level = 1;

    @Getter @Setter private int numCrystal = 0;

    public CityInfoData(int cityId) {
        this.cityId = cityId;
    }

    public CityInfo toProto() {
        return CityInfo.newBuilder()
                .setCityId(cityId)
                .setLevel(level)
                .setCrystalNum(numCrystal)
                .build();
    }
}
