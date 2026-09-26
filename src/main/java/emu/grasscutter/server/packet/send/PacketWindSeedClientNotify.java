package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.utils.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PacketWindSeedClientNotify extends BasePacket {
    static int cmdId() {
        int configured = Configuration.GAME_OPTIONS.watermark.cmdId;
        return configured > 0 ? configured : PacketOpcodes.WindSeedType1Notify;
    }

    private static final int DEFAULT_PAYLOAD_FIELD =
            emu.grasscutter.net.proto.WindSeedType1NotifyOuterClass.WindSeedType1Notify.getDescriptor()
                    .findFieldByName("_payload")
                    .getNumber();

    public static boolean disabled() {
        return Configuration.GAME_OPTIONS.watermark.cmdId < 0;
    }

    static byte[] encode(byte[] luac) {
        return encode(luac, Configuration.GAME_OPTIONS.watermark.payloadField);
    }

    private static final int AREA_NOTIFY_FIELD = 1;

    static byte[] encode(byte[] luac, int field) {
        if (field <= 0) field = DEFAULT_PAYLOAD_FIELD;

        var bos = new ByteArrayOutputStream();
        var out = CodedOutputStream.newInstance(bos);
        try {
            out.writeByteArray(field, luac);
            out.flush();
        } catch (IOException e) {
            Grasscutter.getLogger().error("Failed to encode the wind seed payload.", e);
        }
        return bos.toByteArray();
    }

    public PacketWindSeedClientNotify(String givenPath) {
        super(cmdId());
        final Path path = Paths.get(givenPath, new String[0]);
        byte[] data;
        try {
            data = Files.readAllBytes(path);
        } catch (Exception e) {
            data = FileUtils.readResource("/lua/UID.luac");
        }

        this.setData(encode(data));
    }

    public PacketWindSeedClientNotify(byte[] data) {
        super(cmdId());
        this.setData(encode(data));
    }
}
