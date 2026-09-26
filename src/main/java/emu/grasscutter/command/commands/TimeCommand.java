package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.World;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

@Command(
        label = "time",
        usage = {"set <HH:mm> [animate] [seconds]", "step <HH:mm> [animate] [seconds]", "get"},
        permission = "player.time",
        permissionTargeted = "player.time.others")
public final class TimeCommand implements CommandHandler {

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args.size() < 1) {
            this.sendUsageMessage(sender);
            return;
        }
        if (targetPlayer == null) {
            CommandHandler.sendMessage(sender, "请指定目标玩家 @UID。");
            return;
        }

        switch (args.get(0).toLowerCase()) {
            case "get" -> {
                int minutes = targetPlayer.getWorld().getGameTime();
                CommandHandler.sendMessage(sender, "当前游戏内时间: " + formatTime(minutes) + "。");
            }
            case "set" -> {
                if (args.size() < 2) {
                    this.sendUsageMessage(sender);
                    return;
                }
                int[] hm = parseTime(args.get(1));
                if (hm == null) {
                    CommandHandler.sendMessage(sender, "时间格式错误，应为 HH:mm（如 10:00）。");
                    return;
                }
                boolean animate = args.size() >= 3 && parseBool(args.get(2));
                int seconds = args.size() >= 4 ? parseIntOrDefault(args.get(3), 5) : 5;

                int target = hm[0] * 60 + hm[1];
                applyTime(sender, targetPlayer, target, false, animate, seconds);
            }
            case "step" -> {
                if (args.size() < 2) {
                    this.sendUsageMessage(sender);
                    return;
                }
                int[] hm = parseTime(args.get(1));
                if (hm == null) {
                    CommandHandler.sendMessage(sender, "时长格式错误，应为 HH:mm（如 00:10 或 10:00）。");
                    return;
                }
                int step = hm[0] * 60 + hm[1];
                if (step == 0) {
                    CommandHandler.sendMessage(sender, "步进时长为 0，未做修改。");
                    return;
                }
                boolean animate = args.size() >= 3 && parseBool(args.get(2));
                int seconds = args.size() >= 4 ? parseIntOrDefault(args.get(3), 5) : 5;

                World world = targetPlayer.getWorld();
                int target = (world.getGameTime() + step) % 1440;
                applyTime(sender, targetPlayer, target, true, animate, seconds);
            }
            default -> this.sendUsageMessage(sender);
        }
    }

    private void applyTime(
            Player sender, Player targetPlayer, int targetMinute, boolean isStep, boolean animate, int seconds) {
        World world = targetPlayer.getWorld();
        int startMinute = world.getGameTime();
        int diff = targetMinute - startMinute;
        if (diff < 0) diff += 1440;

        if (!animate || diff == 0 || seconds <= 0) {
            world.changeTime(targetMinute, 0);
            world.updateTime();
            CommandHandler.sendMessage(
                    sender,
                    isStep
                            ? "时间已步进到 " + formatTime(targetMinute) + "。"
                            : "时间已设置为 " + formatTime(targetMinute) + "。");
            return;
        }

        long startMs = world.getWorldTime();
        long targetMs = startMs + diff * 1000L;
        int totalFrames = Math.max(1, seconds * 50);
        Timer timer = new Timer("time-command-anim", true);
        final long[] frame = {0};
        timer.scheduleAtFixedRate(
                new TimerTask() {
                    @Override
                    public void run() {
                        frame[0]++;
                        synchronized (world) {
                            if (frame[0] >= totalFrames) {
                                world.changeTime(targetMs);
                                timer.cancel();
                            } else {
                                world.changeTime(startMs + (targetMs - startMs) * frame[0] / totalFrames);
                            }
                            world.updateTime();
                        }
                    }
                },
                20,
                20);
        CommandHandler.sendMessage(
                sender,
                (isStep ? "时间正在平滑步进到 " : "时间正在平滑过渡到 ")
                        + formatTime(targetMinute)
                        + "（"
                        + seconds
                        + " 秒动画）。");
    }

    private static String formatTime(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    private static int[] parseTime(String s) {
        String[] parts = s.split(":");
        if (parts.length < 2) return null;
        try {
            int h = Integer.parseInt(parts[0].trim());
            int m = Integer.parseInt(parts[1].trim());
            if (h < 0 || h > 23 || m < 0 || m > 59) return null;
            return new int[] {h, m};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean parseBool(String s) {
        return s != null
                && (s.equalsIgnoreCase("true")
                        || s.equalsIgnoreCase("yes")
                        || s.equals("1")
                        || s.equalsIgnoreCase("on"));
    }

    private static int parseIntOrDefault(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
