package emu.grasscutter.plugin.api;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.auth.AuthenticationSystem;
import emu.grasscutter.command.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.http.*;
import emu.grasscutter.server.scheduler.ServerTaskScheduler;
import java.util.*;
import java.util.stream.Stream;

public final class ServerHelper {
    private static ServerHelper instance;
    private final GameServer gameServer;
    private final HttpServer httpServer;

    public ServerHelper(GameServer gameServer, HttpServer httpServer) {
        this.gameServer = gameServer;
        this.httpServer = httpServer;

        instance = this;
    }

    public static ServerHelper getInstance() {
        return instance;
    }

    public Grasscutter.ServerRunMode getRunMode() {
        return Grasscutter.getRunMode();
    }

    public GameServer getGameServer() {
        return this.gameServer;
    }

    public HttpServer getHttpServer() {
        return this.httpServer;
    }

    public List<Player> getOnlinePlayers() {
        return new ArrayList<>(this.gameServer.getPlayers().values());
    }

    public Stream<Player> getOnlinePlayersStream() {
        return this.gameServer.getPlayers().values().stream();
    }

    public void registerCommand(CommandHandler handler) {
        Class<? extends CommandHandler> clazz = handler.getClass();
        if (!clazz.isAnnotationPresent(Command.class))
            throw new IllegalArgumentException("Command handler must be annotated with @Command.");
        Command commandData = clazz.getAnnotation(Command.class);
        CommandMap.getInstance().registerCommand(commandData.label(), handler);
    }

    public void addRouter(Router router) {
        this.addRouter(router.getClass());
    }

    public void addRouter(Class<? extends Router> router) {
        this.httpServer.addRouter(router);
    }

    public void setAuthSystem(AuthenticationSystem authSystem) {
        Grasscutter.setAuthenticationSystem(authSystem);
    }

    public void setPermissionHandler(PermissionHandler permHandler) {
        Grasscutter.setPermissionHandler(permHandler);
    }

    public ServerTaskScheduler getScheduler() {
        if (this.getGameServer() == null) return null;

        return this.getGameServer().getScheduler();
    }
}
