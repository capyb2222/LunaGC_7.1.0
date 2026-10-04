package emu.grasscutter.game.props;

import it.unimi.dsi.fastutil.ints.*;
import java.util.stream.Stream;
import lombok.Getter;

public enum PlayerProperty {
    PROP_NONE(0),
    PROP_EXP(1001, 0),
    PROP_BREAK_LEVEL(1002),
    PROP_SATIATION_VAL(1003),
    PROP_SATIATION_PENALTY_TIME(1004),
    PROP_LEVEL(4001, 0, 90),
    PROP_LAST_CHANGE_AVATAR_TIME(10001),
    PROP_MAX_SPRING_VOLUME(
            10002, 0, 8_500_000),
    PROP_CUR_SPRING_VOLUME(
            10003, true),
    PROP_IS_SPRING_AUTO_USE(
            10004, 0, 1),
    PROP_SPRING_AUTO_USE_PERCENT(10005, 0, 100),
    PROP_IS_FLYABLE(
            10006, 0, 1),
    PROP_IS_WEATHER_LOCKED(10007, 0, 1),
    PROP_IS_GAME_TIME_LOCKED(10008, 0, 1),
    PROP_IS_TRANSFERABLE(10009, 0, 1),
    PROP_MAX_STAMINA(10010, 0, 24_000),
    PROP_CUR_PERSIST_STAMINA(10011, true),
    PROP_CUR_TEMPORARY_STAMINA(10012),
    PROP_PLAYER_LEVEL(10013, 1, 60),
    PROP_PLAYER_EXP(10014, 0),
    PROP_PLAYER_HCOIN(10015),
    PROP_PLAYER_SCOIN(10016, 0),
    PROP_PLAYER_MP_SETTING_TYPE(
            10017, 0, 2),
    PROP_IS_MP_MODE_AVAILABLE(10018, 0, 1),
    PROP_PLAYER_WORLD_LEVEL(10019, 0, 9),
    PROP_PLAYER_RESIN(
            10020, 0, 2000),
    PROP_PLAYER_WAIT_SUB_HCOIN(10022),
    PROP_PLAYER_WAIT_SUB_SCOIN(10023),
    PROP_IS_ONLY_MP_WITH_PS_PLAYER(10024, 0, 1),
    PROP_PLAYER_MCOIN(10025),
    PROP_PLAYER_WAIT_SUB_MCOIN(10026),
    PROP_PLAYER_LEGENDARY_KEY(10027, 0),
    PROP_IS_HAS_FIRST_SHARE(10028),
    PROP_PLAYER_FORGE_POINT(10029, 0, 300_000),
    PROP_CUR_CLIMATE_METER(10035),
    PROP_CUR_CLIMATE_TYPE(10036),
    PROP_CUR_CLIMATE_AREA_ID(10037),
    PROP_CUR_CLIMATE_AREA_CLIMATE_TYPE(10038),
    PROP_PLAYER_WORLD_LEVEL_LIMIT(10039, 0, 9),
    PROP_PLAYER_WORLD_LEVEL_ADJUST_CD(10040),
    PROP_PLAYER_LEGENDARY_DAILY_TASK_NUM(10041),
    PROP_PLAYER_HOME_COIN(10042, 0),
    PROP_PLAYER_WAIT_SUB_HOME_COIN(10043),
    PROP_IS_AUTO_UNLOCK_SPECIFIC_EQUIP(10044),
    PROP_PLAYER_GCG_COIN(10045),
    PROP_PLAYER_WAIT_SUB_GCG_COIN(10046),
    PROP_PLAYER_ONLINE_TIME(10047),
    PROP_PLAYER_CAN_DIVE(10048, 0, 1),
    PROP_DIVE_MAX_STAMINA(
            10049, 0, 10000),
    PROP_DIVE_CUR_STAMINA(
        10050, 0, 10000),
        PROP_PHLOGISTON_ENABLE(10052, 0, 1), 
        PROP_PHLOGISTON_MAX_VALUE(
                10053, 0, 10000),
                PROP_CUR_PHLOGISTON(10054),
    PROP_MAX_TPS_STAMINA(10080, 0),
    PROP_CUR_PERSIST_TPS_STAMINA(10081, true);

    private static final int inf = Integer.MAX_VALUE;
    private static final Int2ObjectMap<PlayerProperty> map = new Int2ObjectOpenHashMap<>();

    static {
        Stream.of(values()).forEach(e -> map.put(e.getId(), e));
    }

    @Getter private final int id, min, max;
    @Getter private final boolean dynamicRange;

    PlayerProperty(int id, int min, int max, boolean dynamicRange) {
        this.id = id;
        this.min = min;
        this.max = max;
        this.dynamicRange = dynamicRange;
    }

    PlayerProperty(int id, int min) {
        this(id, min, inf, false);
    }

    PlayerProperty(int id, int min, int max) {
        this(id, min, max, false);
    }

    PlayerProperty(int id) {
        this(id, Integer.MIN_VALUE, inf, false);
    }

    PlayerProperty(int id, boolean dynamicRange) {
        this(id, Integer.MIN_VALUE, inf, dynamicRange);
    }

    public static PlayerProperty getPropById(int value) {
        return map.getOrDefault(value, null);
    }
}
