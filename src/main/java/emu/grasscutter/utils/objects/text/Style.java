package emu.grasscutter.utils.objects.text;

import java.awt.*;
import java.util.*;
import lombok.*;

@Builder
@Data
public final class Style {
    private static final Map<Character, String> unity = new HashMap<>();
    private static final Map<Character, String> ansi = new HashMap<>();

    static {
        unity.put('0', "#000000");
        unity.put('1', "#0000AA");
        unity.put('2', "#00AA00");
        unity.put('3', "#00AAAA");
        unity.put('4', "#AA0000");
        unity.put('5', "#AA00AA");
        unity.put('6', "#FFAA00");
        unity.put('7', "#AAAAAA");
        unity.put('8', "#555555");
        unity.put('9', "#5555FF");
        unity.put('a', "#55FF55");
        unity.put('b', "#55FFFF");
        unity.put('c', "#FF5555");
        unity.put('d', "#FF55FF");
        unity.put('e', "#FFFF55");
        unity.put('f', "#FFFFFF");

        ansi.put('0', "\u001B[30m");
        ansi.put('1', "\u001B[34m");
        ansi.put('2', "\u001B[32m");
        ansi.put('3', "\u001B[36m");
        ansi.put('4', "\u001B[31m");
        ansi.put('5', "\u001B[35m");
        ansi.put('6', "\u001B[33m");
        ansi.put('7', "\u001B[37m");
        ansi.put('8', "\u001B[90m");
        ansi.put('9', "\u001B[94m");
        ansi.put('a', "\u001B[92m");
        ansi.put('b', "\u001B[96m");
        ansi.put('c', "\u001B[91m");
        ansi.put('d', "\u001B[95m");
        ansi.put('e', "\u001B[93m");
        ansi.put('f', "\u001B[97m");
    }

    @Builder.Default private int size = -1;
    @Builder.Default private boolean bold = false;
    @Builder.Default private boolean italic = false;

    @Builder.Default private Color color = null;

    private String replaceUnity(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        var output = new StringBuilder();
        var i = 0;
        while (i < input.length()) {
            var c = input.charAt(i);
            if (c == '&') {
                if (i + 1 < input.length() && unity.containsKey(input.charAt(i + 1))) {
                    output.append("<color=").append(unity.get(input.charAt(i + 1))).append(">");

                    i += 2;

                    var end = input.indexOf('&', i);
                    if (end == -1) {
                        end = input.length();
                    }

                    output.append(input, i, end);

                    output.append("</color>");

                    i = end;
                } else {
                    output.append(c);
                    i++;
                }
            } else {
                output.append(c);
                i++;
            }
        }

        return output.toString();
    }

    private String replaceTerminal(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        var output = new StringBuilder();
        var i = 0;
        while (i < input.length()) {
            var c = input.charAt(i);
            if (c == '&') {
                if (i + 1 < input.length() && ansi.containsKey(input.charAt(i + 1))) {
                    output.append(ansi.get(input.charAt(i + 1)));

                    i += 2;

                    var end = input.indexOf('&', i);
                    if (end == -1) {
                        end = input.length();
                    }

                    output.append(input, i, end);

                    output.append("\u001B[0m");

                    i = end;
                } else {
                    output.append(c);
                    i++;
                }
            } else {
                output.append(c);
                i++;
            }
        }

        return output.toString();
    }

    public String toUnity(String text) {
        var builder = new StringBuilder();

        if (this.size != -1) {
            builder.append("<size=").append(this.size).append(">");
        }

        if (this.color != null) {
            builder
                    .append("<color=")
                    .append(
                            String.format(
                                    "#%02x%02x%02x",
                                    this.color.getRed(), this.color.getGreen(), this.color.getBlue()))
                    .append(">");
        }

        if (this.bold) builder.append("<b>");
        if (this.italic) builder.append("<i>");

        builder.append(this.replaceUnity(text));

        if (this.italic) builder.append("</i>");
        if (this.bold) builder.append("</b>");
        if (this.color != null) builder.append("</color>");
        if (this.size != -1) builder.append("</size>");

        return builder.toString();
    }

    public String toTerminal(String text) {
        if (this.color == null) return this.replaceTerminal(text);

        var ansiColor =
                this.color.getRed() > 127
                        ? this.color.getGreen() > 127
                                ? this.color.getBlue() > 127 ? 15 : 11
                                : this.color.getBlue() > 127 ? 13 : 9
                        : this.color.getGreen() > 127
                                ? this.color.getBlue() > 127 ? 14 : 10
                                : this.color.getBlue() > 127 ? 12 : 8;

        return "\u001B[38;5;" + ansiColor + "m" + this.replaceTerminal(text) + "\u001B[0m";
    }
}
