package emu.grasscutter.data.server;

import com.github.davidmoten.rtreemulti.RTree;
import com.github.davidmoten.rtreemulti.geometry.Geometry;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.world.*;
import emu.grasscutter.scripts.SceneIndexManager;
import java.util.*;

public class Grid {
    private static final int MAX_ENTITY_LOAD_RANGE = 500;

    public transient RTree<Map.Entry<GridPosition, Set<Integer>>, Geometry> gridOptimized = null;
    private transient Set<Integer> nearbyGroups = new HashSet<>(100);

    public Map<GridPosition, Set<Integer>> grid = new LinkedHashMap<>();

    private void optimize() {
        if (this.gridOptimized == null) {
            var gridValues = new ArrayList<Map.Entry<GridPosition, Set<Integer>>>();
            this.grid.forEach((k, v) -> gridValues.add(new AbstractMap.SimpleEntry<>(k, v)));
            this.gridOptimized =
                    SceneIndexManager.buildIndex(2, gridValues, entry -> entry.getKey().toPoint());
        }
    }

    public Map<GridPosition, Set<Integer>> getGrid() {
        return this.grid;
    }

    public Set<Integer> getNearbyGroups(int vision_level, Position position) {
        this.optimize();

        int width = Grasscutter.getConfig().server.game.visionOptions[vision_level].gridWidth;
        int vision_range = Grasscutter.getConfig().server.game.visionOptions[vision_level].visionRange;

        this.nearbyGroups.clear();

        if (!Grasscutter.getConfig().server.game.gameOptions.isPreventEntityError) {
            int vision_range_grid = vision_range / width;
            GridPosition pos = new GridPosition(position, width);
            SceneIndexManager.queryNeighbors(gridOptimized, pos.toDoubleArray(), vision_range_grid + 1)
                    .forEach(e -> nearbyGroups.addAll(e.getValue()));
            return this.nearbyGroups;
        }

        if (width > MAX_ENTITY_LOAD_RANGE) {
            return this.nearbyGroups;
        }

        int maxRangeGrid = Math.max(0, MAX_ENTITY_LOAD_RANGE / width - 1);
        int vision_range_grid = Math.min(vision_range, MAX_ENTITY_LOAD_RANGE) / width;
        int queryRange = Math.min(vision_range_grid + 1, maxRangeGrid);

        GridPosition pos = new GridPosition(position, width);

        SceneIndexManager.queryNeighbors(gridOptimized, pos.toDoubleArray(), queryRange)
                .forEach(e -> nearbyGroups.addAll(e.getValue()));
        return this.nearbyGroups;
    }
}
