package emu.grasscutter.game.ability;

import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.world.Position;
import java.util.*;
import java.util.regex.Pattern;

public final class AbilityTargetSelector {
    private static final Pattern RADIUS = Pattern.compile("R(\\d+(?:\\.\\d+)?)");

    private static final Pattern HEIGHT = Pattern.compile("H(\\d+(?:\\.\\d+)?)");

    private AbilityTargetSelector() {}

    public static List<GameEntity> select(
            Map<String, Object> otherTargets, Ability ability, GameEntity self) {
        if (otherTargets == null || self == null) return null;
        if (!"SelectTargetsByShape".equals(otherTargets.get("$type"))) return null;

        var scene = self.getScene();
        if (scene == null) return null;

        var wanted = entityTypes(otherTargets);
        var shape = String.valueOf(otherTargets.getOrDefault("shapeName", ""));
        float radius = dimension(RADIUS, shape);
        float height = dimension(HEIGHT, shape);
        var camp = String.valueOf(otherTargets.getOrDefault("campTargetType", "Enemy"));
        var origin = self.getPosition();

        var found = new ArrayList<GameEntity>();
        for (var entity : scene.getEntities().values()) {
            if (entity == self || !entity.isAlive()) continue;
            if (!wanted.isEmpty() && !isOfType(entity, wanted)) continue;
            if (!matchesCamp(entity, camp, ability)) continue;
            if (radius > 0 && horizontalDistance(origin, entity.getPosition()) > radius) continue;
            if (height > 0 && Math.abs(origin.getY() - entity.getPosition().getY()) > height) continue;

            found.add(entity);
        }

        if ("Nearest".equals(otherTargets.get("sortType"))) {
            found.sort(Comparator.comparingDouble(e -> origin.computeDistance(e.getPosition())));
        }

        int limit = asInt(otherTargets.get("topLimit"));
        return limit > 0 && found.size() > limit ? new ArrayList<>(found.subList(0, limit)) : found;
    }

    private static Set<String> entityTypes(Map<String, Object> otherTargets) {
        var names = new HashSet<String>();
        for (var key : new String[] {"entityTypes", "entityTypePriority"}) {
            if (otherTargets.get(key) instanceof List<?> list) {
                list.forEach(name -> names.add(String.valueOf(name)));
            }
        }
        return names;
    }

    private static boolean isOfType(GameEntity entity, Set<String> wanted) {
        for (var name : wanted) {
            var matches =
                    switch (name) {
                        case "Monster" -> entity instanceof EntityMonster;
                        case "Gadget" -> entity instanceof EntityBaseGadget;
                        case "Avatar", "RemoteAvatar" -> entity instanceof EntityAvatar;
                        case "Vehicle" -> entity instanceof EntityVehicle;
                        default -> false;
                    };
            if (matches) return true;
        }
        return false;
    }

    private static boolean matchesCamp(GameEntity entity, String camp, Ability ability) {
        var player = ability != null ? ability.getPlayerOwner() : null;
        boolean own =
                entity instanceof EntityAvatar avatar && avatar.getPlayer() == player
                        || entity instanceof EntityClientGadget gadget && gadget.getOwner() == player;

        return switch (camp) {
            case "Enemy" -> !own;
            case "Alliance", "SelfCamp" -> own;
            default -> true;
        };
    }

    private static float horizontalDistance(Position a, Position b) {
        var dx = a.getX() - b.getX();
        var dz = a.getZ() - b.getZ();
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private static float dimension(Pattern pattern, String shapeName) {
        var matcher = pattern.matcher(shapeName);
        return matcher.find() ? Float.parseFloat(matcher.group(1)) : 0f;
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
