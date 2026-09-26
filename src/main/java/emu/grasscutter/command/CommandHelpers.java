package emu.grasscutter.command;

import emu.grasscutter.game.world.Position;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.*;
import javax.annotation.Nonnull;

public class CommandHelpers {
    public static final Pattern lvlRegex =
            Pattern.compile("(?<!\\w)l(?:vl?)?(\\d+)");
    public static final Pattern amountRegex =
            Pattern.compile("((?<=(?<!\\w)x)\\d+|\\d+(?=x)(?!x\\d))");
    public static final Pattern refineRegex = Pattern.compile("(?<!\\w)r(\\d+)");
    public static final Pattern rankRegex = Pattern.compile("(\\d+)\\*");
    public static final Pattern constellationRegex = Pattern.compile("(?<!\\w)c(\\d+)");
    public static final Pattern skillLevelRegex = Pattern.compile("sl(\\d+)");
    public static final Pattern stateRegex = Pattern.compile("state(\\d+)");
    public static final Pattern blockRegex = Pattern.compile("blk(\\d+)");
    public static final Pattern groupRegex = Pattern.compile("grp(\\d+)");
    public static final Pattern configRegex = Pattern.compile("cfg(\\d+)");
    public static final Pattern hpRegex = Pattern.compile("(?<!\\w)hp(\\d+)");
    public static final Pattern maxHPRegex = Pattern.compile("maxhp(\\d+)");
    public static final Pattern atkRegex = Pattern.compile("atk(\\d+)");
    public static final Pattern defRegex = Pattern.compile("def(\\d+)");
    public static final Pattern aiRegex = Pattern.compile("ai(\\d+)");
    public static final Pattern sceneRegex = Pattern.compile("scene(\\d+)");
    public static final Pattern suiteRegex = Pattern.compile("suite(\\d+)");

    public static int matchIntOrNeg(Pattern pattern, String arg) {
        Matcher match = pattern.matcher(arg);
        if (match.find()) {
            return Integer.parseInt(
                    match.group(
                            1));
        }
        return -1;
    }

    public static <T> List<String> parseIntParameters(
            List<String> args, @Nonnull T params, Map<Pattern, BiConsumer<T, Integer>> map) {
        args.removeIf(
                arg -> {
                    var argL = arg.toLowerCase();
                    boolean deleteArg = false;
                    for (var entry : map.entrySet()) {
                        int argNum = matchIntOrNeg(entry.getKey(), argL);
                        if (argNum != -1) {
                            entry.getValue().accept(params, argNum);
                            deleteArg = true;
                        }
                    }
                    return deleteArg;
                });
        return args;
    }

    public static float parseRelative(String input, Float current) {
        if (input.contains("~")) {
            if (!input.equals("~")) {
                current += Float.parseFloat(input.replace("~", ""));
            }
        } else {
            current = Float.parseFloat(input);
        }
        return current;
    }

    public static Position parsePosition(
            String inputX, String inputY, String inputZ, Position curPos, Position curRot) {
        Position offset = new Position();
        Position target = new Position(curPos);
        if (inputX.contains("~")) {
            if (!inputX.equals("~")) {
                target.addX(Float.parseFloat(inputX.replace("~", "")));
            }
        } else if (inputX.contains("^")) {
            if (!inputX.equals("^")) {
                offset.setX(Float.parseFloat(inputX.replace("^", "")));
            }
        } else {
            target.setX(Float.parseFloat(inputX));
        }

        if (inputY.contains("~")) {
            if (!inputY.equals("~")) {
                target.addY(Float.parseFloat(inputY.replace("~", "")));
            }
        } else if (inputY.contains("^")) {
            if (!inputY.equals("^")) {
                offset.setY(Float.parseFloat(inputY.replace("^", "")));
            }
        } else {
            target.setY(Float.parseFloat(inputY));
        }

        if (inputZ.contains("~")) {
            if (!inputZ.equals("~")) {
                target.addZ(Float.parseFloat(inputZ.replace("~", "")));
            }
        } else if (inputZ.contains("^")) {
            if (!inputZ.equals("^")) {
                offset.setZ(Float.parseFloat(inputZ.replace("^", "")));
            }
        } else {
            target.setZ(Float.parseFloat(inputZ));
        }

        if (!offset.equal3d(Position.ZERO)) {
            return calculateOffset(target, curRot, offset);
        } else {
            return target;
        }
    }

    public static Position calculateOffset(Position pos, Position rot, Position offset) {
        float angleZ = (float) Math.toRadians(rot.getY());
        float angleX = (float) Math.toRadians(rot.getY() + 90);

        return new Position(
                pos.getX()
                        + offset.getZ() * (float) Math.sin(angleZ)
                        + offset.getX() * (float) Math.sin(angleX),
                pos.getY() + offset.getY(),
                pos.getZ()
                        + offset.getZ() * (float) Math.cos(angleZ)
                        + offset.getX() * (float) Math.cos(angleX));
    }
}
