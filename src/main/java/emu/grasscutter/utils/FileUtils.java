package emu.grasscutter.utils;

import emu.grasscutter.Grasscutter;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.FileSystem;
import java.util.*;
import java.util.stream.*;
import lombok.val;

public final class FileUtils {
    private static final Path DATA_DEFAULT_PATH;
    private static final Path DATA_USER_PATH = Path.of(Grasscutter.config.folderStructure.data);
    private static final Path PACKETS_PATH = Path.of(Grasscutter.config.folderStructure.packets);
    private static final Path PLUGINS_PATH = Path.of(Grasscutter.config.folderStructure.plugins);
    private static final Path CACHE_PATH = Path.of(Grasscutter.config.folderStructure.cache);
    private static final Path RESOURCES_PATH;
    private static final Path SCRIPTS_PATH;
    private static final String[] TSJ_JSON_TSV = {"tsj", "json", "tsv"};

    static {
        FileSystem fs = null;
        Path path = null;
        try {
            var uri = Grasscutter.class.getResource("/defaults/data").toURI();
            switch (uri.getScheme()) {
                case "jar":
                case "zip":
                    fs =
                            FileSystems.newFileSystem(
                                    uri,
                                    Map.of());
                case "file":
                    path = Path.of(uri);
                    break;
                default:
                    Grasscutter.getLogger()
                            .error("Invalid URI scheme for class resources: " + uri.getScheme());
                    break;
            }
        } catch (URISyntaxException | IOException e) {
            Grasscutter.getLogger().error("Failed to load jar?!");
        } finally {
            DATA_DEFAULT_PATH = path;
            Grasscutter.getLogger().debug("Setting path for default data: " + path.toAbsolutePath());
        }

        final String resources = Grasscutter.config.folderStructure.resources;
        fs = null;
        path = Path.of(resources);
        if (resources.endsWith(
                ".zip")) {
            try {
                fs = FileSystems.newFileSystem(path);
            } catch (IOException e) {
                Grasscutter.getLogger().error("Failed to load resources zip \"" + resources + "\"");
            }
        }

        if (fs != null) {
            var root = fs.getPath("");
            try (Stream<Path> pathStream =
                    Files.find(
                            root,
                            3,
                            (p, a) -> {
                                var filename = p.getFileName();
                                if (filename == null) return false;
                                return filename.toString().equals("ExcelBinOutput");
                            })) {
                var excelBinOutput = pathStream.findFirst();
                if (excelBinOutput.isPresent()) {
                    path = excelBinOutput.get().getParent();
                    if (path == null) path = root;
                    Grasscutter.getLogger()
                            .debug("Resources will be loaded from \"" + resources + "/" + path + "\"");
                } else {
                    Grasscutter.getLogger()
                            .error("Failed to find ExcelBinOutput in resources zip \"" + resources + "\"");
                }
            } catch (IOException e) {
                Grasscutter.getLogger().error("Failed to scan resources zip \"" + resources + "\"");
            }
        }
        RESOURCES_PATH = path;

        final String scripts = Grasscutter.config.folderStructure.scripts;
        SCRIPTS_PATH =
                (scripts.startsWith("resources:"))
                        ? RESOURCES_PATH.resolve(scripts.substring("resources:".length()))
                        : Path.of(scripts);
    }

    private static final Path[] DATA_PATHS = {DATA_USER_PATH, DATA_DEFAULT_PATH};

    public static Path getDataPathTsjJsonTsv(String filename) {
        return getDataPathTsjJsonTsv(filename, true);
    }

    public static Path getDataPathTsjJsonTsv(String filename, boolean fallback) {
        val name = getFilenameWithoutExtension(filename);
        for (val data_path : DATA_PATHS) {
            for (val ext : TSJ_JSON_TSV) {
                val path = data_path.resolve(name + "." + ext);
                if (Files.exists(path)) return path;
            }
        }
        return fallback
                ? DATA_USER_PATH.resolve(name + ".tsj")
                : null;
    }

    public static Path getDataPath(String path) {
        Path userPath = DATA_USER_PATH.resolve(path);
        if (Files.exists(userPath)) return userPath;
        Path defaultPath = DATA_DEFAULT_PATH.resolve(path);
        if (Files.exists(defaultPath)) return defaultPath;
        return userPath;
    }

    public static Path getDataUserPath(String path) {
        return DATA_USER_PATH.resolve(path);
    }

    public static Path getCachePath(String path) {
        return CACHE_PATH.resolve(path);
    }

    public static Path getPacketPath(String path) {
        return PACKETS_PATH.resolve(path);
    }

    public static Path getPluginPath(String path) {
        return PLUGINS_PATH.resolve(path);
    }

    public static Path getResourcePath(String path) {
        return RESOURCES_PATH.resolve(path);
    }

    public static Path getExcelPath(String filename) {
        Path p = getTsjJsonTsv(RESOURCES_PATH.resolve("Server"), filename);
        return Files.exists(p) ? p : getTsjJsonTsv(RESOURCES_PATH.resolve("ExcelBinOutput"), filename);
    }

    public static Path getTsjJsonTsv(Path root, String filename) {
        val name = getFilenameWithoutExtension(filename);
        for (val ext : TSJ_JSON_TSV) {
            val path = root.resolve(name + "." + ext);
            if (Files.exists(path)) return path;
        }
        return root.resolve(name + ".tsj");
    }

    public static Path getScriptPath(String path) {
        return SCRIPTS_PATH.resolve(path);
    }

    public static void write(String dest, byte[] bytes) {
        Path path = Path.of(dest);

        try {
            Files.write(path, bytes);
        } catch (IOException e) {
            Grasscutter.getLogger().warn("Failed to write file: " + dest);
        }
    }

    public static byte[] read(String dest) {
        return read(Path.of(dest));
    }

    public static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            Grasscutter.getLogger().warn("Failed to read file: " + path);
        }

        return new byte[0];
    }

    public static InputStream readResourceAsStream(String resourcePath) {
        return Grasscutter.class.getResourceAsStream(resourcePath);
    }

    public static byte[] readResource(String resourcePath) {
        try (InputStream is = Grasscutter.class.getResourceAsStream(resourcePath)) {
            return is.readAllBytes();
        } catch (Exception exception) {
            Grasscutter.getLogger().warn("Failed to read resource: " + resourcePath);
            Grasscutter.getLogger().debug("Failed to load resource: " + resourcePath, exception);
        }

        return new byte[0];
    }

    public static byte[] read(File file) {
        return read(file.getPath());
    }

    public static void copyResource(String resourcePath, String destination) {
        try {
            byte[] resource = FileUtils.readResource(resourcePath);
            FileUtils.write(destination, resource);
        } catch (Exception exception) {
            Grasscutter.getLogger().warn("Failed to copy resource: " + resourcePath + "\n" + exception);
        }
    }

    @Deprecated
    public static String getFilenameWithoutPath(String filename) {
        return getFilenameWithoutExtension(filename);
    }

    public static String getFilenameWithoutExtension(String filename) {
        int i = filename.lastIndexOf(".");
        return (i < 0) ? filename : filename.substring(0, i);
    }

    public static String getFileExtension(Path path) {
        val filename = path.toString();
        int i = filename.lastIndexOf(".");
        return (i < 0) ? "" : filename.substring(i + 1);
    }

    public static List<Path> getPathsFromResource(String folder) throws URISyntaxException {
        try {
            return Files.walk(Path.of(Grasscutter.class.getResource(folder).toURI()))
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            try {
                return Files.walk(Path.of(System.getProperty("user.dir"), folder))
                        .filter(Files::isRegularFile)
                        .collect(Collectors.toList());
            } catch (IOException ignored) {
                return null;
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static String readToString(InputStream file) throws IOException {
        byte[] content = file.readAllBytes();

        return new String(content, StandardCharsets.UTF_8);
    }
}
