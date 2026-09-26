package emu.grasscutter.utils;

import static emu.grasscutter.config.Configuration.*;

import ch.qos.logback.classic.*;
import emu.grasscutter.*;
import emu.grasscutter.net.packet.PacketOpcodesUtils;
import emu.grasscutter.tools.Dumpers;
import java.util.*;
import java.util.function.Function;
import org.slf4j.LoggerFactory;

public interface StartupArguments {
    Map<String, Function<String, Boolean>> argumentHandlers =
            new HashMap<>() {
                {
                    putAll(
                            Map.of(
                                    "-dumppacketids",
                                    parameter -> {
                                        PacketOpcodesUtils.dumpPacketIds();
                                        return true;
                                    },
                                    "-version",
                                    StartupArguments::printVersion,
                                    "-debug",
                                    StartupArguments::enableDebug,
                                    "-lang",
                                    parameter -> {
                                        Grasscutter.setPreferredLanguage(parameter);
                                        return false;
                                    },
                                    "-game",
                                    parameter -> {
                                        Grasscutter.setRunModeOverride(Grasscutter.ServerRunMode.GAME_ONLY);
                                        return false;
                                    },
                                    "-dispatch",
                                    parameter -> {
                                        Grasscutter.setRunModeOverride(Grasscutter.ServerRunMode.DISPATCH_ONLY);
                                        return false;
                                    },
                                    "-noconsole",
                                    parameter -> {
                                        Grasscutter.setNoConsole(true);
                                        return false;
                                    },
                                    "-test",
                                    parameter -> {
                                        SERVER.game.enableConsole = false;
                                        SERVER.http.encryption.useEncryption = false;
                                        return false;
                                    },
                                    "-dump",
                                    StartupArguments::dump,

                                    "-v",
                                    StartupArguments::printVersion));
                    putAll(
                            Map.of(
                                    "-debugall",
                                    parameter -> {
                                        StartupArguments.enableDebug("all");
                                        return false;
                                    }));
                }
            };

    static boolean parse(String[] args) {
        boolean exitEarly = false;

        for (var input : args) {
            var containsParameter = input.contains("=");

            var argument = containsParameter ? input.split("=")[0] : input;
            var handler = argumentHandlers.get(argument.toLowerCase());

            if (handler != null) {
                exitEarly |= handler.apply(containsParameter ? input.split("=")[1] : null);
            }
        }

        return exitEarly;
    }

    private static boolean printVersion(String parameter) {
        System.out.println("Grasscutter version: " + BuildConfig.VERSION + "-" + BuildConfig.GIT_HASH);
        return true;
    }

    private static boolean enableDebug(String parameter) {
        if (parameter != null && parameter.equals("all")) {
            GAME_INFO.isShowLoopPackets = DEBUG_MODE_INFO.isShowLoopPackets;
            GAME_INFO.isShowPacketPayload = DEBUG_MODE_INFO.isShowPacketPayload;
            GAME_INFO.logPackets = DEBUG_MODE_INFO.logPackets;
            DISPATCH_INFO.logRequests = DEBUG_MODE_INFO.logRequests;

            Level loggerLevel = DEBUG_MODE_INFO.servicesLoggersLevel;
            ((Logger) LoggerFactory.getLogger("io.javalin")).setLevel(loggerLevel);
            ((Logger) LoggerFactory.getLogger("org.quartz")).setLevel(loggerLevel);
            ((Logger) LoggerFactory.getLogger("org.reflections")).setLevel(loggerLevel);
            ((Logger) LoggerFactory.getLogger("org.eclipse.jetty")).setLevel(loggerLevel);
            ((Logger) LoggerFactory.getLogger("org.mongodb.driver")).setLevel(loggerLevel);
        }

        Grasscutter.getLogger().setLevel(DEBUG_MODE_INFO.serverLoggerLevel);
        Grasscutter.getLogger().debug("The logger is now running in debug mode.");
        GameConstants.DEBUG = true;
        return false;
    }

    private static boolean dump(String parameter) {
        if (!parameter.contains(",")) {
            Grasscutter.getLogger().error("Dumper usage: -dump=<content>,<language>");
            return true;
        }

        var split = parameter.split(",");
        var content = split[0];
        var language = split[1];

        try {
            switch (content.toLowerCase()) {
                case "commands" -> Dumpers.dumpCommands(language);
                case "avatars" -> Dumpers.dumpAvatars(language);
                case "items" -> Dumpers.dumpItems(language);
                case "scenes" -> Dumpers.dumpScenes();
                case "entities" -> Dumpers.dumpEntities(language);
                case "quests" -> Dumpers.dumpQuests(language);
                case "areas" -> Dumpers.dumpAreas(language);
            }

            Grasscutter.getLogger().info("Finished dumping.");
        } catch (Exception exception) {
            Grasscutter.getLogger().error("Unable to complete dump.", exception);
        }

        return true;
    }
}
