package emu.grasscutter.server.http.handlers;

import emu.grasscutter.server.http.Router;
import io.javalin.Javalin;
import io.javalin.http.Context;

public final class LogHandler implements Router {
    private static void log(Context ctx) {
        ctx.result("{\"code\":0}");
    }

    @Override
    public void applyRoutes(Javalin javalin) {
        javalin.post("/log", LogHandler::log);
        javalin.post("/crash/dataUpload", LogHandler::log);
    }
}
