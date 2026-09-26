package emu.grasscutter.net.packet;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
public @interface Opcodes {
    int value();

    boolean disabled() default false;
}
