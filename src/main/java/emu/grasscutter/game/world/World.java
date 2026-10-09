package emu.grasscutter.game.world;

import emu.grasscutter.game.combat.DamageLog;
import static emu.grasscutter.server.event.player.PlayerTeleportEvent.TeleportType.SCRIPT;

import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.entity.EntityWorld;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.Player.SceneLoadState;
import emu.grasscutter.game.props.EnterReason;
import emu.grasscutter.game.props.EntityIdType;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.EnterTypeOuterClass.EnterType;
import emu.grasscutter.net.proto.SystemHintOuterClass;
import emu.grasscutter.net.proto.SystemHintTypeOuterClass;
import emu.grasscutter.scripts.data.SceneConfig;
import emu.grasscutter.server.event.player.PlayerTeleportEvent;
import emu.grasscutter.server.event.player.PlayerTeleportEvent.TeleportType;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.ConversionUtils;
import io.netty.util.concurrent.FastThreadLocalThread;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.val;
import org.jetbrains.annotations.NotNull;

public class World implements Iterable<Player> {
    @Getter private final GameServer server;
    @Getter private Player host;
    @Getter private final List<Player> players;
    @Getter private final Int2ObjectMap<Scene> scenes;
    @Getter private final DamageLog damageLog = new DamageLog();

    @Getter private EntityWorld entity;
    private int nextEntityId = 0;
    private int nextPeerId = 0;
    private int worldLevel;

    @Getter private boolean isMultiplayer = false;
    @Getter private boolean timeLocked;

    private long lastUpdateTime;
    @Getter protected int tickCount = 0;
    private long lastTimeSync = 0;
    private long lastTimeStore = 0;
    @Getter private boolean isPaused = false;
    @Getter private long currentWorldTime;

    private static final ExecutorService eventExecutor =
            new ThreadPoolExecutor(
                    4,
                    4,
                    60,
                    TimeUnit.SECONDS,
                    new LinkedBlockingDeque<>(1000),
                    FastThreadLocalThread::new,
                    new ThreadPoolExecutor.AbortPolicy());

    public World(Player player) {
        this(player, false);
    }

    public World(Player player, boolean isMultiplayer) {
        this.host = player;
        this.server = player.getServer();
        this.players = Collections.synchronizedList(new ArrayList<>());
        this.scenes = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());

        this.entity = new EntityWorld(this);
        this.worldLevel = player.getWorldLevel();
        this.isMultiplayer = isMultiplayer;
        this.timeLocked = player.getProperty(PlayerProperty.PROP_IS_GAME_TIME_LOCKED) != 0;

        this.lastUpdateTime = System.currentTimeMillis();
        this.currentWorldTime = host.getPlayerGameTime();

        this.host.getServer().registerWorld(this);
    }

    public World(GameServer server, Player owner) {
        this.server = server;
        this.host = owner;
        this.players = Collections.synchronizedList(new ArrayList<>());
        this.scenes = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
        this.entity = new EntityWorld(this);
        this.lastUpdateTime = System.currentTimeMillis();

        server.registerWorld(this);
    }

    public int getLevelEntityId() {
        return entity.getId();
    }

    public int getHostPeerId() {
        return this.getHost() == null ? 0 : this.getHost().getPeerId();
    }

    public int getNextPeerId() {
        return ++this.nextPeerId;
    }

    public int getWorldLevel() {
        return worldLevel;
    }

    public void setWorldLevel(int worldLevel) {
        this.worldLevel = worldLevel;
    }

    protected synchronized void setHost(Player host) {
        this.host = host;
    }

    @Nullable public Scene getSceneById(int sceneId) {
        var scene = this.getScenes().get(sceneId);
        if (scene != null) {
            return scene;
        }

        var sceneData = GameData.getSceneDataMap().get(sceneId);
        if (sceneData != null) {
            scene = new Scene(this, sceneData);
            this.registerScene(scene);
            return scene;
        }

        return null;
    }

    public int getPlayerCount() {
        return this.players.size();
    }

    public synchronized int getNextEntityId(EntityIdType idType) {
        return (idType.getId() << GameConstants.ENTITY_ID_BIT_SHIFT) + ++this.nextEntityId;
    }

    public synchronized void addPlayer(Player player) {
        if (this.getPlayers().contains(player)) {
            return;
        }

        if (player.getWorld() != null) {
            player.getWorld().removePlayer(player);
        }

        player.setWorld(this);
        this.getPlayers().add(player);

        player.setPeerId(this.getNextPeerId());
        player.getTeamManager().setEntity(new EntityTeam(player));

        if (this.isMultiplayer()) {
            player
                    .getTeamManager()
                    .getMpTeam()
                    .copyFrom(
                            player.getTeamManager().getCurrentSinglePlayerTeamInfo(),
                            player.getTeamManager().getMaxTeamSize());
            player.getTeamManager().setCurrentCharacterIndex(0);
        }

        Scene scene = this.getSceneById(player.getSceneId());
        scene.addPlayer(player);

        if (this.getPlayers().size() > 1) {
            this.updatePlayerInfos(player);
        }
    }

    public synchronized void addPlayer(Player player, int newSceneId) {
        if (this.getPlayers().contains(player)) {
            return;
        }

        if (player.getWorld() != null) {
            player.getWorld().removePlayer(player);
        }

        player.setWorld(this);
        this.getPlayers().add(player);

        player.setPeerId(this.getNextPeerId());
        player.getTeamManager().setEntity(new EntityTeam(player));

        if (this.isMultiplayer()) {
            player
                    .getTeamManager()
                    .getMpTeam()
                    .copyFrom(
                            player.getTeamManager().getCurrentSinglePlayerTeamInfo(),
                            player.getTeamManager().getMaxTeamSize());
            player.getTeamManager().setCurrentCharacterIndex(0);

            if (player != this.getHost()) {
                this.broadcastPacket(
                        new PacketPlayerChatNotify(
                                player,
                                0,
                                SystemHintOuterClass.SystemHint.newBuilder()
                                        .setType(SystemHintTypeOuterClass.SystemHintType.SYSTEM_HINT_TYPE_CHAT_ENTER_WORLD.getNumber())
                                        .build()));
            }
        }

        player.setSceneId(newSceneId);
        Scene scene = this.getSceneById(player.getSceneId());
        scene.addPlayer(player);

        if (this.getPlayers().size() > 1) {
            this.updatePlayerInfos(player);
        }
    }

    public synchronized void removePlayer(Player player) {
        player.sendPacket(
                new PacketDelTeamEntityNotify(
                        player.getSceneId(),
                        this.getPlayers().stream()
                                .map(
                                        p ->
                                                p.getTeamManager().getEntity() == null
                                                        ? 0
                                                        : p.getTeamManager().getEntity().getId())
                                .toList()));

        this.getPlayers().remove(player);
        player.setWorld(null);

        Scene scene = this.getSceneById(player.getSceneId());
        scene.removePlayer(player);

        if (this.getPlayers().size() > 0) {
            this.updatePlayerInfos(player);
        }

        if (this.getHost() == player) {
            List<Player> kicked = new ArrayList<>(this.getPlayers());
            for (Player victim : kicked) {
                World world = new World(victim);
                world.addPlayer(victim);

                victim.sendPacket(
                        new PacketPlayerEnterSceneNotify(
                                victim,
                                EnterType.EnterType_ENTER_SELF,
                                EnterReason.TeamKick,
                                victim.getSceneId(),
                                victim.getPosition()));
            }
        } else {
            this.broadcastPacket(
                    new PacketPlayerChatNotify(
                            player,
                            0,
                            SystemHintOuterClass.SystemHint.newBuilder()
                                    .setType(SystemHintTypeOuterClass.SystemHintType.SYSTEM_HINT_TYPE_CHAT_LEAVE_WORLD.getNumber())
                                    .build()));
        }
    }

    public void registerScene(Scene scene) {
        this.getScenes().put(scene.getId(), scene);
    }

    public void deregisterScene(Scene scene) {
        scene.saveGroups();
        this.getScenes().remove(scene.getId());
    }

    public void save() {
        this.getScenes().values().forEach(Scene::saveGroups);
    }

    public void queueTransferPlayerToScene(Player player, int sceneId, Position pos, int delayMs) {
        player.setQueuedTeleport(
                eventExecutor.submit(
                        () -> {
                            try {
                                Thread.sleep(delayMs);
                                transferPlayerToScene(player, sceneId, pos);
                            } catch (InterruptedException e) {
                                Grasscutter.getLogger()
                                        .trace(
                                                "queueTransferPlayerToScene: teleport to scene {} is interrupted", sceneId);
                            } catch (Throwable e) {
                                Grasscutter.getLogger()
                                        .error("Queued teleport to scene {} failed.", sceneId, e);
                            }
                        }));
    }

    public boolean transferPlayerToScene(Player player, int sceneId, Position pos) {
        return this.transferPlayerToScene(player, sceneId, TeleportType.INTERNAL, null, pos);
    }

    public boolean transferPlayerToScene(
            Player player, int sceneId, TeleportType teleportType, Position pos) {
        return this.transferPlayerToScene(player, sceneId, teleportType, null, pos);
    }

    public boolean transferPlayerToScene(Player player, int sceneId, DungeonData data) {
        return this.transferPlayerToScene(player, sceneId, TeleportType.DUNGEON, data, null);
    }

    public boolean transferPlayerToScene(
            Player player,
            int sceneId,
            TeleportType teleportType,
            DungeonData dungeonData,
            Position teleportTo) {
        EnterReason enterReason =
                switch (teleportType) {
                    case INTERNAL -> EnterReason.TransPoint;
                    case WAYPOINT -> EnterReason.TransPoint;
                    case MAP -> EnterReason.TransPoint;
                    case COMMAND -> EnterReason.Gm;
                    case SCRIPT -> EnterReason.Lua;
                    case CLIENT -> EnterReason.ClientTransmit;
                    case DUNGEON -> EnterReason.DungeonEnter;
                    default -> EnterReason.None;
                };
        return transferPlayerToScene(
                player, sceneId, teleportType, enterReason, dungeonData, teleportTo);
    }

    public boolean transferPlayerToScene(
            Player player,
            int sceneId,
            TeleportType teleportType,
            EnterReason enterReason,
            DungeonData dungeonData,
            Position teleportTo) {
        val teleportProps =
                TeleportProperties.builder()
                        .sceneId(sceneId)
                        .teleportType(teleportType)
                        .enterReason(enterReason)
                        .teleportTo(teleportTo)
                        .enterType(EnterType.EnterType_ENTER_JUMP);

        val sceneData = GameData.getSceneDataMap().get(sceneId);
        if (dungeonData != null) {
            teleportProps
                    .teleportTo(dungeonData.getStartPosition())
                    .teleportRot(dungeonData.getStartRotation());
            teleportProps.enterType(EnterType.EnterType_ENTER_DUNGEON).enterReason(EnterReason.DungeonEnter);
            teleportProps.dungeonId(dungeonData.getId());
        } else if (player.getSceneId() == sceneId) {
            teleportProps.enterType(EnterType.EnterType_ENTER_GOTO);
        } else if (sceneData != null && sceneData.getSceneType() == SceneType.SCENE_HOME_WORLD) {
            teleportProps.enterType(EnterType.EnterType_ENTER_SELF_HOME).enterReason(EnterReason.EnterHome);
        }

        return transferPlayerToScene(player, teleportProps.build());
    }

    public boolean transferPlayerToScene(Player player, TeleportProperties teleportProperties) {
        synchronized (player) {
            var queuedTeleport = player.getQueuedTeleport();
            if (queuedTeleport != null) {
                player.setQueuedTeleport(null);
                queuedTeleport.cancel(true);
            }
        }

        if (teleportProperties.getTeleportTo() == null)
            teleportProperties.setTeleportTo(player.getPosition());

        PlayerTeleportEvent event =
                new PlayerTeleportEvent(player, teleportProperties, player.getPosition());
        event.call();
        if (event.isCanceled()) {
            return false;
        }

        if (GameData.getSceneDataMap().get(teleportProperties.getSceneId()) == null) {
            return false;
        }

        Scene oldScene = player.getScene();
        var newScene = this.getSceneById(teleportProperties.getSceneId());

        if (newScene == oldScene && teleportProperties.getTeleportType() == TeleportType.COMMAND) {
            if (teleportProperties.getTeleportTo() != null) {
                player.getPosition().set(teleportProperties.getTeleportTo());
            }
            if (teleportProperties.getTeleportRot() != null) {
                player.getRotation().set(teleportProperties.getTeleportRot());
            }
            player.sendPacket(new PacketSceneEntityAppearNotify(player));
            return true;
        }

        if (oldScene != null) {
            if (oldScene == newScene) {
                oldScene.setDontDestroyWhenEmpty(true);
            }
            oldScene.removePlayer(player);
        }

        if (newScene != null) {
            newScene.addPlayer(player);

            player.getTeamManager().applyAbilities(newScene);

            SceneConfig config = newScene.getScriptManager().getConfig();
            if (teleportProperties.getTeleportTo() == null && config != null) {
                if (config.born_pos != null) {
                    teleportProperties.setTeleportTo(config.born_pos);
                }
                if (config.born_rot != null) {
                    teleportProperties.setTeleportRot(config.born_rot);
                }
            }
        }

        if (teleportProperties.getTeleportTo() != null) {
            player.getPosition().set(teleportProperties.getTeleportTo());
        }
        if (teleportProperties.getTeleportRot() != null) {
            player.getRotation().set(teleportProperties.getTeleportRot());
        }

        if (oldScene != null && newScene != null && newScene != oldScene) {
            newScene.setPrevScenePoint(oldScene.getPrevScenePoint());
            oldScene.setDontDestroyWhenEmpty(false);
        }

        player.sendPacket(new PacketPlayerEnterSceneNotify(player, teleportProperties));

        if (teleportProperties.getTeleportType() != TeleportType.INTERNAL
                && teleportProperties.getTeleportType() != SCRIPT) {
            player.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_ANY_MANUAL_TRANSPORT);
        }

        return true;
    }

    protected void updatePlayerInfos(Player paramPlayer) {
        for (Player player : this.getPlayers()) {
            if (!player.hasSentLoginPackets() || player == paramPlayer) {
                continue;
            }

            if (this.isMultiplayer()) {
                player
                        .getTeamManager()
                        .getMpTeam()
                        .copyFrom(
                                player.getTeamManager().getMpTeam(), player.getTeamManager().getMaxTeamSize());
                player.getTeamManager().updateTeamEntities(null);
            }

            if (player.getSceneLoadState().getValue() >= SceneLoadState.INIT.getValue()) {
                player.getSession().send(new PacketWorldPlayerInfoNotify(this));
                player.getSession().send(new PacketScenePlayerInfoNotify(this));
                player.getSession().send(new PacketWorldPlayerRTTNotify(this));

                player.getSession().send(new PacketSyncTeamEntityNotify(player));
                player.getSession().send(new PacketSyncScenePlayTeamEntityNotify(player));
            }
        }
    }

    public void broadcastPacket(BasePacket packet) {
        for (Player player : this.getPlayers()) {
            player.getSession().send(packet);
        }
    }

    public boolean onTick() {
        if (this.getPlayerCount() == 0) return true;
        this.getScenes()
                .forEach(
                        (k, scene) -> {
                            if (scene.getPlayerCount() == 0) return;

                            try {
                                scene.onTick();
                            } catch (Throwable e) {
                                Grasscutter.getLogger().error("Scene {} threw while ticking.", k, e);
                            }
                        });

        var now = System.currentTimeMillis();

        if (now - this.lastTimeSync >= 10_000L) {
            this.lastTimeSync = now;
            this.getPlayers().forEach(p -> p.sendPacket(new PacketPlayerGameTimeNotify(p)));
        }

        if (now - this.lastTimeStore >= 60_000L && !this.timeLocked) {
            this.lastTimeStore = now;
            this.getHost().updatePlayerGameTime(this.currentWorldTime);
        }

        this.tickCount++;
        return false;
    }

    public void close() {}

    public long getWorldTime() {
        if (!this.isPaused && !this.timeLocked) {
            var newUpdateTime = System.currentTimeMillis();
            this.currentWorldTime += (newUpdateTime - lastUpdateTime);
            this.lastUpdateTime = newUpdateTime;
        }

        return this.currentWorldTime;
    }

    public int getGameTime() {
        return (int) (getTotalGameTimeMinutes() % 1440);
    }

    public int getGameTimeHours() {
        return this.getGameTime() / 60;
    }

    public long getTotalGameTimeDays() {
        return ConversionUtils.gameTimeToDays(getTotalGameTimeMinutes());
    }

    public long getTotalGameTimeHours() {
        return ConversionUtils.gameTimeToHours(getTotalGameTimeMinutes());
    }

    public long getTotalGameTimeMinutes() {
        return this.getWorldTime() / 1000;
    }

    public void setPaused(boolean paused) {
        if (this.isMultiplayer) return;

        this.getWorldTime();
        this.updateTime();

        if (this.isPaused != paused && !paused) {
            this.lastUpdateTime = System.currentTimeMillis();
        }

        this.isPaused = paused;
        this.getPlayers().forEach(player -> player.setPaused(paused));
        this.getScenes().forEach((key, scene) -> scene.setPaused(paused));
    }

    public void changeTime(long gameTime) {
        this.currentWorldTime = gameTime;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public void changeTime(int time, int days) {
        if (this.timeLocked) return;

        var currentTime = this.getGameTime();
        var diff = time - currentTime;
        if (diff < 0) diff = 1440 + diff;

        this.currentWorldTime += days * 1440 * 1000L + diff * 1000L;

        this.host.updatePlayerGameTime(currentWorldTime);
        this.players.forEach(
                player -> player.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_GAME_TIME_TICK));
    }

    public void updateTime() {
        this.getPlayers().forEach(p -> p.sendPacket(new PacketPlayerGameTimeNotify(p)));
        this.getPlayers().forEach(p -> p.sendPacket(new PacketSceneTimeNotify(p)));
    }

    public void lockTime(boolean locked) {
        this.timeLocked = locked;

        this.updateTime();
        this.getPlayers()
                .forEach(player -> player.setProperty(PlayerProperty.PROP_IS_GAME_TIME_LOCKED, locked));
    }

    @NotNull @Override
    public Iterator<Player> iterator() {
        return this.getPlayers().iterator();
    }
}
