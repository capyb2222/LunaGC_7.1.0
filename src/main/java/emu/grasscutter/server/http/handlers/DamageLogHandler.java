package emu.grasscutter.server.http.handlers;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.http.Router;
import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.http.Context;

public final class DamageLogHandler implements Router {
    private static Player findPlayer(Context ctx) {
        var server = Grasscutter.getGameServer();
        if (server == null) return null;

        var uid = ctx.queryParam("uid");
        if (uid != null) {
            try {
                return server.getPlayerByUid(Integer.parseInt(uid));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        var players = server.getPlayers().values();
        String ip = ctx.ip();
        for (Player player : players) {
            var address = player.getSession() == null ? null : player.getSession().getAddress();
            if (address != null
                    && address.getAddress() != null
                    && address.getAddress().getHostAddress().equals(ip)) {
                return player;
            }
        }
        return players.size() == 1 ? players.iterator().next() : null;
    }

    private static void get(Context ctx) {
        Player player = findPlayer(ctx);
        ctx.contentType(ContentType.APPLICATION_JSON);
        if (player == null || player.getWorld() == null) {
            ctx.result("{\"online\":false}");
            return;
        }

        var json = player.getWorld().getDamageLog().toJson();
        json.addProperty("online", true);
        json.addProperty("uid", player.getUid());
        ctx.result(json.toString());
    }

    private static void reset(Context ctx) {
        Player player = findPlayer(ctx);
        if (player != null && player.getWorld() != null) {
            player.getWorld().getDamageLog().reset();
        }
        ctx.contentType(ContentType.APPLICATION_JSON);
        ctx.result("{\"code\":0}");
    }

    @Override
    public void applyRoutes(Javalin javalin) {
        javalin.get("/lunagc/damagelog", DamageLogHandler::get);
        javalin.post("/lunagc/damagelog/reset", DamageLogHandler::reset);
    }
}
