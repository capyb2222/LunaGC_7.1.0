package emu.grasscutter.utils.objects.text;

import java.awt.*;
import lombok.*;

public final class Text {
    public static Text of(String text) {
        return new Text(text, false);
    }

    @Getter private final boolean raw;
    private final Style.StyleBuilder style = Style.builder();

    @Setter private String text;

    public Text(String text) {
        this.raw = false;
        this.text = text;
    }

    public Text(String text, boolean raw) {
        this.raw = raw;
        this.text = text;
    }

    public Text size(int size) {
        this.style.size(size);
        return this;
    }

    public Text color(Color color) {
        this.style.color(color);
        return this;
    }

    public Text bold(boolean bold) {
        this.style.bold(bold);
        return this;
    }

    public Text italic(boolean italic) {
        this.style.italic(italic);
        return this;
    }

    public String toString(boolean console) {
        var style = this.style.build();
        var text = this.text;

        return console ? style.toTerminal(text) : style.toUnity(text);
    }
}
