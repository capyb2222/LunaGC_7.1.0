package emu.grasscutter.server.http.handlers;

import static emu.grasscutter.config.Configuration.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.DataLoader;
import emu.grasscutter.server.http.Router;
import emu.grasscutter.server.http.objects.HttpJsonResponse;
import emu.grasscutter.utils.FileUtils;
import io.javalin.Javalin;
import io.javalin.http.*;
import java.io.*;
import java.util.*;

public final class AnnouncementsHandler implements Router {
    private static void getAnnouncement(Context ctx) {
        String data = "";
        if (Objects.equals(
                ctx.endpointHandlerPath(), "/common/hk4e_global/announcement/api/getAnnContent")) {
            try {
                data = FileUtils.readToString(DataLoader.load("GameAnnouncement.json"));
            } catch (Exception e) {
                if (e.getClass() == IOException.class) {
                    Grasscutter.getLogger().info("Unable to read file 'GameAnnouncementList.json'. \n" + e);
                }
            }
        } else if (Objects.equals(
                ctx.endpointHandlerPath(), "/common/hk4e_global/announcement/api/getAnnList")) {
            try {
                data = FileUtils.readToString(DataLoader.load("GameAnnouncementList.json"));
            } catch (Exception e) {
                if (e.getClass() == IOException.class) {
                    Grasscutter.getLogger().info("Unable to read file 'GameAnnouncementList.json'. \n" + e);
                }
            }
        } else {
            ctx.result("{\"retcode\":404,\"message\":\"Unknown request path\"}");
        }

        if (data.isEmpty()) {
            ctx.result("{\"retcode\":500,\"message\":\"Unable to fetch requsted content\"}");
            return;
        }

        String dispatchDomain =
                "http"
                        + (HTTP_ENCRYPTION.useInRouting ? "s" : "")
                        + "://"
                        + lr(HTTP_INFO.accessAddress, HTTP_INFO.bindAddress)
                        + ":"
                        + lr(HTTP_INFO.accessPort, HTTP_INFO.bindPort);

        data =
                data.replace("{{DISPATCH_PUBLIC}}", dispatchDomain)
                        .replace("{{SYSTEM_TIME}}", String.valueOf(System.currentTimeMillis()));
        ctx.result("{\"retcode\":0,\"message\":\"OK\",\"data\": " + data + "}");
    }

    private static void getPageResources(Context ctx) {
        String[] path = ctx.path().split("/");
        StringJoiner stringJoiner = new StringJoiner("/");
        for (String pathName : path) {
            if (!pathName.isEmpty() && !pathName.equals("..") && !pathName.contains("\\")) {
                stringJoiner.add(pathName);
            }
        }
        try (InputStream filestream = DataLoader.load(stringJoiner.toString())) {
            String possibleFilename = ctx.path();

            ContentType fromExtension =
                    ContentType.getContentTypeByExtension(
                            possibleFilename.substring(possibleFilename.lastIndexOf(".") + 1));
            ctx.contentType(fromExtension != null ? fromExtension : ContentType.APPLICATION_OCTET_STREAM);
            ctx.result(filestream.readAllBytes());
        } catch (Exception e) {
            Grasscutter.getLogger().warn("File does not exist: " + ctx.path());
            ctx.status(404);
        }
    }

    @Override
    public void applyRoutes(Javalin javalin) {
        this.allRoutes(
                javalin,
                "/common/hk4e_global/announcement/api/getAlertPic",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"total\":0,\"list\":[]}}"));
        this.allRoutes(
                javalin,
                "/common/hk4e_global/announcement/api/getAlertAnn",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"alert\":false,\"alert_id\":0,\"remind\":true}}"));
        this.allRoutes(
                javalin,
                "/common/hk4e_global/announcement/api/getAnnList",
                AnnouncementsHandler::getAnnouncement);
        this.allRoutes(
                javalin,
                "/common/hk4e_global/announcement/api/getAnnContent",
                AnnouncementsHandler::getAnnouncement);
        this.allRoutes(
                javalin,
                "/hk4e_global/mdk/shopwindow/shopwindow/listPriceTier",
                new HttpJsonResponse(
                        "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"suggest_currency\":\"USD\",\"tiers\":[]}}"));

        javalin.get("/hk4e/announcement/*", AnnouncementsHandler::getPageResources);
    }
}
