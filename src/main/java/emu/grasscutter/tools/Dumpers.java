package emu.grasscutter.tools;

import emu.grasscutter.command.*;
import emu.grasscutter.command.Command.TargetRequirement;
import emu.grasscutter.data.*;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.utils.JsonUtils;
import emu.grasscutter.utils.lang.Language;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;

public interface Dumpers {

    private static String commandDescription(String locale, Command command) {
        try {
            var language = Language.getLanguage(locale);
            if (language == null) throw new IllegalArgumentException("Invalid language.");

            return language.get("commands." + command.label() + ".description");
        } catch (IllegalArgumentException ignored) {
            return command.label();
        }
    }

    private static String miniEncode(Map<Integer, ?> dump) {
        return dump.entrySet().stream()
                .map(entry -> entry.getKey() + "," + entry.getValue().toString())
                .collect(Collectors.joining("\n"));
    }

    private static String miniEncode(Map<Integer, ?> dump, String... headers) {
        return String.join(",", headers) + "\n" + Dumpers.miniEncode(dump);
    }

    static void dumpCommands(String locale) {
        var commandMap = CommandMap.getInstance();
        if (commandMap == null) commandMap = new CommandMap(true);

        var dump = new HashMap<String, CommandInfo>();
        commandMap
                .getAnnotationsAsList()
                .forEach(
                        command -> {
                            var description = Dumpers.commandDescription(locale, command);
                            var labels =
                                    new ArrayList<String>() {
                                        {
                                            this.add(command.label());
                                            this.addAll(List.of(command.aliases()));
                                        }
                                    };

                            dump.put(
                                    command.label(),
                                    new CommandInfo(
                                            labels,
                                            description,
                                            List.of(command.usage()),
                                            List.of(command.permission(), command.permissionTargeted()),
                                            command.targetRequirement()));
                        });

        try {
            var file = new File("commands.json");
            if (file.exists() && !file.delete()) throw new RuntimeException("Failed to delete file.");
            if (!file.exists() && !file.createNewFile())
                throw new RuntimeException("Failed to create file.");

            Files.writeString(file.toPath(), JsonUtils.encode(dump));
        } catch (IOException ignored) {
            throw new RuntimeException("Failed to write to file.");
        }
    }

    static void dumpAvatars(String locale) {
        ResourceLoader.loadAll();
        Language.loadTextMaps();

        var dump = new HashMap<Integer, AvatarInfo>();
        GameData.getAvatarDataMap()
                .forEach(
                        (id, avatar) -> {
                            var langHash = avatar.getNameTextMapHash();
                            dump.put(
                                    id,
                                    new AvatarInfo(
                                            langHash == 0
                                                    ? avatar.getName()
                                                    : Language.getTextMapKey(langHash).get(locale),
                                            avatar.getQualityType().equals("QUALITY_PURPLE")
                                                    ? Quality.EPIC
                                                    : Quality.LEGENDARY));
                        });

        try {
            var file = new File("avatars.csv");
            if (file.exists() && !file.delete()) throw new RuntimeException("Failed to delete file.");
            if (!file.exists() && !file.createNewFile())
                throw new RuntimeException("Failed to create file.");

            Files.writeString(file.toPath(), Dumpers.miniEncode(dump));
        } catch (IOException ignored) {
            throw new RuntimeException("Failed to write to file.");
        }
    }

    static void dumpItems(String locale) {
        ResourceLoader.loadAll();
        Language.loadTextMaps();

        var originalDump = new ArrayList<ItemInfo>();
        GameData.getItemDataMap()
                .forEach(
                        (id, item) ->
                                originalDump.add(
                                        new ItemInfo(
                                                id,
                                                Language.getTextMapKey(item.getNameTextMapHash()).get(locale),
                                                Quality.from(item.getRankLevel()),
                                                item.getItemType(),
                                                item.getIcon().length() > 0 ? item.getIcon().substring(3) : "")));

        var names = new ArrayList<String>();
        var dump = new HashMap<Integer, ItemInfo>();
        originalDump.forEach(
                item -> {
                    if (item.name.contains("[CHS]")) return;
                    if (names.contains(item.name)) return;
                    if (dump.containsKey(item.id)) return;
                    names.add(item.name);
                    dump.put(item.id, item);
                });

        try {
            var file = new File("items.csv");
            if (file.exists() && !file.delete()) throw new RuntimeException("Failed to delete file.");
            if (!file.exists() && !file.createNewFile())
                throw new RuntimeException("Failed to create file.");

            Files.writeString(file.toPath(), Dumpers.miniEncode(dump));
        } catch (IOException ignored) {
            throw new RuntimeException("Failed to write to file.");
        }
    }

    static void dumpScenes() {
        ResourceLoader.loadAll();
        Language.loadTextMaps();

        var dump = new HashMap<Integer, SceneInfo>();
        GameData.getSceneDataMap()
                .forEach(
                        (id, scene) ->
                                dump.put(id, new SceneInfo(scene.getScriptData(), scene.getSceneType())));

        try {
            var file = new File("scenes.csv");
            if (file.exists() && !file.delete()) throw new RuntimeException("Failed to delete file.");
            if (!file.exists() && !file.createNewFile())
                throw new RuntimeException("Failed to create file.");

            Files.writeString(file.toPath(), Dumpers.miniEncode(dump));
        } catch (IOException ignored) {
            throw new RuntimeException("Failed to write to file.");
        }
    }

    static void dumpEntities(String locale) {
        ResourceLoader.loadAll();
        Language.loadTextMaps();

        var dump = new HashMap<Integer, EntityInfo>();
        GameData.getMonsterDataMap()
                .forEach(
                        (id, monster) -> {
                            var langHash = monster.getNameTextMapHash();
                            dump.put(
                                    id,
                                    new EntityInfo(
                                            langHash == 0
                                                    ? monster.getMonsterName()
                                                    : Language.getTextMapKey(langHash).get(locale),
                                            monster.getMonsterName()));
                        });

        try {
            var file = new File("entities.csv");
            if (file.exists() && !file.delete()) throw new RuntimeException("Failed to delete file.");
            if (!file.exists() && !file.createNewFile())
                throw new RuntimeException("Failed to create file.");

            Files.writeString(file.toPath(), Dumpers.miniEncode(dump));
        } catch (IOException ignored) {
            throw new RuntimeException("Failed to write to file.");
        }
    }

    static void dumpQuests(String locale) {
        ResourceLoader.loadAll();
        Language.loadTextMaps();

        var dump = new HashMap<Integer, QuestInfo>();
        GameData.getQuestDataMap()
                .forEach(
                        (id, quest) -> {
                            var langHash = quest.getDescTextMapHash();
                            dump.put(
                                    id,
                                    new QuestInfo(
                                            langHash == 0
                                                    ? "Unknown"
                                                    : Language.getTextMapKey(langHash).get(locale).replaceAll(",", "\\\\"),
                                            quest.getMainId()));
                        });

        var mainDump = new HashMap<Integer, MainQuestInfo>();
        GameData.getMainQuestDataMap()
                .forEach(
                        (id, mainQuest) -> {
                            var langHash = mainQuest.getTitleTextMapHash();
                            mainDump.put(
                                    id,
                                    new MainQuestInfo(
                                            langHash == 0
                                                    ? "Unknown"
                                                    : Language.getTextMapKey(langHash).get(locale).replaceAll(",", "\\\\")));
                        });

        try {
            var file = new File("quests.csv");
            if (file.exists() && !file.delete()) throw new RuntimeException("Failed to delete file.");
            if (!file.exists() && !file.createNewFile())
                throw new RuntimeException("Failed to create file.");

            Files.writeString(file.toPath(), Dumpers.miniEncode(dump, "id", "description", "mainId"));
        } catch (IOException ignored) {
            throw new RuntimeException("Failed to write to file.");
        }

        try {
            var file = new File("mainquests.csv");
            if (file.exists() && !file.delete()) throw new RuntimeException("Failed to delete file.");
            if (!file.exists() && !file.createNewFile())
                throw new RuntimeException("Failed to create file.");

            Files.writeString(file.toPath(), Dumpers.miniEncode(mainDump, "id", "title"));
        } catch (IOException ignored) {
            throw new RuntimeException("Failed to write to file.");
        }
    }

    static void dumpAreas(String locale) {
        ResourceLoader.loadAll();
        Language.loadTextMaps();

        var dump = new HashMap<Integer, AreaInfo>();
        GameData.getWorldAreaDataMap()
                .forEach(
                        (id, area) -> {
                            var langHash = area.getTextMapHash();
                            dump.put(
                                    area.getChildArea() == 0 ? area.getParentArea() : area.getChildArea(),
                                    new AreaInfo(
                                            area.getParentArea(),
                                            langHash == 0 ? "Unknown" : Language.getTextMapKey(langHash).get(locale)));
                        });

        try {
            var file = new File("areas.csv");
            if (file.exists() && !file.delete()) throw new RuntimeException("Failed to delete file.");
            if (!file.exists() && !file.createNewFile())
                throw new RuntimeException("Failed to create file.");

            Files.writeString(file.toPath(), Dumpers.miniEncode(dump, "id", "parent", "name"));
        } catch (IOException ignored) {
            throw new RuntimeException("Failed to write to file.");
        }
    }

    @AllArgsConstructor
    class CommandInfo {
        public List<String> name;
        public String description;
        public List<String> usage;
        public List<String> permission;
        public TargetRequirement target;
    }

    @AllArgsConstructor
    class AvatarInfo {
        public String name;
        public Quality quality;

        @Override
        public String toString() {
            return this.name + "," + this.quality;
        }
    }

    @AllArgsConstructor
    class ItemInfo {
        public Integer id;
        public String name;
        public Quality quality;
        public ItemType type;
        public String icon;

        @Override
        public String toString() {
            return this.name + "," + this.quality + "," + this.type + "," + this.icon;
        }
    }

    @AllArgsConstructor
    class SceneInfo {
        public String identifier;
        public SceneType type;

        @Override
        public String toString() {
            return this.identifier + "," + this.type;
        }
    }

    @AllArgsConstructor
    class EntityInfo {
        public String name;
        public String internal;

        @Override
        public String toString() {
            return this.name + "," + this.internal;
        }
    }

    @AllArgsConstructor
    class MainQuestInfo {
        public String title;

        @Override
        public String toString() {
            return this.title;
        }
    }

    @AllArgsConstructor
    class QuestInfo {
        public String description;
        public int mainQuest;

        @Override
        public String toString() {
            return this.description + "," + this.mainQuest;
        }
    }

    @AllArgsConstructor
    class AreaInfo {
        public int parent;
        public String name;

        @Override
        public String toString() {
            return this.parent + "," + this.name;
        }
    }

    enum Quality {
        LEGENDARY,
        EPIC,
        RARE,
        UNCOMMON,
        COMMON,
        UNKNOWN;

        static Quality from(int rankLevel) {
            return switch (rankLevel) {
                case 0 -> UNKNOWN;
                case 1 -> COMMON;
                case 2 -> UNCOMMON;
                case 3 -> RARE;
                case 4 -> EPIC;
                default -> LEGENDARY;
            };
        }
    }
}
