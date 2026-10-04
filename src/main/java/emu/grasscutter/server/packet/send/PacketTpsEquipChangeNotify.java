package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SceneWeaponInfoOuterClass.SceneWeaponInfo;
import emu.grasscutter.net.proto.TpsEquipChangeNotifyOuterClass.TpsEquipChangeNotify;
import java.util.List;

public class PacketTpsEquipChangeNotify extends BasePacket {
    public PacketTpsEquipChangeNotify(Avatar avatar, List<SceneWeaponInfo> weapons) {
        super(PacketOpcodes.TpsEquipChangeNotify);

        this.setData(
                TpsEquipChangeNotify.newBuilder()
                        .setAvatarGuid(avatar.getGuid())
                        .addAllTpsWeaponList(weapons));
    }
}
