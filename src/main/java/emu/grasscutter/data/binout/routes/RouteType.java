package emu.grasscutter.data.binout.routes;

import lombok.Getter;

public enum RouteType  {
    Unknown(-1),
    OneWay(0),
    Reciprocate(1),
    Loop(2);

    @Getter private final int id;

    RouteType(int id) {
        this.id = id;
    }

    public int getValue() {
        return id;
    }
}
