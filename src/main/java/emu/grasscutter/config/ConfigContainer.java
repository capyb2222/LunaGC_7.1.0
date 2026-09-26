package emu.grasscutter.config;

import ch.qos.logback.classic.Level;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.utils.*;
import lombok.NoArgsConstructor;

import java.util.*;

import static emu.grasscutter.Grasscutter.*;

public class ConfigContainer {
    private static int version() {
        return 13;
    }

    public static void updateConfig() {
        try {
            var configObject = JsonUtils.loadToClass(Grasscutter.configFile.toPath(), JsonObject.class);
            if (!configObject.has("version")) {
                Grasscutter.getLogger().info("Updating legacy config...");
                Grasscutter.saveConfig(null);
            }
        } catch (Exception ignored) { }

        var existing = config.version;
        var latest = version();

        if (existing == latest)
            return;

        var updated = new ConfigContainer();
        var fields = ConfigContainer.class.getDeclaredFields();
        Arrays.stream(fields).forEach(field -> {
            try {
                field.set(updated, field.get(config));
            } catch (Exception exception) {
                Grasscutter.getLogger().error("Failed to update a configuration field.", exception);
            }
        }); updated.version = version();

        try {
            Grasscutter.saveConfig(updated);
            Grasscutter.loadConfig();
        } catch (Exception exception) {
            Grasscutter.getLogger().warn("Failed to save the updated configuration.", exception);
        }
    }

    public Structure folderStructure = new Structure();
    public Database databaseInfo = new Database();
    public Language language = new Language();
    public Account account = new Account();
    public Server server = new Server();

    public int version = version();


    public static class Database {
        public DataStore server = new DataStore();
        public DataStore game = new DataStore();

        public static class DataStore {
            public String connectionUri = "mongodb://localhost:27017";
            public String collection = "grasscutter";
        }
    }

    public static class Structure {
        public String resources = "./resources/";
        public String data = "./data/";
        public String packets = "./packets/";
        public String scripts = "resources:Scripts/";
        public String plugins = "./plugins/";
        public String cache = "./cache/";

    }

    public static class Server {
        public Set<Integer> debugWhitelist = Set.of();
        public Set<Integer> debugBlacklist = Set.of();
        public ServerRunMode runMode = ServerRunMode.HYBRID;
        public boolean logCommands = false;

        public boolean fastRequire = true;

        public HTTP http = new HTTP();
        public Game game = new Game();

        public Dispatch dispatch = new Dispatch();
        public DebugMode debugMode = new DebugMode();
    }

    public static class Language {
        public Locale language = Locale.getDefault();
        public Locale fallback = Locale.US;
        public String document = "EN";
    }

    public static class Account {
        public boolean autoCreate = false;
        public boolean EXPERIMENTAL_RealPassword = false;
        public String[] defaultPermissions = {};
        public int maxPlayer = -1;
    }


    public static class HTTP {
        public boolean startImmediately = false;

        public String bindAddress = "0.0.0.0";
        public int bindPort = 8088;

        public String accessAddress = "127.0.0.1";
        public int accessPort = 0;

        public Encryption encryption = new Encryption();
        public Policies policies = new Policies();
        public Files files = new Files();
    }

    public static class Game {
        public String bindAddress = "0.0.0.0";
        public int bindPort = 22101;

        public String accessAddress = "127.0.0.1";
        public int accessPort = 0;

        public boolean useUniquePacketKey = true;

        public boolean useXorEncryption = true;

        public int loadEntitiesForPlayerRange = 300;
        public boolean enableScriptInBigWorld = true;
        public boolean enableConsole = true;

        public int tickRateMs = 200;

        public int kcpInterval = 20;
        public ServerDebugMode logPackets = ServerDebugMode.NONE;
        public boolean isShowPacketPayload = false;
        public boolean isShowLoopPackets = false;

        public boolean cacheSceneEntitiesEveryRun = false;

        public GameOptions gameOptions = new GameOptions();
        public JoinOptions joinOptions = new JoinOptions();
        public ConsoleAccount serverAccount = new ConsoleAccount();

        public VisionOptions[] visionOptions = new VisionOptions[] {
            new VisionOptions("VISION_LEVEL_NORMAL"         , 80    , 20),
            new VisionOptions("VISION_LEVEL_LITTLE_REMOTE"  , 16    , 40),
            new VisionOptions("VISION_LEVEL_REMOTE"         , 1000  , 250),
            new VisionOptions("VISION_LEVEL_SUPER"          , 4000  , 1000),
            new VisionOptions("VISION_LEVEL_NEARBY"         , 40    , 20),
            new VisionOptions("VISION_LEVEL_SUPER_NEARBY"   , 20    , 20)
        };
    }


    public static class Dispatch {
        public List<Region> regions = List.of();

        public String dispatchUrl = "ws://127.0.0.1:1111";
        public byte[] encryptionKey = Crypto.createSessionKey(32);
        public String dispatchKey = Utils.base64Encode(
            Crypto.createSessionKey(32));

        public String defaultName = "Grasscutter";

        public ServerDebugMode logRequests = ServerDebugMode.NONE;
    }

    public static class DebugMode {
        public Level serverLoggerLevel = Level.DEBUG;

        public Level servicesLoggersLevel = Level.INFO;

        public ServerDebugMode logPackets = ServerDebugMode.ALL;

        public boolean isShowPacketPayload = false;

        public boolean isShowLoopPackets = false;

        public ServerDebugMode logRequests = ServerDebugMode.ALL;
    }

    public static class Encryption {
        public boolean useEncryption = false;
        public boolean useInRouting = false;
        public String keystore = "./keystore.p12";
        public String keystorePassword = "123456";
    }

    public static class Policies {
        public Policies.CORS cors = new Policies.CORS();

        public static class CORS {
            public boolean enabled = true;
            public String[] allowedOrigins = new String[]{"*"};
        }
    }

    public static class GameOptions {
        public InventoryLimits inventoryLimits = new InventoryLimits();
        public AvatarLimits avatarLimits = new AvatarLimits();
        public int sceneEntityLimit = 1000;

        public boolean isPreventEntityError = true;

        public boolean watchGachaConfig = false;
        public boolean enableShopItems = false;
        public ArtifactShopOptions artifactShop = new ArtifactShopOptions();
        public boolean staminaUsage = true;
        public boolean energyUsage = true;
        public boolean fishhookTeleport = true;
        public boolean trialCostumes = false;

        public int firstLoginCutscene = 0;

        public boolean disableCutscenes = false;

        public boolean forceFinishMainQuestsOnLogin = false;

        public static class ArtifactShopOptions {
            public boolean enabled = true;

            public int shopId = 1004;

            public int costMora = 20000;
            public int costPrimogems = 0;
            public int costItemId = 0;
            public int costItemCount = 0;
            public int buyLimit = 0;

            public int artifactLevel = 20;
        }

        public NewAccountIntro newAccountIntro = new NewAccountIntro();

        public static class NewAccountIntro {
            public boolean enabled = false;
            public int doSetPlayerBornDataNotify = 0;
            public int setPlayerBornDataRsp = 0;

            public int fallbackSeconds = 15;
        }

        @SerializedName(value = "questing", alternate = "questOptions")
        public Questing questing = new Questing();
        public ResinOptions resinOptions = new ResinOptions();
        public Rates rates = new Rates();
        public TowerOptions tower = new TowerOptions();

        public HandbookOptions handbook = new HandbookOptions();
        public BirthdayMailOptions birthdayMail = new BirthdayMailOptions();
        public WatermarkOptions watermark = new WatermarkOptions();

        public static class InventoryLimits {
            public int weapons = 2000;
            public int relics = 2000;
            public int materials = 2000;
            public int furniture = 2000;
            public int all = 30000;
        }

        public static class AvatarLimits {
            public int singlePlayerTeam = 4;
            public int multiplayerTeam = 4;
        }

        public static class Rates {
            public float adventureExp = 1.0f;
            public float mora = 1.0f;
            public float leyLines = 1.0f;
        }

        public static class TowerOptions {
            public int scheduleId = 0;

            public boolean rotate = false;

            public int rotationPool = 12;

            public boolean skipEntranceFloors = true;
        }

        public static class ResinOptions {
            public boolean resinUsage = false;
            public int cap = 200;
            public int rechargeTime = 480;
        }

        public static class Questing {
            public boolean enabled = false;
        }

        public static class WatermarkOptions {
            public boolean enabled = true;
            public String text = "CapyGC";
            public String color = "#FFFFFF";
            public String gradientTo = "#6032a8";
            public int cmdId = 0;
            public int payloadField = 0;
        }

        public static class BirthdayMailOptions {
            public boolean enabled = true;
            public int expireDays = 7;
            public GiftItem[] gifts =
                    new GiftItem[] {
                        new GiftItem(202, 10000000),
                        new GiftItem(201, 600000)
                    };

            public static class GiftItem {
                public int itemId;
                public int count;

                public GiftItem() {
                    this(202, 1);
                }

                public GiftItem(int itemId, int count) {
                    this.itemId = itemId;
                    this.count = count;
                }
            }
        }

        public static class HandbookOptions {
            public boolean enable = false;
            public boolean allowCommands = true;

            public Limits limits = new Limits();
            public Server server = new Server();

            public static class Limits {
                public boolean enabled = false;
                public int interval = 3;

                public int maxRequests = 10;
                public int maxEntities = 25;
            }

            public static class Server {
                public boolean enforced = false;
                public String address = "127.0.0.1";
                public int port = 443;
                public boolean canChange = true;
            }
        }
    }

    public static class VisionOptions {
        public String name;
        public int visionRange;
        public int gridWidth;

        public VisionOptions(String name, int visionRange, int gridWidth) {
            this.name = name;
            this.visionRange = visionRange;
            this.gridWidth = gridWidth;
        }
    }

    public static class JoinOptions {
        public int[] welcomeEmotes = {2007, 1002, 4010};
        public String welcomeMessage = "Welcome to LunaGC {version}";
        public JoinOptions.Mail welcomeMail = new JoinOptions.Mail();

        public static class Mail {
            public String title = "Welcome to LunaGC {version}";
            public String content = """
                    Hi there!\r\nWelcome to LunaGC!
                    """;
            public String sender = "Kei-Luna and pmagixc";
            public emu.grasscutter.game.mail.Mail.MailItem[] items = {
            };
        }
    }

    public static class ConsoleAccount {
        public int avatarId = 10000007;
        public int nameCardId = 210001;
        public int adventureRank = 1;
        public int worldLevel = 0;

        public String nickName = "LunaGC";
        public String signature = "Welcome to LunaGC";
    }

    public static class Files {
        public String indexFile = "./index.html";
        public String errorFile = "./404.html";
    }


    @NoArgsConstructor
    public static class Region {
        public String Name = "os_usa";
        public String Title = "Grasscutter";
        public String Ip = "127.0.0.1";
        public int Port = 22102;

        public Region(
            String name, String title,
            String address, int port
        ) {
            this.Name = name;
            this.Title = title;
            this.Ip = address;
            this.Port  = port;
        }
    }
}
