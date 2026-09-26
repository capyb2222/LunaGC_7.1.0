package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketSceneAreaUnlockNotify;
import emu.grasscutter.server.packet.send.PacketScenePointUnlockNotify;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

@Command(
        label = "waypoints",
        aliases = {"wp", "unlockwp"},
        usage = {
            "",
            "<area id> ...",
            "list"
        },
        permission = "player.waypoints",
        permissionTargeted = "player.waypoints.others")
public final class WaypointsCommand implements CommandHandler {

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        int sceneId = targetPlayer.getSceneId();

        var waypoints = new TreeMap<Integer, Integer>();
        var pointIds = GameData.getScenePointsPerScene().get(sceneId);
        if (pointIds != null) {
            for (var pointId : pointIds) {
                var entry = GameData.getScenePointEntryById(sceneId, pointId);
                if (entry == null) continue;
                var data = entry.getPointData();
                if (data == null || data.isForbidSimpleUnlock()) continue;
                if (!"TransPointNormal".equals(data.getType())) continue;
                waypoints.put(pointId, data.getAreaId());
            }
        }

        if (waypoints.isEmpty()) {
            CommandHandler.sendMessage(
                    sender, "No waypoints are known for scene " + sceneId + ".");
            return;
        }

        if (!args.isEmpty() && args.get(0).equalsIgnoreCase("list")) {
            this.list(sender, sceneId, waypoints, targetPlayer);
            return;
        }

        Set<Integer> wanted = null;
        if (!args.isEmpty()) {
            wanted = new HashSet<>();
            for (var arg : args) {
                try {
                    wanted.add(Integer.parseInt(arg));
                } catch (NumberFormatException e) {
                    CommandHandler.sendMessage(sender, "Not an area id: " + arg);
                    return;
                }
            }
        }

        var points = new ArrayList<Integer>();
        var areas = new HashSet<Integer>();
        for (var e : waypoints.entrySet()) {
            if (wanted != null && !wanted.contains(e.getValue())) continue;
            points.add(e.getKey());
            if (e.getValue() != 0) areas.add(e.getValue());
        }

        if (points.isEmpty()) {
            CommandHandler.sendMessage(sender, "No waypoints matched those areas. Try /waypoints list");
            return;
        }

        var alreadyUnlocked = targetPlayer.getUnlockedScenePoints(sceneId);
        int fresh = (int) points.stream().filter(p -> !alreadyUnlocked.contains(p)).count();

        alreadyUnlocked.addAll(points);
        for (int area = 1; area < 1000; area++) areas.add(area);
        targetPlayer.getUnlockedSceneAreas(sceneId).addAll(areas);
        targetPlayer.save();

        targetPlayer.sendPacket(new PacketSceneAreaUnlockNotify(
                sceneId, targetPlayer.getUnlockedSceneAreas(sceneId)));
        targetPlayer.sendPacket(new PacketScenePointUnlockNotify(sceneId, alreadyUnlocked));

        CommandHandler.sendMessage(
                sender,
                "Unlocked "
                        + points.size()
                        + " waypoint(s) in scene "
                        + sceneId
                        + " ("
                        + fresh
                        + " new), and opened every area of the scene."
                        + " If a region is still walled off, try /tag unlockall - the barrier at a"
                        + " region boundary is usually a scene tag rather than an area lock.");
    }

    private void list(
            Player sender, int sceneId, TreeMap<Integer, Integer> waypoints, Player targetPlayer) {
        var perArea = new TreeMap<Integer, int[]>();
        var unlocked = targetPlayer.getUnlockedScenePoints(sceneId);
        for (var e : waypoints.entrySet()) {
            var counts = perArea.computeIfAbsent(e.getValue(), k -> new int[2]);
            counts[0]++;
            if (unlocked.contains(e.getKey())) counts[1]++;
        }

        var sb = new StringBuilder("Waypoints in scene " + sceneId + " by area (unlocked/total):");
        perArea.forEach(
                (area, counts) ->
                        sb.append("\n  area ")
                                .append(area)
                                .append(": ")
                                .append(counts[1])
                                .append("/")
                                .append(counts[0]));
        CommandHandler.sendMessage(sender, sb.toString());
    }
}
