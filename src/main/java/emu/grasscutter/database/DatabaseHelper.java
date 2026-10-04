package emu.grasscutter.database;

import static com.mongodb.client.model.Filters.eq;

import com.mongodb.MongoWriteException;

import dev.morphia.query.*;
import dev.morphia.query.experimental.filters.Filters;
import emu.grasscutter.*;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.achievement.Achievements;
import emu.grasscutter.game.activity.PlayerActivityData;
import emu.grasscutter.game.activity.musicgame.MusicGameBeatmap;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.friends.Friendship;
import emu.grasscutter.game.gacha.GachaRecord;
import emu.grasscutter.game.home.GameHome;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.GameMainQuest;
import emu.grasscutter.game.world.SceneGroupInstance;
import emu.grasscutter.utils.objects.Returnable;
import io.netty.util.concurrent.FastThreadLocalThread;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.Getter;

public final class DatabaseHelper {
    @Getter
    private static final ExecutorService eventExecutor =
            new ThreadPoolExecutor(
                    6,
                    6,
                    60,
                    TimeUnit.SECONDS,
                    new LinkedBlockingDeque<>(),
                    FastThreadLocalThread::new,
                    new ThreadPoolExecutor.AbortPolicy());

    public static void saveAccountAsync(Object object) {
        DatabaseHelper.eventExecutor.submit(() -> DatabaseManager.getAccountDatastore().save(object));
    }

    public static void saveGameAsync(Object object) {
        DatabaseHelper.eventExecutor.submit(() -> saveWithRetry(object));
    }

    private static void saveWithRetry(Object object) {
        var name = object.getClass().getSimpleName();
        try {
            DatabaseManager.getGameDatastore().save(object);
        } catch (MongoWriteException e) {
            if (e.getError() == null || e.getError().getCode() != 11000) {
                Grasscutter.getLogger().error("Failed to save {}.", name, e);
                return;
            }
            try {
                DatabaseManager.getGameDatastore().save(object);
            } catch (Throwable t) {
                Grasscutter.getLogger().error("Failed to save {} after a duplicate key.", name, t);
            }
        } catch (ConcurrentModificationException e) {
            for (var attempt = 0; attempt < 8; attempt++) {
                try {
                    Thread.sleep(100);
                    DatabaseManager.getGameDatastore().save(object);
                    return;
                } catch (ConcurrentModificationException ignored) {
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Throwable t) {
                    Grasscutter.getLogger().error("Failed to save {}.", name, t);
                    return;
                }
            }
            Grasscutter.getLogger().error("Failed to save {} - it kept being modified.", name, e);
        } catch (Throwable t) {
            Grasscutter.getLogger().error("Failed to save {}.", name, t);
        }
    }

    public static void asyncOperation(Runnable runnable) {
        DatabaseHelper.eventExecutor.submit(runnable);
    }

    public static <T> CompletableFuture<T> fetchAsync(Returnable<T> task) {
        var future = new CompletableFuture<T>();

        DatabaseHelper.eventExecutor.submit(
                () -> {
                    try {
                        future.complete(task.invoke());
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });

        return future;
    }

    public static Account createAccount(String username) {
        return createAccountWithUid(username, 0);
    }

    public static Account createAccountWithUid(String username, int reservedUid) {
        if (DatabaseHelper.checkIfAccountExists(username)) {
            return null;
        }

        if (reservedUid > 0) {
            if (reservedUid == GameConstants.SERVER_CONSOLE_UID) {
                return null;
            }

            if (DatabaseHelper.checkIfAccountExists(reservedUid)) {
                return null;
            }

            if (DatabaseHelper.checkIfPlayerExists(reservedUid)) {
                return null;
            }
        }

        @SuppressWarnings("deprecation")
        Account account = new Account();
        account.setUsername(username);
        account.setId(Integer.toString(DatabaseManager.getNextId(account)));

        if (reservedUid > 0) {
            account.setReservedPlayerUid(reservedUid);
        }

        DatabaseHelper.saveAccount(account);
        return account;
    }

    @Deprecated
    public static Account createAccountWithPassword(String username, String password) {
        Account exists = DatabaseHelper.getAccountByName(username);
        if (exists != null) {
            return null;
        }

        Account account = new Account();
        account.setId(Integer.toString(DatabaseManager.getNextId(account)));
        account.setUsername(username);
        account.setPassword(password);
        DatabaseHelper.saveAccount(account);
        return account;
    }

    public static void saveAccount(Account account) {
        DatabaseHelper.saveAccountAsync(account);
    }

    public static Account getAccountByName(String username) {
        return DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(Filters.eq("username", username))
                .first();
    }

    public static Account getAccountByToken(String token) {
        if (token == null) return null;
        return DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(Filters.eq("token", token))
                .first();
    }

    public static Account getAccountBySessionKey(String sessionKey) {
        if (sessionKey == null) return null;
        return DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(Filters.eq("sessionKey", sessionKey))
                .first();
    }

    public static Account getAccountById(String uid) {
        return DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(Filters.eq("_id", uid))
                .first();
    }

    public static Account getAccountByPlayerId(int playerId) {
        return DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(Filters.eq("reservedPlayerId", playerId))
                .first();
    }

    public static boolean checkIfAccountExists(String name) {
        return DatabaseManager.getAccountDatastore()
                        .find(Account.class)
                        .filter(Filters.eq("username", name))
                        .count()
                > 0;
    }

    public static boolean checkIfAccountExists(int reservedUid) {
        return DatabaseManager.getAccountDatastore()
                        .find(Account.class)
                        .filter(Filters.eq("reservedPlayerId", reservedUid))
                        .count()
                > 0;
    }

    public static synchronized void deleteAccount(Account target) {
        Player player = Grasscutter.getGameServer().getPlayerByAccountId(target.getId());

        if (player != null) {
            player.getSession().close();
        } else {
            player = getPlayerByAccount(target);
            if (player == null) return;
        }
        int uid = player.getUid();

        DatabaseHelper.asyncOperation(
                () -> {
                    DatabaseManager.getGameDatabase()
                            .getCollection("achievements")
                            .deleteMany(eq("uid", uid));
                    DatabaseManager.getGameDatabase().getCollection("activities").deleteMany(eq("uid", uid));
                    DatabaseManager.getGameDatabase().getCollection("homes").deleteMany(eq("ownerUid", uid));
                    DatabaseManager.getGameDatabase().getCollection("mail").deleteMany(eq("ownerUid", uid));
                    DatabaseManager.getGameDatabase().getCollection("avatars").deleteMany(eq("ownerId", uid));
                    DatabaseManager.getGameDatabase().getCollection("gachas").deleteMany(eq("ownerId", uid));
                    DatabaseManager.getGameDatabase().getCollection("items").deleteMany(eq("ownerId", uid));
                    DatabaseManager.getGameDatabase().getCollection("quests").deleteMany(eq("ownerUid", uid));
                    DatabaseManager.getGameDatabase()
                            .getCollection("battlepass")
                            .deleteMany(eq("ownerUid", uid));

                    DatabaseManager.getGameDatabase()
                            .getCollection("friendships")
                            .deleteMany(eq("ownerId", uid));
                    DatabaseManager.getGameDatabase()
                            .getCollection("friendships")
                            .deleteMany(eq("friendId", uid));

                    DatabaseManager.getGameDatastore()
                            .find(Player.class)
                            .filter(Filters.eq("id", uid))
                            .delete();

                    DatabaseManager.getAccountDatastore()
                            .find(Account.class)
                            .filter(Filters.eq("id", target.getId()))
                            .delete();
                });
    }

    public static <T> Stream<T> getByGameClass(Class<T> classType) {
        return DatabaseManager.getGameDatastore().find(classType).stream();
    }

    @Deprecated(forRemoval = true)
    public static List<Player> getAllPlayers() {
        return DatabaseManager.getGameDatastore().find(Player.class).stream().toList();
    }

    public static Player getPlayerByUid(int id) {
        return DatabaseManager.getGameDatastore()
                .find(Player.class)
                .filter(Filters.eq("_id", id))
                .first();
    }

    @Deprecated
    public static Player getPlayerByAccount(Account account) {
        return DatabaseManager.getGameDatastore()
                .find(Player.class)
                .filter(Filters.eq("accountId", account.getId()))
                .first();
    }

    public static Player getPlayerByAccount(Account account, Class<? extends Player> playerClass) {
        return DatabaseManager.getGameDatastore()
                .find(playerClass)
                .filter(Filters.eq("accountId", account.getId()))
                .first();
    }

    public static Player getPlayerByAccount(String accountId) {
        return DatabaseManager.getGameDatastore()
                .find(Player.class)
                .filter(Filters.eq("accountId", accountId))
                .first();
    }

    public static boolean checkIfPlayerExists(int uid) {
        return DatabaseManager.getGameDatastore()
                        .find(Player.class)
                        .filter(Filters.eq("_id", uid))
                        .count()
                > 0;
    }

    public static synchronized void generatePlayerUid(Player character, int reservedId) {
        int id;
        if (reservedId > 0 && !checkIfPlayerExists(reservedId)) {
            id = reservedId;
            character.setUid(id);
        } else {
            do {
                id = DatabaseManager.getNextId(character);
            } while (checkIfPlayerExists(id));
            character.setUid(id);
        }

        DatabaseHelper.saveGameAsync(character);
    }

    public static synchronized int getNextPlayerId(int reservedId) {
        int id;
        if (reservedId > 0 && !checkIfPlayerExists(reservedId)) {
            id = reservedId;
        } else {
            do {
                id = DatabaseManager.getNextId(Player.class);
            } while (checkIfPlayerExists(id));
        }
        return id;
    }

    public static void savePlayer(Player character) {
        DatabaseHelper.saveGameAsync(character);
    }

    public static void saveAvatar(Avatar avatar) {
        DatabaseHelper.saveGameAsync(avatar);
    }

    public static void deleteAvatar(Avatar avatar) {
        DatabaseManager.getGameDatastore().delete(avatar);
    }

    public static List<Avatar> getAvatars(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(Avatar.class)
                .filter(Filters.eq("ownerId", player.getUid()))
                .stream()
                .toList();
    }

    public static void saveItem(GameItem item) {
        DatabaseHelper.saveGameAsync(item);
    }

    public static void deleteItem(GameItem item) {
        DatabaseHelper.asyncOperation(() -> DatabaseManager.getGameDatastore().delete(item));
    }

    public static List<GameItem> getInventoryItems(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(GameItem.class)
                .filter(Filters.eq("ownerId", player.getUid()))
                .stream()
                .toList();
    }

    public static List<Friendship> getFriends(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(Friendship.class)
                .filter(Filters.eq("ownerId", player.getUid()))
                .stream()
                .toList();
    }

    public static List<Friendship> getReverseFriends(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(Friendship.class)
                .filter(Filters.eq("friendId", player.getUid()))
                .stream()
                .toList();
    }

    public static void saveFriendship(Friendship friendship) {
        DatabaseHelper.saveGameAsync(friendship);
    }

    public static void deleteFriendship(Friendship friendship) {
        DatabaseHelper.asyncOperation(() -> DatabaseManager.getGameDatastore().delete(friendship));
    }

    public static Friendship getReverseFriendship(Friendship friendship) {
        return DatabaseManager.getGameDatastore()
                .find(Friendship.class)
                .filter(
                        Filters.and(
                                Filters.eq("ownerId", friendship.getFriendId()),
                                Filters.eq("friendId", friendship.getOwnerId())))
                .first();
    }

    public static List<GachaRecord> getGachaRecords(int ownerId, int page, int gachaType) {
        return getGachaRecords(ownerId, page, gachaType, 10);
    }

    public static List<GachaRecord> getGachaRecords(
            int ownerId, int page, int gachaType, int pageSize) {
        return DatabaseManager.getGameDatastore()
                .find(GachaRecord.class)
                .filter(Filters.eq("ownerId", ownerId), Filters.eq("gachaType", gachaType))
                .iterator(
                        new FindOptions()
                                .sort(Sort.descending("transactionDate"))
                                .skip(pageSize * page)
                                .limit(pageSize))
                .toList();
    }

    public static long getGachaRecordsMaxPage(int ownerId, int page, int gachaType) {
        return getGachaRecordsMaxPage(ownerId, page, gachaType, 10);
    }

    public static long getGachaRecordsMaxPage(int ownerId, int page, int gachaType, int pageSize) {
        long count =
                DatabaseManager.getGameDatastore()
                        .find(GachaRecord.class)
                        .filter(Filters.eq("ownerId", ownerId), Filters.eq("gachaType", gachaType))
                        .count();
        return count / 10 + (count % 10 > 0 ? 1 : 0);
    }

    public static void saveGachaRecord(GachaRecord gachaRecord) {
        DatabaseHelper.saveGameAsync(gachaRecord);
    }

    public static List<Mail> getAllMail(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(Mail.class)
                .filter(Filters.eq("ownerUid", player.getUid()))
                .stream()
                .toList();
    }

    public static void saveMail(Mail mail) {
        DatabaseHelper.saveGameAsync(mail);
    }

    public static void deleteMail(Mail mail) {
        DatabaseHelper.asyncOperation(() -> DatabaseManager.getGameDatastore().delete(mail));
    }

    public static List<GameMainQuest> getAllQuests(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(GameMainQuest.class)
                .filter(Filters.eq("ownerUid", player.getUid()))
                .stream()
                .toList();
    }

    public static void saveQuest(GameMainQuest quest) {
        DatabaseHelper.saveGameAsync(quest);
    }

    public static void deleteQuest(GameMainQuest quest) {
        DatabaseHelper.asyncOperation(() -> DatabaseManager.getGameDatastore().delete(quest));
    }

    public static GameHome getHomeByUid(int id) {
        return DatabaseManager.getGameDatastore()
                .find(GameHome.class)
                .filter(Filters.eq("ownerUid", id))
                .first();
    }

    public static void saveHome(GameHome gameHome) {
        DatabaseHelper.saveGameAsync(gameHome);
    }

    public static emu.grasscutter.game.dailytask.DailyTaskManager loadDailyTaskManager(Player player) {
        var manager =
                DatabaseManager.getGameDatastore()
                        .find(emu.grasscutter.game.dailytask.DailyTaskManager.class)
                        .filter(Filters.eq("ownerUid", player.getUid()))
                        .first();

        if (manager == null) {
            manager = new emu.grasscutter.game.dailytask.DailyTaskManager(player);
            manager.save();
        } else {
            manager.setPlayer(player);
        }

        return manager;
    }

    public static void saveDailyTaskManager(emu.grasscutter.game.dailytask.DailyTaskManager manager) {
        DatabaseManager.getGameDatastore().save(manager);
    }

    public static BattlePassManager loadBattlePass(Player player) {
        BattlePassManager manager =
                DatabaseManager.getGameDatastore()
                        .find(BattlePassManager.class)
                        .filter(Filters.eq("ownerUid", player.getUid()))
                        .first();
        if (manager == null) {
            manager = new BattlePassManager(player);
            manager.save();
        } else {
            manager.setPlayer(player);
        }
        return manager;
    }

    public static void saveBattlePass(BattlePassManager manager) {
        DatabaseHelper.saveGameAsync(manager);
    }

    public static PlayerActivityData getPlayerActivityData(int uid, int activityId) {
        return DatabaseManager.getGameDatastore()
                .find(PlayerActivityData.class)
                .filter(Filters.and(Filters.eq("uid", uid), Filters.eq("activityId", activityId)))
                .first();
    }

    public static void savePlayerActivityData(PlayerActivityData playerActivityData) {
        DatabaseHelper.saveGameAsync(playerActivityData);
    }

    public static MusicGameBeatmap getMusicGameBeatmap(long musicShareId) {
        return DatabaseManager.getGameDatastore()
                .find(MusicGameBeatmap.class)
                .filter(Filters.eq("musicShareId", musicShareId))
                .first();
    }

    public static void saveMusicGameBeatmap(MusicGameBeatmap musicGameBeatmap) {
        DatabaseHelper.saveGameAsync(musicGameBeatmap);
    }

    @Nullable public static Achievements getAchievementData(int uid) {
        try {
            return DatabaseManager.getGameDatastore()
                    .find(Achievements.class)
                    .filter(Filters.and(Filters.eq("uid", uid)))
                    .first();
        } catch (IllegalArgumentException e) {
            Grasscutter.getLogger()
                    .debug("Error occurred while getting uid " + uid + "'s achievement data", e);
            DatabaseManager.getGameDatabase().getCollection("achievements").deleteMany(eq("uid", uid));
            return null;
        }
    }

    public static void saveAchievementData(Achievements achievements) {
        DatabaseHelper.saveGameAsync(achievements);
    }

    public static void saveGroupInstance(SceneGroupInstance instance) {
        DatabaseHelper.saveGameAsync(instance);
    }

    public static SceneGroupInstance loadGroupInstance(int groupId, Player owner) {
        return DatabaseManager.getGameDatastore()
                .find(SceneGroupInstance.class)
                .filter(Filters.and(Filters.eq("ownerUid", owner.getUid()), Filters.eq("groupId", groupId)))
                .first();
    }
}
