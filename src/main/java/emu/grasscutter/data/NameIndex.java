package emu.grasscutter.data;

import emu.grasscutter.game.inventory.EquipType;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.utils.lang.Language;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class NameIndex {
    private static final Index THINGS = new Index();

    private static final Index ENTITIES = new Index();

    private static final Index SETS = new Index();

    private static boolean built;

    private NameIndex() {}

    public static int resolve(String first, List<String> rest) {
        build();
        return THINGS.resolve(first, rest);
    }

    public static int resolveEntity(String first, List<String> rest) {
        build();
        return ENTITIES.resolve(first, rest);
    }

    private static final Map<String, EquipType> SLOTS =
            Map.ofEntries(
                    Map.entry("flower", EquipType.EQUIP_BRACER),
                    Map.entry("bracer", EquipType.EQUIP_BRACER),
                    Map.entry("plume", EquipType.EQUIP_NECKLACE),
                    Map.entry("feather", EquipType.EQUIP_NECKLACE),
                    Map.entry("necklace", EquipType.EQUIP_NECKLACE),
                    Map.entry("sands", EquipType.EQUIP_SHOES),
                    Map.entry("sand", EquipType.EQUIP_SHOES),
                    Map.entry("timepiece", EquipType.EQUIP_SHOES),
                    Map.entry("hourglass", EquipType.EQUIP_SHOES),
                    Map.entry("goblet", EquipType.EQUIP_RING),
                    Map.entry("cup", EquipType.EQUIP_RING),
                    Map.entry("circlet", EquipType.EQUIP_DRESS),
                    Map.entry("crown", EquipType.EQUIP_DRESS),
                    Map.entry("hat", EquipType.EQUIP_DRESS));

    public static int resolveRelic(String first, List<String> rest) {
        build();

        var match = SETS.match(first, rest);
        if (match == null || match.consumed >= rest.size()) return 0;

        var slot = SLOTS.get(normalise(rest.get(match.consumed)));
        if (slot == null) return 0;

        var piece = bestPiece(match.id, slot);
        if (piece == 0) return 0;

        for (var i = 0; i <= match.consumed; i++) rest.remove(0);
        return piece;
    }

    private static final List<EquipType> WHOLE_SET =
            List.of(
                    EquipType.EQUIP_BRACER,
                    EquipType.EQUIP_NECKLACE,
                    EquipType.EQUIP_SHOES,
                    EquipType.EQUIP_RING,
                    EquipType.EQUIP_DRESS);

    private static final Set<String> EVERY_SLOT = Set.of("all", "set", "full", "everything");

    public static List<Integer> resolveRelicSet(String first, List<String> rest) {
        build();

        var match = SETS.match(first, rest);
        if (match == null || match.consumed >= rest.size()) return List.of();
        if (!EVERY_SLOT.contains(normalise(rest.get(match.consumed)))) return List.of();

        var pieces = new ArrayList<Integer>(WHOLE_SET.size());
        for (var slot : WHOLE_SET) {
            var piece = bestPiece(match.id, slot);
            if (piece != 0) pieces.add(piece);
        }

        if (pieces.isEmpty()) return List.of();

        for (var i = 0; i <= match.consumed; i++) rest.remove(0);
        return pieces;
    }

    public static void warmUpInBackground() {
        var thread = new Thread(NameIndex::build, "name-index");
        thread.setDaemon(true);
        thread.start();
    }

    public static String describe(int id) {
        build();

        var name = nameOf(id);
        return name == null || name.isBlank() ? String.valueOf(id) : name + " (" + id + ")";
    }

    public static List<String> search(String query, int limit) {
        build();

        var needle = normalise(query);
        if (needle.isEmpty()) return List.of();

        var matches = new ArrayList<Map.Entry<String, Integer>>();
        var seen = new HashSet<Integer>();

        for (var index : List.of(THINGS, ENTITIES)) {
            for (var entry : index.byName.entrySet()) {
                if (entry.getKey().contains(needle) && seen.add(entry.getValue())) matches.add(entry);
            }
        }

        matches.sort(
                java.util.Comparator.comparingInt((Map.Entry<String, Integer> e) -> internal(e.getValue()))
                        .thenComparingInt(e -> rank(e.getKey(), needle))
                        .thenComparingInt(e -> e.getKey().length())
                        .thenComparing(Map.Entry::getKey));

        return matches.stream().limit(limit).map(e -> describe(e.getValue())).toList();
    }

    private static int internal(int id) {
        var name = nameOf(id);
        return name != null && name.indexOf('_') >= 0 ? 1 : 0;
    }

    private static int rank(String name, String needle) {
        if (name.equals(needle)) return 0;
        if (name.startsWith(needle)) return 1;
        return 2;
    }

    private static String nameOf(int id) {
        var item = GameData.getItemDataMap().get(id);
        if (item != null) return text(item.getNameTextMapHash());

        var avatar = GameData.getAvatarDataMap().get(id);
        if (avatar != null) return text(avatar.getNameTextMapHash());

        var monster = GameData.getMonsterDataMap().get(id);
        if (monster != null) {
            var describe = GameData.getMonsterDescribeDataMap().get(monster.getDescribeId());
            var named = describe == null ? null : text(describe.getNameTextMapHash());
            return named != null ? named : monster.getMonsterName();
        }

        var gadget = GameData.getGadgetDataMap().get(id);
        return gadget == null ? null : gadget.getJsonName();
    }

    private static int bestPiece(int setId, EquipType slot) {
        var best = 0;
        var bestRank = -1;

        for (var item : GameData.getItemDataMap().values()) {
            if (item.getSetId() != setId || item.getEquipType() != slot) continue;

            if (item.getRankLevel() > bestRank || (item.getRankLevel() == bestRank && item.getId() < best)) {
                bestRank = item.getRankLevel();
                best = item.getId();
            }
        }

        return best;
    }

    private static synchronized void build() {
        if (built) return;
        built = true;

        GameData.getAvatarDataMap()
                .forEach((id, avatar) -> THINGS.claim(text(avatar.getNameTextMapHash()), id, 3));

        GameData.getItemDataMap()
                .forEach(
                        (id, item) -> {
                            var name = text(item.getNameTextMapHash());
                            var weapon = item.getItemType() == ItemType.ITEM_WEAPON;
                            THINGS.claim(name, id, weapon ? 2 : 1);
                            ENTITIES.claim(name, id, 1);
                        });

        GameData.getMonsterDataMap()
                .forEach(
                        (id, monster) -> {
                            var describe = GameData.getMonsterDescribeDataMap().get(monster.getDescribeId());
                            if (describe != null) ENTITIES.claim(text(describe.getNameTextMapHash()), id, 3);

                            ENTITIES.claim(monster.getMonsterName(), id, 2);
                        });

        GameData.getGadgetDataMap()
                .forEach((id, gadget) -> ENTITIES.claim(gadget.getJsonName(), id, 2));

        var affixNames = new HashMap<Integer, String>();
        GameData.getEquipAffixDataMap()
                .forEach(
                        (affixId, affix) ->
                                affixNames.putIfAbsent(affix.getMainId(), text(affix.getNameTextMapHash())));

        GameData.getReliquarySetDataMap()
                .forEach((setId, set) -> SETS.claim(affixNames.get(set.getEquipAffixId()), setId, 1));
    }

    private static String text(long hash) {
        var strings = Language.getTextMapKey(hash);
        return strings == null ? null : strings.get(0);
    }

    private static final class Index {
        private final TreeMap<String, Integer> byName = new TreeMap<>();

        private final Map<String, Integer> rank = new HashMap<>();

        void claim(String name, int id, int claimant) {
            var key = normalise(name);
            if (key.isEmpty()) return;

            var held = this.rank.getOrDefault(key, 0);
            if (claimant < held || (claimant == held && this.byName.get(key) <= id)) return;

            this.rank.put(key, claimant);
            this.byName.put(key, id);
        }

        Match match(String first, List<String> rest) {
            var phrase = new StringBuilder(normalise(first));
            var found = this.byName.get(phrase.toString());
            var best = found == null ? null : new Match(found, 0);

            for (var i = 0; i < rest.size(); i++) {
                phrase.append(normalise(rest.get(i)));

                var candidate = this.byName.get(phrase.toString());
                if (candidate != null) best = new Match(candidate, i + 1);
                else if (!isPrefix(phrase.toString())) break;
            }

            return best;
        }

        int resolve(String first, List<String> rest) {
            var phrase = new StringBuilder(normalise(first));
            var best = this.byName.getOrDefault(phrase.toString(), 0);
            var consumed = 0;

            for (var i = 0; i < rest.size(); i++) {
                phrase.append(normalise(rest.get(i)));

                var candidate = this.byName.get(phrase.toString());
                if (candidate != null) {
                    best = candidate;
                    consumed = i + 1;
                } else if (!isPrefix(phrase.toString())) {
                    break;
                }
            }

            for (var i = 0; i < consumed; i++) rest.remove(0);
            return best;
        }

        private boolean isPrefix(String phrase) {
            var next = this.byName.ceilingKey(phrase);
            return next != null && next.startsWith(phrase);
        }
    }

    private record Match(int id, int consumed) {}

    private static String normalise(String text) {
        if (text == null) return "";

        var builder = new StringBuilder(text.length());
        for (var c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) builder.append(c);
        }

        return builder.toString();
    }
}
