package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TeamMoonPhaseChangeNotifyOuterClass.TeamMoonPhaseChangeNotify;

public final class PacketTeamMoonPhaseChangeNotify extends BasePacket {

    private static final int MOON_PHASE_TYPE = 10000;

    public PacketTeamMoonPhaseChangeNotify(int moonsignLevel) {
        super(PacketOpcodes.TeamMoonPhaseChangeNotify);

        this.setData(
                TeamMoonPhaseChangeNotify.newBuilder()
                        .setMoonPhaseType(MOON_PHASE_TYPE)
                        .setMoonPhaseLevel(moonsignLevel));
    }
}
