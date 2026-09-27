package emu.grasscutter.game.gacha;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import com.sun.nio.file.SensitivityWatchEventModifier;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.gacha.GachaBanner.BannerType;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.net.proto.GachaItemOuterClass.GachaItem;
import emu.grasscutter.net.proto.GachaTransferItemOuterClass.GachaTransferItem;
import emu.grasscutter.net.proto.GetGachaInfoRspOuterClass.GetGachaInfoRsp;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.event.player.PlayerWishEvent;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.send.PacketDoGachaRsp;
import emu.grasscutter.utils.*;
import it.unimi.dsi.fastutil.ints.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.greenrobot.eventbus.Subscribe;

public class GachaSystem extends BaseGameSystem {
    private static final int starglitterId = 221;
    private static final int stardustId = 222;
    private final Int2ObjectMap<GachaBanner> gachaBanners;
    private WatchService watchService;

    public GachaSystem(GameServer server) {
        super(server);
        this.gachaBanners = new Int2ObjectOpenHashMap<>();
        this.load();
        this.startWatcher(server);
    }

    public Int2ObjectMap<GachaBanner> getGachaBanners() {
        return gachaBanners;
    }

    public int randomRange(int min, int max) {
        return ThreadLocalRandom.current().nextInt(max - min + 1) + min;
    }

    public int getRandom(int[] array) {
        if (array == null || array.length == 0) {
            Grasscutter.getLogger().warn("[Gacha] Tried to roll from an empty item pool.");
            return 0;
        }
        return array[randomRange(0, array.length - 1)];
    }

    public synchronized void load() {
        getGachaBanners().clear();
        int autoScheduleId = 1000;
        int autoSortId = 9000;
        try {
            var banners = DataLoader.loadTableToList("Banners", GachaBanner.class);
            if (!banners.isEmpty()) {
                for (var banner : banners) {
                    banner.onLoad();
                    if (banner.isDeprecated()) {
                        Grasscutter.getLogger()
                                .error(
                                        "A Banner has not been loaded because it contains one or more deprecated fields. Remove the fields mentioned above and reload.");
                    } else if (banner.isDisabled()) {
                        Grasscutter.getLogger().trace("A Banner has not been loaded because it is disabled.");
                    } else {
                        if (banner.scheduleId < 0) banner.scheduleId = autoScheduleId++;
                        if (banner.sortId < 0) banner.sortId = autoSortId--;
                        getGachaBanners().put(banner.scheduleId, banner);
                    }
                }
                Grasscutter.getLogger().debug("Banners successfully loaded.");
            } else {
                Grasscutter.getLogger().error("Unable to load banners. Banners size is 0.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private synchronized int[] removeC6FromPool(int[] itemPool, Player player) {
        IntList temp = new IntArrayList();
        for (int itemId : itemPool) {
            if (InventorySystem.checkPlayerAvatarConstellationLevel(player, itemId) < 6) {
                temp.add(itemId);
            }
        }
        return temp.toIntArray();
    }

    private synchronized int drawRoulette(int[] weights, int cutoff) {
        int total = 0;
        for (int weight : weights) {
            if (weight < 0) {
                throw new IllegalArgumentException("Weights must be non-negative!");
            }
            total += weight;
        }
        int bound = Math.min(total, cutoff);
        if (bound <= 0) {
            return 0;
        }
        int roll = ThreadLocalRandom.current().nextInt(bound);
        int subTotal = 0;
        for (int i = 0; i < weights.length; i++) {
            subTotal += weights[i];
            if (roll < subTotal) {
                return i;
            }
        }
        return 0;
    }

    private synchronized int doFallbackRarePull(
            int[] fallback1,
            int[] fallback2,
            int rarity,
            GachaBanner banner,
            PlayerGachaBannerInfo gachaInfo) {
        if (fallback1.length < 1) {
            if (fallback2.length < 1) {
                return getRandom(
                        (rarity == 5)
                                ? GachaBanner.DEFAULT_FALLBACK_ITEMS_5_POOL_2
                                : GachaBanner.DEFAULT_FALLBACK_ITEMS_4_POOL_2);
            } else {
                return getRandom(fallback2);
            }
        } else if (fallback2.length < 1) {
            return getRandom(fallback1);
        } else {
            int pityPool1 = banner.getPoolBalanceWeight(rarity, gachaInfo.getPityPool(rarity, 1));
            int pityPool2 = banner.getPoolBalanceWeight(rarity, gachaInfo.getPityPool(rarity, 2));
            int chosenPool =
                    switch ((pityPool1 >= pityPool2)
                            ? 1
                            : 0) {
                        case 1 -> 1 + drawRoulette(new int[] {pityPool1, pityPool2}, 10000);
                        default -> 2 - drawRoulette(new int[] {pityPool2, pityPool1}, 10000);
                    };
            return switch (chosenPool) {
                case 1:
                    gachaInfo.setPityPool(rarity, 1, 0);
                    yield getRandom(fallback1);
                default:
                    gachaInfo.setPityPool(rarity, 2, 0);
                    yield getRandom(fallback2);
            };
        }
    }

    private record PullResult(int itemId, boolean capturedRadiance) {}

    private synchronized PullResult doRarePull(
            int[] featured,
            int[] fallback1,
            int[] fallback2,
            int rarity,
            GachaBanner banner,
            PlayerGachaBannerInfo gachaInfo) {
        int itemId = 0;
        boolean epitomized =
                (banner.hasEpitomized()) && (rarity == 5) && (gachaInfo.getWishItemId() != 0);
        boolean pityEpitomized =
                (gachaInfo.getFailedChosenItemPulls()
                        >= banner.getWishMaxProgress());
        boolean pityFeatured =
                (gachaInfo.getFailedFeaturedItemPulls(rarity) >= 1);
        boolean rollFeatured =
                (this.randomRange(1, 100) <= banner.getEventChance(rarity));
        boolean capturedRadiance = false;
        if ((rarity == 5) && !pityFeatured) {
            int radianceChance =
                    banner.getCapturingRadianceChance(gachaInfo.getCapturingRadianceCounter());
            if (radianceChance >= 100) {
                capturedRadiance = true;
            } else if (!rollFeatured) {
                capturedRadiance = (radianceChance > 0) && (this.randomRange(1, 100) <= radianceChance);
            }
        }
        boolean pullFeatured = pityFeatured || rollFeatured || capturedRadiance;

        boolean captured = false;
        if (epitomized && pityEpitomized) {
            gachaInfo.setFailedFeaturedItemPulls(
                    rarity, 0);
            itemId = gachaInfo.getWishItemId();
        } else {
            if (pullFeatured && (featured.length > 0)) {
                gachaInfo.setFailedFeaturedItemPulls(rarity, 0);
                if ((rarity == 5) && !pityFeatured)
                    gachaInfo.onFiftyFifty(true, capturedRadiance, banner.getCapturingRadianceMax());
                captured = capturedRadiance;
                itemId = getRandom(featured);
            } else {
                gachaInfo.addFailedFeaturedItemPulls(
                        rarity,
                        1);
                if ((rarity == 5) && !pityFeatured)
                    gachaInfo.onFiftyFifty(false, false, banner.getCapturingRadianceMax());
                itemId = doFallbackRarePull(fallback1, fallback2, rarity, banner, gachaInfo);
            }
        }

        if (epitomized) {
            if (itemId == gachaInfo.getWishItemId()) {
                gachaInfo.setFailedChosenItemPulls(0);
            } else {
                gachaInfo.addFailedChosenItemPulls(1);
            }
        }
        return new PullResult(itemId, captured);
    }

    private synchronized PullResult doPull(
            GachaBanner banner, PlayerGachaBannerInfo gachaInfo, BannerPools pools) {
        gachaInfo.incPityAll();

        int[] weights = {
            banner.getWeight(5, gachaInfo.getPity5()), banner.getWeight(4, gachaInfo.getPity4()), 10000
        };
        int levelWon = 5 - drawRoulette(weights, 10000);

        return switch (levelWon) {
            case 5:
                gachaInfo.setPity5(0);
                yield doRarePull(
                        pools.rateUpItems5,
                        pools.fallbackItems5Pool1,
                        pools.fallbackItems5Pool2,
                        5,
                        banner,
                        gachaInfo);
            case 4:
                gachaInfo.setPity4(0);
                yield doRarePull(
                        pools.rateUpItems4,
                        pools.fallbackItems4Pool1,
                        pools.fallbackItems4Pool2,
                        4,
                        banner,
                        gachaInfo);
            default:
                yield new PullResult(getRandom(banner.getFallbackItems3()), false);
        };
    }

    private static int ascensionLimitItemId() {
        return GameData.getAvatarExtraLevelDataMap().values().stream()
                .flatMap(data -> Arrays.stream(data.getCostItems()))
                .mapToInt(ItemParamData::getId)
                .findFirst()
                .orElse(0);
    }

    public synchronized void doPulls(Player player, int scheduleId, int times) {
        if (times != 10 && times != 1) {
            player.sendPacket(new PacketDoGachaRsp(Retcode.RET_GACHA_INVALID_TIMES));
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory.getInventoryTab(ItemType.ITEM_WEAPON).getSize() + times
                > inventory.getInventoryTab(ItemType.ITEM_WEAPON).getMaxCapacity()) {
            player.sendPacket(new PacketDoGachaRsp(Retcode.RET_ITEM_EXCEED_LIMIT));
            return;
        }

        GachaBanner banner = this.getGachaBanners().get(scheduleId);
        if (banner == null) {
            player.sendPacket(new PacketDoGachaRsp());
            return;
        }

        PlayerGachaBannerInfo gachaInfo = player.getGachaInfo().getBannerInfo(banner);
        var event =
                new PlayerWishEvent(
                        player,
                        banner,
                        times,
                        new PlayerWishEvent.Pity(
                                gachaInfo.getPity5(),
                                gachaInfo.getPity4(),
                                gachaInfo.getFailedFeaturedItemPulls(4) > 0,
                                banner.hasEpitomized()
                                        ? gachaInfo.getFailedChosenItemPulls() >= banner.getWishMaxProgress()
                                        : gachaInfo.getFailedFeaturedItemPulls(5) > 0));
        if (!event.call()) {
            player.sendPacket(new PacketDoGachaRsp(Retcode.RET_SVR_ERROR));
            return;
        }

        banner = event.getBanner();
        times = event.getWishCount();

        int gachaTimesLimit = banner.getGachaTimesLimit();
        if (gachaTimesLimit != Integer.MAX_VALUE
                && (gachaInfo.getTotalPulls() + times) > gachaTimesLimit) {
            player.sendPacket(new PacketDoGachaRsp(Retcode.RET_GACHA_TIMES_LIMIT));
            return;
        }

        ItemParamData cost = banner.getCost(times);
        if (cost.getCount() > 0 && !inventory.payItem(cost)) {
            player.sendPacket(new PacketDoGachaRsp(Retcode.RET_GACHA_COST_ITEM_NOT_ENOUGH));
            return;
        }

        gachaInfo.addTotalPulls(times);
        BannerPools pools = new BannerPools(banner);
        List<GachaItem> list = new ArrayList<>();
        int stardust = 0, starglitter = 0, masterlessStella = 0;
        int masterlessStellaId = ascensionLimitItemId();

        if (banner.isRemoveC6FromPool()) {
            pools.rateUpItems4 = removeC6FromPool(pools.rateUpItems4, player);
            pools.rateUpItems5 = removeC6FromPool(pools.rateUpItems5, player);
            pools.fallbackItems4Pool1 = removeC6FromPool(pools.fallbackItems4Pool1, player);
            pools.fallbackItems4Pool2 = removeC6FromPool(pools.fallbackItems4Pool2, player);
            pools.fallbackItems5Pool1 = removeC6FromPool(pools.fallbackItems5Pool1, player);
            pools.fallbackItems5Pool2 = removeC6FromPool(pools.fallbackItems5Pool2, player);
        }

        var items = new ArrayList<PlayerWishEvent.WishCompute>();
        for (int i = 0; i < times; i++) {
            PullResult pull = doPull(banner, gachaInfo, pools);
            int itemId = pull.itemId();
            ItemData itemData = GameData.getItemDataMap().get(itemId);
            if (itemData == null) {
                Grasscutter.getLogger()
                        .warn(
                                "[Gacha] Banner {} rolled item {}, which does not exist in the loaded resources. Fix the pools in Banners.json.",
                                banner.getScheduleId(),
                                itemId);
                continue;
            }

            GachaRecord gachaRecord = new GachaRecord(itemId, player.getUid(), banner.getGachaType());
            DatabaseHelper.saveGachaRecord(gachaRecord);

            GachaItem.Builder gachaItem = GachaItem.newBuilder();
            if (pull.capturedRadiance()) gachaItem.setIsFlashCard(true);
            int addStardust = 0, addStarglitter = 0;
            boolean isTransferItem = false;

            int constellation = InventorySystem.checkPlayerAvatarConstellationLevel(player, itemId);
            switch (constellation) {
                case -2:
                    switch (itemData.getRankLevel()) {
                        case 5 -> addStarglitter = 10;
                        case 4 -> addStarglitter = 2;
                        default -> addStardust = 15;
                    }
                    isTransferItem = addStarglitter > 0;
                    break;
                case -1:
                    gachaItem.setIsGachaItemNew(true);
                    break;
                default:
                    if (constellation >= 6) {
                        addStarglitter = (itemData.getRankLevel() == 5) ? 25 : 5;
                        if (itemData.getRankLevel() == 5 && masterlessStellaId > 0) {
                            masterlessStella++;
                            gachaItem.addTransferItems(
                                    GachaTransferItem.newBuilder()
                                            .setItem(ItemParam.newBuilder().setItemId(masterlessStellaId).setCount(1))
                                            .setIsTransferItemNew(
                                                    inventory.getInventoryTab(ItemType.ITEM_MATERIAL).getItemById(masterlessStellaId)
                                                            == null));
                        }
                    } else {
                        if (banner.isRemoveC6FromPool()
                                && constellation
                                        == 5) {
                            pools.removeFromAllPools(new int[] {itemId});
                        }
                        addStarglitter = (itemData.getRankLevel() == 5) ? 10 : 2;
                        int constItemId =
                                itemId + 100;
                        boolean haveConstItem =
                                inventory.getInventoryTab(ItemType.ITEM_MATERIAL).getItemById(constItemId) == null;
                        gachaItem.addTransferItems(
                                GachaTransferItem.newBuilder()
                                        .setItem(ItemParam.newBuilder().setItemId(constItemId).setCount(1))
                                        .setIsTransferItemNew(haveConstItem));
                    }
                    isTransferItem = true;
                    break;
            }

            GameItem item = new GameItem(itemData);
            items.add(
                    new PlayerWishEvent.WishCompute(
                            item, gachaItem, addStardust, addStarglitter, isTransferItem));
        }

        event.finish(items.stream().map(PlayerWishEvent.WishCompute::getItem).toList());

        var eventItems = event.getReceivedItems();
        for (var i = 0; i < items.size(); i++) {
            var compute = items.get(i);
            var gameItem = eventItems.get(i);
            var gachaItem = compute.getGacha();

            gachaItem.setGachaItem(gameItem.toItemParam());
            inventory.addItem(gameItem);

            stardust += compute.getAddStardust();
            starglitter += compute.getAddStarglitter();

            if (compute.getAddStardust() > 0) {
                gachaItem.addTokenItemList(
                        ItemParam.newBuilder().setItemId(stardustId).setCount(compute.getAddStardust()));
            }
            if (compute.getAddStarglitter() > 0) {
                ItemParam starglitterParam =
                        ItemParam.newBuilder()
                                .setItemId(starglitterId)
                                .setCount(compute.getAddStarglitter())
                                .build();
                if (compute.isTransferItem()) {
                    gachaItem.addTransferItems(GachaTransferItem.newBuilder().setItem(starglitterParam));
                }
                gachaItem.addTokenItemList(starglitterParam);
            }

            list.add(gachaItem.build());
        }

        if (stardust > 0) {
            inventory.addItem(stardustId, stardust);
        }
        if (starglitter > 0) {
            inventory.addItem(starglitterId, starglitter);
        }
        if (masterlessStella > 0) {
            inventory.addItem(masterlessStellaId, masterlessStella);
        }

        player.sendPacket(new PacketDoGachaRsp(banner, list, gachaInfo));

        player.getBattlePassManager().triggerMission(WatcherTriggerType.TRIGGER_GACHA_NUM, 0, times);
    }

    private synchronized void startWatcher(GameServer server) {
        if (this.watchService == null) {
            try {
                this.watchService = FileSystems.getDefault().newWatchService();
                FileUtils.getDataUserPath("")
                        .register(
                                watchService,
                                new WatchEvent.Kind[] {StandardWatchEventKinds.ENTRY_MODIFY},
                                SensitivityWatchEventModifier.HIGH);
            } catch (Exception e) {
                Grasscutter.getLogger()
                        .error(
                                "Unable to load the Gacha Manager Watch Service. If ServerOptions.watchGacha is true it will not auto-reload");
                e.printStackTrace();
            }
        } else {
            Grasscutter.getLogger().error("Cannot reinitialise watcher ");
        }
    }

    @Subscribe
    public synchronized void watchBannerJson(GameServerTickEvent tickEvent) {
        if (GAME_OPTIONS.watchGachaConfig) {
            try {
                WatchKey watchKey = watchService.poll();
                if (watchKey == null) return;

                for (WatchEvent<?> event : watchKey.pollEvents()) {
                    final Path changed = (Path) event.context();
                    if (changed.endsWith("Banners.json")) {
                        Grasscutter.getLogger()
                                .info("Change detected with banners.json. Reloading gacha config");
                        this.load();
                    }
                }

                boolean valid = watchKey.reset();
                if (!valid) {
                    Grasscutter.getLogger()
                            .error(
                                    "Unable to reset Gacha Manager Watch Key. Auto-reload of banners.json will no longer work.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private synchronized GetGachaInfoRsp createProto(Player player) {
        GetGachaInfoRsp.Builder proto = GetGachaInfoRsp.newBuilder().setGachaRandom(12345);

        long currentTime = System.currentTimeMillis() / 1000L;

        for (GachaBanner banner : getGachaBanners().values()) {
            boolean timeOk = banner.getEndTime() >= currentTime && banner.getBeginTime() <= currentTime;
            boolean isStandard = banner.getBannerType() == BannerType.STANDARD;
            if (timeOk || isStandard) {
                proto.addGachaInfoList(banner.toProto(player));
            } else {
                Grasscutter.getLogger()
                        .debug(
                                "[Gacha] Banner {} (schedule {}) is outside its window {}-{}, skipping.",
                                banner.getGachaType(),
                                banner.getScheduleId(),
                                banner.getBeginTime(),
                                banner.getEndTime());
            }
        }

        if (proto.getGachaInfoListCount() == 0) {
            Grasscutter.getLogger()
                    .warn(
                            "[Gacha] No banner is currently active - the wish screen will be empty. Check the beginTime/endTime of the {} banner(s) in Banners.json.",
                            getGachaBanners().size());
        }

        return proto.build();
    }

    public GetGachaInfoRsp toProto(Player player) {
        return createProto(player);
    }

    private class BannerPools {
        public int[] rateUpItems4;
        public int[] rateUpItems5;
        public int[] fallbackItems4Pool1;
        public int[] fallbackItems4Pool2;
        public int[] fallbackItems5Pool1;
        public int[] fallbackItems5Pool2;

        public BannerPools(GachaBanner banner) {
            rateUpItems4 = banner.getRateUpItems4();
            rateUpItems5 = banner.getRateUpItems5();
            fallbackItems4Pool1 = banner.getFallbackItems4Pool1();
            fallbackItems4Pool2 = banner.getFallbackItems4Pool2();
            fallbackItems5Pool1 = banner.getFallbackItems5Pool1();
            fallbackItems5Pool2 = banner.getFallbackItems5Pool2();

            if (banner.isAutoStripRateUpFromFallback()) {
                fallbackItems4Pool1 = Utils.setSubtract(fallbackItems4Pool1, rateUpItems4);
                fallbackItems4Pool2 = Utils.setSubtract(fallbackItems4Pool2, rateUpItems4);
                fallbackItems5Pool1 = Utils.setSubtract(fallbackItems5Pool1, rateUpItems5);
                fallbackItems5Pool2 = Utils.setSubtract(fallbackItems5Pool2, rateUpItems5);
            }
        }

        public void removeFromAllPools(int[] itemIds) {
            rateUpItems4 = Utils.setSubtract(rateUpItems4, itemIds);
            rateUpItems5 = Utils.setSubtract(rateUpItems5, itemIds);
            fallbackItems4Pool1 = Utils.setSubtract(fallbackItems4Pool1, itemIds);
            fallbackItems4Pool2 = Utils.setSubtract(fallbackItems4Pool2, itemIds);
            fallbackItems5Pool1 = Utils.setSubtract(fallbackItems5Pool1, itemIds);
            fallbackItems5Pool2 = Utils.setSubtract(fallbackItems5Pool2, itemIds);
        }
    }
}
