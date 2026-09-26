package emu.grasscutter.utils;

import static emu.grasscutter.utils.Utils.nonRegexSplit;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import emu.grasscutter.Grasscutter;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import java.io.IOException;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.*;
import lombok.val;

public final class TsvUtils {
    private static final Map<Type, Object> defaultValues =
            Map.ofEntries(
                    Map.entry(Integer.class, 0),
                    Map.entry(int.class, 0),
                    Map.entry(Long.class, 0L),
                    Map.entry(long.class, 0L),
                    Map.entry(Float.class, 0f),
                    Map.entry(float.class, 0f),
                    Map.entry(Double.class, 0d),
                    Map.entry(double.class, 0d),
                    Map.entry(Boolean.class, false),
                    Map.entry(boolean.class, false));
    private static final Set<Type> primitiveTypes =
            Set.of(
                    String.class,
                    Integer.class,
                    int.class,
                    Long.class,
                    long.class,
                    Float.class,
                    float.class,
                    Double.class,
                    double.class,
                    Boolean.class,
                    boolean.class);

    private static final Function<String, Object> parseString = value -> value;
    private static final Function<String, Object> parseInt =
            value -> (int) Double.parseDouble(value);
    private static final Function<String, Object> parseLong =
            value -> (long) Double.parseDouble(value);
    private static final Map<Class<?>, Function<String, Object>> enumTypeParsers = new HashMap<>();
    private static final Map<Type, Function<String, Object>> primitiveTypeParsers =
            Map.ofEntries(
                    Map.entry(String.class, parseString),
                    Map.entry(Integer.class, parseInt),
                    Map.entry(int.class, parseInt),
                    Map.entry(Long.class, parseLong),
                    Map.entry(long.class, parseLong),
                    Map.entry(Float.class, Float::parseFloat),
                    Map.entry(float.class, Float::parseFloat),
                    Map.entry(Double.class, Double::parseDouble),
                    Map.entry(double.class, Double::parseDouble),
                    Map.entry(Boolean.class, Boolean::parseBoolean),
                    Map.entry(boolean.class, Boolean::parseBoolean));
    private static final Map<Type, Function<String, Object>> typeParsers =
            new HashMap<>(primitiveTypeParsers);
    private static final Map<Class<?>, Map<String, FieldParser>> cachedClassFieldMaps =
            new HashMap<>();

    @SuppressWarnings("unchecked")
    private static <T> T parsePrimitive(Class<T> type, String string) {
        if (string == null || string.isEmpty()) return (T) defaultValues.get(type);
        return (T) primitiveTypeParsers.get(type).apply(string);
    }

    private static double parseNumber(String string) {
        if (string == null || string.isEmpty()) return 0d;
        return Double.parseDouble(string);
    }

    @SuppressWarnings("unchecked")
    private static <T> T parseEnum(Class<T> enumType, String string) {
        if (string == null || string.isEmpty()) return null;
        return (T) getEnumTypeParser(enumType).apply(string);
    }

    public static <T> T newObj(Class<T> objClass) {
        try {
            return objClass.getDeclaredConstructor().newInstance();
        } catch (Exception ignored) {
            return JsonUtils.decode("{}", objClass);
        }
    }

    @SuppressWarnings("deprecated")
    private static Function<String, Object> makeEnumTypeParser(Class<?> enumClass) {
        if (!enumClass.isEnum()) {
            return null;
        }

        val map = new HashMap<String, Object>();
        val enumConstants = enumClass.getEnumConstants();
        for (val constant : enumConstants) map.put(constant.toString(), constant);

        for (Field f : enumClass.getDeclaredFields()) {
            if (switch (f.getName()) {
                case "value", "id" -> true;
                default -> false;
            }) {
                try {
                    for (var constant : enumConstants) {
                        var accessible = f.canAccess(constant);
                        f.setAccessible(true);
                        map.put(String.valueOf(f.getInt(constant)), constant);
                        f.setAccessible(accessible);
                    }
                } catch (IllegalAccessException e) {
                }
                break;
            }
        }
        return map::get;
    }

    private static synchronized Function<String, Object> getEnumTypeParser(Class<?> enumType) {
        if (enumType == null) {
            return null;
        }
        return enumTypeParsers.computeIfAbsent(enumType, TsvUtils::makeEnumTypeParser);
    }

    private static synchronized Function<String, Object> getTypeParser(Type type) {
        if (type == null) return parseString;
        return typeParsers.computeIfAbsent(type, t -> value -> JsonUtils.decode(value, t));
    }

    private static Type class2Type(Class<?> classType) {
        return classType.getGenericSuperclass();
    }

    private static Class<?> type2Class(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) type).getRawType();
        } else {
            return type.getClass();
        }
    }

    private static Map<String, FieldParser> makeClassFieldMap(Class<?> classType) {
        val fieldMap = new HashMap<String, FieldParser>();
        for (Field field : classType.getDeclaredFields()) {
            field.setAccessible(
                    true);
            val fieldParser = new FieldParser(field);

            val a = field.getDeclaredAnnotation(SerializedName.class);
            if (a == null) {
                fieldMap.put(field.getName(), fieldParser);
            } else {
                fieldMap.put(a.value(), fieldParser);
                for (val alt : a.alternate()) {
                    fieldMap.put(alt, fieldParser);
                }
            }
        }
        return fieldMap;
    }

    private static synchronized Map<String, FieldParser> getClassFieldMap(Class<?> classType) {
        return cachedClassFieldMaps.computeIfAbsent(classType, TsvUtils::makeClassFieldMap);
    }

    public static <T> List<T> loadTsvToListSetField(Path filename, Class<T> classType) {
        try (val fileReader = Files.newBufferedReader(filename, StandardCharsets.UTF_8)) {

            val headerNames = nonRegexSplit(fileReader.readLine(), '\t');
            val columns = headerNames.size();
            val stringTree = new StringTree();
            headerNames.forEach(stringTree::addPath);

            return fileReader
                    .lines()
                    .parallel()
                    .map(
                            line -> {
                                val tokens = nonRegexSplit(line, '\t');
                                val m = Math.min(tokens.size(), columns);
                                int t = 0;
                                StringValueTree tree = new StringValueTree(stringTree);
                                try {
                                    for (t = 0; t < m; t++) {
                                        String token = tokens.get(t);
                                        if (!token.isEmpty()) {
                                            tree.setValue(headerNames.get(t), token);
                                        }
                                    }
                                    return tree.toClass(classType, null);
                                } catch (Exception e) {
                                    Grasscutter.getLogger()
                                            .warn(
                                                    "Error deserializing an instance of class "
                                                            + classType.getCanonicalName());
                                    Grasscutter.getLogger().warn("At token #" + t + " of #" + m);
                                    Grasscutter.getLogger().warn("Header names are: " + headerNames);
                                    Grasscutter.getLogger().warn("Tokens are: " + tokens);
                                    Grasscutter.getLogger().warn("Stacktrace is: ", e);
                                    return null;
                                }
                            })
                    .toList();
        } catch (Exception e) {
            Grasscutter.getLogger().error("Error loading file '" + filename + "' - Stacktrace is: ", e);
            return null;
        }
    }

    public static <T> List<T> loadTsjToListSetField(Path filename, Class<T> classType) {
        try (val fileReader = Files.newBufferedReader(filename, StandardCharsets.UTF_8)) {
            val fieldMap = getClassFieldMap(classType);
            val constructor = classType.getDeclaredConstructor();

            val headerNames = nonRegexSplit(fileReader.readLine(), '\t');
            val columns = headerNames.size();
            val fieldParsers = headerNames.stream().map(fieldMap::get).toList();

            return fileReader
                    .lines()
                    .parallel()
                    .map(
                            line -> {
                                val tokens = nonRegexSplit(line, '\t');
                                val m = Math.min(tokens.size(), columns);
                                int t = 0;
                                try {
                                    T obj = constructor.newInstance();
                                    for (t = 0; t < m; t++) {
                                        val fieldParser = fieldParsers.get(t);
                                        if (fieldParser == null) continue;

                                        String token = tokens.get(t);
                                        if (!token.isEmpty()) {
                                            fieldParser.parse(obj, token);
                                        }
                                    }
                                    return obj;
                                } catch (Exception e) {
                                    Grasscutter.getLogger()
                                            .warn(
                                                    "Error deserializing an instance of class "
                                                            + classType.getCanonicalName());
                                    Grasscutter.getLogger().warn("At token #" + t + " of #" + m);
                                    Grasscutter.getLogger().warn("Header names are: " + headerNames);
                                    Grasscutter.getLogger().warn("Tokens are: " + tokens);
                                    Grasscutter.getLogger().warn("Stacktrace is: ", e);
                                    return null;
                                }
                            })
                    .toList();
        } catch (NoSuchFileException e) {
            Grasscutter.getLogger()
                    .error(
                            "Error loading file '"
                                    + filename
                                    + "' - File does not exist. You are missing resources. Note that this file may exist in JSON, TSV, or TSJ format, any of which are suitable.");
            return null;
        } catch (IOException e) {
            Grasscutter.getLogger().error("Error loading file '" + filename + "' - Stacktrace is: ", e);
            return null;
        } catch (NoSuchMethodException e) {
            Grasscutter.getLogger()
                    .error("Error loading file '" + filename + "' - Class is missing NoArgsConstructor");
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Pair<Constructor<T>, String[]> getAllArgsConstructor(Class<T> classType) {
        for (var c : classType.getDeclaredConstructors()) {
            val consParameters =
                    (java.beans.ConstructorProperties)
                            c.getAnnotation(java.beans.ConstructorProperties.class);
            if (consParameters != null) {
                return Pair.of((Constructor<T>) c, consParameters.value());
            }
        }
        return null;
    }

    public static <T> List<List<T>> loadTsjsToListsConstructor(Class<T> classType, Path... filenames)
            throws Exception {
        val pair = getAllArgsConstructor(classType);
        if (pair == null) {
            Grasscutter.getLogger().error("No AllArgsContructor found for class: " + classType);
            return null;
        }
        val constructor = pair.left();
        val conArgNames = pair.right();
        val numArgs = constructor.getParameterCount();

        val argMap = new Object2IntArrayMap<String>();
        for (int i = 0; i < conArgNames.length; i++) {
            argMap.put(conArgNames[i], i);
        }

        val argTypes =
                new Type[numArgs];
        for (Field field : classType.getDeclaredFields()) {
            int index = argMap.getOrDefault(field.getName(), -1);
            if (index < 0) continue;

            argTypes[index] = field.getGenericType();

            val a = field.getDeclaredAnnotation(SerializedName.class);
            if (a != null) {
                argMap.put(a.value(), index);
                for (val alt : a.alternate()) {
                    argMap.put(alt, index);
                }
            }
        }
        val argParsers = Stream.of(argTypes).map(TsvUtils::getTypeParser).toList();

        val defaultArgs = new Object[numArgs];
        for (int i = 0; i < numArgs; i++) {
            defaultArgs[i] = defaultValues.get(argTypes[i]);
        }

        return Stream.of(filenames)
                .parallel()
                .map(
                        filename -> {
                            try (val fileReader = Files.newBufferedReader(filename, StandardCharsets.UTF_8)) {
                                val headerNames = nonRegexSplit(fileReader.readLine(), '\t');
                                val columns = headerNames.size();
                                val argPositions =
                                        headerNames.stream().mapToInt(name -> argMap.getOrDefault(name, -1)).toArray();

                                return fileReader
                                        .lines()
                                        .parallel()
                                        .map(
                                                line -> {
                                                    val tokens = nonRegexSplit(line, '\t');
                                                    val args = defaultArgs.clone();
                                                    val m = Math.min(tokens.size(), columns);
                                                    int t = 0;
                                                    try {
                                                        for (t = 0; t < m; t++) {
                                                            val argIndex = argPositions[t];
                                                            if (argIndex < 0) continue;

                                                            String token = tokens.get(t);
                                                            if (!token.isEmpty()) {
                                                                args[argIndex] = argParsers.get(argIndex).apply(token);
                                                            }
                                                        }
                                                        return constructor.newInstance(args);
                                                    } catch (Exception e) {
                                                        Grasscutter.getLogger()
                                                                .warn(
                                                                        "Error deserializing an instance of class "
                                                                                + classType.getCanonicalName()
                                                                                + " : "
                                                                                + constructor.getName());
                                                        Grasscutter.getLogger().warn("At token #" + t + " of #" + m);
                                                        Grasscutter.getLogger()
                                                                .warn("Arg names are: " + Arrays.toString(conArgNames));
                                                        Grasscutter.getLogger()
                                                                .warn("Arg types are: " + Arrays.toString(argTypes));
                                                        Grasscutter.getLogger()
                                                                .warn("Default Args are: " + Arrays.toString(defaultArgs));
                                                        Grasscutter.getLogger().warn("Args are: " + Arrays.toString(args));
                                                        Grasscutter.getLogger().warn("Header names are: " + headerNames);
                                                        Grasscutter.getLogger()
                                                                .warn(
                                                                        "Header types are: "
                                                                                + IntStream.of(argPositions)
                                                                                        .mapToObj(i -> (i >= 0) ? argTypes[i] : null)
                                                                                        .toList());
                                                        Grasscutter.getLogger().warn("Tokens are: " + tokens);
                                                        Grasscutter.getLogger().warn("Stacktrace is: ", e);
                                                        return null;
                                                    }
                                                })
                                        .toList();
                            } catch (IOException e) {
                                Grasscutter.getLogger()
                                        .error("Error loading file '" + filename + "' - Stacktrace is: ", e);
                                return null;
                            }
                        })
                .toList();
    }

    private static class FieldParser {
        public final Field field;
        public final Type type;
        public final Class<?> classType;
        public final Function<String, Object> parser;

        FieldParser(Field field) {
            this.field = field;
            this.type = field.getGenericType();
            this.classType = field.getType();
            this.parser = getTypeParser(this.type);
        }

        public Object parse(String token) {
            return this.parser.apply(token);
        }

        public void parse(Object obj, String token) throws IllegalAccessException {
            this.field.set(obj, this.parser.apply(token));
        }
    }

    private static class StringTree {
        public final Map<String, StringTree> children = new TreeMap<>();

        public void addPath(String path) {
            if (path.isEmpty()) return;

            val firstDot = path.indexOf('.');
            val fieldPath = (firstDot < 0) ? path : path.substring(0, firstDot);
            val remainder = (firstDot < 0) ? "" : path.substring(firstDot + 1);
            this.children.computeIfAbsent(fieldPath, k -> new StringTree()).addPath(remainder);
        }
    }

    @SuppressWarnings("unchecked")
    private static class StringValueTree {
        public final SortedMap<String, StringValueTree> children = new TreeMap<>();
        public final Int2ObjectSortedMap<StringValueTree> arrayChildren = new Int2ObjectRBTreeMap<>();
        public String value;

        public StringValueTree(StringTree from) {
            from.children.forEach(
                    (k, v) -> {
                        try {
                            this.arrayChildren.put(Integer.parseInt(k), new StringValueTree(v));
                        } catch (NumberFormatException e) {
                            this.children.put(k, new StringValueTree(v));
                        }
                    });
        }

        public void setValue(String path, String value) {
            if (path.isEmpty()) {
                this.value = value;
                return;
            }

            val firstDot = path.indexOf('.');
            val fieldPath = (firstDot < 0) ? path : path.substring(0, firstDot);
            val remainder = (firstDot < 0) ? "" : path.substring(firstDot + 1);
            try {
                this.arrayChildren.get(Integer.parseInt(fieldPath)).setValue(remainder, value);
            } catch (NumberFormatException e) {
                this.children.get(fieldPath).setValue(remainder, value);
            }
        }

        public JsonElement toJson() {
            if (this.value != null) {
                return new JsonPrimitive(this.value);
            }
            if (!this.arrayChildren.isEmpty()) {
                val arr = new JsonArray(this.arrayChildren.lastIntKey() + 1);
                arrayChildren.forEach((k, v) -> arr.set(k, v.toJson()));
                return arr;
            } else if (this.children.isEmpty()) {
                return JsonNull.INSTANCE;
            } else {
                val obj = new JsonObject();
                children.forEach(
                        (k, v) -> {
                            val j = v.toJson();
                            if (j != JsonNull.INSTANCE) obj.add(k, v.toJson());
                        });
                return obj;
            }
        }

        public <T> T toClass(Class<T> classType, Type type) {
            if (type == null) type = class2Type(classType);

            if (primitiveTypeParsers.containsKey(classType)) {
                return parsePrimitive(classType, this.value);
            } else if (classType.isEnum()) {
                return parseEnum(classType, this.value);
            } else if (classType.isArray()) {
                return this.toArray(classType);
            } else if (List.class.isAssignableFrom(classType)) {
                val elementType = ((ParameterizedType) type).getActualTypeArguments()[0];
                return (T) this.toList(type2Class(elementType), elementType);
            } else if (Map.class.isAssignableFrom(classType)) {
                val keyType = ((ParameterizedType) type).getActualTypeArguments()[0];
                val valueType = ((ParameterizedType) type).getActualTypeArguments()[1];
                return (T) this.toMap(type2Class(keyType), type2Class(valueType), valueType);
            } else {
                return this.toObj(classType, type);
            }
        }

        private <T> T toObj(Class<T> objClass, Type objType) {
            try {
                val obj = newObj(objClass);
                val fieldMap = getClassFieldMap(objClass);
                this.children.forEach(
                        (name, tree) -> {
                            val field = fieldMap.get(name);
                            if (field == null) return;
                            try {
                                if (primitiveTypes.contains(field.type)) {
                                    if ((tree.value != null) && !tree.value.isEmpty()) field.parse(obj, tree.value);
                                } else {
                                    val value = tree.toClass(field.classType, field.type);
                                    field.field.set(obj, value);
                                }
                            } catch (Exception e) {
                                Grasscutter.getLogger()
                                        .error(
                                                "Exception while setting field "
                                                        + name
                                                        + " ("
                                                        + field.classType
                                                        + ")"
                                                        + " for class "
                                                        + objClass
                                                        + " - ",
                                                e);
                            }
                        });
                return obj;
            } catch (Exception e) {
                Grasscutter.getLogger()
                        .error("Exception while creating object of class " + objClass + " - ", e);
                return null;
            }
        }

        public <T> T toArray(Class<T> classType) {
            val containedClass = classType.getComponentType();
            val arraySize = this.arrayChildren.lastIntKey() + 1;
            if (containedClass == int.class) {
                val output = new int[arraySize];
                this.arrayChildren.forEach((idx, tree) -> output[idx] = (int) parseNumber(tree.value));
                return (T) output;
            } else if (containedClass == long.class) {
                val output = new long[arraySize];
                this.arrayChildren.forEach((idx, tree) -> output[idx] = (long) parseNumber(tree.value));
                return (T) output;
            } else if (containedClass == float.class) {
                val output = new float[arraySize];
                this.arrayChildren.forEach((idx, tree) -> output[idx] = (float) parseNumber(tree.value));
                return (T) output;
            } else if (containedClass == double.class) {
                val output = new double[arraySize];
                this.arrayChildren.forEach((idx, tree) -> output[idx] = parseNumber(tree.value));
                return (T) output;
            } else if (containedClass == byte.class) {
                val output = new byte[arraySize];
                this.arrayChildren.forEach((idx, tree) -> output[idx] = (byte) parseNumber(tree.value));
                return (T) output;
            } else if (containedClass == char.class) {
                val output = new char[arraySize];
                this.arrayChildren.forEach((idx, tree) -> output[idx] = (char) parseNumber(tree.value));
                return (T) output;
            } else if (containedClass == short.class) {
                val output = new short[arraySize];
                this.arrayChildren.forEach((idx, tree) -> output[idx] = (short) parseNumber(tree.value));
                return (T) output;
            } else if (containedClass == boolean.class) {
                val output = new boolean[arraySize];
                this.arrayChildren.forEach(
                        (idx, tree) -> {
                            val value =
                                    (tree.value != null) && !tree.value.isEmpty() && Boolean.parseBoolean(tree.value);
                            output[idx] = value;
                        });
                return (T) output;
            } else {
                val output = Array.newInstance(containedClass, arraySize);
                this.arrayChildren.forEach(
                        (idx, tree) -> ((Object[]) output)[idx] = tree.toClass(containedClass, null));
                return (T) output;
            }
        }

        private <E> List<E> toList(Class<E> valueClass, Type valueType) {
            val arraySize = this.arrayChildren.lastIntKey() + 1;
            val list = new ArrayList<E>(arraySize);
            for (int i = 0; i < arraySize; i++) list.add(null);
            this.arrayChildren.forEach((idx, tree) -> list.set(idx, tree.toClass(valueClass, valueType)));
            return list;
        }

        private <K, V> Map<K, V> toMap(Class<K> keyClass, Class<V> valueClass, Type valueType) {
            val map = new HashMap<K, V>();
            val keyParser = getTypeParser(keyClass);
            this.children.forEach(
                    (key, tree) -> {
                        if ((key != null) && !key.isEmpty())
                            map.put((K) keyParser.apply(key), tree.toClass(valueClass, valueType));
                    });
            return map;
        }
    }

    private TsvUtils() {
    }
}
