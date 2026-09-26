package emu.grasscutter.utils.lang;

import static emu.grasscutter.config.Configuration.FALLBACK_LANGUAGE;
import static emu.grasscutter.utils.FileUtils.*;

import com.google.gson.*;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.achievement.AchievementData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.*;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;
import java.util.stream.*;
import lombok.EqualsAndHashCode;

public final class Language {
    private static final Map<String, Language> cachedLanguages = new ConcurrentHashMap<>();
    private static final int TEXTMAP_CACHE_VERSION = 0x9CCACE07;
    private static final Pattern textMapKeyValueRegex = Pattern.compile("\"(\\d+)\": \"(.+)\"");
    private static final Path TEXTMAP_CACHE_PATH = getCachePath("TextMap/TextMapCache.bin");
    private static boolean scannedTextmaps =
            false;
    private static Int2ObjectMap<TextStrings> textMapStrings;
    private final String languageCode;
    private final Map<String, String> translations = new ConcurrentHashMap<>();

    private Language(LanguageStreamDescription description) {
        languageCode = description.getLanguageCode();

        try {
            var object =
                    JsonUtils.decode(
                            Utils.readFromInputStream(description.getLanguageFile()), JsonObject.class);
            object
                    .entrySet()
                    .forEach(entry -> putFlattenedKey(translations, entry.getKey(), entry.getValue()));
        } catch (Exception exception) {
            Grasscutter.getLogger()
                    .warn("Failed to load language file: " + description.getLanguageCode(), exception);
        }
    }

    public static Language getLanguage(String langCode) {
        if (cachedLanguages.containsKey(langCode)) {
            return cachedLanguages.get(langCode);
        }

        var fallbackLanguageCode = Utils.getLanguageCode(FALLBACK_LANGUAGE);
        var description = getLanguageFileDescription(langCode, fallbackLanguageCode);
        var actualLanguageCode = description.getLanguageCode();

        Language languageInst;
        if (description.getLanguageFile() != null) {
            languageInst = new Language(description);
            cachedLanguages.put(actualLanguageCode, languageInst);
        } else {
            languageInst = cachedLanguages.get(actualLanguageCode);
            cachedLanguages.put(langCode, languageInst);
        }

        return languageInst;
    }

    public static String translate(String key, Object... args) {
        String translated = Grasscutter.getLanguage().get(key);

        for (int i = 0; i < args.length; i++) {
            args[i] =
                    switch (args[i].getClass().getSimpleName()) {
                        case "String" -> args[i];
                        case "TextStrings" -> ((TextStrings) args[i])
                                .get(0)
                                .replace("\\\\n", "\\n");
                        default -> args[i].toString();
                    };
        }

        try {
            return translated.formatted(args);
        } catch (Exception exception) {
            Grasscutter.getLogger().error("Failed to format string: " + key, exception);
            return translated;
        }
    }

    public static String translate(Locale locale, String key, Object... args) {
        if (locale == null) {
            return translate(key, args);
        }

        var langCode = Utils.getLanguageCode(locale);
        var translated = getLanguage(langCode).get(key);

        for (var i = 0; i < args.length; i++) {
            args[i] =
                    switch (args[i].getClass().getSimpleName()) {
                        case "String" -> args[i];
                        case "TextStrings" -> ((TextStrings) args[i])
                                .getGC(langCode)
                                .replace("\\\\n", "\n");
                        default -> args[i].toString();
                    };
        }

        try {
            return translated.formatted(args);
        } catch (Exception exception) {
            Grasscutter.getLogger().error("Failed to format string: " + key, exception);
            return translated;
        }
    }

    public static String translate(Player player, String key, Object... args) {
        if (player == null) {
            return translate(key, args);
        }

        return translate(player.getAccount().getLocale(), key, args);
    }

    private static void putFlattenedKey(Map<String, String> map, String key, JsonElement element) {
        if (element.isJsonObject()) {
            element
                    .getAsJsonObject()
                    .entrySet()
                    .forEach(
                            entry -> {
                                String keyPrefix = key.isEmpty() ? "" : key + ".";
                                putFlattenedKey(map, keyPrefix + entry.getKey(), entry.getValue());
                            });
        } else {
            map.put(key, element.getAsString());
        }
    }

    private static LanguageStreamDescription getLanguageFileDescription(
            String languageCode, String fallbackLanguageCode) {
        var fileName = languageCode + ".json";
        var fallback = fallbackLanguageCode + ".json";

        String actualLanguageCode = languageCode;
        InputStream file = Grasscutter.class.getResourceAsStream("/languages/" + fileName);

        if (file == null) {
            Grasscutter.getLogger()
                    .warn("Failed to load language file: " + fileName + ", falling back to: " + fallback);
            actualLanguageCode = fallbackLanguageCode;
            if (cachedLanguages.containsKey(actualLanguageCode)) {
                return new LanguageStreamDescription(actualLanguageCode, null);
            }

            file = Grasscutter.class.getResourceAsStream("/languages/" + fallback);
        }

        if (file == null) {
            Grasscutter.getLogger()
                    .warn("Failed to load language file: " + fallback + ", falling back to: en-US.json");
            actualLanguageCode = "en-US";
            if (cachedLanguages.containsKey(actualLanguageCode)) {
                return new LanguageStreamDescription(actualLanguageCode, null);
            }

            file = Grasscutter.class.getResourceAsStream("/languages/en-US.json");
        }

        if (file == null)
            throw new RuntimeException(
                    "Unable to load the primary, fallback, and 'en-US' language files.");

        return new LanguageStreamDescription(actualLanguageCode, file);
    }

    private static Int2ObjectMap<String> loadTextMapFile(String language, IntSet nameHashes) {
        Int2ObjectMap<String> output = new Int2ObjectOpenHashMap<>();
        try (BufferedReader file =
                Files.newBufferedReader(
                        getResourcePath("TextMap/TextMap" + language + ".json"), StandardCharsets.UTF_8)) {
            Matcher matcher = textMapKeyValueRegex.matcher("");
            return new Int2ObjectOpenHashMap<>(
                    file.lines()
                            .sequential()
                            .map(matcher::reset)
                            .filter(Matcher::find)
                            .filter(
                                    m ->
                                            nameHashes.contains(
                                                    (int) Long.parseLong(m.group(1))))
                            .collect(
                                    Collectors.toMap(
                                            m -> (int) Long.parseLong(m.group(1)),
                                            m -> m.group(2).replace("\\\"", "\""))));
        } catch (Exception e) {
            Grasscutter.getLogger().error("Error loading textmap: " + language);
            Grasscutter.getLogger().error(e.toString());
        }
        return output;
    }

    private static Int2ObjectMap<TextStrings> loadTextMapFiles(IntSet nameHashes) {
        Map<Integer, Int2ObjectMap<String>>
                mapLanguageMaps =
                TextStrings.LIST_LANGUAGES.parallelStream()
                                .collect(
                                        Collectors.toConcurrentMap(
                                                s -> TextStrings.MAP_LANGUAGES.getInt(s),
                                                s -> loadTextMapFile(s, nameHashes)));
        List<Int2ObjectMap<String>> languageMaps =
                IntStream.range(0, TextStrings.NUM_LANGUAGES)
                        .mapToObj(i -> mapLanguageMaps.get(i))
                        .collect(Collectors.toList());

        Map<TextStrings, TextStrings> canonicalTextStrings = new HashMap<>();
        return new Int2ObjectOpenHashMap<TextStrings>(
                nameHashes
                        .intStream()
                        .boxed()
                        .collect(
                                Collectors.toMap(
                                        key -> key,
                                        key -> {
                                            TextStrings t =
                                                    new TextStrings(
                                                            IntStream.range(0, TextStrings.NUM_LANGUAGES)
                                                                    .mapToObj(i -> languageMaps.get(i).get((int) key))
                                                                    .collect(Collectors.toList()),
                                                            key);
                                            return canonicalTextStrings.computeIfAbsent(t, x -> t);
                                        })));
    }

    @SuppressWarnings("unchecked")
    private static Int2ObjectMap<TextStrings> loadTextMapsCache() throws Exception {
        try (ObjectInputStream file =
                new ObjectInputStream(
                        new BufferedInputStream(Files.newInputStream(TEXTMAP_CACHE_PATH), 0x100000))) {
            final int fileVersion = file.readInt();
            if (fileVersion != TEXTMAP_CACHE_VERSION) throw new Exception("Invalid cache version");
            return (Int2ObjectMap<TextStrings>) file.readObject();
        }
    }

    private static void saveTextMapsCache(Int2ObjectMap<TextStrings> input) throws IOException {
        Files.createDirectories(TEXTMAP_CACHE_PATH.getParent());
        try (var file =
                new ObjectOutputStream(
                        new BufferedOutputStream(
                                Files.newOutputStream(TEXTMAP_CACHE_PATH, StandardOpenOption.CREATE), 0x100000))) {
            file.writeInt(TEXTMAP_CACHE_VERSION);
            file.writeObject(input);
        }
    }

    @Deprecated(forRemoval = true)
    public static Int2ObjectMap<TextStrings> getTextMapStrings() {
        if (textMapStrings == null) loadTextMaps();
        return textMapStrings;
    }

    public static TextStrings getTextMapKey(int key) {
        if ((textMapStrings == null) || (!scannedTextmaps && !textMapStrings.containsKey(key)))
            loadTextMaps();
        return textMapStrings.get(key);
    }

    public static TextStrings getTextMapKey(long hash) {
        return getTextMapKey((int) hash);
    }

    public static void loadTextMaps() {
        Language.loadTextMaps(false);
    }

    public static void loadTextMaps(boolean bypassCache) {
        if (!bypassCache)
            try {
                long cacheModified = Files.getLastModifiedTime(TEXTMAP_CACHE_PATH).toMillis();
                long textmapsModified =
                        Math.max(
                                newestJson(getResourcePath("TextMap")),
                                newestJson(getResourcePath("ExcelBinOutput")));

                Grasscutter.getLogger()
                        .debug(
                                "Cache modified %d, textmap modified %d"
                                        .formatted(cacheModified, textmapsModified));
                if (textmapsModified < cacheModified) {
                    Grasscutter.getLogger().debug("Loading cached 'TextMaps'...");
                    textMapStrings = loadTextMapsCache();
                    scannedTextmaps = true;
                    return;
                }
            } catch (NoSuchFileException ignored) {
            } catch (Exception exception) {
                Grasscutter.getLogger().error("Error loading textmaps cache: " + exception.toString());
            }

        Grasscutter.getLogger().debug("Generating TextMaps cache");
        ResourceLoader.loadAll();
        IntSet usedHashes = new IntOpenHashSet();
        GameData.getAchievementDataMap().values().stream()
                .filter(AchievementData::isUsed)
                .forEach(
                        a -> {
                            usedHashes.add((int) a.getTitleTextMapHash());
                            usedHashes.add((int) a.getDescTextMapHash());
                        });
        GameData.getAvatarDataMap().forEach((k, v) -> usedHashes.add((int) v.getNameTextMapHash()));
        GameData.getAvatarSkillDataMap()
                .forEach(
                        (k, v) -> {
                            usedHashes.add((int) v.getNameTextMapHash());
                            usedHashes.add((int) v.getDescTextMapHash());
                        });
        GameData.getItemDataMap().forEach((k, v) -> usedHashes.add((int) v.getNameTextMapHash()));
        GameData.getHomeWorldBgmDataMap()
                .forEach((k, v) -> usedHashes.add((int) v.getBgmNameTextMapHash()));
        GameData.getMonsterDataMap().forEach((k, v) -> usedHashes.add((int) v.getNameTextMapHash()));
        GameData.getMonsterDescribeDataMap()
                .forEach((k, v) -> usedHashes.add((int) v.getNameTextMapHash()));
        GameData.getEquipAffixDataMap()
                .forEach((k, v) -> usedHashes.add((int) v.getNameTextMapHash()));
        GameData.getMainQuestDataMap().forEach((k, v) -> usedHashes.add((int) v.getTitleTextMapHash()));
        GameData.getQuestDataMap().forEach((k, v) -> usedHashes.add((int) v.getDescTextMapHash()));
        GameData.getWorldAreaDataMap().forEach((k, v) -> usedHashes.add((int) v.getTextMapHash()));
        usedHashes.add((int) 4233146695L);
        usedHashes.add((int) 4231343903L);
        usedHashes.add((int) 332935371L);
        usedHashes.add((int) 2272170627L);
        usedHashes.add((int) 3352513147L);
        usedHashes.add((int) 2864268523L);

        var drifted = new IntOpenHashSet(usedHashes.size());
        for (var hash : usedHashes.toIntArray()) drifted.add(hash + HASH_DRIFT);
        usedHashes.addAll(drifted);

        textMapStrings = loadTextMapFiles(usedHashes);
        nameWhatTheTextMapsMissed(textMapStrings);
        scannedTextmaps = true;
        try {
            saveTextMapsCache(textMapStrings);
        } catch (IOException e) {
            Grasscutter.getLogger().error("Failed to save TextMap cache: ", e);
        }
    }

    private static long newestJson(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return files
                    .filter(path -> path.toString().endsWith(".json"))
                    .mapToLong(
                            path -> {
                                try {
                                    return Files.getLastModifiedTime(path).toMillis();
                                } catch (IOException e) {
                                    return Long.MAX_VALUE;
                                }
                            })
                    .max()
                    .orElse(Long.MAX_VALUE);
        }
    }

    private static final Map<String, String> RENAMED = Map.of("MarionetteNew", "Sandrone");

    private static final int HASH_DRIFT = 512;

    private static void nameWhatTheTextMapsMissed(Int2ObjectMap<TextStrings> strings) {
        var recovered = new int[2];
        for (var avatar : GameData.getAvatarDataMap().values()) {
            nameIfMissing(strings, avatar.getNameTextMapHash(), avatar.getIconName(), recovered);
        }
        for (var item : GameData.getItemDataMap().values()) {
            nameIfMissing(strings, item.getNameTextMapHash(), item.getIcon(), recovered);
        }

        if (recovered[0] + recovered[1] > 0) {
            Grasscutter.getLogger()
                    .info(
                            "Named {} avatars and items the text maps missed ({} recovered by hash drift, {} from their icon).",
                            recovered[0] + recovered[1],
                            recovered[0],
                            recovered[1]);
        }
    }

    private static void nameIfMissing(
            Int2ObjectMap<TextStrings> strings, long hash, String iconName, int[] recovered) {
        var key = (int) hash;
        if (key == 0 || known(strings.get(key))) return;

        var drifted = strings.get(key + HASH_DRIFT);
        if (known(drifted)) {
            strings.put(key, drifted);
            recovered[0]++;
            return;
        }

        var name = displayName(iconName);
        if (name == null) return;

        strings.put(key, new TextStrings(name));
        recovered[1]++;
    }

    private static boolean known(TextStrings strings) {
        return strings != null && !strings.get(0).startsWith("[N/A]");
    }

    private static String displayName(String iconName) {
        if (iconName == null || iconName.isBlank()) return null;

        var internal = iconName.substring(iconName.lastIndexOf('_') + 1);
        if (internal.isBlank()) return null;

        var renamed = RENAMED.get(internal);
        if (renamed != null) return renamed;

        if (internal.length() > 3 && internal.endsWith("New")) {
            internal = internal.substring(0, internal.length() - 3);
        }

        if (internal.chars().allMatch(Character::isDigit)) return null;

        return internal.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public String get(String key) {
        if (translations.containsKey(key)) return translations.get(key);
        String valueNotFoundPattern = "This value does not exist. Please report this to the Discord: ";
        String result = valueNotFoundPattern + key;
        if (!languageCode.equals("en-US")) {
            String englishValue = getLanguage("en-US").get(key);
            if (!englishValue.contains(valueNotFoundPattern)) {
                result += "\nhere is english version:\n" + englishValue;
            }
        }
        return result;
    }

    private static class LanguageStreamDescription {
        private final String languageCode;
        private final InputStream languageFile;

        public LanguageStreamDescription(String languageCode, InputStream languageFile) {
            this.languageCode = languageCode;
            this.languageFile = languageFile;
        }

        public String getLanguageCode() {
            return languageCode;
        }

        public InputStream getLanguageFile() {
            return languageFile;
        }
    }

    @EqualsAndHashCode
    public static class TextStrings implements Serializable {
        public static final String[] ARR_LANGUAGES = {
            "EN", "CHS", "CHT", "JP", "KR", "DE", "ES", "FR", "ID", "PT", "RU", "TH", "VI"
        };
        public static final String[] ARR_GC_LANGUAGES = {
            "en-US", "zh-CN", "zh-TW", "ja-JP", "ko-KR", "en-US", "es-ES", "fr-FR", "en-US", "en-US",
            "ru-RU", "en-US", "en-US"
        };
        public static final int NUM_LANGUAGES = ARR_LANGUAGES.length;
        public static final List<String> LIST_LANGUAGES = Arrays.asList(ARR_LANGUAGES);
        public static final Object2IntMap<String>
                MAP_LANGUAGES =
                new Object2IntOpenHashMap<>(
                                IntStream.range(0, ARR_LANGUAGES.length)
                                        .boxed()
                                        .collect(Collectors.toMap(i -> ARR_LANGUAGES[i], i -> i)));
        public static final Object2IntMap<String> MAP_GC_LANGUAGES =
                new Object2IntOpenHashMap<>(
                        IntStream.range(0, ARR_GC_LANGUAGES.length)
                                .boxed()
                                .collect(
                                        Collectors.toMap(
                                                i -> ARR_GC_LANGUAGES[i],
                                                i -> i,
                                                (i1, i2) -> i1)));
        public String[] strings = new String[ARR_LANGUAGES.length];

        public TextStrings() {}

        public TextStrings(String init) {
            for (int i = 0; i < NUM_LANGUAGES; i++) this.strings[i] = init;
        }

        public TextStrings(List<String> strings, int key) {
            String nullReplacement = "[N/A] %d".formatted((long) key & 0xFFFFFFFFL);
            for (int i = 0; i < NUM_LANGUAGES; i++) {
                String s = strings.get(i);
                if (s != null) {
                    nullReplacement = "[%s] - %s".formatted(ARR_LANGUAGES[i], s);
                    break;
                }
            }
            for (int i = 0; i < NUM_LANGUAGES; i++) {
                String s = strings.get(i);
                if (s != null) this.strings[i] = s;
                else this.strings[i] = nullReplacement;
            }
        }

        public static List<Language> getLanguages() {
            return Arrays.stream(ARR_GC_LANGUAGES).map(Language::getLanguage).toList();
        }

        public String get(int languageIndex) {
            return strings[languageIndex];
        }

        public String get(String languageCode) {
            return strings[MAP_LANGUAGES.getOrDefault(languageCode, 0)];
        }

        public String getGC(String languageCode) {
            return strings[MAP_GC_LANGUAGES.getOrDefault(languageCode, 0)];
        }

        public boolean set(String languageCode, String string) {
            int index = MAP_LANGUAGES.getOrDefault(languageCode, -1);
            if (index < 0) return false;
            strings[index] = string;
            return true;
        }
    }
}
