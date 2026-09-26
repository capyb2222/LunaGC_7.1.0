package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.AllWidgetDataNotifyOuterClass.AllWidgetDataNotify;
import java.util.List;

public class PacketAllWidgetDataNotify extends BasePacket {

    public PacketAllWidgetDataNotify(Player player) {
        super(PacketOpcodes.AllWidgetDataNotify);


        AllWidgetDataNotify.Builder proto =
                AllWidgetDataNotify.newBuilder()
                        .setLunchBoxData(LunchBoxDataOuterClass.LunchBoxData.newBuilder().build())
                        .addAllOneoffGatherPointDetectorDataList(List.of())
                        .addAllCoolDownGroupDataList(List.of())
                        .addAllAnchorPointList(List.of())
                        .addAllClientCollectorDataList(List.of())
                        .addAllNormalCoolDownDataList(List.of());

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

        AllWidgetDataNotify protoData = proto.build();

        this.setData(protoData);
    }
}
