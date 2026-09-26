package emu.grasscutter.data;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.ResourceLoader.AvatarConfig;
import emu.grasscutter.data.excels.reliquary.*;
import emu.grasscutter.game.inventory.EquipType;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.managers.blossom.BlossomConfig;
import emu.grasscutter.game.world.SpawnDataEntry;
import emu.grasscutter.utils.objects.WeightedList;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.*;

public class GameDepot {
    public static final int[] BLOCK_SIZE = new int[] {50, 500};

    private static Int2ObjectMap<WeightedList<ReliquaryMainPropData>> relicRandomMainPropDepot =
            new Int2ObjectOpenHashMap<>();
    private static Int2ObjectMap<List<ReliquaryMainPropData>> relicMainPropDepot =
            new Int2ObjectOpenHashMap<>();
    private static Int2ObjectMap<List<ReliquaryAffixData>> relicAffixDepot =
            new Int2ObjectOpenHashMap<>();
    private static Int2IntMap relicAffixWeight = new Int2IntOpenHashMap();
    @Getter private static double fourSubstatStartChance = 0.2;

    @Getter @Setter private static Map<String, AvatarConfig> playerAbilities = new HashMap<>();

    @Getter
    private static HashMap<SpawnDataEntry.GridBlockId, ArrayList<SpawnDataEntry>> spawnLists =
            new HashMap<>();

    @Getter @Setter private static BlossomConfig blossomConfig;

    @Getter
    public static final class ArtifactRollOdds {
        private Map<EquipType, Map<FightProperty, Integer>> mainStat = Map.of();
        private Map<FightProperty, Integer> subStat = Map.of();
        private double fourSubstatStartChance = 0.2;
    }

    private static ArtifactRollOdds loadArtifactRollOdds() {
        try {
            var odds = DataLoader.loadClass("ArtifactRollOdds.json", ArtifactRollOdds.class);
            if (odds != null) return odds;
        } catch (Exception e) {
            Grasscutter.getLogger().warn("ArtifactRollOdds.json could not be read, using excel weights", e);
        }
        return new ArtifactRollOdds();
    }

    public static void load() {
        var odds = loadArtifactRollOdds();
        fourSubstatStartChance = odds.getFourSubstatStartChance();
        Int2ObjectMap<EquipType> depotSlots = new Int2ObjectOpenHashMap<>();
        GameData.getItemDataMap().values().stream()
                .filter(item -> item.getItemType() == ItemType.ITEM_RELIQUARY)
                .filter(item -> item.getMainPropDepotId() > 0)
                .forEach(item -> depotSlots.putIfAbsent(item.getMainPropDepotId(), item.getEquipType()));

        GameData.getReliquaryMainPropDataMap().values().stream()
                .filter(data -> data.getPropDepotId() > 0)
                .collect(Collectors.groupingBy(ReliquaryMainPropData::getPropDepotId))
                .forEach(
                        (depot, entries) -> {
                            var slotOdds = odds.getMainStat().get(depotSlots.get((int) depot));
                            boolean useOdds =
                                    slotOdds != null
                                            && entries.stream()
                                                    .anyMatch(e -> slotOdds.getOrDefault(e.getFightProp(), 0) > 0);
                            for (var data : entries) {
                                int weight =
                                        useOdds
                                                ? slotOdds.getOrDefault(data.getFightProp(), 0)
                                                : data.getWeight();
                                if (weight <= 0) continue;
                                relicMainPropDepot.computeIfAbsent(depot, k -> new ArrayList<>()).add(data);
                                relicRandomMainPropDepot
                                        .computeIfAbsent(depot, k -> new WeightedList<>())
                                        .add(weight, data);
                            }
                        });

        for (ReliquaryAffixData data : GameData.getReliquaryAffixDataMap().values()) {
            int oddsWeight = odds.getSubStat().getOrDefault(data.getFightProp(), 0);
            if ((data.getWeight() <= 0 && oddsWeight <= 0) || data.getDepotId() <= 0) {
                continue;
            }
            List<ReliquaryAffixData> list =
                    relicAffixDepot.computeIfAbsent(data.getDepotId(), k -> new ArrayList<>());
            list.add(data);
        }
        relicAffixDepot.values().forEach(depot -> weighAffixes(depot, odds.getSubStat()));
        if (relicMainPropDepot.size() == 0 || relicAffixDepot.size() == 0) {
            Grasscutter.getLogger()
                    .error(
                            "Relic properties are missing weights! Please check your ReliquaryMainPropExcelConfigData or ReliquaryAffixExcelConfigData files in your ExcelBinOutput folder.");
        }
    }

    public static ReliquaryMainPropData getRandomRelicMainProp(int depot) {
        WeightedList<ReliquaryMainPropData> depotList = relicRandomMainPropDepot.get(depot);
        if (depotList == null) {
            return null;
        }
        return depotList.next();
    }

    public static List<ReliquaryMainPropData> getRelicMainPropList(int depot) {
        return relicMainPropDepot.get(depot);
    }

    public static List<ReliquaryAffixData> getRelicAffixList(int depot) {
        return relicAffixDepot.get(depot);
    }

    private static void weighAffixes(
            List<ReliquaryAffixData> depot, Map<FightProperty, Integer> subStatOdds) {
        boolean useOdds = depot.stream().anyMatch(a -> subStatOdds.getOrDefault(a.getFightProp(), 0) > 0);
        var rolls = depot.stream().collect(Collectors.groupingBy(ReliquaryAffixData::getFightProp, Collectors.counting()));
        for (var affix : depot) {
            int weight =
                    useOdds
                            ? (int) (subStatOdds.getOrDefault(affix.getFightProp(), 0) * 1200 / rolls.get(affix.getFightProp()))
                            : affix.getWeight();
            relicAffixWeight.put(affix.getId(), weight);
        }
    }

    public static int getRelicAffixWeight(ReliquaryAffixData affix) {
        return relicAffixWeight.getOrDefault(affix.getId(), affix.getWeight());
    }

    public static void addSpawnListById(
            HashMap<SpawnDataEntry.GridBlockId, ArrayList<SpawnDataEntry>> data) {
        spawnLists.putAll(data);
    }
}
