package emu.grasscutter.data.excels.dungeon;

import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.RewardPreviewData;
import emu.grasscutter.game.dungeons.enums.*;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.scripts.data.SceneMeta;
import java.util.List;
import lombok.Getter;

@ResourceType(name = "DungeonExcelConfigData.json")
public class DungeonData extends GameResource {

    @Getter(onMethod_ = @Override)
    private int id;

    @Getter private int sceneId;
    @Getter private int showLevel;
    private DungeonType type;
    private DungeonSubType subType;
    private DungeonPlayType playType;
    private DungeonInvolveType involveType;
    @Getter private int limitLevel;
    @Getter private int passCond;
    @Getter private int passJumpDungeon;
    @Getter private int reviveMaxCount;
    @Getter private int settleCountdownTime;
    @Getter private int failSettleCountdownTime;
    @Getter private int quitSettleCountdownTime;
    @Getter private List<SettleShowType> settleShows;
    @Getter private int passRewardPreviewID;
    @Getter private int statueCostID;
    @Getter private int statueCostCount;
    @Getter private int statueDrop;

    @Getter private RewardPreviewData rewardPreviewData;

    public DungeonType getType() {
        if (type == null) {
            return DungeonType.DUNGEON_NONE;
        }
        return type;
    }

    public DungeonSubType getSubType() {
        if (subType == null) {
            return DungeonSubType.DUNGEON_SUB_NONE;
        }
        return subType;
    }

    public DungeonPlayType getPlayType() {
        if (playType == null) {
            return DungeonPlayType.DUNGEON_PLAY_TYPE_NONE;
        }
        return playType;
    }

    public DungeonInvolveType getInvolveType() {
        if (involveType == null) {
            return DungeonInvolveType.INVOLVE_NONE;
        }
        return involveType;
    }

    public Position getStartPosition() {
        var meta = SceneMeta.of(this.getSceneId());
        if (meta != null && meta.config != null && meta.config.born_pos != null) {
            return meta.config.born_pos;
        }
        var entry = this.getEntryPoint();
        return entry != null ? entry.getTranPos() : null;
    }

    public Position getStartRotation() {
        var meta = SceneMeta.of(this.getSceneId());
        if (meta != null && meta.config != null && meta.config.born_rot != null) {
            return meta.config.born_rot;
        }
        var entry = this.getEntryPoint();
        return entry != null ? entry.getTranRot() : null;
    }

    private PointData getEntryPoint() {
        var pointIds = GameData.getScenePointsPerScene().get(this.getSceneId());
        if (pointIds == null) return null;

        PointData fallback = null;
        for (int pointId : pointIds) {
            var entry = GameData.getScenePointEntryById(this.getSceneId(), pointId);
            if (entry == null || entry.getPointData() == null) continue;
            var point = entry.getPointData();
            if (point.getTranPos() == null) continue;
            if ("DungeonEntry".equals(point.getType())) return point;
            if (fallback == null) fallback = point;
        }
        return fallback;
    }

    @Override
    public void onLoad() {
        if (this.passRewardPreviewID > 0) {
            this.rewardPreviewData = GameData.getRewardPreviewDataMap().get(this.passRewardPreviewID);
        }
    }
}
