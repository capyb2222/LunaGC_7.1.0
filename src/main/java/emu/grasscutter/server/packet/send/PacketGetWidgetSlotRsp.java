package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;
import java.util.List;

public class PacketGetWidgetSlotRsp extends BasePacket {

    public PacketGetWidgetSlotRsp(Player player) {
        super(PacketOpcodes.GetWidgetSlotRsp);

        GetWidgetSlotRspOuterClass.GetWidgetSlotRsp.Builder proto =
                GetWidgetSlotRspOuterClass.GetWidgetSlotRsp.newBuilder();

        if (player.getWidgetId()
                == 0) {
            proto.addAllSlotList(List.of());
        } else {
            proto.addSlotList(
                    WidgetSlotDataOuterClass.WidgetSlotData.newBuilder()
                            .setIsActive(true)
                            .setMaterialId(player.getWidgetId())
                            .build());

            proto.addSlotList(
                    WidgetSlotDataOuterClass.WidgetSlotData.newBuilder()
                            .setTag(WidgetSlotTagOuterClass.WidgetSlotTag.WidgetSlotTag_WIDGET_SLOT_ATTACH_AVATAR)
                            .build());
        }

        GetWidgetSlotRspOuterClass.GetWidgetSlotRsp protoData = proto.build();

        this.setData(protoData);
    }
}
