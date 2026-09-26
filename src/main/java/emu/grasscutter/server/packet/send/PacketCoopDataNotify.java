package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.CoopChapterOuterClass;
import emu.grasscutter.net.proto.CoopDataNotifyOuterClass;
import emu.grasscutter.net.proto.CoopPointOuterClass;

public class PacketCoopDataNotify extends BasePacket {

    public PacketCoopDataNotify() {
        super(PacketOpcodes.CoopDataNotify);

        var proto = CoopDataNotifyOuterClass.CoopDataNotify.newBuilder();
        proto.setIsHaveProgress(false);

        GameData.getCoopChapterDataMap()
                .values()
                .forEach(
                        i -> {
                            var chapter = CoopChapterOuterClass.CoopChapter.newBuilder();
                            chapter.setId(i.getId());

                            chapter.setStateValue(3);

                            var point = CoopPointOuterClass.CoopPoint.newBuilder();
                            var pointList =
                                    GameData.getCoopPointDataMap().values().stream()
                                            .filter(
                                                    j -> j.getChapterId() == i.getId() && j.getType().equals("POINT_START"))
                                            .toList();

                            if (!pointList.isEmpty()) {
                                int pointId = pointList.get(0).getId();
                                point.setId(pointId);
                                chapter.addCoopPointList(point);
                            }

                            proto.addChapterList(chapter);
                        });

        this.setData(proto);
    }
}
