package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.OpenStateData;
import emu.grasscutter.game.player.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.OpenStateUpdateNotifyOuterClass.OpenStateUpdateNotify;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

public class PacketOpenStateUpdateNotify extends BasePacket {

    public PacketOpenStateUpdateNotify(Player player) {
        super(PacketOpcodes.OpenStateUpdateNotify);

        OpenStateUpdateNotify.Builder proto = OpenStateUpdateNotify.newBuilder();

        GameData.getOpenStateList().stream().map(OpenStateData::getId).forEach(id -> {
            if ((id == 45) && !GAME_OPTIONS.resinOptions.resinUsage) {
                proto.putOpenStateMap(45, 0);
                return;
            }
            if (player.getOpenStates().containsKey(id)) {
                proto.putOpenStateMap(id, player.getProgressManager().getOpenState(id));
            }
            else if (PlayerProgressManager.DEFAULT_OPEN_STATES.contains(id)) {
                proto.putOpenStateMap(id, 1);
            }
        });

        this.setData(proto);
    }
}
